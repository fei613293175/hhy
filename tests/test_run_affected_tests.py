from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import os
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import continuity_gate  # noqa: E402
import continuity_lib  # noqa: E402
import run_affected_tests as affected  # noqa: E402


class TestImpactMap(TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.document = affected.load_impact_map(ROOT / "config/test-impact-map.yaml")

    def test_fast_contract_change_selects_only_affected_jobs(self) -> None:
        plan = affected.build_plan(self.document, "FAST", ["contracts/openapi.yaml"], root=ROOT)
        ids = {row["id"] for row in plan["checks"]}
        self.assertIn("contract-openapi", ids)
        self.assertIn("admin-web-typecheck", ids)
        self.assertIn("h5-typecheck", ids)
        self.assertNotIn("backend-module", ids)
        self.assertEqual({"contracts", "web"}, set(plan["ci_jobs"]))

    def test_module_backend_change_includes_backend_without_full_integration(self) -> None:
        plan = affected.build_plan(
            self.document, "MODULE", ["services/backend/access/src/Auth.java"], root=ROOT
        )
        ids = {row["id"] for row in plan["checks"]}
        self.assertIn("backend-module", ids)
        self.assertNotIn("integration-backend", ids)

    def test_android_governance_changes_select_tooling_without_compiling_android(self) -> None:
        gate_paths = [
            ".github/workflows/android-quality-gate.yml",
            ".github/workflows/android-candidate-request.yml",
            ".github/workflows/android-visual-baseline-promotion.yml",
            "config/android-automation.yaml",
            "config/android-candidate-request.yaml",
            "scripts/android_ci_gate.py",
            "scripts/android_candidate_request.py",
            "scripts/run_android_emulator_gate.sh",
            "scripts/switch_android_candidate_route.sh",
            "tests/test_android_ci_gate.py",
            "tests/test_android_candidate_request.py",
            "tests/test_android_candidate_route.py",
        ]
        for path in gate_paths:
            with self.subTest(path=path):
                plan = affected.build_plan(self.document, "MODULE", [path], root=ROOT)
                ids = {row["id"] for row in plan["checks"]}
                self.assertIn("tooling", plan["ci_jobs"])
                self.assertIn("android-governance-unit", ids)
                self.assertNotIn("android-module", ids)

    def test_android_application_change_still_selects_android_job(self) -> None:
        plan = affected.build_plan(
            self.document,
            "MODULE",
            ["apps/android/feature/shell/src/main/java/HhyShell.kt"],
            root=ROOT,
        )
        self.assertIn("android", plan["ci_jobs"])
        self.assertIn("android-module", {row["id"] for row in plan["checks"]})

    def test_impact_map_change_validates_tooling_without_compiling_android(self) -> None:
        plan = affected.build_plan(
            self.document, "MODULE", ["config/test-impact-map.yaml"]
        )
        ids = {row["id"] for row in plan["checks"]}
        self.assertIn("acceleration-tooling-unit", ids)
        self.assertNotIn("android-module", ids)

    def test_candidate_history_changes_select_candidate_tooling(self) -> None:
        paths = [
            "tests/test_r11_candidate.py",
            "tests/test_r12_candidate.py",
            "tests/android/visual-manifests/R11.yaml",
            "artifacts/validation/r11-task007-android/candidate-report.json",
        ]
        for path in paths:
            with self.subTest(path=path):
                plan = affected.build_plan(self.document, "MODULE", [path], root=ROOT)
                self.assertIn("candidate-history-unit", {row["id"] for row in plan["checks"]})
                self.assertIn("tooling", plan["ci_jobs"])

    def test_governance_knowledge_changes_select_governance_tooling(self) -> None:
        paths = [
            "scripts/check_v123_continuity.py",
            "tests/test_context_pack_parallel_policy.py",
            "docs/03-continuity/PROBLEM_REGISTRY.yaml",
            "docs/03-continuity/REUSABLE_PATTERNS.md",
            "docs/03-continuity/PITFALLS.md",
        ]
        for path in paths:
            with self.subTest(path=path):
                plan = affected.build_plan(self.document, "MODULE", [path], root=ROOT)
                self.assertIn("governance-knowledge-unit", {row["id"] for row in plan["checks"]})
                self.assertIn("tooling", plan["ci_jobs"])

    def test_visual_acceptance_changes_select_visual_tooling_without_android(self) -> None:
        paths = [
            "catalogs/ui_visual_acceptance.csv",
            "scripts/check_ui_visual_acceptance.py",
            "tests/test_ui_visual_acceptance.py",
            "tests/test_home_contract_alignment.py",
            "design/R06-UI-FROZEN/specs/SCR-HOME-001.md",
        ]
        for path in paths:
            with self.subTest(path=path):
                plan = affected.build_plan(self.document, "MODULE", [path], root=ROOT)
                ids = {row["id"] for row in plan["checks"]}
                self.assertIn("visual-acceptance-unit", ids)
                self.assertIn("tooling", plan["ci_jobs"])
                self.assertNotIn("android-module", ids)

    def test_integration_ignores_changed_path_filter(self) -> None:
        plan = affected.build_plan(self.document, "INTEGRATION", [], root=ROOT)
        ids = {row["id"] for row in plan["checks"]}
        self.assertIn("integration-backend", ids)
        self.assertIn("integration-android", ids)
        self.assertIn("integration-postgres-migration", ids)
        self.assertNotIn("release-close-gate", ids)

    def test_release_includes_integration_without_machine_close(self) -> None:
        plan = affected.build_plan(self.document, "RELEASE", [], root=ROOT)
        ids = {row["id"] for row in plan["checks"]}
        self.assertIn("integration-backend", ids)
        self.assertIn("integration-postgres-migration", ids)
        self.assertIn("integration-android", ids)
        self.assertNotIn("release-close-gate", ids)

    def test_machine_close_only_verifies_existing_release_evidence(self) -> None:
        with self.assertRaises(affected.ImpactMapError):
            affected.build_plan(self.document, "MACHINE_CLOSE", [], root=ROOT)
        plan = affected.build_plan(
            self.document, "MACHINE_CLOSE", [], root=ROOT, release="R11"
        )
        ids = {row["id"] for row in plan["checks"]}
        self.assertEqual({"release-close-gate"}, ids)
        release = plan["checks"][0]
        self.assertIn("R11", release["command"])
        self.assertIn("--machine-close-gate", release["command"])

    def test_runtime_overrides_avoid_machine_path_assumptions(self) -> None:
        with mock.patch.dict(os.environ, {"HHY_GIT_BIN": "X:/portable/git.exe", "HHY_PNPM_BIN": "X:/portable/pnpm.cmd"}):
            self.assertEqual("X:/portable/git.exe", affected.resolve_git_executable())
            self.assertEqual("X:/portable/pnpm.cmd", affected.resolve_pnpm_executable())

    def test_repository_wrappers_are_absolute_for_portable_execution(self) -> None:
        tools = affected._tool_values(ROOT, None)
        self.assertTrue(Path(tools["maven_wrapper"]).is_absolute())
        self.assertTrue(Path(tools["gradle_wrapper"]).is_absolute())
        self.assertEqual("mvnw.cmd" if os.name == "nt" else "mvnw",
                         Path(tools["maven_wrapper"]).name)
        self.assertEqual("gradlew.bat" if os.name == "nt" else "gradlew",
                         Path(tools["gradle_wrapper"]).name)

    @mock.patch.object(affected.subprocess, "run")
    def test_execute_plan_records_failures_without_stopping(self, run: mock.Mock) -> None:
        run.side_effect = [
            mock.Mock(returncode=1, stdout="first", stderr="failed"),
            mock.Mock(returncode=0, stdout="second", stderr=""),
        ]
        plan = {
            "checks": [
                {"id": "one", "command": ["one"], "cwd": str(ROOT), "env": {}, "timeout_seconds": 1},
                {"id": "two", "command": ["two"], "cwd": str(ROOT), "env": {}, "timeout_seconds": 1},
            ]
        }
        result = affected.execute_plan(plan)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual(["FAIL", "PASS"], [row["status"] for row in result["results"]])
        child_environment = run.call_args_list[0].kwargs["env"]
        self.assertEqual(affected.resolve_git_executable(), child_environment["HHY_GIT_BIN"])
        self.assertIn(str(Path(affected.resolve_git_executable()).parent), child_environment["PATH"])


class TestConditionalDocumentationGate(TestCase):
    def test_document_inputs_trigger_gate_but_application_code_does_not(self) -> None:
        self.assertTrue(continuity_gate.document_gate_required(["contracts/openapi.yaml"]))
        self.assertTrue(continuity_gate.document_gate_required(["releases/R02/STORIES.yaml"]))
        self.assertFalse(continuity_gate.document_gate_required(["services/backend/access/Auth.java"]))

    @mock.patch.object(continuity_gate, "run_command")
    def test_unaffected_gate_is_skipped_without_subprocess(self, run_command: mock.Mock) -> None:
        report = continuity_gate.Report("pre-commit")
        continuity_gate.run_document_gate(
            report,
            ["apps/android/app/Main.kt", "catalogs/session_index.csv"],
        )
        run_command.assert_not_called()
        self.assertEqual("SKIPPED_UNAFFECTED", report.metrics["document_gate"]["status"])

    def test_first_push_uses_session_base_instead_of_entire_history(self) -> None:
        session = {"git": {"base_commit": "abc123"}}
        self.assertEqual("abc123", continuity_gate.prepush_base_ref({"upstream": None}, session))
        self.assertEqual(
            "origin/task/TASK-R02-001",
            continuity_gate.prepush_base_ref(
                {"upstream": "origin/task/TASK-R02-001"}, session
            ),
        )


class TestTreeFingerprintPruning(TestCase):
    def test_excluded_dependency_and_build_directories_are_not_traversed(self) -> None:
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "src").mkdir()
            (root / "src" / "main.txt").write_text("source", encoding="utf-8")
            (root / "node_modules" / "pkg").mkdir(parents=True)
            (root / "node_modules" / "pkg" / "index.js").write_text("ignored", encoding="utf-8")
            (root / "apps" / "demo" / "build").mkdir(parents=True)
            (root / "apps" / "demo" / "build" / "output.bin").write_bytes(b"ignored")
            original_walk = os.walk
            visited: list[str] = []

            def observing_walk(*args, **kwargs):
                for current, directories, files in original_walk(*args, **kwargs):
                    visited.append(Path(current).relative_to(root).as_posix())
                    yield current, directories, files

            with mock.patch.object(continuity_lib.os, "walk", side_effect=observing_walk):
                result = continuity_lib.tree_fingerprint(root)

            self.assertEqual(1, result["file_count"])
            self.assertFalse(any("node_modules" in row or row.endswith("build") for row in visited))
