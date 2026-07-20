from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import json
import unittest
import yaml

from scripts.android_ci_gate import analyze, finalize, is_enforced_release, load_policy


ROOT = Path(__file__).resolve().parents[1]


class AndroidCiGateTest(unittest.TestCase):
    def test_policy_enforces_from_r06_and_blocks_early_owner_test(self) -> None:
        policy = load_policy()
        self.assertFalse(is_enforced_release(policy, "R05"))
        self.assertTrue(is_enforced_release(policy, "R06"))
        self.assertTrue(policy["delivery"]["forbid_owner_request_before_pass"])
        self.assertTrue(policy["delivery"]["desktop_copy_after_actions_pass"])
        self.assertTrue(policy["delivery"]["desktop_test_guide_required"])

    def test_runtime_failure_creates_remediation_queue(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "exit.txt").write_text("1\n", encoding="utf-8")
            (root / "logcat.txt").write_text("FATAL EXCEPTION: main\n", encoding="utf-8")
            (root / "junit").mkdir()
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R05", commit="a" * 40,
                attempt=1, test_exit_code_file=str(root / "exit.txt"), junit_root=str(root / "junit"),
                logcat=str(root / "logcat.txt"), screenshots=str(root / "screenshots"),
                baseline_root=str(root / "baseline"), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(1, result)
            self.assertEqual("FAIL", payload["status"])
            self.assertEqual("REMEDIATION_REQUIRED", payload["remediation"]["status"])
            self.assertFalse(payload["owner_test_allowed"])

    def test_grandfathered_runtime_pass_produces_candidate_eligible_report(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "exit.txt").write_text("0\n", encoding="utf-8")
            (root / "logcat.txt").write_text("I HHY candidate started\n", encoding="utf-8")
            junit = root / "junit"
            junit.mkdir()
            (junit / "TEST-smoke.xml").write_text(
                '<testsuite tests="1" failures="0" errors="0"/>', encoding="utf-8",
            )
            screenshots = root / "screenshots"
            screenshots.mkdir()
            for index in range(4):
                (screenshots / f"0{index + 1}.png").write_bytes(b"evidence")
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R05", commit="a" * 40,
                attempt=1, test_exit_code_file=str(root / "exit.txt"), junit_root=str(junit),
                logcat=str(root / "logcat.txt"), screenshots=str(screenshots),
                baseline_root=str(root / "baseline"), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("PASS", payload["status"])
            self.assertTrue(payload["candidate_eligible"])
            self.assertTrue(payload["owner_test_allowed"])

    def test_finalize_rejects_candidate_without_runtime_pass(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            runtime = root / "runtime.json"
            runtime.write_text(json.dumps({"status": "FAIL", "owner_test_allowed": False}), encoding="utf-8")
            apk = root / "app.apk"
            apk.write_bytes(b"apk")
            output = root / "candidate.json"
            result = finalize(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R06", commit="b" * 40,
                run_id="123", build_result="success", emulator_report=str(runtime), apk=str(apk), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(1, result)
            self.assertEqual("FAIL", payload["status"])
            self.assertFalse(payload["owner_test_allowed"])

    def test_finalize_rejects_stale_runtime_evidence(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            runtime = root / "runtime.json"
            runtime.write_text(json.dumps({
                "policy_id": "HHY-ANDROID-AUTOMATION-V1",
                "release": "R06",
                "commit": "c" * 40,
                "status": "PASS",
                "owner_test_allowed": True,
            }), encoding="utf-8")
            apk = root / "app.apk"
            apk.write_bytes(b"apk")
            output = root / "candidate.json"
            result = finalize(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R06", commit="d" * 40,
                run_id="123", build_result="success", emulator_report=str(runtime), apk=str(apk), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(1, result)
            self.assertIn("emulator/runtime commit does not match", payload["errors"])

    def test_finalize_allows_only_matching_green_evidence(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            commit = "e" * 40
            runtime = root / "runtime.json"
            runtime.write_text(json.dumps({
                "policy_id": "HHY-ANDROID-AUTOMATION-V1",
                "release": "R06",
                "commit": commit,
                "status": "PASS",
                "owner_test_allowed": True,
            }), encoding="utf-8")
            apk = root / "app.apk"
            apk.write_bytes(b"verified-apk")
            output = root / "candidate.json"
            result = finalize(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R06", commit=commit,
                run_id="456", build_result="success", emulator_report=str(runtime), apk=str(apk), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("PASS", payload["status"])
            self.assertTrue(payload["owner_test_allowed"])
            self.assertFalse(payload["release_completion_allowed"])

    def test_workflow_contains_every_required_stage_and_bounded_gradle(self) -> None:
        workflow_path = ROOT / ".github/workflows/android-quality-gate.yml"
        workflow = yaml.load(workflow_path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
        self.assertIn("workflow_call", workflow["on"])
        self.assertIn("workflow_dispatch", workflow["on"])
        self.assertEqual(
            {"build", "emulator", "candidate", "remediation-queue"},
            set(workflow["jobs"]),
        )
        source = workflow_path.read_text(encoding="utf-8")
        self.assertIn("platforms;android-36", source)
        self.assertNotIn("platforms;android-37", source)
        self.assertIn("git config core.quotepath false", source)
        self.assertIn("--no-parallel --max-workers=1", source)
        self.assertIn("-Xmx1536m", source)
        self.assertIn("android_ci_gate.py finalize", source)
        self.assertIn("script: bash scripts/run_android_emulator_gate.sh", source)
        self.assertIn("! -name '*androidTest*'", source)
        self.assertIn("path: candidate-output", source)
        self.assertRegex(
            source,
            r"reactivecircus/android-emulator-runner@[0-9a-f]{40}",
        )
        emulator_action = next(
            step for step in workflow["jobs"]["emulator"]["steps"]
            if str(step.get("uses", "")).startswith("reactivecircus/android-emulator-runner@")
        )
        self.assertEqual(
            "bash scripts/run_android_emulator_gate.sh",
            emulator_action["with"]["script"],
        )

    def test_main_ci_delegates_android_to_the_reusable_quality_gate(self) -> None:
        ci_source = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("./.github/workflows/android-quality-gate.yml", ci_source)

    def test_main_ci_pins_node_for_python_gates_and_keeps_diagnostics(self) -> None:
        ci_source = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertGreaterEqual(ci_source.count("uses: actions/setup-node@v4"), 3)
        self.assertGreaterEqual(ci_source.count("node-version: '24'"), 3)
        self.assertIn("tooling-tests.log", ci_source)
        self.assertIn("contracts.log", ci_source)
        self.assertGreaterEqual(ci_source.count("uses: actions/upload-artifact@v4"), 2)

    def test_emulator_gate_script_is_single_process_and_preserves_evidence(self) -> None:
        source = (ROOT / "scripts/run_android_emulator_gate.sh").read_text(encoding="utf-8")
        smoke_test = (
            ROOT
            / "apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("connectedDebugAndroidTest", source)
        self.assertIn("test_rc=${PIPESTATUS[0]}", source)
        self.assertIn("android_ci_gate.py analyze", source)
        self.assertIn("adb logcat", source)
        self.assertIn("runtime-report.json", source)
        self.assertIn("::error title=Android emulator gate failed::", source)
        self.assertIn("/sdcard/Pictures/hhy-ci-screenshots", source)
        self.assertNotIn("/sdcard/Android/data/", source)
        self.assertIn("androidTest-results/connected", source)
        self.assertIn("MediaStore.Images.Media.EXTERNAL_CONTENT_URI", smoke_test)
        self.assertIn("MediaStore.Images.Media.RELATIVE_PATH", smoke_test)
        self.assertIn("Pictures/hhy-ci-screenshots", smoke_test)
        self.assertNotIn("getExternalFilesDir", smoke_test)
        self.assertNotIn("executeShellCommand", smoke_test)

    def test_every_android_module_uses_the_stable_compile_sdk(self) -> None:
        module_builds = sorted((ROOT / "apps/android").glob("**/build.gradle.kts"))
        compile_sdk_builds = [
            path for path in module_builds
            if "compileSdk" in path.read_text(encoding="utf-8")
        ]
        self.assertTrue(compile_sdk_builds)
        for path in compile_sdk_builds:
            source = path.read_text(encoding="utf-8")
            self.assertIn("compileSdk = 36", source, path.as_posix())
            self.assertNotIn("compileSdk = 37", source, path.as_posix())


if __name__ == "__main__":
    unittest.main()
