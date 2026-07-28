#!/usr/bin/env python3
"""R14+ repository-evidence release sequencing regression tests."""
from __future__ import annotations

from hashlib import sha256
from pathlib import Path
import inspect
import json
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from continuity import (  # noqa: E402
    ContinuityError,
    command_close,
    command_recover,
    command_resume,
    command_start,
    command_takeover,
    strict_release_machine_completion_errors,
    validate_release_machine_chain,
    validate_sequential_release_number,
)


def write_yaml(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        yaml.safe_dump(payload, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def complete_release(root: Path, release: str, marker: str) -> dict:
    commit = marker * 40
    apk_sha = chr(ord(marker) + 1) * 64
    fingerprint = chr(ord(marker) + 2) * 64
    apk_name = f"hhy-{release.lower()}-{commit[:7]}-debug.apk"
    version_code = 11000 + int(release[1:])
    size = 4096 + int(release[1:])

    write_yaml(
        root / f"releases/{release}/TASKS.yaml",
        {
            "release": release,
            "tasks": [
                {"id": f"TASK-{release}-001", "status": "DONE"},
                {"id": f"TASK-{release}-008", "status": "DONE"},
            ],
        },
    )
    write_yaml(
        root / f"artifacts/apk/{release}/APK_MANIFEST.yaml",
        {
            "release": release,
            "commit": commit,
            "apk_file": apk_name,
            "version_name": "1.2.2-debug",
            "version_code": version_code,
            "sha256": apk_sha,
            "size_bytes": size,
            "signing_fingerprint": fingerprint,
        },
    )
    build_path = f"artifacts/validation/{release.lower()}-test-apk/build-evidence.json"
    delivery_path = f"artifacts/validation/{release.lower()}-test-apk/delivery-evidence.json"
    candidate_path = f"artifacts/validation/{release.lower()}-candidate/candidate-report.json"
    guide_path = f"artifacts/reports/{release}/{release}-version-test-guide.md"
    close_path = f"artifacts/reports/{release}/{release}-machine-close.md"
    write_json(
        root / build_path,
        {
            "release": release,
            "commit": commit,
            "version_name": "1.2.2-debug",
            "version_code": version_code,
            "build_status": "PASS",
            "checks": [
                "verifyApiBaseUrl",
                "testDebugUnitTest",
                "lintDebug",
                "assembleDebug",
                "apksigner",
                "zipalign",
                "embeddedApiBaseUrl",
                "packageIdentity",
            ],
            "stable_signing": True,
            "api_base_url": "https://api.orbexa.cc",
            "apk_sha256": apk_sha,
            "apk_size_bytes": size,
            "signing_profile_id": "hhy-staging-test-v2",
            "signing_fingerprint": fingerprint,
        },
    )
    write_json(
        root / delivery_path,
        {
            "release": release,
            "commit": commit,
            "apk_file": apk_name,
            "version_name": "1.2.2-debug",
            "version_code": version_code,
            "sha256": apk_sha,
            "size_bytes": size,
            "signing": {
                "status": "PASS",
                "stable": True,
                "profile_id": "hhy-staging-test-v2",
                "fingerprint": fingerprint,
            },
            "local": {"status": "PASS"},
            "desktop": {"status": "PASS", "sha256": apk_sha},
            "remote": {"status": "PASS", "sha256": apk_sha, "size_bytes": size},
            "https": {
                "status": "PASS",
                "sha256": apk_sha,
                "size_bytes": size,
                "http_status": 200,
                "range_status": 206,
            },
        },
    )
    candidate = {
        "release": release,
        "commit": commit,
        "status": "PASS",
        "owner_test_allowed": True,
    }
    write_json(root / candidate_path, candidate)
    guide = root / guide_path
    guide.parent.mkdir(parents=True, exist_ok=True)
    guide.write_text("PASS\n", encoding="utf-8")
    close = root / close_path
    close.parent.mkdir(parents=True, exist_ok=True)
    close.write_text("PASS\n", encoding="utf-8")
    write_yaml(
        root / f"releases/{release}/RELEASE_MANIFEST.yaml",
        {
            "release": release,
            "android_delivery": {
                "source_commit": commit,
                "apk_file": apk_name,
                "version_name": "1.2.2-debug",
                "version_code": version_code,
                "sha256": apk_sha,
                "signing_profile_id": "hhy-staging-test-v2",
                "signing_fingerprint": fingerprint,
                "machine_delivery": "PASS",
                "owner_physical_test": "PENDING",
                "next_release_development": "ALLOWED",
                "build_evidence": build_path,
                "evidence": delivery_path,
                "test_guide": guide_path,
            },
            "android_automation": {
                "policy_id": "HHY-ANDROID-AUTOMATION-V1",
                "mode": "MAJOR_RELEASE_MACHINE_CLOSE_REQUIRED",
                "status": "PASS",
                "owner_test_allowed": True,
                "owner_physical_test": "PENDING",
                "next_release_development": "ALLOWED",
                "commit": commit,
                "candidate_report": candidate_path,
                "evidence": candidate_path,
            },
            "machine_completion": {
                "status": "PASS",
                "owner_feedback_mode": "ASYNC_NON_BLOCKING",
                "formal_release_acceptance": "PENDING_OWNER_PHYSICAL_TEST",
                "production_activation": "BLOCKED_OWNER_PHYSICAL_TEST",
                "next_release_development": "ALLOWED",
                "evidence": close_path,
            },
        },
    )
    return {
        "commit": commit,
        "candidate_path": candidate_path,
        "guide_path": guide_path,
    }


def snapshot(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): sha256(path.read_bytes()).hexdigest()
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


class ContinuityVersionSequenceTest(unittest.TestCase):
    def test_r14_complete_allows_r15_and_owner_remains_pending(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-pass-") as directory:
            root = Path(directory)
            complete_release(root, "R14", "a")
            validate_release_machine_chain(root, "R15")

    def test_r14_cannot_skip_directly_to_r16(self) -> None:
        with self.assertRaisesRegex(ContinuityError, "禁止跨版本跳跃"):
            validate_sequential_release_number("R14", "R16")

    def test_r16_requires_both_r14_and_r15_machine_close(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-gap-") as directory:
            root = Path(directory)
            complete_release(root, "R14", "a")
            with self.assertRaisesRegex(ContinuityError, "R15:TASKS_MISSING"):
                validate_release_machine_chain(root, "R16")
            complete_release(root, "R15", "d")
            validate_release_machine_chain(root, "R16")

    def test_every_transition_from_r14_through_r32_must_be_adjacent(self) -> None:
        for current_number in range(14, 32):
            current = f"R{current_number:02d}"
            adjacent = f"R{current_number + 1:02d}"
            with self.subTest(current=current, target=adjacent):
                validate_sequential_release_number(current, adjacent)

            if current_number < 31:
                skipped = f"R{current_number + 2:02d}"
                with self.subTest(current=current, target=skipped):
                    with self.assertRaisesRegex(ContinuityError, "禁止跨版本跳跃"):
                        validate_sequential_release_number(current, skipped)

    def test_r32_requires_every_r14_through_r31_machine_close(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-r32-") as directory:
            root = Path(directory)
            for number in range(14, 32):
                complete_release(root, f"R{number:02d}", "a")

            validate_release_machine_chain(root, "R32")

            tasks_path = root / "releases/R27/TASKS.yaml"
            tasks = yaml.safe_load(tasks_path.read_text(encoding="utf-8"))
            tasks["tasks"][0]["status"] = "BLOCKED"
            write_yaml(tasks_path, tasks)

            with self.assertRaisesRegex(
                ContinuityError,
                "R27:TASK_NOT_DONE:TASK-R27-001",
            ):
                validate_release_machine_chain(root, "R32")

    def test_candidate_release_and_commit_mismatch_fail_without_writes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-candidate-") as directory:
            root = Path(directory)
            facts = complete_release(root, "R14", "a")
            report_path = root / facts["candidate_path"]
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["release"] = "R15"
            report["commit"] = "f" * 40
            write_json(report_path, report)
            before = snapshot(root)
            with self.assertRaisesRegex(ContinuityError, "CANDIDATE_RELEASE_MISMATCH"):
                validate_release_machine_chain(root, "R15")
            self.assertEqual(before, snapshot(root))

    def test_missing_guide_and_incomplete_task_are_both_reported(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-evidence-") as directory:
            root = Path(directory)
            facts = complete_release(root, "R14", "a")
            tasks_path = root / "releases/R14/TASKS.yaml"
            tasks = yaml.safe_load(tasks_path.read_text(encoding="utf-8"))
            tasks["tasks"][0]["status"] = "BLOCKED"
            write_yaml(tasks_path, tasks)
            (root / facts["guide_path"]).unlink()
            errors = strict_release_machine_completion_errors(root, "R14")
            self.assertIn("R14:TASK_NOT_DONE:TASK-R14-001", errors)
            self.assertIn("R14:TEST_GUIDE_MISSING", errors)
            self.assertIn("R14:TEST_APK_IDENTITY_MISMATCH", errors)

    def test_close_may_assume_only_current_final_task_done(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-close-") as directory:
            root = Path(directory)
            complete_release(root, "R14", "a")
            tasks_path = root / "releases/R14/TASKS.yaml"
            tasks = yaml.safe_load(tasks_path.read_text(encoding="utf-8"))
            tasks["tasks"][-1]["status"] = "IN_PROGRESS"
            write_yaml(tasks_path, tasks)
            validate_release_machine_chain(
                root,
                "R15",
                assumed_done={("R14", "TASK-R14-008")},
            )
            with self.assertRaisesRegex(ContinuityError, "TASK-R14-008"):
                validate_release_machine_chain(root, "R15")

    def test_all_continuation_entrypoints_call_gate_before_mutation(self) -> None:
        checks = {
            command_start: "create_session(",
            command_resume: "build_context_pack(",
            command_takeover: "terminate_old_session(",
            command_recover: "terminate_old_session(",
            command_close: 'session["status"] = "CLOSING"',
        }
        for function, first_mutation in checks.items():
            source = inspect.getsource(function)
            with self.subTest(function=function.__name__):
                self.assertIn("validate_release_machine_chain", source)
                self.assertLess(
                    source.index("validate_release_machine_chain"),
                    source.index(first_mutation),
                )

    def test_current_r14_session_is_legal_without_r13_rewrite(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-r14-") as directory:
            validate_release_machine_chain(Path(directory), "R14")


if __name__ == "__main__":
    unittest.main()
