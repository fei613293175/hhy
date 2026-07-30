#!/usr/bin/env python3
"""Validate and attest the exact six-test R04 release matrix."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "releases/R04/RELEASE_MANIFEST.yaml"
CATALOG = ROOT / "catalogs/test_cases.csv"
EXPECTED_COUNT = 6
EVIDENCE_SCHEMA = "hhy.r04.test-evidence/v1"
RESULT_SCHEMA = "hhy.r04.test-matrix-result/v1"
RUNNER = "r04-java21-pg17-android-contracts"
ANDROID_IMAGE = "hhy-android-toolchain:r01-46fb273"
API_BASE_URL = "https://api.orbexa.cc"
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
    "artifacts/validation/r04-test-evidence/",
    "artifacts/reports/R04/",
    "docs/03-continuity/sessions/",
)


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
        return [], [f"cannot load R04 manifest: {exc}"]
    manifest_ids = manifest.get("tests")
    if not isinstance(manifest_ids, list) or not all(isinstance(item, str) for item in manifest_ids):
        return [], ["manifest tests must be a string list"]
    if len(manifest_ids) != EXPECTED_COUNT:
        errors.append(f"manifest test count expected={EXPECTED_COUNT} actual={len(manifest_ids)}")
    if len(set(manifest_ids)) != len(manifest_ids):
        errors.append("manifest contains duplicate test IDs")

    with CATALOG.open(encoding="utf-8-sig", newline="") as handle:
        catalog = {row.get("测试ID", "").strip(): row for row in csv.DictReader(handle)}
    manifest_set = set(manifest_ids)
    missing = sorted(manifest_set - set(catalog))
    if missing:
        errors.append(f"R04 manifest tests missing from catalog: {missing}")

    root = ROOT.resolve()
    seen_paths: dict[str, str] = {}
    for test_id in manifest_ids:
        row = catalog.get(test_id, {})
        if row.get("计划版本", "").strip() != "R04":
            errors.append(f"catalog test is not frozen for R04: {test_id}")
        raw_path = row.get("自动化路径", "").strip().replace("\\", "/")
        if not raw_path or raw_path.startswith(("/", "../")) or "/../" in raw_path:
            errors.append(f"catalog adapter path escapes repository for {test_id}: {raw_path!r}")
            continue
        if raw_path in seen_paths:
            errors.append(f"duplicate adapter path for {test_id} and {seen_paths[raw_path]}")
            continue
        seen_paths[raw_path] = test_id
        target = (root / raw_path).resolve()
        if not inside(target, root) or not target.is_file():
            errors.append(f"catalog adapter is missing for {test_id}: {raw_path}")
            continue
        try:
            adapter = json.loads(target.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"invalid adapter for {test_id}: {exc}")
            continue
        expected = {
            "schema": "hhy.r04.test-adapter/v1",
            "test_id": test_id,
            "central_runner": "scripts/run_r04_test_matrix.py",
            "assignment_source": "external",
            "runner": RUNNER,
        }
        for field, value in expected.items():
            if adapter.get(field) != value:
                errors.append(
                    f"adapter {test_id} {field} expected={value!r} actual={adapter.get(field)!r}"
                )
    return list(manifest_ids), errors


def git_head() -> str | None:
    completed = subprocess.run(
        [os.environ.get("HHY_GIT", "git"), "rev-parse", "HEAD"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    value = completed.stdout.strip()
    return value if completed.returncode == 0 and SHA_PATTERN.fullmatch(value) else None


def validate_source_commit(source_commit: str, head: str | None) -> list[str]:
    if not head:
        return ["cannot resolve current Git HEAD"]
    if not SHA_PATTERN.fullmatch(source_commit):
        return [f"invalid evidence source_commit {source_commit!r}"]
    if source_commit == head:
        return []
    git = os.environ.get("HHY_GIT", "git")
    ancestor = subprocess.run(
        [git, "merge-base", "--is-ancestor", source_commit, head],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if ancestor.returncode != 0:
        return [f"evidence source_commit is not an ancestor of HEAD: {source_commit}"]
    changed = subprocess.run(
        [git, "diff", "--name-only", f"{source_commit}..{head}"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
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


def validate_evidence(
    evidence_dir: Path, head: str | None, inventory: list[str] | None = None
) -> tuple[dict[str, dict[str, Any]], list[str]]:
    inventory = inventory if inventory is not None else load_inventory()[0]
    expected = set(inventory)
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
    if report.get("suite") != RUNNER:
        errors.append(f"evidence suite must be {RUNNER}")
    errors.extend(validate_source_commit(str(report.get("source_commit", "")), head))
    if not isinstance(report.get("command"), list) or not report["command"]:
        errors.append("evidence command must be a non-empty list")
    environment = report.get("environment")
    if not isinstance(environment, dict):
        errors.append("evidence environment must be an object")
    else:
        if "21" not in str(environment.get("java", "")):
            errors.append("evidence requires Java 21")
        if "17" not in str(environment.get("postgresql", "")):
            errors.append("evidence requires PostgreSQL 17")
        if environment.get("android_image") != ANDROID_IMAGE:
            errors.append(f"evidence android_image must be {ANDROID_IMAGE}")
        if environment.get("api_base_url") != API_BASE_URL:
            errors.append(f"evidence api_base_url must be {API_BASE_URL}")
        if not str(environment.get("maven", "")).strip():
            errors.append("evidence environment.maven is required")
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
        if test_id not in expected:
            errors.append(f"unexpected evidence test ID {test_id!r}")
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
            not isinstance(assertion, dict) or assertion.get("status") != "PASS"
            for assertion in assertions
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
    missing = sorted(expected - set(results))
    if missing:
        errors.append(f"evidence missing test IDs: {missing}")
    return results, errors


def build_result(
    inventory: list[str], errors: list[str], evidence: dict[str, dict[str, Any]] | None, executed: bool
) -> dict[str, Any]:
    rows = []
    for test_id in inventory:
        item = (evidence or {}).get(test_id)
        rows.append({"test_id": test_id, "status": item.get("status") if item else "NOT_RUN"})
    return {
        "schema": RESULT_SCHEMA,
        "release": "R04",
        "status": "PASS" if not errors else "FAIL",
        "executed": executed,
        "summary": {
            "total": len(inventory),
            "passed": sum(row["status"] == "PASS" for row in rows),
            "not_run": sum(row["status"] == "NOT_RUN" for row in rows),
        },
        "results": rows,
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--list", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()

    inventory, errors = load_inventory()
    evidence = None
    if args.check:
        if args.evidence_dir is None:
            errors.append("--check requires --evidence-dir")
        else:
            evidence, evidence_errors = validate_evidence(args.evidence_dir, git_head(), inventory)
            errors.extend(evidence_errors)
    report = build_result(inventory, errors, evidence, args.check)
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(payload, encoding="utf-8")
    print(payload, end="")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
