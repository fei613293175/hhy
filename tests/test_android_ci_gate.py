from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import json
import hashlib
import unittest
import yaml
from PIL import Image

from scripts.android_ci_gate import (
    GateError,
    analyze,
    finalize,
    is_enforced_release,
    load_policy,
    promote,
    resolve_attempt_policy,
)


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
        route_activation = policy["authentication"]["public_route_activation"]
        self.assertTrue(route_activation["required"])
        self.assertEqual(
            "scripts/switch_android_candidate_route.sh",
            route_activation["script"],
        )
        self.assertEqual(
            "HHY_CANDIDATE_ROUTE_CONFIRM",
            route_activation["confirmation_env"],
        )
        self.assertEqual(
            "/www/server/panel/vhost/nginx/api.orbexa.cc.conf",
            route_activation["nginx_config"],
        )
        self.assertEqual(
            "https://api.orbexa.cc/public-api/v1/platform/status",
            route_activation["public_probe_url"],
        )
        self.assertEqual(
            {
                "target_container_local_http_200",
                "nginx_upstream_exact",
                "nginx_config_test_pass",
                "public_request_id_in_target_container_log",
                "automatic_rollback_on_failure",
            },
            set(route_activation["required_proofs"]),
        )
        bootstrap = policy["visual"]["baseline_bootstrap"]
        self.assertEqual("SINGLE_EMULATOR_CAPTURE_THEN_LIGHTWEIGHT_PROMOTION", bootstrap["mode"])
        self.assertFalse(bootstrap["promotion_rebuild_allowed"])
        self.assertFalse(bootstrap["promotion_emulator_allowed"])
        self.assertEqual(0.005, policy["visual"]["minimum_cross_screen_changed_pixel_ratio"])
        self.assertEqual(3, policy["remediation"]["max_ai_attempts"])
        self.assertEqual(
            {
                "mode": "AI_STANDING_DELEGATION",
                "authority": "AI_IMPLEMENTATION_AGENT",
                "standing_owner_confirmation_cr": "CR-0358",
                "prompt_owner_each_attempt": False,
                "require_previous_request_consumed": True,
                "require_distinct_root_cause": True,
                "require_fix_evidence": True,
                "require_change_request": True,
                "require_contiguous_attempts": True,
                "require_unique_request_id": True,
                "max_candidate_runs": 1,
                "owner_only_actions": [
                    "NEW_SECRET",
                    "THIRD_PARTY_PERMISSION",
                    "FUNDS_OR_LEDGER_POLICY",
                    "PRODUCTION_ACTIVATION",
                ],
            },
            policy["remediation"]["candidate_authorization"],
        )
        self.assertEqual([
            {
                "exception_id": "CR-0344",
                "release": "R12",
                "attempt": 4,
                "request_id": "R12-CANDIDATE-20260726-004",
                "required_fix_commit": "daae207af319008411995b7ac23a13c9042fc5a2",
                "max_candidate_runs": 1,
            },
            {
                "exception_id": "CR-0352",
                "release": "R12",
                "attempt": 5,
                "request_id": "R12-CANDIDATE-20260726-005",
                "required_fix_commit": "fbde523e8e751b74f7300ef125c70fa9eb4d03fd",
                "max_candidate_runs": 1,
            },
            {
                "exception_id": "CR-0355",
                "release": "R12",
                "attempt": 6,
                "request_id": "R12-CANDIDATE-20260726-006",
                "required_fix_commit": "f11901ea1d3c61ccbcc59ec4cab3e77e24b1f1b0",
                "max_candidate_runs": 1,
            },
            {
                "exception_id": "CR-0358",
                "release": "R12",
                "attempt": 7,
                "request_id": "R12-CANDIDATE-20260726-007",
                "required_fix_commit": "bcde496e9821dce301d4b13bfe0b1f590f1380f5",
                "max_candidate_runs": 1,
            },
            {
                "exception_id": "CR-0383",
                "release": "R13",
                "attempt": 4,
                "request_id": "R13-CANDIDATE-20260727-004",
                "required_fix_commit": "c602d2f0194980efcf19f233148f0b0932254ba6",
                "max_candidate_runs": 1,
            },
        ], policy["remediation"]["approved_attempt_exceptions"])

    def test_attempt_exception_is_exact_and_never_changes_the_global_limit(self) -> None:
        policy = load_policy()
        exact = resolve_attempt_policy(
            policy,
            release="R12",
            attempt=4,
            request_id="R12-CANDIDATE-20260726-004",
            exception_id="CR-0344",
            required_fix_commit="daae207af319008411995b7ac23a13c9042fc5a2",
        )
        self.assertEqual(3, exact["max_ai_attempts"])
        self.assertEqual(4, exact["effective_attempt_limit"])
        self.assertEqual(1, exact["max_candidate_runs"])
        invalid = (
            {"release": "R13"},
            {"request_id": "R12-CANDIDATE-20260726-999"},
            {"exception_id": "CR-9999"},
            {"required_fix_commit": "b" * 40},
            {"attempt": 5},
        )
        base = {
            "release": "R12",
            "attempt": 4,
            "request_id": "R12-CANDIDATE-20260726-004",
            "exception_id": "CR-0344",
            "required_fix_commit": "daae207af319008411995b7ac23a13c9042fc5a2",
        }
        for override in invalid:
            with self.subTest(override=override), self.assertRaises(GateError):
                resolve_attempt_policy(policy, **(base | override))
        with self.assertRaises(GateError):
            resolve_attempt_policy(policy, release="R12", attempt=4)

    def test_attempt_five_exception_is_exact(self) -> None:
        policy = load_policy()
        exact = resolve_attempt_policy(
            policy,
            release="R12",
            attempt=5,
            request_id="R12-CANDIDATE-20260726-005",
            exception_id="CR-0352",
            required_fix_commit="fbde523e8e751b74f7300ef125c70fa9eb4d03fd",
        )
        self.assertEqual(3, exact["max_ai_attempts"])
        self.assertEqual(5, exact["effective_attempt_limit"])
        self.assertEqual(1, exact["max_candidate_runs"])
        base = {
            "release": "R12",
            "attempt": 5,
            "request_id": "R12-CANDIDATE-20260726-005",
            "exception_id": "CR-0352",
            "required_fix_commit": "fbde523e8e751b74f7300ef125c70fa9eb4d03fd",
        }
        invalid = (
            {"release": "R13"},
            {"request_id": "R12-CANDIDATE-20260726-004"},
            {"exception_id": "CR-0344"},
            {"required_fix_commit": "b" * 40},
            {"attempt": 6},
        )
        for override in invalid:
            with self.subTest(override=override), self.assertRaises(GateError):
                resolve_attempt_policy(policy, **(base | override))

    def test_r13_attempt_four_sequence_is_independent_and_attempt_five_is_rejected(self) -> None:
        policy = load_policy()
        exact = resolve_attempt_policy(
            policy,
            release="R13",
            attempt=4,
            request_id="R13-CANDIDATE-20260727-004",
            exception_id="CR-0383",
            required_fix_commit="c602d2f0194980efcf19f233148f0b0932254ba6",
        )
        self.assertEqual(3, exact["max_ai_attempts"])
        self.assertEqual(4, exact["effective_attempt_limit"])
        self.assertEqual(1, exact["max_candidate_runs"])
        with self.assertRaises(GateError):
            resolve_attempt_policy(
                policy,
                release="R13",
                attempt=5,
                request_id="R13-CANDIDATE-20260727-005",
                exception_id="CR-0383",
                required_fix_commit="c602d2f0194980efcf19f233148f0b0932254ba6",
            )

    def test_attempt_six_exception_is_exact(self) -> None:
        policy = load_policy()
        exact = resolve_attempt_policy(
            policy,
            release="R12",
            attempt=6,
            request_id="R12-CANDIDATE-20260726-006",
            exception_id="CR-0355",
            required_fix_commit="f11901ea1d3c61ccbcc59ec4cab3e77e24b1f1b0",
        )
        self.assertEqual(3, exact["max_ai_attempts"])
        self.assertEqual(6, exact["effective_attempt_limit"])
        self.assertEqual(1, exact["max_candidate_runs"])
        base = {
            "release": "R12",
            "attempt": 6,
            "request_id": "R12-CANDIDATE-20260726-006",
            "exception_id": "CR-0355",
            "required_fix_commit": "f11901ea1d3c61ccbcc59ec4cab3e77e24b1f1b0",
        }
        invalid = (
            {"release": "R13"},
            {"request_id": "R12-CANDIDATE-20260726-005"},
            {"exception_id": "CR-0352"},
            {"required_fix_commit": "b" * 40},
            {"attempt": 7},
        )
        for override in invalid:
            with self.subTest(override=override), self.assertRaises(GateError):
                resolve_attempt_policy(policy, **(base | override))

    def test_attempt_seven_exception_is_exact_and_rejects_attempt_eight(self) -> None:
        policy = load_policy()
        exact = resolve_attempt_policy(
            policy,
            release="R12",
            attempt=7,
            request_id="R12-CANDIDATE-20260726-007",
            exception_id="CR-0358",
            required_fix_commit="bcde496e9821dce301d4b13bfe0b1f590f1380f5",
        )
        self.assertEqual(3, exact["max_ai_attempts"])
        self.assertEqual(7, exact["effective_attempt_limit"])
        self.assertEqual(1, exact["max_candidate_runs"])
        base = {
            "release": "R12",
            "attempt": 7,
            "request_id": "R12-CANDIDATE-20260726-007",
            "exception_id": "CR-0358",
            "required_fix_commit": "bcde496e9821dce301d4b13bfe0b1f590f1380f5",
        }
        invalid = (
            {"release": "R13"},
            {"request_id": "R12-CANDIDATE-20260726-006"},
            {"exception_id": "CR-0355"},
            {"required_fix_commit": "b" * 40},
            {"attempt": 8},
        )
        for override in invalid:
            with self.subTest(override=override), self.assertRaises(GateError):
                resolve_attempt_policy(policy, **(base | override))

    def test_policy_rejects_a_global_limit_other_than_three(self) -> None:
        with TemporaryDirectory() as temp:
            policy = yaml.safe_load((ROOT / "config/android-automation.yaml").read_text(encoding="utf-8"))
            policy["remediation"]["max_ai_attempts"] = 4
            path = Path(temp) / "policy.yaml"
            path.write_text(yaml.safe_dump(policy, allow_unicode=True), encoding="utf-8")
            with self.assertRaises(GateError):
                load_policy(path)

    def test_policy_rejects_candidate_authorization_drift(self) -> None:
        mutations = (
            lambda authorization: authorization.update(prompt_owner_each_attempt=True),
            lambda authorization: authorization.update(require_fix_evidence=False),
            lambda authorization: authorization.update(max_candidate_runs=2),
            lambda authorization: authorization["owner_only_actions"].pop(),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate), TemporaryDirectory() as temp:
                policy = yaml.safe_load(
                    (ROOT / "config/android-automation.yaml").read_text(encoding="utf-8")
                )
                mutate(policy["remediation"]["candidate_authorization"])
                path = Path(temp) / "policy.yaml"
                path.write_text(
                    yaml.safe_dump(policy, allow_unicode=True),
                    encoding="utf-8",
                )
                with self.assertRaises(GateError):
                    load_policy(path)

    def test_policy_rejects_incomplete_public_route_activation_proof(self) -> None:
        mutations = (
            lambda route: route.update(required=False),
            lambda route: route.update(script="scripts/manual-route.sh"),
            lambda route: route.update(confirmation_env="UNSAFE_CONFIRM"),
            lambda route: route.update(nginx_config="/etc/nginx/nginx.conf"),
            lambda route: route.update(public_probe_url="http://127.0.0.1/status"),
            lambda route: route["required_proofs"].pop(),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate), TemporaryDirectory() as temp:
                policy = yaml.safe_load(
                    (ROOT / "config/android-automation.yaml").read_text(encoding="utf-8")
                )
                mutate(policy["authentication"]["public_route_activation"])
                path = Path(temp) / "policy.yaml"
                path.write_text(
                    yaml.safe_dump(policy, allow_unicode=True),
                    encoding="utf-8",
                )
                with self.assertRaises(GateError):
                    load_policy(path)

    def test_attempt_exception_history_must_be_contiguous_per_release_ordered_and_unique(self) -> None:
        source = yaml.safe_load(
            (ROOT / "config/android-automation.yaml").read_text(encoding="utf-8")
        )
        mutations = {
            "gap": lambda rows: rows.pop(0),
            "reordered": lambda rows: rows.reverse(),
            "duplicate_cr": lambda rows: rows[1].update(
                exception_id=rows[0]["exception_id"]
            ),
            "duplicate_request": lambda rows: rows[1].update(
                request_id=rows[0]["request_id"]
            ),
            "duplicate_commit": lambda rows: rows[1].update(
                required_fix_commit=rows[0]["required_fix_commit"]
            ),
            "more_than_one_run": lambda rows: rows[1].update(max_candidate_runs=2),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name), TemporaryDirectory() as temp:
                policy = yaml.safe_load(yaml.safe_dump(source))
                mutate(policy["remediation"]["approved_attempt_exceptions"])
                path = Path(temp) / "policy.yaml"
                path.write_text(yaml.safe_dump(policy, allow_unicode=True), encoding="utf-8")
                with self.assertRaises(GateError):
                    load_policy(path)

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

    def test_attempt_four_runtime_report_keeps_global_and_effective_limits(self) -> None:
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
            (manifests / "R12.yaml").write_text(yaml.safe_dump({
                "schema": "hhy.android-visual-manifest/v1",
                "release": "R12",
                "screens": [{"file": "01.png"}, {"file": "02.png"}],
            }), encoding="utf-8")
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R12", commit="c" * 40,
                run_id="444", attempt=4, request_id="R12-CANDIDATE-20260726-004",
                attempt_exception_id="CR-0344",
                required_fix_commit="daae207af319008411995b7ac23a13c9042fc5a2",
                test_exit_code_file=str(root / "exit.txt"), junit_root=str(junit),
                logcat=str(root / "logcat.txt"), screenshots=str(screenshots),
                baseline_root=str(root / "baseline"), visual_manifest_root=str(manifests), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual(3, payload["max_ai_attempts"])
            self.assertEqual(4, payload["effective_attempt_limit"])
            self.assertEqual("CR-0344", payload["attempt_exception_id"])
            self.assertEqual(1, payload["max_candidate_runs"])

    def test_attempt_six_runtime_report_keeps_global_and_effective_limits(self) -> None:
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
            (manifests / "R12.yaml").write_text(yaml.safe_dump({
                "schema": "hhy.android-visual-manifest/v1",
                "release": "R12",
                "screens": [{"file": "01.png"}, {"file": "02.png"}],
            }), encoding="utf-8")
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R12", commit="c" * 40,
                run_id="666", attempt=6, request_id="R12-CANDIDATE-20260726-006",
                attempt_exception_id="CR-0355",
                required_fix_commit="f11901ea1d3c61ccbcc59ec4cab3e77e24b1f1b0",
                test_exit_code_file=str(root / "exit.txt"), junit_root=str(junit),
                logcat=str(root / "logcat.txt"), screenshots=str(screenshots),
                baseline_root=str(root / "baseline"), visual_manifest_root=str(manifests), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual(3, payload["max_ai_attempts"])
            self.assertEqual(6, payload["effective_attempt_limit"])
            self.assertEqual("CR-0355", payload["attempt_exception_id"])
            self.assertEqual(1, payload["max_candidate_runs"])

    def test_attempt_seven_runtime_report_keeps_global_and_effective_limits(self) -> None:
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
            (manifests / "R12.yaml").write_text(yaml.safe_dump({
                "schema": "hhy.android-visual-manifest/v1",
                "release": "R12",
                "screens": [{"file": "01.png"}, {"file": "02.png"}],
            }), encoding="utf-8")
            output = root / "runtime.json"
            result = analyze(SimpleNamespace(
                policy=str(ROOT / "config/android-automation.yaml"), release="R12", commit="c" * 40,
                run_id="777", attempt=7, request_id="R12-CANDIDATE-20260726-007",
                attempt_exception_id="CR-0358",
                required_fix_commit="bcde496e9821dce301d4b13bfe0b1f590f1380f5",
                test_exit_code_file=str(root / "exit.txt"), junit_root=str(junit),
                logcat=str(root / "logcat.txt"), screenshots=str(screenshots),
                baseline_root=str(root / "baseline"), visual_manifest_root=str(manifests), output=str(output),
            ))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual(3, payload["max_ai_attempts"])
            self.assertEqual(7, payload["effective_attempt_limit"])
            self.assertEqual("CR-0358", payload["attempt_exception_id"])
            self.assertEqual(1, payload["max_candidate_runs"])

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
            {"historical-visual", "build", "emulator", "candidate", "remediation-queue"},
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
        self.assertIn("android_ci_gate.py attempt-check", source)
        self.assertIn("git merge-base --is-ancestor", source)
        self.assertIn("inputs.effective_attempt_limit", source)
        self.assertNotIn("inputs.remediation_attempt }}/3", source)
        self.assertIn("! -name '*androidTest*'", source)
        self.assertIn("path: candidate-output", source)
        self.assertEqual(
            "${{ inputs.release == 'HISTORICAL-UI' }}",
            workflow["jobs"]["historical-visual"]["if"],
        )
        self.assertEqual(
            "${{ inputs.release != 'HISTORICAL-UI' }}",
            workflow["jobs"]["build"]["if"],
        )
        historical_job = workflow["jobs"]["historical-visual"]
        historical_source = yaml.safe_dump(historical_job, allow_unicode=True)
        historical_action = next(
            step for step in historical_job["steps"]
            if str(step.get("uses", "")).startswith("reactivecircus/android-emulator-runner@")
        )
        self.assertEqual(
            "bash scripts/run_android_historical_visual_audit.sh",
            historical_action["with"]["script"],
        )
        runner_source = (ROOT / "scripts/run_android_historical_visual_audit.sh").read_text(encoding="utf-8")
        self.assertIn("HistoricalVisualAuditTest", runner_source)
        self.assertIn("screenshot_count", runner_source)
        self.assertIn("-ne 22", runner_source)
        self.assertNotIn("HHY_CI_BOOTSTRAP_CODE", historical_source + runner_source)
        self.assertNotIn("assembleDebug", historical_source + runner_source)
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
        self.assertEqual(
            "${{ needs.request.outputs.candidate == 'true' }}",
            workflow["jobs"]["quality"]["with"]["candidate"],
        )
        self.assertEqual(
            "${{ steps.request.outputs.candidate }}",
            workflow["jobs"]["request"]["outputs"]["candidate"],
        )
        self.assertEqual(
            "${{ steps.request.outputs.attempt_exception_id }}",
            workflow["jobs"]["request"]["outputs"]["attempt_exception_id"],
        )
        self.assertEqual(
            "${{ needs.request.outputs.required_fix_commit }}",
            workflow["jobs"]["quality"]["with"]["required_fix_commit"],
        )
        self.assertEqual(
            "${{ fromJSON(needs.request.outputs.effective_attempt_limit) }}",
            workflow["jobs"]["quality"]["with"]["effective_attempt_limit"],
        )
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
        self.assertIn("'artifacts/validation/project-doctor-v1.2.3.json'", source)
        self.assertIn("'artifacts/validation/continuity-integration-v1.2.3.json'", source)
        self.assertIn("'artifacts/validation/continuity-lifecycle-integration-v1.2.3.json'", source)
        self.assertIn("'artifacts/validation/continuity-lifecycle-integration-v1.2.3.log'", source)
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
        historical_test = (
            ROOT
            / "apps/android/app/src/androidTest/java/cc/orbexa/hhy/HistoricalVisualAuditTest.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("connectedDebugAndroidTest", source)
        self.assertIn("test_rc=${PIPESTATUS[0]}", source)
        self.assertIn("android_ci_gate.py analyze", source)
        self.assertIn("--request-id", source)
        self.assertIn("--attempt-exception-id", source)
        self.assertIn("--required-fix-commit", source)
        self.assertIn("adb logcat", source)
        self.assertIn("runtime-report.json", source)
        self.assertIn("::error title=Android emulator gate failed::", source)
        self.assertIn("/sdcard/Pictures/hhy-ci-screenshots", source)
        self.assertNotIn("/sdcard/Android/data/", source)
        self.assertIn("androidTest-results/connected", source)
        self.assertIn(
            "android.testInstrumentationRunnerArguments.class=cc.orbexa.hhy.ReleaseCandidateSmokeTest",
            source,
        )
        self.assertIn("MediaStore.Images.Media.EXTERNAL_CONTENT_URI", smoke_test)
        self.assertIn("MediaStore.Images.Media.RELATIVE_PATH", smoke_test)
        self.assertIn("Pictures/hhy-ci-screenshots", smoke_test)
        self.assertNotIn("getExternalFilesDir", smoke_test)
        self.assertNotIn("executeShellCommand", smoke_test)
        self.assertIn("hhyCiBootstrapCode", smoke_test)
        self.assertIn("/internal-ci/v1/android/session", smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r06.home.loaded")', smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r13.favorites.content"', smoke_test)
        self.assertIn('waitForScreen("hhy.screen.r13.history.content"', smoke_test)
        self.assertIn('waitForScreen("hhy.sheet.r13.share"', smoke_test)
        self.assertIn('waitForScreen("hhy.sheet.r13.invalid-feedback"', smoke_test)
        self.assertIn('captureStable("01-favorites.png")', smoke_test)
        self.assertIn('captureStable("02-history.png")', smoke_test)
        self.assertIn('captureStable("03-share-sheet.png")', smoke_test)
        self.assertIn('captureStable("04-invalid-feedback-sheet.png")', smoke_test)
        self.assertEqual(4, smoke_test.count('captureStable("'))
        self.assertNotIn('captureStable("26-r06-home.png")', smoke_test)
        self.assertNotIn('captureStable("01-project-list.png")', smoke_test)
        self.assertIn('captureStable("10-r02-startup.png")', historical_test)
        self.assertIn('captureStable("20-r02-account-cancellation.png")', historical_test)
        self.assertIn('captureStable("25-r05-identity-result.png")', historical_test)
        self.assertIn("AuthVisualAuditScreen", historical_test)
        self.assertIn("IdentityVisualAuditScreen", historical_test)
        self.assertIn("stableMatches >= 4", smoke_test)
        self.assertIn("digest != previousScreenDigest", smoke_test)
        self.assertIn("waitForIdle(2_000)", smoke_test)
        self.assertIn('clickResource("mine.favorites")', smoke_test)
        self.assertIn('clickResource("mine.history")', smoke_test)
        self.assertIn('clickResource("r13.action.project.share")', smoke_test)
        self.assertIn('clickResource("r13.action.project.invalid-feedback")', smoke_test)
        self.assertIn("generateSequence(textNode) { current -> current.parent }", smoke_test)
        self.assertIn(".firstOrNull { it.isClickable && it.isEnabled }", smoke_test)
        self.assertIn('clickExactText("再检查一下")', smoke_test)
        self.assertIn("assertR13BusinessLabels", smoke_test)
        self.assertIn('"PROJECT", "GROUP_CHAT", "TEAM_LEADER"', smoke_test)
        self.assertNotIn("assertSecureWindow", smoke_test)

        r08_manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R08.yaml").read_text(encoding="utf-8"))
        current_r08_screens = [
            row for row in r08_manifest["screens"]
            if row["screen_id"] in {"SCR-LIST-001", "SCR-DETAIL-001", "SCR-PUB-002"}
        ]
        for row in current_r08_screens:
            self.assertIn("合作项目", row["required_text"])
            self.assertIn("北京", row["required_text"])
            self.assertIn("COOPERATION", row["forbidden_text"])
            self.assertIn("CN-11", row["forbidden_text"])
        editor = next(row for row in r08_manifest["screens"] if row["screen_id"] == "SCR-PUB-002")
        self.assertIn("微信", editor["required_text"])
        self.assertIn("WECHAT", editor["forbidden_text"])
        self.assertEqual(3, len(r08_manifest["screens"]))
        self.assertEqual(
            {"01-project-list.png", "02-project-detail.png", "03-project-editor.png"},
            {row["file"] for row in r08_manifest["screens"]},
        )
        secure_surfaces = {row["surface"] for row in r08_manifest["security_assertions"]}
        self.assertIn("SHEET-CONTACT-001", secure_surfaces)

        r09_manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R09.yaml").read_text(encoding="utf-8"))
        self.assertEqual(3, len(r09_manifest["screens"]))
        self.assertEqual(
            {"01-app-list.png", "02-app-detail.png", "03-app-editor.png"},
            {row["file"] for row in r09_manifest["screens"]},
        )
        for row in r09_manifest["screens"]:
            self.assertIn("TOOLS", row["forbidden_text"])
            self.assertIn("ANDROID", row["forbidden_text"])

        r10_manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R10.yaml").read_text(encoding="utf-8"))
        self.assertEqual("AI_IMPLEMENTATION_AGENT", r10_manifest["review_authority"])
        self.assertEqual(4, len(r10_manifest["screens"]))
        self.assertEqual(
            {"01-home.png", "02-group-list.png", "03-group-detail.png", "04-group-editor.png"},
            {row["file"] for row in r10_manifest["screens"]},
        )
        detail = next(row for row in r10_manifest["screens"] if row["screen_id"] == "SCR-DETAIL-003")
        self.assertIn("R10-ci-owner", detail["forbidden_text"])
        self.assertIn("R10-ci-join", detail["forbidden_text"])
        self.assertIn("JOIN_PASSWORD", detail["forbidden_text"])

        r11_manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R11.yaml").read_text(encoding="utf-8"))
        self.assertEqual("AI_IMPLEMENTATION_AGENT", r11_manifest["review_authority"])
        self.assertEqual(4, len(r11_manifest["screens"]))
        self.assertEqual(
            {
                "01-home.png", "02-team-leader-list.png",
                "03-team-leader-detail.png", "04-team-leader-editor.png",
            },
            {row["file"] for row in r11_manifest["screens"]},
        )
        r11_detail = next(row for row in r11_manifest["screens"] if row["screen_id"] == "SCR-DETAIL-004")
        self.assertIn("hhy-contact-v1:r11-ci-team-contact", r11_detail["forbidden_text"])
        self.assertIn("WECHAT", r11_detail["forbidden_text"])

        r12_manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R12.yaml").read_text(encoding="utf-8"))
        self.assertEqual("AI_IMPLEMENTATION_AGENT", r12_manifest["review_authority"])
        self.assertEqual(10, len(r12_manifest["screens"]))
        self.assertEqual(
            {
                "SCR-PUB-001", "SCR-PUB-006", "SCR-PUB-007",
                "SCR-MYC-001", "SCR-MYC-002", "SCR-MYC-003",
                "SCR-MYC-004", "SCR-MYC-005", "SCR-ME-001", "SCR-ME-002",
            },
            {row["screen_id"] for row in r12_manifest["screens"]},
        )
        self.assertEqual(
            {
                "01-publish-center.png", "02-me-home.png", "03-profile.png", "04-drafts.png",
                "05-content-management-detail.png", "06-publish-preview.png", "07-submit-result.png",
                "08-my-contents.png", "09-content-reviews.png", "10-content-analytics.png",
            },
            {row["file"] for row in r12_manifest["screens"]},
        )

        r13_manifest = yaml.safe_load((ROOT / "tests/android/visual-manifests/R13.yaml").read_text(encoding="utf-8"))
        self.assertEqual("AI_IMPLEMENTATION_AGENT", r13_manifest["review_authority"])
        self.assertEqual(4, len(r13_manifest["screens"]))
        self.assertEqual(
            {"SCR-FAV-001", "SCR-HIS-001", "SHEET-SHARE-001", "SHEET-CONTENT-INVALID-001"},
            {row["screen_id"] for row in r13_manifest["screens"]},
        )
        self.assertEqual(
            {"01-favorites.png", "02-history.png", "03-share-sheet.png", "04-invalid-feedback-sheet.png"},
            {row["file"] for row in r13_manifest["screens"]},
        )

        shell = (
            ROOT
            / "apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("modifier = Modifier.weight(1f)", shell)
        self.assertIn("alwaysShowLabel = true", shell)
        self.assertIn('it.type == "BANNER"', shell)
        self.assertIn('it.type == "NOTICE"', shell)
        self.assertIn('HomeCategory("项目"', shell)
        self.assertIn('HomeCategory("App"', shell)
        self.assertIn('HomeCategory("群聊"', shell)
        self.assertIn('HomeCategory("团队长"', shell)
        self.assertIn("HomeEmptyState(", shell)

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
