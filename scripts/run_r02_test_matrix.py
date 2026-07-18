#!/usr/bin/env python3
"""Execute and attest the exact R02 25-test release matrix."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Sequence

import yaml


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "releases/R02/RELEASE_MANIFEST.yaml"
CATALOG = ROOT / "catalogs/test_cases.csv"
EXPECTED_COUNT = 25
EVIDENCE_SCHEMA = "hhy.r02.test-evidence/v1"
RESULT_SCHEMA = "hhy.r02.test-matrix-result/v1"
SHA_PATTERN = re.compile(r"^[0-9a-f]{40,64}$", re.IGNORECASE)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$", re.IGNORECASE)
POST_TEST_ALLOWED_EXACT = {
    "CURRENT_STATUS.yaml",
    "NEXT_TASK.yaml",
    ".continuity/ACTIVE_SESSION.yaml",
    ".continuity/EVENT_LOG.jsonl",
    ".continuity/SESSION_INDEX.yaml",
    ".continuity/STATE.yaml",
    "catalogs/session_index.csv",
}
POST_TEST_ALLOWED_PREFIXES = (
    ".continuity/checkpoints/",
    ".continuity/sessions/",
    "artifacts/context/",
    "artifacts/validation/r02-test-evidence/",
    "artifacts/reports/R02/",
    "docs/03-continuity/sessions/",
)


@dataclass(frozen=True)
class Assignment:
    source: str
    runner: str


@dataclass(frozen=True)
class Runner:
    description: str
    commands: tuple[tuple[str, ...], ...]


SHARED_ADMIN_IDS = {
    f"TST-ADMIN_OPS_001-{suffix}" for suffix in ("DRIFT", "HAPPY", "REJECT", "SECURITY")
}
AUTH_IDS = {
    f"TST-AUTH_{number:03d}-{suffix}"
    for number in range(1, 6)
    for suffix in ("HAPPY", "IDEMPOTENT", "REJECT", "SECURITY")
}
EXTERNAL_IDS = AUTH_IDS | {"TST-V122-011"}

LOCAL_RUNNERS = {
    "documentation-r02": Runner(
        "strict R02 documentation and generated contract gates",
        (
            ("{python}", "scripts/check_v123_documentation.py", "--strict", "--release", "R02"),
            ("{python}", "scripts/check_api_contract.py"),
            ("{python}", "scripts/check_db_schema.py"),
            ("{python}", "scripts/check_generated_assets.py"),
        ),
    ),
    "admin-web-integration": Runner(
        "administrator user controls unit, type and production build gates",
        (
            ("{pnpm}", "--dir", "apps/admin-web", "test"),
            ("{pnpm}", "--dir", "apps/admin-web", "run", "typecheck"),
            ("{pnpm}", "--dir", "apps/admin-web", "run", "build"),
        ),
    ),
}


def assignments() -> dict[str, Assignment]:
    result = {
        "TST-ADMIN_OPS_001-DRIFT": Assignment("local", "documentation-r02"),
    }
    for test_id in SHARED_ADMIN_IDS - {"TST-ADMIN_OPS_001-DRIFT"}:
        result[test_id] = Assignment("local", "admin-web-integration")
    for test_id in EXTERNAL_IDS:
        result[test_id] = Assignment("external", "r02-java21-pg17-android-real-api")
    return result


def release_applies(plan: str, release: str = "R02") -> bool:
    target = int(release[1:])
    for part in (item.strip() for item in plan.split("/")):
        if part == release:
            return True
        match = re.fullmatch(r"R(\d+)-R(\d+)", part)
        if match and int(match.group(1)) <= target <= int(match.group(2)):
            return True
    return False


def inside(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def load_inventory() -> tuple[list[str], list[str]]:
    errors: list[str] = []
    try:
        manifest = yaml.safe_load(MANIFEST.read_text(encoding="utf-8")) or {}
    except (OSError, UnicodeError, yaml.YAMLError) as exc:
        return [], [f"cannot load R02 manifest: {exc}"]
    manifest_ids = manifest.get("tests")
    if not isinstance(manifest_ids, list) or not all(isinstance(item, str) for item in manifest_ids):
        return [], ["manifest tests must be a string list"]
    if len(manifest_ids) != EXPECTED_COUNT:
        errors.append(f"manifest test count expected={EXPECTED_COUNT} actual={len(manifest_ids)}")
    if len(set(manifest_ids)) != len(manifest_ids):
        errors.append("manifest contains duplicate test IDs")

    with CATALOG.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    catalog = {row.get("测试ID", "").strip(): row for row in rows}
    manifest_set = set(manifest_ids)
    missing_catalog = sorted(manifest_set - set(catalog))
    if missing_catalog:
        errors.append(f"R02 manifest tests missing from catalog: {missing_catalog}")
    not_applicable = sorted(
        test_id
        for test_id in manifest_set & set(catalog)
        if not release_applies(catalog[test_id].get("计划版本", ""))
    )
    if not_applicable:
        errors.append(f"R02 manifest tests not applicable to R02 in catalog: {not_applicable}")
    mapping = assignments()
    if manifest_set != set(mapping):
        errors.append(
            "R02 execution mapping mismatch "
            f"missing={sorted(manifest_set - set(mapping))} extra={sorted(set(mapping) - manifest_set)}"
        )

    root = ROOT.resolve()
    seen_paths: dict[str, str] = {}
    for test_id in sorted(manifest_set):
        row = catalog.get(test_id, {})
        raw_path = row.get("自动化路径", "").strip().replace("\\", "/")
        label = f"catalog adapter for {test_id}"
        if not raw_path or raw_path.startswith(("/", "../")) or "/../" in raw_path:
            errors.append(f"{label}: path must stay inside repository: {raw_path!r}")
            continue
        if raw_path in seen_paths:
            errors.append(f"{label}: duplicate path also used by {seen_paths[raw_path]}")
            continue
        seen_paths[raw_path] = test_id
        target = (root / raw_path).resolve()
        if not inside(target, root) or not target.is_file():
            errors.append(f"{label}: adapter file is missing: {raw_path}")
            continue
        try:
            adapter = json.loads(target.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{label}: invalid JSON: {exc}")
            continue
        expected_assignment = mapping.get(test_id)
        if adapter.get("schema") == "hhy.test-adapter/v2":
            actual = {"test_id": adapter.get("test_id"), **adapter.get("assignments", {}).get("R02", {})}
        else:
            actual = adapter
            if adapter.get("schema") != "hhy.r02.test-adapter/v1":
                errors.append(f"{label}: unsupported schema {adapter.get('schema')!r}")
        expected = {
            "test_id": test_id,
            "central_runner": "scripts/run_r02_test_matrix.py",
            "assignment_source": expected_assignment.source if expected_assignment else None,
            "runner": expected_assignment.runner if expected_assignment else None,
        }
        for field, value in expected.items():
            if actual.get(field) != value:
                errors.append(f"{label}: {field} expected={value!r} actual={actual.get(field)!r}")
    return list(manifest_ids), errors


def git_head() -> str | None:
    try:
        completed = subprocess.run(
            [os.environ.get("HHY_GIT", "git"), "rev-parse", "HEAD"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
    except OSError:
        return None
    value = completed.stdout.strip()
    return value if completed.returncode == 0 and SHA_PATTERN.fullmatch(value) else None


def validate_source_commit(source_commit: str, head: str | None) -> list[str]:
    if not head:
        return ["cannot resolve current Git HEAD"]
    if not SHA_PATTERN.fullmatch(str(source_commit)):
        return [f"invalid evidence source_commit {source_commit!r}"]
    if source_commit == head:
        return []
    git = os.environ.get("HHY_GIT", "git")
    shallow = subprocess.run(
        [git, "rev-parse", "--is-shallow-repository"],
        cwd=ROOT, text=True, capture_output=True, check=False,
    )
    if shallow.returncode != 0 or shallow.stdout.strip() == "true":
        return ["ancestor evidence is forbidden in a shallow or unreadable repository"]
    ancestor = subprocess.run(
        [git, "merge-base", "--is-ancestor", source_commit, head],
        cwd=ROOT, text=True, capture_output=True, check=False,
    )
    if ancestor.returncode != 0:
        return [f"evidence source_commit is not an ancestor of HEAD: {source_commit}"]
    changed = subprocess.run(
        [git, "diff", "--name-only", f"{source_commit}..{head}"],
        cwd=ROOT, text=True, capture_output=True, check=False,
    )
    if changed.returncode != 0:
        return ["cannot inspect paths changed after evidence source commit"]
    disallowed = []
    for raw_path in changed.stdout.splitlines():
        path = raw_path.strip().replace("\\", "/")
        if path in POST_TEST_ALLOWED_EXACT or path.startswith(POST_TEST_ALLOWED_PREFIXES):
            continue
        disallowed.append(path)
    return [f"evidence is stale because executable/source paths changed: {disallowed}"] if disallowed else []


def parse_time(value: Any, label: str, errors: list[str]) -> datetime | None:
    if not isinstance(value, str):
        errors.append(f"{label} must be RFC3339 text")
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        errors.append(f"{label} must be RFC3339: {value!r}")
        return None
    if parsed.tzinfo is None:
        errors.append(f"{label} must include timezone")
        return None
    return parsed


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_evidence(evidence_dir: Path, head: str | None) -> tuple[dict[str, dict[str, Any]], list[str]]:
    errors: list[str] = []
    results: dict[str, dict[str, Any]] = {}
    files = sorted(evidence_dir.glob("*.evidence.json")) if evidence_dir.is_dir() else []
    if len(files) != 1:
        return {}, [f"evidence directory must contain exactly one *.evidence.json, found {len(files)}"]
    try:
        report = json.loads(files[0].read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        return {}, [f"cannot load evidence: {exc}"]
    if report.get("schema") != EVIDENCE_SCHEMA:
        errors.append(f"evidence schema must be {EVIDENCE_SCHEMA}")
    if report.get("suite") != "r02-java21-pg17-android-real-api":
        errors.append("evidence suite is not the frozen R02 external runner")
    errors.extend(validate_source_commit(str(report.get("source_commit", "")), head))
    if not isinstance(report.get("command"), list) or not report.get("command"):
        errors.append("evidence command must be a non-empty list")
    environment = report.get("environment")
    if not isinstance(environment, dict):
        errors.append("evidence environment must be an object")
    else:
        required_environment = ("java", "maven", "postgresql", "android_image", "api_base_url")
        for field in required_environment:
            if not isinstance(environment.get(field), str) or not environment[field].strip():
                errors.append(f"evidence environment.{field} is required")
        if "17" not in str(environment.get("postgresql", "")):
            errors.append("evidence requires PostgreSQL 17")
        if not str(environment.get("api_base_url", "")).startswith("https://"):
            errors.append("evidence api_base_url must be HTTPS")
    parse_time(report.get("generated_at"), "evidence.generated_at", errors)

    raw_results = report.get("results")
    if not isinstance(raw_results, list):
        return {}, errors + ["evidence results must be a list"]
    evidence_root = evidence_dir.resolve()
    for index, item in enumerate(raw_results):
        label = f"evidence.results[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{label} must be an object")
            continue
        test_id = item.get("test_id")
        if test_id in results:
            errors.append(f"duplicate evidence result {test_id}")
            continue
        if test_id not in EXTERNAL_IDS:
            errors.append(f"unexpected external evidence test ID {test_id!r}")
            continue
        results[test_id] = item
        if item.get("status") != "PASS" or item.get("executed") is not True or item.get("exit_code") != 0:
            errors.append(f"{label} must be PASS, executed=true and exit_code=0")
        started = parse_time(item.get("started_at"), f"{label}.started_at", errors)
        finished = parse_time(item.get("finished_at"), f"{label}.finished_at", errors)
        if started and finished and finished < started:
            errors.append(f"{label} finished before it started")
        assertions = item.get("assertions")
        if not isinstance(assertions, list) or not assertions or any(
            not isinstance(assertion, dict) or assertion.get("status") != "PASS" for assertion in assertions
        ):
            errors.append(f"{label} requires at least one PASS assertion")
        artifacts = item.get("artifacts")
        if not isinstance(artifacts, list) or not artifacts:
            errors.append(f"{label} requires at least one artifact")
            continue
        for artifact in artifacts:
            raw_path = artifact.get("path", "") if isinstance(artifact, dict) else ""
            expected_sha = artifact.get("sha256", "") if isinstance(artifact, dict) else ""
            target = (evidence_root / raw_path).resolve()
            if not raw_path or not inside(target, evidence_root) or not target.is_file() or target.stat().st_size == 0:
                errors.append(f"{label} invalid artifact path {raw_path!r}")
            elif not SHA256_PATTERN.fullmatch(expected_sha) or hash_file(target).lower() != expected_sha.lower():
                errors.append(f"{label} artifact SHA256 mismatch {raw_path!r}")
    missing = sorted(EXTERNAL_IDS - set(results))
    if missing:
        errors.append(f"external evidence missing test IDs: {missing}")
    return results, errors


def expand_command(command: Sequence[str]) -> list[str]:
    pnpm = os.environ.get("HHY_PNPM", "pnpm")
    return [part.format(python=sys.executable, pnpm=pnpm) for part in command]


def execute_runner(key: str) -> tuple[bool, str]:
    runner = LOCAL_RUNNERS[key]
    output: list[str] = []
    for command in runner.commands:
        expanded = expand_command(command)
        completed = subprocess.run(expanded, cwd=ROOT, text=True, capture_output=True, check=False)
        output.append(f"$ {' '.join(expanded)}\n{completed.stdout}{completed.stderr}")
        if completed.returncode != 0:
            return False, "\n".join(output)[-12000:]
    return True, "\n".join(output)[-12000:]


def build_result(manifest_ids: list[str], inventory_errors: list[str], evidence_dir: Path | None, check: bool) -> dict[str, Any]:
    head = git_head()
    mapping = assignments()
    errors = list(inventory_errors)
    external: dict[str, dict[str, Any]] = {}
    local_status: dict[str, tuple[bool, str]] = {}
    if check and not inventory_errors:
        with ThreadPoolExecutor(max_workers=len(LOCAL_RUNNERS)) as pool:
            futures = {pool.submit(execute_runner, key): key for key in LOCAL_RUNNERS}
            for future in as_completed(futures):
                key = futures[future]
                try:
                    local_status[key] = future.result()
                except Exception as exc:  # pragma: no cover - defensive runner boundary
                    local_status[key] = (False, str(exc))
        if evidence_dir is None:
            errors.append("--evidence-dir is required with --check")
        else:
            external, evidence_errors = validate_evidence(evidence_dir, head)
            errors.extend(evidence_errors)

    rows: list[dict[str, Any]] = []
    for test_id in manifest_ids:
        assignment = mapping.get(test_id)
        status = "NOT_RUN"
        details = "inventory validated; execution not requested"
        if check and assignment:
            if assignment.source == "local":
                passed, details = local_status.get(assignment.runner, (False, "runner did not execute"))
                status = "PASS" if passed else "FAIL"
            else:
                item = external.get(test_id)
                status = item.get("status", "FAIL") if item else "FAIL"
                details = item.get("details", "external evidence missing") if item else "external evidence missing"
        rows.append({
            "test_id": test_id,
            "status": status,
            "assignment_source": assignment.source if assignment else None,
            "runner": assignment.runner if assignment else None,
            "details": details,
        })
    if check:
        for key, (passed, output) in local_status.items():
            if not passed:
                errors.append(f"local runner failed: {key}\n{output}")
    passed_count = sum(row["status"] == "PASS" for row in rows)
    return {
        "schema": RESULT_SCHEMA,
        "release": "R02",
        "source_commit": head,
        "mode": "check" if check else "list",
        "status": "PASS" if not errors and (not check or passed_count == EXPECTED_COUNT) else "FAIL",
        "summary": {
            "expected": EXPECTED_COUNT,
            "passed": passed_count,
            "failed": sum(row["status"] == "FAIL" for row in rows),
            "not_run": sum(row["status"] == "NOT_RUN" for row in rows),
        },
        "errors": errors,
        "results": rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--list", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()

    manifest_ids, inventory_errors = load_inventory()
    report = build_result(manifest_ids, inventory_errors, args.evidence_dir, args.check)
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
