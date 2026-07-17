#!/usr/bin/env python3
"""Execute and attest the P00 59-test release matrix.

The release manifest is the authoritative list of required test IDs.  The
global test catalog supplies metadata; P00 is allowed to reuse frozen V1.2.2
tests, so membership is deliberately checked globally rather than by an exact
``计划版本 == P00`` comparison.

Checks that are deterministic on a source checkout are executed directly.
Environment-dependent checks (PostgreSQL, JVM, Web, Android, staging and APK)
must be supplied as structured evidence.  Missing, N/A, unexecuted, stale or
tampered evidence is a hard failure.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

import yaml


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "releases/P00/RELEASE_MANIFEST.yaml"
CATALOG_PATH = ROOT / "catalogs/test_cases.csv"
EVIDENCE_SCHEMA = "hhy.p00.test-evidence/v1"
RESULT_SCHEMA = "hhy.p00.test-matrix-result/v1"
EXPECTED_COUNT = 59
SHA_PATTERN = re.compile(r"^[0-9a-f]{40,64}$", re.IGNORECASE)

# Evidence may be committed before continuity/release closure metadata.  This
# allowlist is intentionally path-exact: implementation, migrations, contracts,
# configuration and build scripts are absent and therefore invalidate evidence.
POST_TEST_ALLOWED_EXACT = frozenset(
    {
        "CURRENT_STATUS.yaml",
        "NEXT_TASK.yaml",
        "CHANGELOG.md",
        "catalogs/TRACEABILITY_MATRIX.csv",
        "catalogs/task_transition_ledger.csv",
        "catalogs/release_story_backlog.csv",
        "catalogs/release_plan.csv",
        "catalogs/release_definition_of_ready.csv",
        "catalogs/release_artifact_index.csv",
        "catalogs/capability_completion_matrix.csv",
        "releases/P00/ACCEPTANCE_MATRIX.csv",
        "releases/P00/DEFINITION_OF_READY.yaml",
        "releases/P00/RELEASE_MANIFEST.yaml",
        "releases/P00/STORIES.yaml",
        "releases/P00/TASKS.yaml",
    }
)
POST_TEST_ALLOWED_PREFIXES = (
    ".continuity/",
    "artifacts/context/",
    "artifacts/validation/p00-test-evidence/",
    "artifacts/validation/p00-test-matrix",
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
    "continuity-bootstrap": Runner(
        "continuity-bootstrap",
        "cold-start/bootstrap recovery integration tests",
        (("{python}", "tests/test_continuity_bootstrap_recovery.py"),),
    ),
    "continuity-reject": Runner(
        "continuity-reject",
        "continuity protocol rejection/tamper integration tests",
        (("{python}", "scripts/test_continuity_protocol.py"),),
    ),
    "continuity-idempotent": Runner(
        "continuity-idempotent",
        "cross-release close and retry/idempotency integration tests",
        (("{python}", "tests/test_continuity_cross_release_close.py"),),
    ),
    "contract-openapi": Runner(
        "contract-openapi",
        "OpenAPI/catalog/generated-client semantic contract gate",
        (("{python}", "scripts/check_api_contract.py"),),
    ),
    "contract-drift": Runner(
        "contract-drift",
        "frozen contract registry hash/drift gate",
        (("{python}", "scripts/generate_contracts.py", "--check"),),
    ),
    "contract-idempotent": Runner(
        "contract-idempotent",
        "authoritative/runtime contract and migration byte-for-byte gate",
        (("{python}", "scripts/sync_runtime_assets.py", "--check"),),
    ),
    "documentation-p00": Runner(
        "documentation-p00",
        "P00 DoR/page/action/security/traceability documentation contract gate",
        (
            (
                "{python}",
                "scripts/check_v123_documentation.py",
                "--strict",
                "--release",
                "P00",
                "--json-out",
                "{temp_report}",
            ),
        ),
    ),
    "mapping-static": Runner(
        "mapping-static",
        "frontend/backend ownership and traceability mapping gate",
        (("{python}", "scripts/check_frontend_backend_matrix.py"),),
    ),
    "secrets-static": Runner(
        "secrets-static",
        "configuration registry secret-default static gate",
        (("{python}", "tests/p00/test_secret_defaults.py"),),
    ),
}


def _ids(prefix: str, suffixes: Sequence[str]) -> list[str]:
    return [f"{prefix}{suffix}" for suffix in suffixes]


def build_assignments() -> dict[str, Assignment]:
    """Return the closed, explicit P00 execution/evidence map."""

    local: dict[str, str] = {
        "TST-CONT_001-HAPPY": "continuity-bootstrap",
        "TST-CONT_001-REJECT": "continuity-reject",
        "TST-CONT_001-IDEMPOTENT": "continuity-idempotent",
        "TST-CONTRACT_001-HAPPY": "contract-openapi",
        "TST-CONTRACT_001-REJECT": "contract-drift",
        "TST-CONTRACT_001-IDEMPOTENT": "contract-idempotent",
        "TST-GATE-OPENAPI": "contract-openapi",
        "TST-GATE-STATE": "documentation-p00",
        "TST-GATE-MAPPING": "mapping-static",
        "TST-GATE-SECRETS": "secrets-static",
    }
    for test_id in (
        _ids("TST-DOR_001-", ("HAPPY", "REJECT", "SECURITY", "DRIFT"))
        + _ids("TST-PAGE_SPEC_001-", ("HAPPY", "REJECT", "SECURITY", "DRIFT"))
    ):
        local[test_id] = "documentation-p00"

    external: dict[str, str] = {}

    def assign(suite: str, test_ids: Iterable[str]) -> None:
        for test_id in test_ids:
            if test_id in external:
                raise AssertionError(f"duplicate external assignment: {test_id}")
            external[test_id] = suite

    triad = ("HAPPY", "REJECT", "IDEMPOTENT")
    assign("p00-ui-integration", _ids("TST-UI_001-", triad) + ["TST-V122-011"])
    assign(
        "p00-engineering-builds",
        _ids("TST-ENG_001-", triad)
        + ["TST-GATE-WEB-BUILD", "TST-GATE-BACKEND-BUILD", "TST-GATE-ANDROID-BUILD"],
    )
    assign("p00-apk-validation", _ids("TST-APK_001-", triad))
    assign(
        "p00-architecture",
        _ids("TST-ARCH_001-", triad) + ["TST-GATE-MODULE-BOUNDARY"],
    )
    assign(
        "p00-database-invariants",
        _ids("TST-DATA_001-", triad) + ["TST-GATE-DB-MIGRATION"],
    )
    assign(
        "p00-outbox-invariants",
        _ids("TST-EVENT_001-", triad) + ["TST-GATE-OUTBOX"],
    )
    assign(
        "p00-ledger-invariants",
        _ids(
            "TST-LEDGER_001-",
            ("HAPPY", "REJECT", "IDEMPOTENT", "CONCURRENCY", "RECOVERY"),
        )
        + ["TST-GATE-LEDGER"],
    )
    assign("p00-admin-integration", _ids("TST-ADMIN_BASE_001-", triad))
    assign("p00-observability-staging", _ids("TST-OBS_002-", triad))
    assign(
        "p00-secret-security",
        _ids("TST-SECRET_001-", ("HAPPY", "REJECT", "IDEMPOTENT", "SECURITY")),
    )

    overlap = set(local) & set(external)
    if overlap:
        raise AssertionError(f"local/external assignment overlap: {sorted(overlap)}")
    assignments = {key: Assignment("local", value) for key, value in local.items()}
    assignments.update({key: Assignment("external", value) for key, value in external.items()})
    return assignments


def load_inventory(
    manifest_path: Path = MANIFEST_PATH,
    catalog_path: Path = CATALOG_PATH,
) -> tuple[list[str], dict[str, dict[str, str]], list[str]]:
    manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8")) or {}
    manifest_ids = manifest.get("tests")
    errors: list[str] = []
    if not isinstance(manifest_ids, list) or not all(isinstance(item, str) for item in manifest_ids):
        return [], {}, ["manifest tests must be a list of string IDs"]
    if len(manifest_ids) != EXPECTED_COUNT:
        errors.append(f"manifest test count expected={EXPECTED_COUNT} actual={len(manifest_ids)}")
    duplicates = sorted({item for item in manifest_ids if manifest_ids.count(item) > 1})
    if duplicates:
        errors.append(f"manifest duplicate test IDs: {duplicates}")

    catalog: dict[str, dict[str, str]] = {}
    catalog_duplicates: list[str] = []
    with catalog_path.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            test_id = row.get("测试ID", "").strip()
            if not test_id:
                errors.append("catalog contains a row with an empty test ID")
                continue
            if test_id in catalog:
                catalog_duplicates.append(test_id)
            catalog[test_id] = row
    if catalog_duplicates:
        errors.append(f"catalog duplicate test IDs: {sorted(set(catalog_duplicates))}")

    manifest_set = set(manifest_ids)
    catalog_set = set(catalog)
    missing_catalog = sorted(manifest_set - catalog_set)
    if missing_catalog:
        errors.append(f"manifest test IDs missing from global catalog: {missing_catalog}")

    assignments = build_assignments()
    missing_mapping = sorted(manifest_set - set(assignments))
    extra_mapping = sorted(set(assignments) - manifest_set)
    if missing_mapping:
        errors.append(f"manifest test IDs missing execution mapping: {missing_mapping}")
    if extra_mapping:
        errors.append(f"execution mappings not present in manifest: {extra_mapping}")
    if len(assignments) != EXPECTED_COUNT:
        errors.append(f"execution mapping count expected={EXPECTED_COUNT} actual={len(assignments)}")
    return list(manifest_ids), catalog, errors


def git_head(root: Path = ROOT) -> str | None:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
        )
    except OSError:
        return None
    value = result.stdout.strip()
    return value if result.returncode == 0 and SHA_PATTERN.fullmatch(value) else None


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
    return normalized in POST_TEST_ALLOWED_EXACT or normalized.startswith(
        POST_TEST_ALLOWED_PREFIXES
    )


def _git(root: Path, arguments: Sequence[str], *, binary: bool = False) -> subprocess.CompletedProcess[Any]:
    return subprocess.run(
        ["git", *arguments],
        cwd=root,
        text=not binary,
        capture_output=True,
        check=False,
    )


def validate_source_commit(
    source_commit: str,
    current_head: str | None,
    root: Path = ROOT,
) -> tuple[dict[str, Any], list[str]]:
    """Prove that an older tested commit remains valid at ``current_head``.

    Exact HEAD is always valid.  An older commit is accepted only with complete
    history, an ancestor proof and a path-level diff containing release closure
    metadata exclusively.  Shallow repositories cannot provide this proof.
    """

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
            "-c",
            "core.quotepath=false",
            "diff",
            "--name-only",
            "--no-renames",
            "-z",
            f"{source_commit}..{current_head}",
            "--",
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


def parse_timestamp(value: Any, field: str, errors: list[str]) -> datetime | None:
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
    expected_ids = {
        test_id for test_id, assignment in assignments.items() if assignment.source == "external"
    }
    results: dict[str, dict[str, Any]] = {}
    errors: list[str] = []
    if evidence_dir is None:
        return results, ["--evidence-dir is required because P00 has external test assignments"]
    if not evidence_dir.is_dir():
        return results, [f"evidence directory does not exist: {evidence_dir}"]
    root = evidence_dir.resolve()
    files = sorted(evidence_dir.rglob("*.evidence.json"))
    if not files:
        return results, [f"evidence directory contains no *.evidence.json reports: {evidence_dir}"]

    source_validation_cache: dict[str, tuple[dict[str, Any], list[str]]] = {}
    for path in files:
        relative = path.relative_to(evidence_dir).as_posix()
        try:
            report = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{relative}: invalid JSON: {exc}")
            continue
        if not isinstance(report, dict) or report.get("schema") != EVIDENCE_SCHEMA:
            errors.append(f"{relative}: schema must be {EVIDENCE_SCHEMA}")
            continue
        report_id = report.get("report_id")
        suite = report.get("suite")
        producer = report.get("producer")
        source_commit = report.get("source_commit")
        command = report.get("command")
        environment = report.get("environment")
        if not isinstance(report_id, str) or not report_id.strip():
            errors.append(f"{relative}: report_id must be non-empty")
        if not isinstance(suite, str) or not suite.strip():
            errors.append(f"{relative}: suite must be non-empty")
        if not isinstance(producer, str) or not producer.strip():
            errors.append(f"{relative}: producer must be non-empty")
        source_details: dict[str, Any] = {}
        source_errors: list[str] = []
        if not isinstance(source_commit, str) or not SHA_PATTERN.fullmatch(source_commit):
            errors.append(f"{relative}: source_commit must be a 40-64 character hexadecimal SHA")
        else:
            if source_commit not in source_validation_cache:
                source_validation_cache[source_commit] = validate_source_commit(
                    source_commit,
                    expected_commit,
                    repo_root,
                )
            source_details, source_errors = source_validation_cache[source_commit]
            errors.extend(
                f"{relative}: {message}" for message in source_errors
            )
        if not isinstance(command, list) or not command or not all(
            isinstance(item, str) and item.strip() for item in command
        ):
            errors.append(f"{relative}: command must be a non-empty string array")
        if not isinstance(environment, dict) or not environment:
            errors.append(f"{relative}: environment must be a non-empty object")
        parse_timestamp(report.get("generated_at"), f"{relative}.generated_at", errors)

        report_results = report.get("results")
        if not isinstance(report_results, list) or not report_results:
            errors.append(f"{relative}: results must be a non-empty array")
            continue
        for index, item in enumerate(report_results):
            label = f"{relative}.results[{index}]"
            if not isinstance(item, dict):
                errors.append(f"{label}: result must be an object")
                continue
            test_id = item.get("test_id")
            if not isinstance(test_id, str) or test_id not in expected_ids:
                errors.append(f"{label}: unexpected or locally executed test_id {test_id!r}")
                continue
            if test_id in results:
                errors.append(f"{label}: duplicate external evidence for {test_id}")
                continue
            assignment = assignments[test_id]
            item_errors: list[str] = []
            if suite != assignment.runner:
                item_errors.append(f"suite expected={assignment.runner} actual={suite!r}")
            if item.get("status") != "PASS":
                item_errors.append("status must be PASS; FAIL/N/A/NOT_RUN cannot satisfy P00")
            if item.get("executed") is not True:
                item_errors.append("executed must be true")
            if type(item.get("exit_code")) is not int or item.get("exit_code") != 0:
                item_errors.append("exit_code must be integer 0")
            started = parse_timestamp(item.get("started_at"), f"{label}.started_at", item_errors)
            finished = parse_timestamp(item.get("finished_at"), f"{label}.finished_at", item_errors)
            if started and finished and finished < started:
                item_errors.append("finished_at precedes started_at")
            assertions = item.get("assertions")
            if not isinstance(assertions, list) or not assertions:
                item_errors.append("assertions must be a non-empty array")
            else:
                for assertion_index, assertion in enumerate(assertions):
                    if (
                        not isinstance(assertion, dict)
                        or not isinstance(assertion.get("name"), str)
                        or not assertion.get("name", "").strip()
                        or assertion.get("status") != "PASS"
                    ):
                        item_errors.append(
                            f"assertions[{assertion_index}] must have a name and PASS status"
                        )
            artifacts = item.get("artifacts")
            if not isinstance(artifacts, list) or not artifacts:
                item_errors.append("artifacts must contain at least one SHA256-bound file")
            else:
                for artifact_index, artifact in enumerate(artifacts):
                    artifact_label = f"artifacts[{artifact_index}]"
                    if not isinstance(artifact, dict):
                        item_errors.append(f"{artifact_label} must be an object")
                        continue
                    artifact_path = artifact.get("path")
                    expected_hash = artifact.get("sha256")
                    if not isinstance(artifact_path, str) or not artifact_path.strip():
                        item_errors.append(f"{artifact_label}.path must be non-empty")
                        continue
                    target = (root / artifact_path).resolve()
                    if not _inside(target, root):
                        item_errors.append(f"{artifact_label}.path escapes evidence directory")
                        continue
                    if not target.is_file():
                        item_errors.append(f"{artifact_label}.path does not exist: {artifact_path}")
                        continue
                    if not isinstance(expected_hash, str) or not re.fullmatch(
                        r"[0-9a-fA-F]{64}", expected_hash
                    ):
                        item_errors.append(f"{artifact_label}.sha256 must be 64 hexadecimal characters")
                    elif sha256(target).lower() != expected_hash.lower():
                        item_errors.append(f"{artifact_label}.sha256 mismatch: {artifact_path}")
            if not isinstance(item.get("details"), str) or not item.get("details", "").strip():
                item_errors.append("details must be non-empty")
            result_errors = [f"source_commit: {message}" for message in source_errors] + item_errors
            results[test_id] = {
                "test_id": test_id,
                "status": "PASS" if not result_errors else "FAIL",
                "source": "external",
                "runner": assignment.runner,
                "report": relative,
                "source_commit_validation": source_details,
                "errors": result_errors,
            }
            errors.extend(f"{label}: {message}" for message in item_errors)

    missing = sorted(expected_ids - set(results))
    if missing:
        errors.append(f"missing external evidence for {len(missing)} test IDs: {missing}")
    return results, errors


def _expand_command(command: Sequence[str], temp_report: Path) -> list[str]:
    substitutions = {
        "{python}": sys.executable,
        "{temp_report}": str(temp_report),
    }
    return [substitutions.get(value, value) for value in command]


def execute_local_runners(
    assignments: Mapping[str, Assignment],
) -> tuple[dict[str, dict[str, Any]], list[str]]:
    results: dict[str, dict[str, Any]] = {}
    errors: list[str] = []
    required = sorted(
        {assignment.runner for assignment in assignments.values() if assignment.source == "local"}
    )
    runner_results: dict[str, dict[str, Any]] = {}

    def run_one(key: str, temporary_root: Path) -> tuple[str, dict[str, Any]]:
        runner = LOCAL_RUNNERS.get(key)
        if runner is None:
            return key, {
                "status": "FAIL",
                "description": "undefined local runner",
                "invocations": [],
            }
        invocations: list[dict[str, Any]] = []
        runner_ok = True
        for index, raw in enumerate(runner.commands):
            report_path = temporary_root / f"{key}-{index}.json"
            command = _expand_command(raw, report_path)
            environment = dict(os.environ)
            environment["PYTHONDONTWRITEBYTECODE"] = "1"
            try:
                completed = subprocess.run(
                    command,
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    timeout=600,
                    env=environment,
                    check=False,
                )
                invocation = {
                    "command": command,
                    "exit_code": completed.returncode,
                    "stdout": completed.stdout[-32768:],
                    "stderr": completed.stderr[-32768:],
                }
                if completed.returncode != 0:
                    runner_ok = False
            except subprocess.TimeoutExpired as exc:
                runner_ok = False
                invocation = {
                    "command": command,
                    "exit_code": None,
                    "stdout": (exc.stdout or "")[-32768:] if isinstance(exc.stdout, str) else "",
                    "stderr": (exc.stderr or "")[-32768:] if isinstance(exc.stderr, str) else "",
                    "error": "timeout after 600 seconds",
                }
            invocations.append(invocation)
        return key, {
            "status": "PASS" if runner_ok else "FAIL",
            "description": runner.description,
            "invocations": invocations,
        }

    with tempfile.TemporaryDirectory(prefix="hhy-p00-matrix-") as temporary:
        temporary_root = Path(temporary)
        with ThreadPoolExecutor(max_workers=min(4, max(1, len(required)))) as executor:
            futures = {executor.submit(run_one, key, temporary_root): key for key in required}
            for future in as_completed(futures):
                key = futures[future]
                try:
                    completed_key, result = future.result()
                    runner_results[completed_key] = result
                except Exception as exc:  # defensive: preserve a failed attestation, never a false PASS
                    runner_results[key] = {
                        "status": "FAIL",
                        "description": "runner raised an unexpected exception",
                        "invocations": [],
                        "error": repr(exc),
                    }
    for key in required:
        if runner_results.get(key, {}).get("status") != "PASS":
            errors.append(f"local runner failed: {key}")

    for test_id, assignment in assignments.items():
        if assignment.source != "local":
            continue
        runner_result = runner_results.get(assignment.runner)
        status = runner_result.get("status") if runner_result else "FAIL"
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
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _catalog_release(row: Mapping[str, str]) -> str:
    return row.get("计划版本", "")


def build_list_payload(
    manifest_ids: Sequence[str],
    catalog: Mapping[str, dict[str, str]],
    inventory_errors: Sequence[str],
) -> dict[str, Any]:
    assignments = build_assignments()
    rows: list[dict[str, Any]] = []
    for test_id in manifest_ids:
        assignment = assignments.get(test_id)
        catalog_row = catalog.get(test_id, {})
        rows.append(
            {
                "test_id": test_id,
                "catalog_release": _catalog_release(catalog_row),
                "native_p00": _catalog_release(catalog_row) == "P00",
                "source": assignment.source if assignment else "UNMAPPED",
                "runner": assignment.runner if assignment else "UNMAPPED",
                "status": "NOT_RUN",
            }
        )
    native = sum(1 for row in rows if row["native_p00"])
    return {
        "schema": RESULT_SCHEMA,
        "mode": "list",
        "status": "VALID" if not inventory_errors else "INVALID",
        "summary": {
            "expected": EXPECTED_COUNT,
            "manifest": len(manifest_ids),
            "catalog_matched": sum(1 for test_id in manifest_ids if test_id in catalog),
            "native_p00": native,
            "baseline_reused": len(rows) - native,
            "local": sum(1 for row in rows if row["source"] == "local"),
            "external": sum(1 for row in rows if row["source"] == "external"),
            "executed": 0,
        },
        "results": rows,
        "errors": list(inventory_errors),
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Execute the exact P00 59-test matrix")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--list", action="store_true", help="validate and list assignments; execute nothing")
    mode.add_argument("--check", action="store_true", help="execute local checks and consume external evidence")
    parser.add_argument("--evidence-dir", type=Path, help="directory of structured external evidence reports")
    parser.add_argument("--json-out", type=Path, help="write the complete result as JSON")
    args = parser.parse_args(argv)

    manifest_ids, catalog, inventory_errors = load_inventory()
    if args.list:
        payload = build_list_payload(manifest_ids, catalog, inventory_errors)
        for row in payload["results"]:
            print(
                f"{row['test_id']}\t{row['source']}\t{row['runner']}\t"
                f"catalog={row['catalog_release']}\tNOT_RUN"
            )
        print(json.dumps(payload["summary"], ensure_ascii=False, sort_keys=True))
        for error in payload["errors"]:
            print(f"ERROR {error}", file=sys.stderr)
        if args.json_out:
            atomic_write_json(args.json_out, payload)
        return 0 if payload["status"] == "VALID" else 1

    assignments = build_assignments()
    local_results, local_errors = execute_local_runners(assignments)
    expected_commit = git_head()
    evidence_results, evidence_errors = validate_evidence(
        args.evidence_dir,
        assignments,
        expected_commit,
    )
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
    pass_count = sum(1 for row in rows if row.get("status") == "PASS")
    overall = "PASS" if not all_errors and pass_count == EXPECTED_COUNT else "FAIL"
    catalog_releases = [_catalog_release(catalog.get(test_id, {})) for test_id in manifest_ids]
    native = sum(1 for value in catalog_releases if value == "P00")
    payload = {
        "schema": RESULT_SCHEMA,
        "mode": "check",
        "status": overall,
        "source_commit": expected_commit,
        "summary": {
            "expected": EXPECTED_COUNT,
            "manifest": len(manifest_ids),
            "catalog_matched": sum(1 for test_id in manifest_ids if test_id in catalog),
            "native_p00": native,
            "baseline_reused": len(manifest_ids) - native,
            "local": sum(1 for item in assignments.values() if item.source == "local"),
            "external": sum(1 for item in assignments.values() if item.source == "external"),
            "passed": pass_count,
            "failed_or_missing": len(rows) - pass_count,
        },
        "results": rows,
        "errors": all_errors,
    }
    print(json.dumps(payload["summary"], ensure_ascii=False, sort_keys=True))
    for error in all_errors:
        print(f"ERROR {error}", file=sys.stderr)
    if args.json_out:
        atomic_write_json(args.json_out, payload)
    return 0 if overall == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
