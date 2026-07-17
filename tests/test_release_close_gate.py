#!/usr/bin/env python3
"""Isolated positive and negative tests for the release-close artifact gate."""
from __future__ import annotations

from pathlib import Path
import csv
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
OPERATIONS = [
    "appReleaseGetAppVersionCheck",
    "publicGetPlatformStatus",
    "appReleasePostAppVersionCheck",
]


def dump_yaml(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )


def load_yaml(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=repo, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60,
    )
    if result.returncode != 0:
        raise AssertionError(result.stdout + "\n" + result.stderr)
    return result.stdout.strip()


def build_fixture(repo: Path) -> str:
    (repo / "scripts").mkdir(parents=True)
    shutil.copy2(ROOT / "scripts/check_release_artifacts.py", repo / "scripts/check_release_artifacts.py")
    (repo / "source.txt").write_text("release code\n", encoding="utf-8")
    git(repo, "init", "-q")
    git(repo, "config", "user.name", "Release Gate Test")
    git(repo, "config", "user.email", "release-gate@example.invalid")
    git(repo, "add", "source.txt")
    git(repo, "commit", "-q", "-m", "test: release code")
    commit = git(repo, "rev-parse", "HEAD")
    git(repo, "tag", "p00-v1.2.3", commit)

    catalog = repo / "catalogs/release_plan.csv"
    catalog.parent.mkdir(parents=True)
    with catalog.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["版本", "Android测试APK"])
        writer.writeheader()
        writer.writerow({"版本": "P00", "Android测试APK": "YES"})

    tasks = [
        {"id": f"TASK-P00-{number:03d}", "title": f"Task {number}", "status": "DONE"}
        for number in range(1, 9)
    ]
    dump_yaml(repo / "releases/P00/TASKS.yaml", {"release": "P00", "tasks": tasks})
    dump_yaml(
        repo / "releases/P00/RELEASE_MANIFEST.yaml",
        {
            "release": "P00",
            "status": "DONE",
            "android_test_apk_required": True,
            "requirements": ["REQ-ENG-001"],
            "operation_ids": OPERATIONS,
            "release_commit": commit,
            "release_tag": "p00-v1.2.3",
        },
    )
    contracts = repo / "contracts/openapi.yaml"
    contracts.parent.mkdir(parents=True)
    contracts.write_text(
        "openapi: 3.1.0\npaths:\n"
        + "".join(
            f"  /test/{index}:\n    get:\n      operationId: {operation}\n"
            for index, operation in enumerate(OPERATIONS, 1)
        ),
        encoding="utf-8",
    )

    evidence_dir = repo / "artifacts/reports/P00"
    evidence_dir.mkdir(parents=True)
    acceptance_rows = []
    for number in range(1, 7):
        acceptance_id = f"AC-P00-{number:03d}"
        evidence = f"artifacts/reports/P00/{acceptance_id}.md"
        (repo / evidence).write_text(f"# {acceptance_id}\n\nPASS\n", encoding="utf-8")
        acceptance_rows.append({
            "验收ID": acceptance_id,
            "版本": "P00",
            "验收项": f"Acceptance {number}",
            "证据路径": evidence,
            "状态": "PASS",
            "阻断级别": "BLOCKING",
        })
    acceptance_path = repo / "releases/P00/ACCEPTANCE_MATRIX.csv"
    with acceptance_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(acceptance_rows[0]))
        writer.writeheader()
        writer.writerows(acceptance_rows)

    apk_bytes = b"isolated-p00-apk"
    apk_dir = repo / "artifacts/apk/P00"
    apk_dir.mkdir(parents=True)
    (apk_dir / "hhy-p00.apk").write_bytes(apk_bytes)
    dump_yaml(
        apk_dir / "APK_MANIFEST.yaml",
        {
            "release": "P00",
            "apk_file": "hhy-p00.apk",
            "version_name": "1.2.3-p00",
            "version_code": 12300,
            "commit": commit,
            "sha256": hashlib.sha256(apk_bytes).hexdigest(),
            "size_bytes": len(apk_bytes),
            "test_status": "PASS",
            "download_url": "https://downloads.example.invalid/hhy-p00.apk",
        },
    )

    dump_yaml(
        repo / "releases/R01/TASKS.yaml",
        {
            "release": "R01",
            "tasks": [
                {"id": "TASK-R01-001", "title": "R01 start", "status": "READY"},
                {"id": "TASK-R01-002", "title": "R01 second", "status": "BLOCKED"},
            ],
        },
    )
    dump_yaml(
        repo / "NEXT_TASK.yaml",
        {"id": "TASK-R01-001", "release": "R01", "status": "READY"},
    )
    dump_yaml(
        repo / "CURRENT_STATUS.yaml",
        {
            "phase": "R01",
            "active_release": "R01",
            "active_task": "TASK-R01-001",
            "next_task": "TASK-R01-001",
            "status": "READY",
            "last_green_commit": commit,
            "completed_tasks": [task["id"] for task in tasks],
            "in_progress_tasks": [],
            "continuity": {"active_session_id": None},
        },
    )
    return commit


