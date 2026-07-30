#!/usr/bin/env python3
"""Validate the ten authoritative R07 search, publisher and contact tests."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "releases/R07/RELEASE_MANIFEST.yaml"
CATALOG = ROOT / "catalogs/test_cases.csv"
SCREEN = ROOT / "apps/android/feature/discovery/src/main/java/cc/orbexa/hhy/discovery/R07DiscoveryScreens.kt"
EXPECTED_IDS = {
    "TST-CONTACT_001-HAPPY",
    "TST-CONTACT_001-IDEMPOTENT",
    "TST-CONTACT_001-REJECT",
    "TST-PUBLISHER_001-HAPPY",
    "TST-PUBLISHER_001-IDEMPOTENT",
    "TST-PUBLISHER_001-REJECT",
    "TST-SEARCH_001-HAPPY",
    "TST-SEARCH_001-IDEMPOTENT",
    "TST-SEARCH_001-REJECT",
    "TST-V122-004",
}
ADAPTER_SCHEMA = "hhy.r07.test-adapter/v1"
EVIDENCE_SCHEMA = "hhy.r07.specialized-evidence/v1"
RUNNER = "r07-java21-postgresql17-search-publisher-contact"
TEST_ID_PATTERN = re.compile(
    r"^(?:TST-(?:CONTACT_001|PUBLISHER_001|SEARCH_001)-(?:HAPPY|REJECT|IDEMPOTENT)|TST-V122-004)$"
)
METHOD_PATTERN = r"\b(?:void|fun)\s+{method}\s*\("
SHA_PATTERN = re.compile(r"^[0-9a-f]{40,64}$", re.IGNORECASE)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$", re.IGNORECASE)
REQUIRED_ARTIFACTS = {"backend-java21.log", "postgresql17.log", "android-module.log"}


def _inside(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def load_inventory() -> tuple[list[str], list[str]]:
    errors: list[str] = []
    manifest = yaml.safe_load(MANIFEST.read_text(encoding="utf-8")) or {}
    declared = manifest.get("tests", [])
    inventory = [item for item in declared if isinstance(item, str) and TEST_ID_PATTERN.fullmatch(item)]
    if len(inventory) != len(EXPECTED_IDS) or set(inventory) != EXPECTED_IDS:
        errors.append(
            f"R07 inventory mismatch expected={sorted(EXPECTED_IDS)} actual={sorted(set(inventory))}"
        )

    with CATALOG.open(encoding="utf-8-sig", newline="") as handle:
        rows = {row.get("测试ID", "").strip(): row for row in csv.DictReader(handle)}
    root = ROOT.resolve()
    for test_id in inventory:
        row = rows.get(test_id)
        if row is None:
            errors.append(f"catalog row missing: {test_id}")
            continue
        if row.get("计划版本", "").strip() != "R07":
            errors.append(f"catalog release mismatch: {test_id}")
        if row.get("状态", "").strip() != "AUTOMATED":
            errors.append(f"catalog test is not automated: {test_id}")
        raw_adapter = row.get("自动化路径", "").strip().replace("\\", "/")
        adapter_path = (root / raw_adapter).resolve()
        if not raw_adapter or not _inside(adapter_path, root) or not adapter_path.is_file():
            errors.append(f"adapter missing or unsafe for {test_id}: {raw_adapter!r}")
            continue
        try:
            adapter = json.loads(adapter_path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as failure:
            errors.append(f"adapter invalid for {test_id}: {failure}")
            continue
        expected = {
            "schema": ADAPTER_SCHEMA,
            "test_id": test_id,
            "central_runner": "scripts/run_r07_specialized_matrix.py",
            "runner": RUNNER,
        }
        for key, value in expected.items():
            if adapter.get(key) != value:
                errors.append(f"adapter {test_id} {key} mismatch")
        selectors = adapter.get("selectors")
        if not isinstance(selectors, list) or not selectors:
            errors.append(f"adapter {test_id} selectors must be non-empty")
            continue
        for selector in selectors:
            if not isinstance(selector, str) or "#" not in selector:
                errors.append(f"adapter {test_id} selector is invalid: {selector!r}")
                continue
            raw_source, method = selector.rsplit("#", 1)
            source = (root / raw_source).resolve()
            if not _inside(source, root) or not source.is_file():
                errors.append(f"adapter {test_id} source is missing: {raw_source}")
                continue
            text = source.read_text(encoding="utf-8")
            pattern = METHOD_PATTERN.format(method=re.escape(method))
            if not re.search(pattern, text):
                errors.append(f"adapter {test_id} method is missing: {method}")
        faults = adapter.get("fault_classes")
        if not isinstance(faults, list) or not faults or not all(isinstance(item, str) and item for item in faults):
            errors.append(f"adapter {test_id} fault_classes must be non-empty strings")

    screen_text = SCREEN.read_text(encoding="utf-8")
    for token in (
        'Modifier.testTag("hhy.dialog.r07.search-history-clear")',
        'Text("清空搜索历史？")',
        'intentKeys.key("clear-history", "current-account")',
        'intentKeys.complete("clear-history")',
        "api.clearHistory(accessToken, key)",
    ):
        if token not in screen_text:
            errors.append(f"DIALOG-SEARCH-001 contract token missing: {token}")
    return inventory, errors


def _git(*args: str) -> subprocess.CompletedProcess[str]:
    git = os.environ.get("HHY_GIT_BIN") or os.environ.get("HHY_GIT") or "git"
    command = [git, *args]
    try:
        return subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    except OSError as failure:
        return subprocess.CompletedProcess(command, 127, "", str(failure))


def _hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_evidence(path: Path, inventory: list[str]) -> list[str]:
    errors: list[str] = []
    try:
        evidence = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        return [f"cannot load evidence: {failure}"]
    if evidence.get("schema") != EVIDENCE_SCHEMA:
        errors.append(f"evidence schema must be {EVIDENCE_SCHEMA}")
    if evidence.get("suite") != RUNNER:
        errors.append(f"evidence suite must be {RUNNER}")
    source_commit = str(evidence.get("source_commit", ""))
    if not SHA_PATTERN.fullmatch(source_commit):
        errors.append("evidence source_commit is invalid")
    else:
        ancestor = _git("merge-base", "--is-ancestor", source_commit, "HEAD")
        if ancestor.returncode != 0:
            errors.append("evidence source_commit is not an ancestor of HEAD")
        changed = _git(
            "diff", "--name-only", f"{source_commit}..HEAD", "--",
            "services/backend", "apps/android/feature/discovery", "tests/r07",
            "scripts/run_r07_specialized_matrix.py", "catalogs/test_cases.csv",
            "releases/R07/RELEASE_MANIFEST.yaml", "releases/R07/TASKS.yaml",
        )
        if changed.returncode != 0 or changed.stdout.strip():
            errors.append("R07 specialized evidence is stale for executable or catalog inputs")
    environment = evidence.get("environment")
    if not isinstance(environment, dict):
        errors.append("evidence environment must be an object")
    else:
        if "21" not in str(environment.get("java", "")):
            errors.append("evidence requires Java 21")
        if "17" not in str(environment.get("postgresql", "")):
            errors.append("evidence requires PostgreSQL 17")
        if "21" not in str(environment.get("android_gradle_jdk", "")):
            errors.append("evidence requires Android Gradle JDK 21")
    commands = evidence.get("commands")
    if not isinstance(commands, list) or not commands or not all(isinstance(item, str) and item for item in commands):
        errors.append("evidence commands must be non-empty strings")
    results = evidence.get("results")
    if not isinstance(results, list):
        return errors + ["evidence results must be a list"]
    by_id = {item.get("test_id"): item for item in results if isinstance(item, dict)}
    if set(by_id) != set(inventory) or len(results) != len(inventory):
        errors.append("evidence test IDs do not exactly match the ten-test inventory")
    evidence_root = path.parent.resolve()
    seen_artifacts: set[str] = set()
    for test_id in inventory:
        result = by_id.get(test_id, {})
        if result.get("status") != "PASS" or result.get("exit_code") != 0:
            errors.append(f"test did not pass: {test_id}")
        if not isinstance(result.get("assertions"), list) or not result.get("assertions"):
            errors.append(f"test has no assertions: {test_id}")
        artifacts = result.get("artifacts")
        if not isinstance(artifacts, list) or not artifacts:
            errors.append(f"test has no artifacts: {test_id}")
            continue
        for artifact in artifacts:
            if not isinstance(artifact, dict):
                errors.append(f"invalid artifact for {test_id}")
                continue
            raw_path = str(artifact.get("path", ""))
            target = (evidence_root / raw_path).resolve()
            expected_sha = str(artifact.get("sha256", ""))
            seen_artifacts.add(Path(raw_path).name)
            if not _inside(target, evidence_root) or not target.is_file() or target.stat().st_size == 0:
                errors.append(f"artifact is missing for {test_id}: {raw_path}")
            elif not SHA256_PATTERN.fullmatch(expected_sha) or _hash(target) != expected_sha.lower():
                errors.append(f"artifact digest mismatch for {test_id}: {raw_path}")
    missing_artifacts = REQUIRED_ARTIFACTS - seen_artifacts
    if missing_artifacts:
        errors.append(f"required evidence artifacts missing: {sorted(missing_artifacts)}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    inventory, errors = load_inventory()
    if args.check:
        if args.evidence is None:
            errors.append("--check requires --evidence")
        else:
            errors.extend(validate_evidence(args.evidence, inventory))
    result: dict[str, Any] = {
        "schema": "hhy.r07.specialized-matrix-result/v1",
        "release": "R07",
        "executed": args.check,
        "status": "PASS" if not errors else "FAIL",
        "test_count": len(inventory),
        "test_ids": inventory,
        "errors": errors,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
