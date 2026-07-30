#!/usr/bin/env python3
"""Execute and attest the exact R01 27-test release matrix.

The R01 release manifest is authoritative and must be a one-to-one match with
the R01-applicable rows in the global test catalog.  Deterministic repository
checks run locally and in parallel.  Backend integration checks require
external evidence from Maven, PostgreSQL 17 and real HTTP API execution.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Mapping, Sequence

import yaml


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "releases/R01/RELEASE_MANIFEST.yaml"
CATALOG_PATH = ROOT / "catalogs/test_cases.csv"
EVIDENCE_SCHEMA = "hhy.r01.test-evidence/v1"
RESULT_SCHEMA = "hhy.r01.test-matrix-result/v1"
EXPECTED_COUNT = 27
SHA_PATTERN = re.compile(r"^[0-9a-f]{40,64}$", re.IGNORECASE)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$", re.IGNORECASE)

# Evidence is normally committed after the tested source.  Only release
# closure metadata may follow the tested commit; implementation and executable
# specification paths are deliberately absent from this allowlist.
POST_TEST_ALLOWED_EXACT = frozenset(
    {
        "CHANGELOG.md",
        "CURRENT_STATUS.yaml",
        "NEXT_TASK.yaml",
        ".continuity/ACTIVE_SESSION.yaml",
        ".continuity/CHANGE_REQUEST_INDEX.yaml",
        ".continuity/EVENT_LOG.jsonl",
        ".continuity/SESSION_INDEX.yaml",
        ".continuity/STATE.yaml",
        ".continuity/TASK_CLAIMS.yaml",
        ".continuity/TASK_TRANSITIONS.yaml",
        "catalogs/change_request_index.csv",
        "catalogs/session_index.csv",
        "catalogs/task_transition_ledger.csv",
        "catalogs/release_story_backlog.csv",
        "catalogs/release_plan.csv",
        "catalogs/release_artifact_index.csv",
        "releases/R01/ACCEPTANCE_MATRIX.csv",
        "releases/R01/DEFINITION_OF_READY.yaml",
        "releases/R01/RELEASE_MANIFEST.yaml",
        "releases/R01/STORIES.yaml",
        "releases/R01/TASKS.yaml",
    }
)
POST_TEST_ALLOWED_PREFIXES = (
    ".continuity/checkpoints/",
    ".continuity/sessions/",
    "artifacts/context/",
    "artifacts/validation/r01-test-evidence/",
    "artifacts/reports/R01/",
    "docs/03-continuity/sessions/",
)


@dataclass(frozen=True)
class Runner:
    key: str
    description: str
    commands: tuple[tuple[str, ...], ...]


@dataclass(frozen=True)
class Assignment:
    source: str
    runner: str


LOCAL_RUNNERS: dict[str, Runner] = {
    "documentation-r01": Runner(
        "documentation-r01",
        "strict R01 documentation, DoR and traceability contract",
        (("{python}", "scripts/check_v123_documentation.py", "--strict", "--release", "R01", "--json-out", "{temp_report}"),),
    ),
    "ui-token-contract": Runner(
        "ui-token-contract",
        "shared design token and component catalog gate",
        (("{python}", "scripts/check_ui_tokens.py"),),
    ),
    "api-db-contract": Runner(
        "api-db-contract",
        "OpenAPI/generated-client and database migration contract gates",
        (
            ("{python}", "scripts/check_api_contract.py"),
            ("{python}", "scripts/check_db_schema.py"),
        ),
    ),
    "admin-web-test-build": Runner(
        "admin-web-test-build",
        "administrator web unit tests, typecheck and production build",
        (
            ("{package_manager}", "{package_dir_flag}", "apps/admin-web", "test"),
            ("{package_manager}", "{package_dir_flag}", "apps/admin-web", "run", "build"),
        ),
    ),
}


def build_assignments() -> dict[str, Assignment]:
    """Return the closed, explicit execution map for all and only R01 tests."""

    local: dict[str, str] = {}
    for prefix in ("TST-ADMIN_OPS_001-", "TST-DOR_001-", "TST-PAGE_SPEC_001-"):
        for suffix in ("DRIFT", "HAPPY", "REJECT", "SECURITY"):
            local[prefix + suffix] = "documentation-r01"
    for suffix in ("HAPPY", "IDEMPOTENT", "REJECT"):
        local["TST-DESIGN_001-" + suffix] = "ui-token-contract"
    local["TST-ADMIN_SECURITY_001-DRIFT"] = "api-db-contract"
    for number in ("012", "013", "014", "056", "057", "058", "059", "060"):
        local["TST-V122-" + number] = "admin-web-test-build"

    external = {
        "TST-ADMIN_SECURITY_001-HAPPY": "r01-backend-maven-pg17-real-api",
        "TST-ADMIN_SECURITY_001-REJECT": "r01-backend-maven-pg17-real-api",
        "TST-ADMIN_SECURITY_001-SECURITY": "r01-backend-maven-pg17-real-api",
    }
    overlap = set(local) & set(external)
    if overlap:
        raise AssertionError(f"local/external assignment overlap: {sorted(overlap)}")
    result = {test_id: Assignment("local", runner) for test_id, runner in local.items()}
    result.update(
        {test_id: Assignment("external", runner) for test_id, runner in external.items()}
    )
    return result


def _release_applies(plan: str, release: str = "R01") -> bool:
    target = int(release[1:])
    for part in (item.strip() for item in plan.split("/")):
        if part == release:
            return True
        match = re.fullmatch(r"R(\d+)-R(\d+)", part)
        if match and int(match.group(1)) <= target <= int(match.group(2)):
            return True
    return False


def load_inventory(
    manifest_path: Path = MANIFEST_PATH,
    catalog_path: Path = CATALOG_PATH,
    repo_root: Path = ROOT,
) -> tuple[list[str], dict[str, dict[str, str]], list[str]]:
    errors: list[str] = []
    try:
        manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8")) or {}
    except (OSError, UnicodeError, yaml.YAMLError) as exc:
        return [], {}, [f"cannot load R01 manifest: {exc}"]
    manifest_ids = manifest.get("tests")
    if not isinstance(manifest_ids, list) or not all(
        isinstance(item, str) and item.strip() for item in manifest_ids
    ):
        return [], {}, ["manifest tests must be a list of non-empty string IDs"]
    if len(manifest_ids) != EXPECTED_COUNT:
        errors.append(f"manifest test count expected={EXPECTED_COUNT} actual={len(manifest_ids)}")
    duplicates = sorted({item for item in manifest_ids if manifest_ids.count(item) > 1})
    if duplicates:
        errors.append(f"manifest duplicate test IDs: {duplicates}")

    catalog: dict[str, dict[str, str]] = {}
    catalog_duplicates: set[str] = set()
    try:
        with catalog_path.open(encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                test_id = row.get("测试ID", "").strip()
                if not test_id:
                    errors.append("catalog contains a row with an empty test ID")
                    continue
                if test_id in catalog:
                    catalog_duplicates.add(test_id)
                catalog[test_id] = row
    except (OSError, UnicodeError, csv.Error) as exc:
        return list(manifest_ids), {}, errors + [f"cannot load test catalog: {exc}"]
    if catalog_duplicates:
        errors.append(f"catalog duplicate test IDs: {sorted(catalog_duplicates)}")

    applicable = {
        test_id
        for test_id, row in catalog.items()
        if _release_applies(row.get("计划版本", ""))
    }
    manifest_set = set(manifest_ids)
    if manifest_set != applicable:
        errors.append(
            "R01 manifest/catalog must match 1:1 "
            f"missing_from_manifest={sorted(applicable - manifest_set)} "
            f"missing_from_catalog={sorted(manifest_set - applicable)}"
        )

    assignments = build_assignments()
    missing_mapping = sorted(manifest_set - set(assignments))
    extra_mapping = sorted(set(assignments) - manifest_set)
    if missing_mapping:
        errors.append(f"manifest test IDs missing execution mapping: {missing_mapping}")
    if extra_mapping:
        errors.append(f"execution mappings not present in manifest: {extra_mapping}")
    if len(assignments) != EXPECTED_COUNT:
        errors.append(f"execution mapping count expected={EXPECTED_COUNT} actual={len(assignments)}")

    adapter_paths: dict[str, str] = {}
    resolved_root = repo_root.resolve()
    for test_id in sorted(applicable):
        row = catalog[test_id]
        raw_path = row.get("自动化路径", "").strip().replace("\\", "/")
        label = f"catalog adapter for {test_id}"
        if (
            not raw_path
            or raw_path.startswith(("/", "../"))
            or "/../" in raw_path
            or re.match(r"^[A-Za-z]:/", raw_path)
        ):
            errors.append(f"{label}: automation path must stay inside repository: {raw_path!r}")
            continue
        if raw_path in adapter_paths:
            errors.append(
                f"catalog adapter path duplicated by {adapter_paths[raw_path]} and {test_id}: {raw_path}"
            )
            continue
        adapter_paths[raw_path] = test_id
        target = (resolved_root / raw_path).resolve()
        if not _inside(target, resolved_root):
            errors.append(f"{label}: automation path escapes repository: {raw_path}")
            continue
        if not target.is_file():
            errors.append(f"{label}: adapter file is missing: {raw_path}")
            continue
        try:
            adapter = json.loads(target.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{label}: adapter is not valid UTF-8 JSON: {exc}")
            continue
        if not isinstance(adapter, dict):
            errors.append(f"{label}: adapter must be a JSON object")
            continue
        assignment = assignments.get(test_id)
        if adapter.get("schema") == "hhy.test-adapter/v2":
            release_assignment = adapter.get("assignments", {}).get("R01", {})
            expected = {
                "test_id": test_id,
                "central_runner": "scripts/run_r01_test_matrix.py",
                "assignment_source": assignment.source if assignment else None,
                "runner": assignment.runner if assignment else None,
            }
            actual = {"test_id": adapter.get("test_id"), **release_assignment}
        else:
            expected = {
                "schema": "hhy.r01.test-adapter/v1",
                "test_id": test_id,
                "central_runner": "scripts/run_r01_test_matrix.py",
                "assignment_source": assignment.source if assignment else None,
                "runner": assignment.runner if assignment else None,
            }
            actual = adapter
        for field, expected_value in expected.items():
            if actual.get(field) != expected_value:
                errors.append(
                    f"{label}: {field} expected={expected_value!r} actual={actual.get(field)!r}"
                )
    return list(manifest_ids), catalog, errors


def git_head(root: Path = ROOT) -> str | None:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True, capture_output=True, check=False
        )
    except OSError:
        return None
    value = completed.stdout.strip()
    return value if completed.returncode == 0 and SHA_PATTERN.fullmatch(value) else None


def post_test_path_allowed(path: str) -> bool:
    normalized = path.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    if (
        not normalized
        or normalized.startswith(("/", "../"))
        or "/../" in normalized
        or re.match(r"^[A-Za-z]:/", normalized)
    ):
        return False
    if normalized in POST_TEST_ALLOWED_EXACT or normalized.startswith(POST_TEST_ALLOWED_PREFIXES):
        return True
    return bool(re.fullmatch(r"artifacts/validation/r01-test-matrix[^/]*\.json", normalized))


def _git(
    root: Path, arguments: Sequence[str], *, binary: bool = False
) -> subprocess.CompletedProcess[Any]:
    return subprocess.run(
        ["git", *arguments], cwd=root, text=not binary, capture_output=True, check=False
    )


def validate_source_commit(
    source_commit: str,
    current_head: str | None,
    root: Path = ROOT,
) -> tuple[dict[str, Any], list[str]]:
    """Prove source freshness, allowing only evidence/closure-only descendants."""

    details: dict[str, Any] = {
        "source_commit": source_commit,
        "current_head": current_head,
        "relationship": "UNVERIFIED",
        "post_test_changes": [],
    }
    if not SHA_PATTERN.fullmatch(source_commit or ""):
        return details, ["source_commit must be a 40-64 character hexadecimal SHA"]
    if not current_head or not SHA_PATTERN.fullmatch(current_head):
        return details, ["current HEAD is unavailable; source_commit cannot be verified"]
    if source_commit.lower() == current_head.lower():
        details["relationship"] = "HEAD"
        return details, []

    try:
        shallow = _git(root, ["rev-parse", "--is-shallow-repository"])
    except OSError as exc:
        return details, [f"git is unavailable; source_commit cannot be verified: {exc}"]
    if shallow.returncode != 0:
        return details, ["cannot determine whether repository history is shallow"]
    if shallow.stdout.strip().lower() != "false":
        details["relationship"] = "SHALLOW_REJECTED"
        return details, ["ancestor evidence is forbidden in a shallow repository"]

    for label, commit in (("source_commit", source_commit), ("current HEAD", current_head)):
        present = _git(root, ["cat-file", "-e", f"{commit}^{{commit}}"])
        if present.returncode != 0:
            return details, [f"{label} object is missing from complete Git history: {commit}"]

    ancestor = _git(root, ["merge-base", "--is-ancestor", source_commit, current_head])
    if ancestor.returncode != 0:
        details["relationship"] = "NON_ANCESTOR_REJECTED"
        if ancestor.returncode == 1:
            return details, [f"source_commit is not an ancestor of current HEAD: {source_commit}"]
        return details, ["Git failed while proving source_commit ancestry"]

    changed = _git(
        root,
        [
            "-c", "core.quotepath=false", "diff", "--name-only", "--no-renames", "-z",
            f"{source_commit}..{current_head}", "--",
        ],
        binary=True,
    )
    if changed.returncode != 0:
        return details, ["Git failed while enumerating post-test changes"]
    paths = [
        value.decode("utf-8", errors="surrogateescape").replace("\\", "/")
        for value in changed.stdout.split(b"\0")
        if value
    ]
    details["post_test_changes"] = paths
    forbidden = sorted(path for path in paths if not post_test_path_allowed(path))
    if forbidden:
        details["relationship"] = "ANCESTOR_WITH_FORBIDDEN_CHANGES"
        return details, [
            "source_commit is stale because post-test changes include non-closure paths: "
            + ", ".join(forbidden)
        ]
    details["relationship"] = "ANCESTOR_CLOSURE_ONLY"
    return details, []


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _parse_timestamp(value: Any, field: str, errors: list[str]) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        errors.append(f"{field} must be a non-empty RFC3339 timestamp")
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        errors.append(f"{field} is not a valid RFC3339 timestamp")
        return None
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        errors.append(f"{field} must include a timezone")
        return None
    return parsed


def _inside(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def validate_evidence(
    evidence_dir: Path | None,
    assignments: Mapping[str, Assignment],
    expected_commit: str | None,
    repo_root: Path = ROOT,
) -> tuple[dict[str, dict[str, Any]], list[str]]:
    """Validate exact-commit, artifact-bound evidence for external assignments."""

    expected_ids = {
        test_id for test_id, assignment in assignments.items() if assignment.source == "external"
    }
    results: dict[str, dict[str, Any]] = {}
    errors: list[str] = []
    if evidence_dir is None:
        return results, ["--evidence-dir is required for R01 external tests"]
    if not evidence_dir.is_dir():
        return results, [f"evidence directory does not exist: {evidence_dir}"]
    root = evidence_dir.resolve()
    files = sorted(evidence_dir.rglob("*.evidence.json"))
    if not files:
        return results, [f"evidence directory contains no *.evidence.json reports: {evidence_dir}"]

    report_ids: set[str] = set()
    source_validation_cache: dict[str, tuple[dict[str, Any], list[str]]] = {}
    allowed_report = {
        "schema", "report_id", "suite", "producer", "generated_at", "source_commit",
        "command", "environment", "results",
    }
    allowed_result = {
        "test_id", "status", "executed", "exit_code", "started_at", "finished_at",
        "assertions", "artifacts", "details",
    }
    for path in files:
        relative = path.relative_to(evidence_dir).as_posix()
        try:
            report = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{relative}: invalid JSON: {exc}")
            continue
        if not isinstance(report, dict):
            errors.append(f"{relative}: report must be an object")
            continue
        unknown = sorted(set(report) - allowed_report)
        if unknown:
            errors.append(f"{relative}: unknown report fields: {unknown}")
        if report.get("schema") != EVIDENCE_SCHEMA:
            errors.append(f"{relative}: schema must be {EVIDENCE_SCHEMA}")
            continue
        report_id = report.get("report_id")
        suite = report.get("suite")
        source_commit = report.get("source_commit")
        if not isinstance(report_id, str) or not report_id.strip():
            errors.append(f"{relative}: report_id must be non-empty")
        elif report_id in report_ids:
            errors.append(f"{relative}: duplicate report_id {report_id!r}")
        else:
            report_ids.add(report_id)
        for field in ("suite", "producer"):
            if not isinstance(report.get(field), str) or not report[field].strip():
                errors.append(f"{relative}: {field} must be non-empty")
        _parse_timestamp(report.get("generated_at"), f"{relative}.generated_at", errors)
        source_details: dict[str, Any] = {}
        source_errors: list[str] = []
        if not isinstance(source_commit, str) or not SHA_PATTERN.fullmatch(source_commit):
            errors.append(f"{relative}: source_commit must be a 40-64 character hexadecimal SHA")
            source_errors = ["source_commit must be a 40-64 character hexadecimal SHA"]
        else:
            if source_commit not in source_validation_cache:
                source_validation_cache[source_commit] = validate_source_commit(
                    source_commit, expected_commit, repo_root
                )
            source_details, source_errors = source_validation_cache[source_commit]
            errors.extend(f"{relative}: {message}" for message in source_errors)
        command = report.get("command")
        if not isinstance(command, list) or not command or not all(
            isinstance(item, str) and item.strip() for item in command
        ):
            errors.append(f"{relative}: command must be a non-empty string array")
        environment = report.get("environment")
        required_environment = {"java", "maven", "postgresql", "api_base_url"}
        if not isinstance(environment, dict) or not environment:
            errors.append(f"{relative}: environment must be a non-empty object")
        else:
            missing_environment = sorted(
                key for key in required_environment
                if not isinstance(environment.get(key), str) or not environment[key].strip()
            )
            if missing_environment:
                errors.append(
                    f"{relative}: environment missing Maven/PG17/real API fields: {missing_environment}"
                )
            pg = str(environment.get("postgresql", ""))
            if pg and not re.search(r"(?:^|\D)17(?:\D|$)", pg):
                errors.append(f"{relative}: PostgreSQL environment must identify major version 17")

        report_results = report.get("results")
        if not isinstance(report_results, list) or not report_results:
            errors.append(f"{relative}: results must be a non-empty array")
            continue
        for index, item in enumerate(report_results):
            label = f"{relative}.results[{index}]"
            if not isinstance(item, dict):
                errors.append(f"{label}: result must be an object")
                continue
            unknown_item = sorted(set(item) - allowed_result)
            if unknown_item:
                errors.append(f"{label}: unknown result fields: {unknown_item}")
            test_id = item.get("test_id")
            if not isinstance(test_id, str) or test_id not in expected_ids:
                errors.append(f"{label}: unexpected or locally executed test_id {test_id!r}")
                continue
            if test_id in results:
                errors.append(f"{label}: duplicate external evidence for {test_id}")
                continue
            item_errors: list[str] = []
            assignment = assignments[test_id]
            if suite != assignment.runner:
                item_errors.append(f"suite expected={assignment.runner} actual={suite!r}")
            if item.get("status") != "PASS":
                item_errors.append("status must be PASS; FAIL/N/A/NOT_RUN cannot satisfy R01")
            if item.get("executed") is not True:
                item_errors.append("executed must be true")
            if type(item.get("exit_code")) is not int or item.get("exit_code") != 0:
                item_errors.append("exit_code must be integer 0")
            started = _parse_timestamp(item.get("started_at"), f"{label}.started_at", item_errors)
            finished = _parse_timestamp(item.get("finished_at"), f"{label}.finished_at", item_errors)
            if started and finished and finished < started:
                item_errors.append("finished_at precedes started_at")
            assertions = item.get("assertions")
            if not isinstance(assertions, list) or not assertions:
                item_errors.append("assertions must be a non-empty array")
            else:
                for assertion_index, assertion in enumerate(assertions):
                    if (
                        not isinstance(assertion, dict)
                        or set(assertion) != {"name", "status"}
                        or not isinstance(assertion.get("name"), str)
                        or not assertion["name"].strip()
                        or assertion.get("status") != "PASS"
                    ):
                        item_errors.append(
                            f"assertions[{assertion_index}] must contain only a name and PASS status"
                        )
            artifacts = item.get("artifacts")
            if not isinstance(artifacts, list) or not artifacts:
                item_errors.append("artifacts must contain at least one SHA256-bound file")
            else:
                seen_artifact_paths: set[str] = set()
                for artifact_index, artifact in enumerate(artifacts):
                    artifact_label = f"artifacts[{artifact_index}]"
                    if not isinstance(artifact, dict) or set(artifact) != {"path", "sha256"}:
                        item_errors.append(f"{artifact_label} must contain only path and sha256")
                        continue
                    artifact_path = artifact.get("path")
                    expected_hash = artifact.get("sha256")
                    if not isinstance(artifact_path, str) or not artifact_path.strip():
                        item_errors.append(f"{artifact_label}.path must be non-empty")
                        continue
                    normalized = artifact_path.replace("\\", "/")
                    if normalized in seen_artifact_paths:
                        item_errors.append(f"{artifact_label}.path is duplicated")
                    seen_artifact_paths.add(normalized)
                    target = (root / artifact_path).resolve()
                    if not _inside(target, root):
                        item_errors.append(f"{artifact_label}.path escapes evidence directory")
                        continue
                    if not target.is_file():
                        item_errors.append(f"{artifact_label}.path does not exist: {artifact_path}")
                        continue
                    if target.stat().st_size <= 0:
                        item_errors.append(f"{artifact_label}.path must be a non-empty real artifact")
                    if not isinstance(expected_hash, str) or not SHA256_PATTERN.fullmatch(expected_hash):
                        item_errors.append(f"{artifact_label}.sha256 must be 64 hexadecimal characters")
                    elif sha256(target).lower() != expected_hash.lower():
                        item_errors.append(f"{artifact_label}.sha256 mismatch: {artifact_path}")
            if not isinstance(item.get("details"), str) or not item["details"].strip():
                item_errors.append("details must be non-empty")
            item_errors.extend(f"source_commit: {message}" for message in source_errors)
            status = "PASS" if not item_errors else "FAIL"
            results[test_id] = {
                "test_id": test_id,
                "status": status,
                "source": "external",
                "runner": assignment.runner,
                "report": relative,
                "source_commit_validation": source_details,
                "errors": item_errors,
            }
            errors.extend(f"{label}: {message}" for message in item_errors)

    missing = sorted(expected_ids - set(results))
    if missing:
        errors.append(f"missing external evidence for {len(missing)} test IDs: {missing}")
    return results, errors


def _package_manager() -> tuple[str, str]:
    npm = shutil.which("npm.cmd" if os.name == "nt" else "npm")
    if npm:
        return npm, "--prefix"
    pnpm = shutil.which("pnpm.cmd" if os.name == "nt" else "pnpm")
    if pnpm:
        return pnpm, "--dir"
    return ("npm.cmd" if os.name == "nt" else "npm"), "--prefix"


def _node_bin_dir() -> Path | None:
    node = shutil.which("node.exe" if os.name == "nt" else "node")
    if node:
        return Path(node).parent
    # Codex desktop exposes Python and Node as sibling runtime dependencies.
    candidate = Path(sys.executable).resolve().parents[1] / "node/bin"
    executable = candidate / ("node.exe" if os.name == "nt" else "node")
    return candidate if executable.is_file() else None


def _expand(command: Sequence[str], temp_report: Path) -> list[str]:
    package_manager, directory_flag = _package_manager()
    values = {
        "{python}": sys.executable,
        "{package_manager}": package_manager,
        "{package_dir_flag}": directory_flag,
        "{temp_report}": str(temp_report),
    }
    return [values.get(item, item) for item in command]


def execute_local_runners(
    assignments: Mapping[str, Assignment],
) -> tuple[dict[str, dict[str, Any]], list[str]]:
    required = sorted(
        {assignment.runner for assignment in assignments.values() if assignment.source == "local"}
    )
    runner_results: dict[str, dict[str, Any]] = {}

    def run_one(key: str, temporary_root: Path) -> tuple[str, dict[str, Any]]:
        runner = LOCAL_RUNNERS.get(key)
        if runner is None:
            return key, {"status": "FAIL", "description": "undefined local runner", "invocations": []}
        invocations: list[dict[str, Any]] = []
        passed = True
        for index, raw in enumerate(runner.commands):
            command = _expand(raw, temporary_root / f"{key}-{index}.json")
            started = datetime.now().astimezone().isoformat()
            environment = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}
            node_bin = _node_bin_dir()
            if node_bin:
                environment["PATH"] = str(node_bin) + os.pathsep + environment.get("PATH", "")
            try:
                completed = subprocess.run(
                    command,
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    timeout=900,
                    env=environment,
                    check=False,
                )
                invocation = {
                    "command": command,
                    "started_at": started,
                    "finished_at": datetime.now().astimezone().isoformat(),
                    "exit_code": completed.returncode,
                    "stdout": completed.stdout[-32768:],
                    "stderr": completed.stderr[-32768:],
                }
                passed = passed and completed.returncode == 0
            except (OSError, subprocess.TimeoutExpired) as exc:
                passed = False
                invocation = {
                    "command": command,
                    "started_at": started,
                    "finished_at": datetime.now().astimezone().isoformat(),
                    "exit_code": None,
                    "stdout": "",
                    "stderr": "",
                    "error": repr(exc),
                }
            invocations.append(invocation)
        return key, {
            "status": "PASS" if passed else "FAIL",
            "description": runner.description,
            "invocations": invocations,
        }

    with tempfile.TemporaryDirectory(prefix="hhy-r01-matrix-") as temporary:
        temporary_root = Path(temporary)
        with ThreadPoolExecutor(max_workers=min(4, max(1, len(required)))) as executor:
            futures = {executor.submit(run_one, key, temporary_root): key for key in required}
            for future in as_completed(futures):
                key = futures[future]
                try:
                    completed_key, result = future.result()
                    runner_results[completed_key] = result
                except Exception as exc:
                    runner_results[key] = {
                        "status": "FAIL", "description": "runner exception", "invocations": [], "error": repr(exc)
                    }

    errors = [f"local runner failed: {key}" for key in required if runner_results.get(key, {}).get("status") != "PASS"]
    results: dict[str, dict[str, Any]] = {}
    for test_id, assignment in assignments.items():
        if assignment.source != "local":
            continue
        runner_result = runner_results.get(assignment.runner, {})
        status = runner_result.get("status", "FAIL")
        results[test_id] = {
            "test_id": test_id,
            "status": status,
            "source": "local",
            "runner": assignment.runner,
            "runner_result": runner_result,
            "errors": [] if status == "PASS" else [f"local runner failed: {assignment.runner}"],
        }
    return results, errors


def atomic_write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    try:
        temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def build_list_payload(
    manifest_ids: Sequence[str],
    catalog: Mapping[str, dict[str, str]],
    inventory_errors: Sequence[str],
) -> dict[str, Any]:
    assignments = build_assignments()
    rows = []
    for test_id in manifest_ids:
        assignment = assignments.get(test_id)
        rows.append(
            {
                "test_id": test_id,
                "catalog_release": catalog.get(test_id, {}).get("计划版本", ""),
                "source": assignment.source if assignment else "UNMAPPED",
                "runner": assignment.runner if assignment else "UNMAPPED",
                "status": "NOT_RUN",
            }
        )
    return {
        "schema": RESULT_SCHEMA,
        "mode": "list",
        "status": "VALID" if not inventory_errors else "INVALID",
        "summary": {
            "expected": EXPECTED_COUNT,
            "manifest": len(manifest_ids),
            "catalog_matched": sum(1 for test_id in manifest_ids if test_id in catalog),
            "local": sum(1 for row in rows if row["source"] == "local"),
            "external": sum(1 for row in rows if row["source"] == "external"),
            "executed": 0,
        },
        "results": rows,
        "errors": list(inventory_errors),
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Execute the exact R01 27-test matrix")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--list", action="store_true", help="validate/list assignments without execution")
    mode.add_argument("--check", action="store_true", help="run local checks and validate external evidence")
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args(argv)

    manifest_ids, catalog, inventory_errors = load_inventory()
    if args.list:
        payload = build_list_payload(manifest_ids, catalog, inventory_errors)
        for row in payload["results"]:
            print(f"{row['test_id']}\t{row['source']}\t{row['runner']}\tNOT_RUN")
        print(json.dumps(payload["summary"], ensure_ascii=False, sort_keys=True))
        for error in payload["errors"]:
            print(f"ERROR {error}", file=sys.stderr)
        if args.json_out:
            atomic_write_json(args.json_out, payload)
        return 0 if payload["status"] == "VALID" else 1

    assignments = build_assignments()
    local_results, local_errors = execute_local_runners(assignments)
    head = git_head()
    evidence_results, evidence_errors = validate_evidence(args.evidence_dir, assignments, head)
    combined = {**local_results, **evidence_results}
    rows: list[dict[str, Any]] = []
    for test_id in manifest_ids:
        result = combined.get(test_id)
        if result is None:
            assignment = assignments.get(test_id)
            result = {
                "test_id": test_id,
                "status": "MISSING_EVIDENCE" if assignment and assignment.source == "external" else "FAIL",
                "source": assignment.source if assignment else "UNMAPPED",
                "runner": assignment.runner if assignment else "UNMAPPED",
                "errors": ["no executed result is available"],
            }
        rows.append(result)
    all_errors = list(inventory_errors) + local_errors + evidence_errors
    passed = sum(1 for row in rows if row.get("status") == "PASS")
    status = "PASS" if not all_errors and passed == EXPECTED_COUNT else "FAIL"
    payload = {
        "schema": RESULT_SCHEMA,
        "mode": "check",
        "status": status,
        "source_commit": head,
        "summary": {
            "expected": EXPECTED_COUNT,
            "manifest": len(manifest_ids),
            "catalog_matched": sum(1 for test_id in manifest_ids if test_id in catalog),
            "local": sum(1 for value in assignments.values() if value.source == "local"),
            "external": sum(1 for value in assignments.values() if value.source == "external"),
            "passed": passed,
            "failed_or_missing": len(rows) - passed,
        },
        "results": rows,
        "errors": all_errors,
    }
    print(json.dumps(payload["summary"], ensure_ascii=False, sort_keys=True))
    for error in all_errors:
        print(f"ERROR {error}", file=sys.stderr)
    if args.json_out:
        atomic_write_json(args.json_out, payload)
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
