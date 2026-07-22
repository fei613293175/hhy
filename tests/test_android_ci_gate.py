from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import json
import hashlib
import unittest
import yaml
from PIL import Image

from scripts.android_ci_gate import analyze, finalize, is_enforced_release, load_policy, promote


ROOT = Path(__file__).resolve().parents[1]


class AndroidCiGateTest(unittest.TestCase):
    def test_policy_enforces_from_r06_and_blocks_early_owner_test(self) -> None:
        policy = load_policy()
        self.assertFalse(is_enforced_release(policy, "R05"))
        self.assertTrue(is_enforced_release(policy, "R06"))
        self.assertTrue(policy["delivery"]["forbid_owner_request_before_pass"])
        self.assertTrue(policy["delivery"]["desktop_copy_after_actions_pass"])
        self.assertTrue(policy["delivery"]["desktop_test_guide_required"])
        self.assertEqual("GITHUB_OIDC_ONE_TIME", policy["authentication"]["mode"])
        self.assertEqual(600, policy["authentication"]["bootstrap_ttl_seconds"])
        self.assertEqual(600, policy["authentication"]["bootstrap_ttl_max_seconds"])
        self.assertEqual(900, policy["authentication"]["session_ttl_seconds"])
        self.assertTrue(policy["authentication"]["consume_once"])
        self.assertFalse(policy["authentication"]["production_enabled"])
        self.assertFalse(policy["enforcement"]["owner_feedback_required_per_version_before_next_release"])
        self.assertFalse(policy["delivery"]["owner_feedback_required_before_next_release"])
        self.assertEqual(
            {"FORMAL_RELEASE_ACCEPTANCE", "PRODUCTION_ACTIVATION"},
            set(policy["enforcement"]["owner_pending_blocks"]),
        )
        self.assertEqual(
            {"NEXT_RELEASE_DEVELOPMENT", "MACHINE_CANDIDATE", "DESKTOP_CANDIDATE_ACCUMULATION"},
            set(policy["enforcement"]["owner_pending_does_not_block"]),
        )
        transient_retry = policy["remediation"]["transient_retry"]
        self.assertEqual(1, transient_retry["max_same_commit_reruns"])
        self.assertEqual("FAILED_JOBS_AND_DEPENDENTS_ONLY", transient_retry["rerun_scope"])
        self.assertTrue(transient_retry["preserve_successful_jobs"])
        self.assertFalse(transient_retry["business_code_change_allowed"])
        self.assertEqual(
            "STOP_RERUN_AND_DIAGNOSE_STAGING_OR_INFRASTRUCTURE",
            transient_retry["repeated_transient_action"],
        )
        self.assertEqual(
            {"repository", "workflow_ref", "commit", "run_id"},
            set(policy["authentication"]["binding_claims"]),
        )
        bootstrap = policy["visual"]["baseline_bootstrap"]
        self.assertEqual("SINGLE_EMULATOR_CAPTURE_THEN_LIGHTWEIGHT_PROMOTION", bootstrap["mode"])
        self.assertFalse(bootstrap["promotion_rebuild_allowed"])
        self.assertFalse(bootstrap["promotion_emulator_allowed"])

    def test_first_release_visuals_require_ai_review_without_forcing_emulator_rerun(self) -> None:
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
            Image.new("RGB", (10, 10), "white").save(screenshots / "01.png")
            Image.new("RGB", (10, 10), "black").save(screenshots / "02.png")
            manifests = root / "manifests"
            manifests.mkdir()
            (manifests / "R07.yaml").write_text(yaml.safe_dump({
                "schema": "hhy.android-visual-manifest/v1",
                "release": "R07",
                "screens": [{"file": "01.png"}, {"file": "02.png"}],
            }), encoding="utf-8")
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R07", commit="a" * 40,
                run_id="123", attempt=1, test_exit_code_file=str(root / "exit.txt"), junit_root=str(junit),
                logcat=str(root / "logcat.txt"), screenshots=str(screenshots),
                baseline_root=str(root / "baseline"), visual_manifest_root=str(manifests), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("BASELINE_REVIEW_REQUIRED", payload["status"])
            self.assertEqual("123", payload["github_run_id"])
            self.assertFalse(payload["candidate_eligible"])
            self.assertFalse(payload["owner_test_allowed"])
            self.assertEqual("AI_REVIEW_AND_LIGHTWEIGHT_PROMOTE_BASELINE", payload["remediation"]["next_action"])

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
                baseline_root=str(root / "baseline"), visual_manifest_root=str(root / "manifests"), output=str(output),
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
                (screenshots / f"0{index + 1}.png").write_bytes(f"evidence-{index}".encode())
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R05", commit="a" * 40,
                attempt=1, test_exit_code_file=str(root / "exit.txt"), junit_root=str(junit),
                logcat=str(root / "logcat.txt"), screenshots=str(screenshots),
                baseline_root=str(root / "baseline"), visual_manifest_root=str(root / "manifests"), output=str(output),
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

    def test_finalize_preserves_baseline_review_as_non_owner_capture(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            commit = "d" * 40
            runtime = root / "runtime.json"
            runtime.write_text(json.dumps({
                "policy_id": "HHY-ANDROID-AUTOMATION-V1",
                "release": "R07",
                "commit": commit,
                "status": "BASELINE_REVIEW_REQUIRED",
                "owner_test_allowed": False,
            }), encoding="utf-8")
            apk = root / "app.apk"
            apk.write_bytes(b"captured-apk")
            output = root / "candidate.json"
            result = finalize(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R07", commit=commit,
                run_id="124", build_result="success", emulator_report=str(runtime), apk=str(apk), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("BASELINE_REVIEW_REQUIRED", payload["status"])
            self.assertFalse(payload["owner_test_allowed"])

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
            self.assertFalse(payload["production_activation_allowed"])

    def test_lightweight_promotion_binds_original_run_screenshots_and_apk(self) -> None:
        with TemporaryDirectory() as temp:
            root = Path(temp)
            release = "R07"
            commit = "f" * 40
            run_id = "789"
            screenshots = root / "screenshots"
            baseline = root / "baseline" / release
            manifests = root / "manifests"
            screenshots.mkdir()
            baseline.mkdir(parents=True)
            manifests.mkdir()
            hashes = {}
            for name, color in (("01.png", "white"), ("02.png", "black")):
                Image.new("RGB", (10, 10), color).save(screenshots / name)
                (baseline / name).write_bytes((screenshots / name).read_bytes())
                hashes[name] = hashlib.sha256((screenshots / name).read_bytes()).hexdigest()
            (manifests / f"{release}.yaml").write_text(yaml.safe_dump({
                "schema": "hhy.android-visual-manifest/v1",
                "release": release,
                "screens": [{"file": name} for name in hashes],
            }), encoding="utf-8")
            approval = root / "APPROVAL.yaml"
            approval.write_text(yaml.safe_dump({
                "schema": "hhy.android-visual-baseline-approval/v1",
                "release": release,
                "status": "APPROVED",
                "authority": "AI_IMPLEMENTATION_AGENT",
                "source": {"github_run_id": run_id, "commit": commit},
                "screens": [{"file": name, "sha256": digest} for name, digest in hashes.items()],
            }), encoding="utf-8")
            runtime_payload = {
                "policy_id": "HHY-ANDROID-AUTOMATION-V1", "release": release, "commit": commit,
                "github_run_id": run_id, "status": "BASELINE_REVIEW_REQUIRED", "owner_test_allowed": False,
                "failures": [{"type": "VISUAL", "detail": f"approved visual baseline missing: {baseline}"}],
                "screenshot_evidence": [
                    {"screenshot": name, "sha256": digest} for name, digest in hashes.items()
                ],
            }
            runtime = root / "runtime.json"
            runtime.write_text(json.dumps(runtime_payload), encoding="utf-8")
            apk = root / "candidate.apk"
            apk.write_bytes(b"candidate-apk")
            candidate = root / "candidate.json"
            candidate.write_text(json.dumps({
                "policy_id": "HHY-ANDROID-AUTOMATION-V1", "release": release, "commit": commit,
                "github_run_id": run_id, "status": "BASELINE_REVIEW_REQUIRED", "owner_test_allowed": False,
                "apk": {"sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "size_bytes": apk.stat().st_size},
            }), encoding="utf-8")
            output = root / "promotion.json"
            result = promote(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), approval=str(approval),
                source_run_id=run_id, run_id="790", emulator_report=str(runtime),
                candidate_report=str(candidate), screenshots=str(screenshots), baseline_root=str(root / "baseline"),
                visual_manifest_root=str(manifests), apk=str(apk), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("PASS", payload["status"])
            self.assertTrue(payload["owner_test_allowed"])
            self.assertEqual(run_id, payload["source_github_run_id"])

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
        self.assertEqual("${{ inputs.candidate }}", workflow["jobs"]["emulator"]["if"])
        self.assertIn("inputs.candidate", workflow["jobs"]["candidate"]["if"])
        self.assertIn("inputs.candidate", workflow["jobs"]["remediation-queue"]["if"])
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
        self.assertIn("candidate: false", ci_source)

    def test_branch_candidate_request_calls_the_same_quality_gate(self) -> None:
        workflow_path = ROOT / ".github/workflows/android-candidate-request.yml"
        workflow = yaml.load(workflow_path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
        self.assertIn("push", workflow["on"])
        self.assertEqual(
            "./.github/workflows/android-quality-gate.yml",
            workflow["jobs"]["quality"]["uses"],
        )
        self.assertEqual("true", workflow["jobs"]["quality"]["with"]["candidate"])
        self.assertIn("config/android-candidate-request.yaml", workflow_path.read_text(encoding="utf-8"))
        self.assertEqual("write", workflow["jobs"]["quality"]["permissions"]["id-token"])

    def test_baseline_promotion_workflow_never_builds_or_starts_an_emulator(self) -> None:
        workflow_path = ROOT / ".github/workflows/android-baseline-promotion.yml"
        workflow = yaml.load(workflow_path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
        self.assertEqual({"promote"}, set(workflow["jobs"]))
        source = workflow_path.read_text(encoding="utf-8")
        self.assertIn("android_ci_gate.py promote", source)
        self.assertIn("actions/download-artifact@v4", source)
        self.assertIn("f'{commit}..HEAD'", source)
        self.assertEqual(2, source.count("'core.quotepath=false'"))
        self.assertIn("baseline promotion range contains product or unapproved files", source)
        self.assertIn("'.continuity/'", source)
        self.assertNotIn("'apps/android/'", source)
        self.assertNotIn("gradlew", source)
        self.assertNotIn("android-emulator-runner", source)

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
        self.assertIn("hhyCiBootstrapCode", smoke_test)
        self.assertIn("/internal-ci/v1/android/session", smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r06.home.loaded")', smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r08.project.list.content"', smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r08.project.detail.content"', smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r08.project.editor.content"', smoke_test)
        self.assertIn('captureStable("03-project-editor.png")', smoke_test)
        self.assertIn("stableMatches >= 2", smoke_test)
        self.assertIn("digest != previousScreenDigest", smoke_test)
        self.assertIn("waitForIdle(2_000)", smoke_test)
        self.assertIn('By.res("r08.project.contact")', smoke_test)
        self.assertIn('By.res("r08.project.edit")', smoke_test)
        self.assertIn('By.textContains("candidate@example")', smoke_test)
        self.assertNotIn("waitForTextContains", smoke_test)

        shell = (
            ROOT
            / "apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("modifier = Modifier.weight(1f)", shell)
        self.assertIn("alwaysShowLabel = true", shell)
        self.assertIn("home != null && home!!.modules.isNotEmpty()", shell)

    def test_release_manifest_and_duplicate_screenshot_guard_are_durable(self) -> None:
        policy = load_policy()
        manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R06.yaml").read_text(encoding="utf-8"))
        self.assertEqual("AI_IMPLEMENTATION_AGENT", manifest["review_authority"])
        self.assertEqual(
            ["01-home-loaded.png", "02-mine.png", "03-about-loaded.png"],
            [row["file"] for row in manifest["screens"]],
        )
        self.assertEqual("AI_IMPLEMENTATION_AGENT", policy["visual"]["review_authority"])
        self.assertEqual("PASS", policy["enforcement"]["release_complete_requires_owner_status"])
        self.assertFalse(policy["enforcement"]["next_release_development_requires_owner_status"])
        self.assertEqual("PASS", policy["enforcement"]["production_activation_requires_owner_status"])
        source = (ROOT / "scripts/android_ci_gate.py").read_text(encoding="utf-8")
        self.assertIn("duplicate screenshot sha256=", source)
        self.assertIn("cross-screen pixel difference too small", source)

    def test_quality_workflow_requests_only_a_one_time_oidc_bootstrap(self) -> None:
        source = (ROOT / ".github/workflows/android-quality-gate.yml").read_text(encoding="utf-8")
        self.assertIn("ACTIONS_ID_TOKEN_REQUEST_TOKEN", source)
        self.assertIn("audience=hhy-android-e2e", source)
        self.assertIn("/internal-ci/v1/android/bootstrap", source)
        self.assertIn("::add-mask::$bootstrap_code", source)
        self.assertNotIn("HHY_E2E_PASSWORD", source)

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
