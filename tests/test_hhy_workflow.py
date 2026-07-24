from __future__ import annotations

from argparse import Namespace
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import hhy_workflow as workflow  # noqa: E402


class WorkflowClassificationTest(TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.policy = workflow.load_policy()

    def test_small_single_module_bugfix_stays_simple_but_runs_module_regression(self) -> None:
        result = workflow.classify_work(
            self.policy,
            "bugfix",
            ["apps/h5/src/views/LoginPage.vue", "apps/h5/src/views/LoginPage.test.ts"],
        )
        self.assertEqual("SIMPLE", result["mode"])
        self.assertEqual("MODULE", result["quality_profile"])
        self.assertEqual(["h5"], result["product_domains"])
        self.assertNotIn("high_risk_path_present", result["reasons"])
        self.assertEqual(600, result["hotfix_timebox_seconds"]["diagnose"])
        self.assertEqual(2100, result["hotfix_timebox_seconds"]["total"])

    def test_simple_document_maintenance_uses_fast_checks(self) -> None:
        result = workflow.classify_work(
            self.policy, "maintenance", ["docs/09-development/guide.md"]
        )
        self.assertEqual("SIMPLE", result["mode"])
        self.assertEqual("FAST", result["quality_profile"])

    def test_feature_is_not_silently_downgraded_to_simple(self) -> None:
        result = workflow.classify_work(
            self.policy, "feature", ["apps/android/feature/auth/src/AuthScreen.kt"]
        )
        self.assertEqual("STANDARD", result["mode"])
        self.assertEqual("MODULE", result["quality_profile"])

    def test_cross_layer_change_is_detected(self) -> None:
        result = workflow.classify_work(
            self.policy,
            "bugfix",
            ["apps/android/feature/auth/src/AuthScreen.kt", "services/backend/access/src/Auth.java"],
        )
        self.assertEqual("CROSS_LAYER", result["mode"])
        self.assertEqual({"android", "backend"}, set(result["product_domains"]))

    def test_database_migration_forces_escalation(self) -> None:
        result = workflow.classify_work(
            self.policy, "bugfix", ["database/migrations/V021__fix.sql"]
        )
        self.assertEqual("STANDARD", result["mode"])
        self.assertIn("high_risk_path_present", result["reasons"])

    def test_explicit_delivery_modes_are_never_inferred_as_simple(self) -> None:
        apk = workflow.classify_work(self.policy, "test-apk", ["apps/android/app/Main.kt"])
        release = workflow.classify_work(self.policy, "release-close", [])
        self.assertEqual("TEST_APK", apk["mode"])
        self.assertEqual("RELEASE_CLOSE", release["mode"])
        self.assertEqual("MACHINE_CLOSE", release["quality_profile"])

    def test_generated_continuity_files_do_not_inflate_product_scope(self) -> None:
        result = workflow.classify_work(
            self.policy,
            "bugfix",
            [
                "apps/h5/src/Login.vue",
                ".continuity/STATE.yaml",
                "artifacts/context/CURRENT_CONTEXT_PACK.md",
                "artifacts/validation/continuity-integration-v1.2.3.json",
                "catalogs/task_transition_ledger.csv",
            ],
        )
        self.assertEqual("SIMPLE", result["mode"])
        self.assertEqual(["apps/h5/src/Login.vue"], result["classified_files"])

    def test_plan_reuses_existing_affected_test_engine(self) -> None:
        args = Namespace(
            policy=workflow.DEFAULT_POLICY,
            impact_map=workflow.DEFAULT_IMPACT_MAP,
            changed_file=["apps/h5/src/views/LoginPage.vue"],
            base_ref=None,
            head_ref="HEAD",
            intent="bugfix",
            release=None,
        )
        plan = workflow.create_plan(args)
        ids = {row["id"] for row in plan["quality_plan"]["checks"]}
        self.assertEqual("SIMPLE", plan["workflow"]["mode"])
        self.assertIn("h5-module", ids)
        self.assertNotIn("integration-web", ids)
        self.assertNotIn("release-close-gate", ids)

    def test_release_close_plan_uses_machine_gate(self) -> None:
        args = Namespace(
            policy=workflow.DEFAULT_POLICY,
            impact_map=workflow.DEFAULT_IMPACT_MAP,
            changed_file=[],
            base_ref=None,
            head_ref="HEAD",
            intent="release-close",
            release="R08",
        )
        plan = workflow.create_plan(args)
        close_checks = [
            row for row in plan["quality_plan"]["checks"]
            if row["id"] == "release-close-gate"
        ]
        self.assertEqual(1, len(close_checks))
        ids = {row["id"] for row in plan["quality_plan"]["checks"]}
        self.assertEqual({"release-close-gate"}, ids)
        self.assertNotIn("integration-backend", ids)
        self.assertNotIn("integration-postgres-migration", ids)
        self.assertNotIn("integration-android", ids)
        self.assertIn("--machine-close-gate", close_checks[0]["command"])
        self.assertNotIn("--production-close-gate", close_checks[0]["command"])
        self.assertTrue(any("asynchronous" in value for value in plan["next_actions"]))

    def test_resumable_execution_skips_prior_pass_for_unchanged_inputs(self) -> None:
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "change.txt"
            source.write_text("same input", encoding="utf-8")
            plan = {
                "workflow": {
                    "mode": "SIMPLE",
                    "quality_profile": "FAST",
                    "classified_files": ["change.txt"],
                    "duration_budget_seconds": 60,
                },
                "quality_plan": {
                    "checks": [
                        {"id": "one", "command": ["one"], "cwd": str(root), "env": {}, "timeout_seconds": 1},
                        {"id": "two", "command": ["two"], "cwd": str(root), "env": {}, "timeout_seconds": 1},
                    ]
                },
            }
            state = root / "state.json"

            def passing(single: dict) -> dict:
                return {
                    "results": [{
                        "id": single["checks"][0]["id"],
                        "status": "PASS",
                        "duration_seconds": 0.1,
                    }]
                }

            with mock.patch.object(workflow, "ROOT", root), mock.patch.object(
                workflow.affected, "execute_plan", side_effect=passing
            ) as execute:
                first = workflow.execute_resumable(plan, state)
                second = workflow.execute_resumable(plan, state)
            self.assertEqual("PASS", first["status"])
            self.assertEqual("PASS", second["status"])
            self.assertEqual(["one", "two"], second["resumed_checks"])
            self.assertEqual(2, execute.call_count)

    def test_per_check_cache_reuses_only_unchanged_inputs(self) -> None:
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "one.txt").write_text("one", encoding="utf-8")
            (root / "two.txt").write_text("two", encoding="utf-8")
            plan = {
                "workflow": {
                    "mode": "CROSS_LAYER",
                    "quality_profile": "MODULE",
                    "classified_files": ["one.txt", "two.txt"],
                    "duration_budget_seconds": 60,
                    "parallel_execution": {"enabled": True, "max_workers": 2},
                },
                "quality_plan": {
                    "checks": [
                        {"id": "one", "paths": ["one.txt"], "command": ["one"], "cwd": str(root), "env": {}, "timeout_seconds": 1},
                        {"id": "two", "paths": ["two.txt"], "command": ["two"], "cwd": str(root), "env": {}, "timeout_seconds": 1},
                    ]
                },
            }
            state = root / "state.json"

            def passing(single: dict) -> dict:
                return {"results": [{
                    "id": single["checks"][0]["id"], "status": "PASS", "duration_seconds": 0.1,
                }]}

            with mock.patch.object(workflow, "ROOT", root), mock.patch.object(
                workflow.affected, "execute_plan", side_effect=passing
            ) as execute:
                workflow.execute_resumable(plan, state)
                (root / "one.txt").write_text("changed", encoding="utf-8")
                second = workflow.execute_resumable(plan, state)
            self.assertEqual(["two"], second["resumed_checks"])
            self.assertEqual(3, execute.call_count)

    def test_parallel_checks_require_and_report_frozen_source(self) -> None:
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "change.txt"
            source.write_text("frozen", encoding="utf-8")
            plan = {
                "workflow": {
                    "mode": "CROSS_LAYER",
                    "quality_profile": "MODULE",
                    "classified_files": ["change.txt"],
                    "duration_budget_seconds": 60,
                    "parallel_execution": {"enabled": True, "max_workers": 2},
                },
                "quality_plan": {"checks": [
                    {"id": "one", "command": ["one"], "cwd": str(root), "env": {}, "timeout_seconds": 1},
                    {"id": "two", "command": ["two"], "cwd": str(root), "env": {}, "timeout_seconds": 1},
                ]},
            }

            def passing(single: dict) -> dict:
                return {"results": [{
                    "id": single["checks"][0]["id"], "status": "PASS", "duration_seconds": 0.1,
                }]}

            with mock.patch.object(workflow, "ROOT", root), mock.patch.object(
                workflow, "validate_frozen_parallel"
            ) as validate, mock.patch.object(
                workflow.affected, "execute_plan", side_effect=passing
            ):
                result = workflow.execute_resumable(
                    plan, root / "state.json", parallel_workers=2, frozen_commit="a" * 40
                )
            validate.assert_called_once()
            self.assertEqual(2, result["parallel_workers"])
            self.assertEqual("a" * 40, result["frozen_commit"])

    def test_portable_environment_pins_hook_runtime(self) -> None:
        with mock.patch.object(workflow.affected, "resolve_git_executable", return_value="C:/tools/git.exe"), mock.patch.object(
            workflow.affected, "resolve_pnpm_executable", return_value="C:/tools/pnpm.cmd"
        ):
            environment = workflow.portable_environment()
        self.assertEqual(sys.executable, environment["HHY_PYTHON"])
        self.assertEqual("C:/tools/git.exe", environment["HHY_GIT_BIN"])
        self.assertEqual("C:/tools/pnpm.cmd", environment["HHY_PNPM_BIN"])
