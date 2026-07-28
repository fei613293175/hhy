#!/usr/bin/env python3
"""Isolated positive and negative tests for the release-close artifact gate."""
from __future__ import annotations

from pathlib import Path
import csv
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import check_release_artifacts as release_gate  # noqa: E402


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
    executable = os.environ.get("HHY_GIT_BIN") or shutil.which("git") or "git"
    result = subprocess.run(
        [executable, *args], cwd=repo, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60,
    )
    if result.returncode != 0:
        raise AssertionError(result.stdout + "\n" + result.stderr)
    return result.stdout.strip()


def build_fixture(repo: Path) -> str:
    (repo / "scripts").mkdir(parents=True)
    shutil.copy2(ROOT / "scripts/check_release_artifacts.py", repo / "scripts/check_release_artifacts.py")
    shutil.copy2(ROOT / "scripts/check_ui_visual_acceptance.py", repo / "scripts/check_ui_visual_acceptance.py")
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

    page_catalog = repo / "catalogs/ui_page_specifications.csv"
    with page_catalog.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["页面ID", "平台", "页面名称", "计划版本"])
        writer.writeheader()
        writer.writerow({"页面ID": "SCR-P00-001", "平台": "ANDROID", "页面名称": "P00测试页", "计划版本": "P00"})

    token = repo / "design/tokens/hhy_design_tokens_v1.2.2.json"
    token.parent.mkdir(parents=True)
    token.write_text("{}\n", encoding="utf-8")
    implementation = repo / "apps/android/feature/p00/P00Screen.kt"
    implementation.parent.mkdir(parents=True)
    implementation.write_text("// visual fixture\n", encoding="utf-8")
    reference = repo / "design/effect-previews/B12/HHY_B12_8PAGE_UI_REFERENCE.png"
    reference.parent.mkdir(parents=True)
    reference.write_bytes(b"reference")
    (reference.parent / "HHY_B12_MANIFEST.json").write_text(
        '{"panels":[{"panel":"P04","name":"P00"}]}\n', encoding="utf-8"
    )
    screenshot = repo / "artifacts/validation/p00-ui/SCR-P00-001.png"
    screenshot.parent.mkdir(parents=True)
    screenshot.write_bytes(b"screenshot")
    visual_path = repo / "catalogs/ui_visual_acceptance.csv"
    visual_fields = [
        "页面ID", "计划版本", "平台", "页面名称", "视觉来源", "覆盖状态",
        "布局建模约束", "业务过滤说明", "Token源", "实现路径", "参考证据",
        "实现截图证据", "验收状态", "说明",
    ]
    with visual_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=visual_fields)
        writer.writeheader()
        writer.writerow({
            "页面ID": "SCR-P00-001",
            "计划版本": "P00",
            "平台": "ANDROID",
            "页面名称": "P00测试页",
            "视觉来源": "B12/P04",
            "覆盖状态": "EXACT",
            "布局建模约束": "按精确面板还原",
            "业务过滤说明": "过滤占位数据",
            "Token源": "design/tokens/hhy_design_tokens_v1.2.2.json",
            "实现路径": "apps/android/feature/p00/P00Screen.kt",
            "参考证据": "design/effect-previews/B12/HHY_B12_8PAGE_UI_REFERENCE.png",
            "实现截图证据": "artifacts/validation/p00-ui/SCR-P00-001.png",
            "验收状态": "PASS",
            "说明": "逐项核对通过；肉眼丰富度=PASS；信息层级=PASS；组件精致度=PASS；真实业务映射=PASS；状态完整性=PASS；AI对照结论=PASS",
        })

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