class ReleaseCloseGateTest(unittest.TestCase):
    def run_gate(self, repo: Path, *args: str, expected: int) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [sys.executable, "scripts/check_release_artifacts.py", *args],
            cwd=repo,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=120,
        )
        self.assertEqual(result.returncode, expected, result.stdout + "\n" + result.stderr)
        return result

    def fixture(self):
        temp = tempfile.TemporaryDirectory(prefix="hhy-release-close-gate-")
        repo = Path(temp.name) / "repository"
        repo.mkdir()
        commit = build_fixture(repo)
        return temp, repo, commit

    def test_complete_p00_release_passes_close_gate(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            result = self.run_gate(repo, "--close-gate", "--release", "P00", expected=0)
            self.assertIn("RELEASE_CLOSE_GATE_OK P00 tasks=8 acceptance=6 operations=3", result.stdout)

    def test_regular_development_check_does_not_require_terminal_state(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            tasks_path = repo / "releases/P00/TASKS.yaml"
            tasks = load_yaml(tasks_path)
            tasks["tasks"][0]["status"] = "READY"
            dump_yaml(tasks_path, tasks)
            manifest_path = repo / "releases/P00/RELEASE_MANIFEST.yaml"
            manifest = load_yaml(manifest_path)
            manifest["status"] = "READY"
            dump_yaml(manifest_path, manifest)
            result = self.run_gate(repo, expected=0)
            self.assertIn("RELEASE_ARTIFACTS_OK 1", result.stdout)
            close = self.run_gate(repo, "--close-gate", "--release", "P00", expected=1)
            self.assertIn("TASKS_NOT_DONE", close.stdout)
            self.assertIn("MANIFEST_NOT_TERMINAL", close.stdout)

    def test_close_gate_rejects_ac_without_pass_and_real_evidence_file(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            path = repo / "releases/P00/ACCEPTANCE_MATRIX.csv"
            with path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            rows[0]["状态"] = "NOT_RUN"
            missing = repo / rows[1]["证据路径"]
            missing.unlink()
            with path.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            result = self.run_gate(repo, "--close-gate", "--release", "P00", expected=1)
            self.assertIn("AC_NOT_PASS", result.stdout)
            self.assertIn("AC_EVIDENCE_NOT_FILE", result.stdout)

    def test_close_gate_rejects_fake_manifest_and_pending_apk(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            manifest_path = repo / "releases/P00/RELEASE_MANIFEST.yaml"
            manifest = load_yaml(manifest_path)
            manifest.update({
                "status": "READY",
                "operation_ids": OPERATIONS[:2],
                "release_commit": "0" * 40,
                "release_tag": "PENDING",
            })
            dump_yaml(manifest_path, manifest)
            apk_path = repo / "artifacts/apk/P00/APK_MANIFEST.yaml"
            apk = load_yaml(apk_path)
            apk.update({
                "sha256": "PENDING",
                "version_name": "",
                "version_code": 0,
                "commit": "0" * 40,
                "test_status": "PENDING",
                "download_url": "PENDING_UPLOAD",
            })
            dump_yaml(apk_path, apk)
            result = self.run_gate(repo, "--close-gate", "--release", "P00", expected=1)
            for code in [
                "MANIFEST_NOT_TERMINAL", "MANIFEST_OPERATION_COUNT",
                "MANIFEST_COMMIT_INVALID", "MANIFEST_TAG_INVALID", "APK_PENDING_VALUE",
                "APK_SHA_INVALID", "APK_VERSION_NAME_INVALID", "APK_VERSION_CODE_INVALID",
                "APK_COMMIT_INVALID", "APK_TEST_NOT_PASS", "APK_URL_INVALID",
            ]:
                self.assertIn(code, result.stdout)

    def test_close_gate_rejects_current_and_next_task_drift(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            status_path = repo / "CURRENT_STATUS.yaml"
            status = load_yaml(status_path)
            status.update({
                "phase": "P00",
                "active_task": "TASK-P00-008",
                "completed_tasks": status["completed_tasks"][:-1],
                "continuity": {"active_session_id": "SES-FAKE"},
            })
            dump_yaml(status_path, status)
            next_path = repo / "NEXT_TASK.yaml"
            next_task = load_yaml(next_path)
            next_task.update({"id": "TASK-R01-002", "status": "BLOCKED"})
            dump_yaml(next_path, next_task)
            result = self.run_gate(repo, "--close-gate", "--release", "P00", expected=1)
            for code in [
                "NEXT_TASK_NOT_READY", "NEXT_TASK_NOT_FIRST", "CURRENT_NEXT_RELEASE_MISMATCH",
                "CURRENT_NEXT_TASK_MISMATCH", "CURRENT_COMPLETED_TASKS_MISSING",
                "CURRENT_SESSION_STILL_ACTIVE",
            ]:
                self.assertIn(code, result.stdout)


if __name__ == "__main__":
    unittest.main()