def promote_fixture_to_r06(repo: Path, candidate_commit: str) -> str:
    (repo / "releases/P00").rename(repo / "releases/R06")
    (repo / "artifacts/apk/P00").rename(repo / "artifacts/apk/R06")
    (repo / "artifacts/reports/P00").rename(repo / "artifacts/reports/R06")
    (repo / "releases/R01").rename(repo / "releases/R07")

    with (repo / "catalogs/release_plan.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["版本", "Android测试APK"])
        writer.writeheader()
        writer.writerow({"版本": "R06", "Android测试APK": "YES"})
    for relative in ["catalogs/ui_page_specifications.csv", "catalogs/ui_visual_acceptance.csv"]:
        path = repo / relative
        text = path.read_text(encoding="utf-8-sig").replace("P00", "R06")
        path.write_text(text, encoding="utf-8-sig")
    (repo / "apps/android/feature/p00/P00Screen.kt").rename(repo / "apps/android/feature/p00/R06Screen.kt")
    (repo / "artifacts/validation/p00-ui/SCR-P00-001.png").rename(repo / "artifacts/validation/p00-ui/SCR-R06-001.png")

    tasks_path = repo / "releases/R06/TASKS.yaml"
    tasks = load_yaml(tasks_path)
    tasks["release"] = "R06"
    for number, task in enumerate(tasks["tasks"], 1):
        task["id"] = f"TASK-R06-{number:03d}"
    dump_yaml(tasks_path, tasks)

    acceptance_path = repo / "releases/R06/ACCEPTANCE_MATRIX.csv"
    with acceptance_path.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    for number, row in enumerate(rows, 1):
        row["验收ID"] = f"AC-R06-{number:03d}"
        row["版本"] = "R06"
        row["证据路径"] = row["证据路径"].replace("/P00/", "/R06/")
    with acceptance_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    next_tasks_path = repo / "releases/R07/TASKS.yaml"
    next_tasks = load_yaml(next_tasks_path)
    next_tasks["release"] = "R07"
    for number, task in enumerate(next_tasks["tasks"], 1):
        task["id"] = f"TASK-R07-{number:03d}"
    dump_yaml(next_tasks_path, next_tasks)

    (repo / "source.txt").write_text("release code\nclosure evidence\n", encoding="utf-8")
    git(repo, "add", "source.txt")
    git(repo, "commit", "-q", "-m", "docs: close release")
    release_commit = git(repo, "rev-parse", "HEAD")
    git(repo, "tag", "r06-v1.2.3", release_commit)

    apk_path = repo / "artifacts/apk/R06/APK_MANIFEST.yaml"
    apk = load_yaml(apk_path)
    apk.update({"release": "R06", "commit": candidate_commit})
    dump_yaml(apk_path, apk)
    report_path = repo / "artifacts/validation/r06-android/candidate-report.json"
    report_path.parent.mkdir(parents=True)
    report_path.write_text(json.dumps({
        "status": "PASS", "owner_test_allowed": True, "commit": candidate_commit,
        "apk": {"sha256": apk["sha256"]},
    }), encoding="utf-8")

    manifest_path = repo / "releases/R06/RELEASE_MANIFEST.yaml"
    manifest = load_yaml(manifest_path)
    manifest.update({
        "release": "R06", "release_commit": release_commit, "release_tag": "r06-v1.2.3",
        "android_delivery": {"source_commit": candidate_commit},
        "android_automation": {
            "policy_id": "HHY-ANDROID-AUTOMATION-V1", "status": "PASS",
            "owner_test_allowed": True, "owner_physical_test": "PASS",
            "commit": candidate_commit,
            "candidate_report": "artifacts/validation/r06-android/candidate-report.json",
        },
    })
    dump_yaml(manifest_path, manifest)

    dump_yaml(repo / "NEXT_TASK.yaml", {"id": "TASK-R07-001", "release": "R07", "status": "READY"})
    dump_yaml(repo / "CURRENT_STATUS.yaml", {
        "phase": "R07", "active_release": "R07", "active_task": "TASK-R07-001",
        "next_task": "TASK-R07-001", "status": "READY", "last_green_commit": release_commit,
        "completed_tasks": [f"TASK-R06-{number:03d}" for number in range(1, 9)],
        "in_progress_tasks": [], "continuity": {"active_session_id": None},
    })
    return release_commit


def configure_on_demand_test_apk(repo: Path, candidate_commit: str) -> None:
    apk_path = repo / "artifacts/apk/R06/APK_MANIFEST.yaml"
    apk = load_yaml(apk_path)
    apk.update({"test_status": "PENDING", "owner_physical_test": "PENDING"})
    dump_yaml(apk_path, apk)

    guide = repo / "artifacts/reports/R06/R06-version-test-guide.md"
    guide.write_text("# R06 TEST_APK\n\nOwner verification is asynchronous.\n", encoding="utf-8")
    build_path = repo / "artifacts/validation/r06-test-apk/build-evidence.json"
    build_path.parent.mkdir(parents=True, exist_ok=True)
    build_path.write_text(json.dumps({
        "release": "R06",
        "commit": candidate_commit,
        "version_name": apk["version_name"],
        "version_code": apk["version_code"],
        "build_status": "PASS",
        "checks": sorted(release_gate.REQUIRED_TEST_APK_CHECKS),
        "stable_signing": True,
        "signing_profile_id": "hhy-staging-test-v2",
        "signing_fingerprint": "b" * 64,
        "api_base_url": "https://api.orbexa.cc",
        "apk_sha256": apk["sha256"],
        "apk_size_bytes": apk["size_bytes"],
    }), encoding="utf-8")
    delivery_path = repo / "artifacts/validation/r06-test-apk/delivery-evidence.json"
    delivery_path.write_text(json.dumps({
        "release": "R06",
        "commit": candidate_commit,
        "apk_file": apk["apk_file"],
        "version_name": apk["version_name"],
        "version_code": apk["version_code"],
        "sha256": apk["sha256"],
        "size_bytes": apk["size_bytes"],
        "signing": {
            "status": "PASS", "stable": True,
            "profile_id": "hhy-staging-test-v2", "fingerprint": "b" * 64,
        },
        "local": {"status": "PASS"},
        "desktop": {"status": "PASS", "sha256": apk["sha256"]},
        "remote": {"status": "PASS", "sha256": apk["sha256"], "size_bytes": apk["size_bytes"]},
        "https": {
            "status": "PASS", "sha256": apk["sha256"], "size_bytes": apk["size_bytes"],
            "http_status": 200, "range_status": 206,
        },
    }), encoding="utf-8")

    apk["signing_fingerprint"] = "b" * 64
    dump_yaml(apk_path, apk)
    manifest_path = repo / "releases/R06/RELEASE_MANIFEST.yaml"
    manifest = load_yaml(manifest_path)
    manifest.update({"status": "MACHINE_COMPLETE_OWNER_PENDING"})
    manifest["android_delivery"] = {
        "source_commit": candidate_commit,
        "apk_file": apk["apk_file"],
        "version_name": apk["version_name"],
        "version_code": apk["version_code"],
        "sha256": apk["sha256"],
        "signing_profile_id": "hhy-staging-test-v2",
        "signing_fingerprint": "b" * 64,
        "machine_delivery": "PASS",
        "owner_physical_test": "PENDING",
        "next_release_development": "ALLOWED",
        "build_evidence": "artifacts/validation/r06-test-apk/build-evidence.json",
        "evidence": "artifacts/validation/r06-test-apk/delivery-evidence.json",
        "test_guide": "artifacts/reports/R06/R06-version-test-guide.md",
    }
    manifest["android_automation"] = {
        "policy_id": "HHY-ANDROID-AUTOMATION-V1",
        "mode": release_gate.ON_DEMAND_ANDROID_MODE,
        "status": "NOT_RUN_NOT_REQUIRED_FOR_TEST_APK",
        "owner_physical_test": "PENDING",
        "next_release_development": "ALLOWED",
    }
    dump_yaml(manifest_path, manifest)


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

    def schema_three_guide_fixture(self, repo: Path) -> tuple[dict, str, dict[str, Path]]:
        commit = "a" * 40
        guide_name = f"hhy-r14-{commit[:7]}-test-guide.md"
        source = repo / "artifacts/reports/R14/R14-version-test-guide.md"
        desktop = repo.parent / "Desktop" / guide_name
        source.parent.mkdir(parents=True, exist_ok=True)
        desktop.parent.mkdir(parents=True, exist_ok=True)
        source.write_text("# R14 test guide\n\nInstall and verify the frozen R14 paths.\n", encoding="utf-8")
        shutil.copy2(source, desktop)
        guide_size = source.stat().st_size
        guide_sha = hashlib.sha256(source.read_bytes()).hexdigest()
        guide = {
            "source_path": "artifacts/reports/R14/R14-version-test-guide.md",
            "desktop_path": str(desktop),
            "file_name": guide_name,
            "size_bytes": guide_size,
            "sha256": guide_sha,
            "status": "PASS",
        }
        apk = {
            "manifest_schema": 3,
            "release": "R14",
            "commit": commit,
            "apk_file": "hhy-r14-aaaaaaa-debug.apk",
            "version_name": "1.2.2-debug",
            "version_code": 10214,
            "sha256": "c" * 64,
            "size_bytes": 1234,
            "signing_fingerprint": "b" * 64,
            "test_guide": dict(guide),
        }
        apk_path = repo / "artifacts/apk/R14/APK_MANIFEST.yaml"
        dump_yaml(apk_path, apk)
        build_path = repo / "artifacts/validation/r14-test-apk/build-evidence.json"
        build_path.parent.mkdir(parents=True, exist_ok=True)
        build_path.write_text(json.dumps({
            "release": "R14",
            "commit": commit,
            "build_status": "PASS",
            "checks": sorted(release_gate.REQUIRED_TEST_APK_CHECKS),
            "stable_signing": True,
            "api_base_url": "https://api.orbexa.cc",
            "version_name": apk["version_name"],
            "version_code": apk["version_code"],
            "apk_sha256": apk["sha256"],
            "apk_size_bytes": apk["size_bytes"],
            "signing_fingerprint": apk["signing_fingerprint"],
            "signing_profile_id": "hhy-staging-test-v2",
        }), encoding="utf-8")
        evidence_path = repo / "artifacts/validation/r14-test-apk/delivery-evidence.json"
        evidence_guide = {**guide, "verified_at": "2026-07-28T12:20:56Z"}
        evidence_path.write_text(json.dumps({
            "schema_version": 2,
            "release": "R14",
            "commit": commit,
            "apk_file": apk["apk_file"],
            "version_name": apk["version_name"],
            "version_code": apk["version_code"],
            "sha256": apk["sha256"],
            "size_bytes": apk["size_bytes"],
            "signing": {
                "status": "PASS",
                "stable": True,
                "profile_id": "hhy-staging-test-v2",
                "fingerprint": apk["signing_fingerprint"],
            },
            "local": {"status": "PASS"},
            "desktop": {"status": "PASS", "sha256": apk["sha256"]},
            "remote": {"status": "PASS", "sha256": apk["sha256"], "size_bytes": apk["size_bytes"]},
            "https": {
                "status": "PASS",
                "sha256": apk["sha256"],
                "size_bytes": apk["size_bytes"],
                "http_status": 200,
                "range_status": 206,
            },
            "test_guide": evidence_guide,
        }), encoding="utf-8")
        manifest = {
            "android_delivery": {
                "source_commit": commit,
                "apk_file": apk["apk_file"],
                "version_name": apk["version_name"],
                "version_code": apk["version_code"],
                "sha256": apk["sha256"],
                "signing_profile_id": "hhy-staging-test-v2",
                "signing_fingerprint": apk["signing_fingerprint"],
                "machine_delivery": "PASS",
                "owner_physical_test": "PENDING",
                "next_release_development": "ALLOWED",
                "build_evidence": "artifacts/validation/r14-test-apk/build-evidence.json",
                "evidence": "artifacts/validation/r14-test-apk/delivery-evidence.json",
                "test_guide": guide["source_path"],
                "desktop_test_guide": {
                    key: guide[key]
                    for key in ("desktop_path", "file_name", "size_bytes", "sha256", "status")
                },
            }
        }
        return manifest, commit, {
            "source": source,
            "desktop": desktop,
            "apk": apk_path,
            "evidence": evidence_path,
        }

    def test_complete_p00_release_passes_close_gate(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            result = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=0)
            self.assertIn("RELEASE_PRODUCTION_CLOSE_GATE_OK P00 tasks=8 acceptance=6 operations=3", result.stdout)

    def test_close_gate_accepts_all_declared_operations_instead_of_fixed_count(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            extra = "adminContentGetContents"
            manifest_path = repo / "releases/P00/RELEASE_MANIFEST.yaml"
            manifest = load_yaml(manifest_path)
            manifest["operation_ids"].append(extra)
            dump_yaml(manifest_path, manifest)
            with (repo / "contracts/openapi.yaml").open("a", encoding="utf-8") as handle:
                handle.write(f"  /test/extra:\n    get:\n      operationId: {extra}\n")
            result = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=0)
            self.assertIn("operations=4", result.stdout)

    def test_r06_candidate_commit_may_precede_release_closure_commit(self) -> None:
        temp, repo, candidate_commit = self.fixture()
        with temp:
            release_commit = promote_fixture_to_r06(repo, candidate_commit)
            self.assertNotEqual(candidate_commit, release_commit)
            result = self.run_gate(repo, "--production-close-gate", "--release", "R06", expected=0)
            self.assertIn("RELEASE_PRODUCTION_CLOSE_GATE_OK R06", result.stdout)

    def test_machine_close_accepts_owner_pending_without_release_tag(self) -> None:
        temp, repo, candidate_commit = self.fixture()
        with temp:
            promote_fixture_to_r06(repo, candidate_commit)
            manifest_path = repo / "releases/R06/RELEASE_MANIFEST.yaml"
            manifest = load_yaml(manifest_path)
            manifest.update({
                "status": "MACHINE_COMPLETE_OWNER_PENDING",
                "operation_ids": [],
                "release_commit": None,
                "release_tag": None,
            })
            manifest["android_automation"]["owner_physical_test"] = "PENDING"
            dump_yaml(manifest_path, manifest)
            apk_path = repo / "artifacts/apk/R06/APK_MANIFEST.yaml"
            apk = load_yaml(apk_path)
            apk.update({"test_status": "PENDING", "owner_physical_test": "PENDING"})
            dump_yaml(apk_path, apk)
            report_path = repo / "artifacts/validation/r06-android/candidate-report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            source_sha = "a" * 64
            report["apk"]["sha256"] = source_sha
            report_path.write_text(json.dumps(report), encoding="utf-8")
            report_path.with_name("build-evidence.json").write_text(json.dumps({
                "release": "R06",
                "commit": candidate_commit,
                "build_status": "PASS",
                "stable_signing": True,
                "source_candidate_apk_sha256": source_sha,
                "apk_sha256": apk["sha256"],
                "apk_size_bytes": apk["size_bytes"],
            }), encoding="utf-8")

            machine = self.run_gate(repo, "--machine-close-gate", "--release", "R06", expected=0)
            self.assertIn("RELEASE_MACHINE_CLOSE_GATE_OK R06", machine.stdout)
            production = self.run_gate(repo, "--production-close-gate", "--release", "R06", expected=1)
            self.assertIn("ANDROID_OWNER_TEST_NOT_PASS", production.stdout)

    def test_machine_close_accepts_complete_on_demand_test_apk_without_candidate(self) -> None:
        temp, repo, candidate_commit = self.fixture()
        with temp:
            promote_fixture_to_r06(repo, candidate_commit)
            configure_on_demand_test_apk(repo, candidate_commit)
            machine = self.run_gate(repo, "--machine-close-gate", "--release", "R06", expected=0)
            self.assertIn("RELEASE_MACHINE_CLOSE_GATE_OK R06", machine.stdout)
            production = self.run_gate(repo, "--production-close-gate", "--release", "R06", expected=1)
            self.assertIn("ANDROID_AUTOMATION_NOT_PASS", production.stdout)

    def test_r14_machine_close_rejects_test_apk_only_mode_without_candidate(self) -> None:
        temp, repo, candidate_commit = self.fixture()
        with temp:
            promote_fixture_to_r06(repo, candidate_commit)
            configure_on_demand_test_apk(repo, candidate_commit)
            manifest = load_yaml(repo / "releases/R06/RELEASE_MANIFEST.yaml")
            original_root = release_gate.ROOT
            try:
                release_gate.ROOT = repo
                gate = release_gate.CloseGate("R14", "machine")
                gate.validate_android_automation(manifest, candidate_commit)
            finally:
                release_gate.ROOT = original_root
            self.assertIn(
                "ANDROID_MAJOR_RELEASE_MODE_INVALID",
                {code for code, _message in gate.errors},
            )
            self.assertIn(
                "ANDROID_AUTOMATION_NOT_PASS",
                {code for code, _message in gate.errors},
            )

    def test_machine_close_rejects_tampered_on_demand_test_apk_evidence(self) -> None:
        cases = [
            ("build-status", "build", lambda value: value.update({"build_status": "FAIL"})),
            ("required-check", "build", lambda value: value.update({"checks": ["assembleDebug"]})),
            ("official-api", "build", lambda value: value.update({"api_base_url": "https://api.example.invalid"})),
            ("stable-signing", "build", lambda value: value.update({"stable_signing": False})),
            ("build-commit", "build", lambda value: value.update({"commit": "c" * 40})),
            ("build-version", "build", lambda value: value.update({"version_code": 1})),
            ("delivery-sha", "delivery", lambda value: value.update({"sha256": "d" * 64})),
            ("delivery-signing", "delivery", lambda value: value["signing"].update({"stable": False})),
            ("local", "delivery", lambda value: value["local"].update({"status": "FAIL"})),
            ("desktop", "delivery", lambda value: value["desktop"].update({"status": "FAIL"})),
            ("remote", "delivery", lambda value: value["remote"].update({"status": "FAIL"})),
            ("https", "delivery", lambda value: value["https"].update({"status": "FAIL"})),
            ("guide", "manifest", lambda value: value["android_delivery"].update({"test_guide": "artifacts/reports/R06/missing.md"})),
        ]
        for name, target, mutate in cases:
            with self.subTest(name=name):
                temp, repo, candidate_commit = self.fixture()
                with temp:
                    promote_fixture_to_r06(repo, candidate_commit)
                    configure_on_demand_test_apk(repo, candidate_commit)
                    paths = {
                        "build": repo / "artifacts/validation/r06-test-apk/build-evidence.json",
                        "delivery": repo / "artifacts/validation/r06-test-apk/delivery-evidence.json",
                        "manifest": repo / "releases/R06/RELEASE_MANIFEST.yaml",
                    }
                    path = paths[target]
                    if path.suffix == ".yaml":
                        document = load_yaml(path)
                        mutate(document)
                        dump_yaml(path, document)
                    else:
                        document = json.loads(path.read_text(encoding="utf-8"))
                        mutate(document)
                        path.write_text(json.dumps(document), encoding="utf-8")
                    result = self.run_gate(repo, "--machine-close-gate", "--release", "R06", expected=1)
                    self.assertIn("TEST_APK_", result.stdout)

    def test_schema_three_desktop_guide_five_way_contract_passes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-guide-gate-") as temporary:
            repo = Path(temporary) / "repository"
            manifest, commit, _paths = self.schema_three_guide_fixture(repo)
            original_root = release_gate.ROOT
            try:
                release_gate.ROOT = repo
                gate = release_gate.CloseGate("R14", "machine")
                gate.validate_test_apk_delivery(manifest, commit)
            finally:
                release_gate.ROOT = original_root
            self.assertEqual([], gate.errors)

    def test_schema_three_desktop_guide_drift_is_rejected(self) -> None:
        mutations = {
            "missing": lambda manifest, paths: paths["desktop"].unlink(),
            "renamed": lambda manifest, paths: paths["desktop"].rename(paths["desktop"].with_name("renamed.md")),
            "empty": lambda manifest, paths: paths["desktop"].write_bytes(b""),
            "size": lambda manifest, paths: paths["desktop"].write_bytes(paths["desktop"].read_bytes() + b"x"),
            "sha": lambda manifest, paths: paths["desktop"].write_bytes(b"x" * paths["desktop"].stat().st_size),
            "source-missing": lambda manifest, paths: paths["source"].unlink(),
            "status": lambda manifest, paths: manifest["android_delivery"]["desktop_test_guide"].update({"status": "FAIL"}),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory(prefix="hhy-guide-gate-") as temporary:
                repo = Path(temporary) / "repository"
                manifest, commit, paths = self.schema_three_guide_fixture(repo)
                mutate(manifest, paths)
                original_root = release_gate.ROOT
                try:
                    release_gate.ROOT = repo
                    gate = release_gate.CloseGate("R14", "machine")
                    gate.validate_test_apk_delivery(manifest, commit)
                finally:
                    release_gate.ROOT = original_root
                self.assertTrue(
                    any(code.startswith("TEST_APK_GUIDE_") for code, _message in gate.errors),
                    gate.errors,
                )

    def test_ambiguous_legacy_close_flag_is_rejected_with_guidance(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            result = self.run_gate(repo, "--close-gate", "--release", "P00", expected=2)
            self.assertIn("AMBIGUOUS_CLOSE_GATE", result.stdout)
            self.assertIn("--machine-close-gate", result.stdout)
            self.assertIn("--production-close-gate", result.stdout)

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
            close = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=1)
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
            result = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=1)
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
            result = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=1)
            for code in [
                "MANIFEST_NOT_TERMINAL",
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
            result = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=1)
            for code in [
                "NEXT_TASK_NOT_READY", "NEXT_TASK_NOT_FIRST", "CURRENT_NEXT_RELEASE_MISMATCH",
                "CURRENT_NEXT_TASK_MISMATCH", "CURRENT_COMPLETED_TASKS_MISSING",
                "CURRENT_SESSION_STILL_ACTIVE",
            ]:
                self.assertIn(code, result.stdout)

    def test_close_gate_rejects_ui_without_exact_visual_pass(self) -> None:
        temp, repo, _commit = self.fixture()
        with temp:
            path = repo / "catalogs/ui_visual_acceptance.csv"
            with path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            rows[0]["视觉来源"] = "B12/P01-P08"
            rows[0]["覆盖状态"] = "PARTIAL"
            rows[0]["验收状态"] = "BLOCKED_REDESIGN"
            rows[0]["实现截图证据"] = ""
            with path.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            result = self.run_gate(repo, "--production-close-gate", "--release", "P00", expected=1)
            for code in [
                "UI_VISUAL_PANEL_RANGE_FORBIDDEN",
                "UI_VISUAL_COVERAGE_NOT_READY",
                "UI_VISUAL_NOT_PASS",
                "UI_VISUAL_SCREENSHOT_EVIDENCE_MISSING",
            ]:
                self.assertIn(code, result.stdout)

    def test_r12_governance_audit_is_commit_and_hash_bound(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-governance-audit-") as temp:
            root = Path(temp)
            evidence = root / "artifacts/reports/R12/R12-governance-drift-audit.md"
            evidence.parent.mkdir(parents=True)
            evidence.write_text("audit pass\n", encoding="utf-8")
            candidate_commit = "a" * 40
            manifest = {
                "governance_audit": {
                    "status": "PASS",
                    "source_commit": candidate_commit,
                    "checks": {key: "PASS" for key in release_gate.GOVERNANCE_AUDIT_CHECKS},
                    "evidence": evidence.relative_to(root).as_posix(),
                    "evidence_sha256": hashlib.sha256(evidence.read_bytes()).hexdigest(),
                }
            }
            original_root = release_gate.ROOT
            release_gate.ROOT = root
            try:
                missing_gate = release_gate.CloseGate("R12", "machine")
                missing_gate.validate_governance_audit({}, candidate_commit)
                self.assertIn("GOVERNANCE_AUDIT_MISSING", {code for code, _ in missing_gate.errors})
                gate = release_gate.CloseGate("R12", "machine")
                gate.validate_governance_audit(manifest, candidate_commit)
                self.assertEqual([], gate.errors)
                evidence.write_text("tampered\n", encoding="utf-8")
                gate = release_gate.CloseGate("R12", "machine")
                gate.validate_governance_audit(manifest, candidate_commit)
                self.assertIn("GOVERNANCE_AUDIT_SHA_MISMATCH", {code for code, _ in gate.errors})
            finally:
                release_gate.ROOT = original_root


if __name__ == "__main__":
    unittest.main()
