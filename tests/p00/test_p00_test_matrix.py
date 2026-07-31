#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/run_p00_test_matrix.py"
SPEC = importlib.util.spec_from_file_location("run_p00_test_matrix", SCRIPT)
assert SPEC and SPEC.loader
matrix = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = matrix
SPEC.loader.exec_module(matrix)


class P00TestMatrixTest(unittest.TestCase):
    def test_apk_reject_adapter_binds_real_automation(self) -> None:
        adapter_path = ROOT / "tests/p00/req-apk-001_reject"
        adapter = json.loads(adapter_path.read_text(encoding="utf-8"))
        self.assertEqual(adapter["schema"], "hhy.p00.test-adapter/v1")
        self.assertEqual(adapter["test_id"], "TST-APK_001-REJECT")
        self.assertEqual(adapter["central_runner"], "scripts/run_p00_test_matrix.py")
        self.assertEqual(adapter["assignment_source"], "external")
        self.assertEqual(adapter["runner"], "p00-apk-validation")
        self.assertGreaterEqual(len(adapter["selectors"]), 8)
        self.assertGreaterEqual(len(adapter["fault_classes"]), 8)

        for selector in adapter["selectors"]:
            relative_path, separator, symbol = selector.partition("#")
            self.assertEqual(separator, "#", selector)
            self.assertTrue(symbol, selector)
            source = ROOT / relative_path
            self.assertTrue(source.is_file(), selector)
            leaf_symbol = symbol.rsplit(".", 1)[-1]
            self.assertIn(leaf_symbol, source.read_text(encoding="utf-8"), selector)

    def test_repository_inventory_is_exact_and_includes_baseline_reuse(self) -> None:
        manifest_ids, catalog, errors = matrix.load_inventory()
        self.assertEqual(errors, [])
        self.assertEqual(len(manifest_ids), 59)
        self.assertEqual(len(set(manifest_ids)), 59)
        self.assertEqual(set(matrix.build_assignments()), set(manifest_ids))
        native = sum(1 for test_id in manifest_ids if catalog[test_id]["计划版本"] == "P00")
        self.assertEqual(native, 50)
        self.assertEqual(len(manifest_ids) - native, 9)

    def test_list_mode_executes_nothing_and_reports_not_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "list.json"
            completed = subprocess.run(
                [sys.executable, str(SCRIPT), "--list", "--json-out", str(output)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(payload["status"], "VALID")
            self.assertEqual(payload["summary"]["manifest"], 59)
            self.assertEqual(payload["summary"]["executed"], 0)
            self.assertEqual({item["status"] for item in payload["results"]}, {"NOT_RUN"})

    def _write_report(
        self,
        root: Path,
        *,
        test_id: str,
        suite: str,
        status: str = "PASS",
        executed: bool = True,
        artifact_hash: str | None = None,
    ) -> None:
        artifact = root / "logs" / "command.log"
        artifact.parent.mkdir(parents=True, exist_ok=True)
        artifact.write_text("real command output\n", encoding="utf-8")
        digest = artifact_hash or hashlib.sha256(artifact.read_bytes()).hexdigest()
        now = datetime.now(timezone.utc).isoformat()
        payload = {
            "schema": matrix.EVIDENCE_SCHEMA,
            "report_id": "unit-report",
            "suite": suite,
            "producer": "unit-test",
            "generated_at": now,
            "source_commit": "a" * 40,
            "command": ["tool", "--verify"],
            "environment": {"kind": "unit-fixture"},
            "results": [
                {
                    "test_id": test_id,
                    "status": status,
                    "executed": executed,
                    "exit_code": 0,
                    "started_at": now,
                    "finished_at": now,
                    "assertions": [{"name": "fixture assertion", "status": "PASS"}],
                    "artifacts": [{"path": "logs/command.log", "sha256": digest}],
                    "details": "unit fixture only; not release evidence",
                }
            ],
        }
        (root / "report.evidence.json").write_text(json.dumps(payload), encoding="utf-8")

    def test_valid_external_evidence_is_accepted_for_expected_subset(self) -> None:
        assignments = {"TST-APK_001-HAPPY": matrix.Assignment("external", "p00-apk-validation")}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(
                root,
                test_id="TST-APK_001-HAPPY",
                suite="p00-apk-validation",
            )
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertEqual(errors, [])
        self.assertEqual(results["TST-APK_001-HAPPY"]["status"], "PASS")

    def test_all_41_external_assignments_can_be_satisfied_exactly(self) -> None:
        assignments = {
            test_id: assignment
            for test_id, assignment in matrix.build_assignments().items()
            if assignment.source == "external"
        }
        self.assertEqual(len(assignments), 41)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact = root / "logs" / "suite.log"
            artifact.parent.mkdir(parents=True)
            artifact.write_text("fixture command output\n", encoding="utf-8")
            digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
            now = datetime.now(timezone.utc).isoformat()
            suites = sorted({assignment.runner for assignment in assignments.values()})
            for suite in suites:
                test_ids = sorted(
                    test_id for test_id, assignment in assignments.items() if assignment.runner == suite
                )
                payload = {
                    "schema": matrix.EVIDENCE_SCHEMA,
                    "report_id": f"fixture-{suite}",
                    "suite": suite,
                    "producer": "unit-test",
                    "generated_at": now,
                    "source_commit": "a" * 40,
                    "command": ["tool", "--verify", suite],
                    "environment": {"kind": "unit-fixture"},
                    "results": [
                        {
                            "test_id": test_id,
                            "status": "PASS",
                            "executed": True,
                            "exit_code": 0,
                            "started_at": now,
                            "finished_at": now,
                            "assertions": [{"name": "fixture assertion", "status": "PASS"}],
                            "artifacts": [{"path": "logs/suite.log", "sha256": digest}],
                            "details": "unit fixture only; not release evidence",
                        }
                        for test_id in test_ids
                    ],
                }
                (root / f"{suite}.evidence.json").write_text(
                    json.dumps(payload), encoding="utf-8"
                )
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertEqual(errors, [])
        self.assertEqual(len(results), 41)
        self.assertEqual({item["status"] for item in results.values()}, {"PASS"})

    def test_na_or_unexecuted_evidence_cannot_pass(self) -> None:
        assignments = {"TST-APK_001-HAPPY": matrix.Assignment("external", "p00-apk-validation")}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(
                root,
                test_id="TST-APK_001-HAPPY",
                suite="p00-apk-validation",
                status="N/A",
                executed=False,
            )
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertTrue(errors)
        self.assertEqual(results["TST-APK_001-HAPPY"]["status"], "FAIL")
        self.assertTrue(any("status must be PASS" in error for error in errors))
        self.assertTrue(any("executed must be true" in error for error in errors))

    def test_tampered_artifact_and_stale_commit_are_rejected(self) -> None:
        assignments = {"TST-APK_001-HAPPY": matrix.Assignment("external", "p00-apk-validation")}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(
                root,
                test_id="TST-APK_001-HAPPY",
                suite="p00-apk-validation",
                artifact_hash="0" * 64,
            )
            results, errors = matrix.validate_evidence(root, assignments, "b" * 40)
        self.assertEqual(results["TST-APK_001-HAPPY"]["status"], "FAIL")
        self.assertTrue(any("source_commit" in error for error in errors))
        self.assertTrue(any("sha256 mismatch" in error for error in errors))

    def test_missing_evidence_is_a_hard_failure(self) -> None:
        assignments = {"TST-APK_001-HAPPY": matrix.Assignment("external", "p00-apk-validation")}
        with tempfile.TemporaryDirectory() as temporary:
            results, errors = matrix.validate_evidence(Path(temporary), assignments, "a" * 40)
        self.assertEqual(results, {})
        self.assertTrue(errors)


class P00SourceCommitPolicyTest(unittest.TestCase):
    def git(self, root: Path, *arguments: str) -> str:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        return completed.stdout.strip()

    def initialize(self, root: Path) -> str:
        self.git(root, "init")
        self.git(root, "config", "user.email", "p00-matrix@example.invalid")
        self.git(root, "config", "user.name", "P00 Matrix Test")
        (root / "business.txt").write_text("tested source\n", encoding="utf-8")
        self.git(root, "add", "business.txt")
        self.git(root, "commit", "-m", "tested source")
        return self.git(root, "rev-parse", "HEAD")

    def commit_file(self, root: Path, relative: str, content: str) -> str:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        self.git(root, "add", "--", relative)
        self.git(root, "commit", "-m", f"change {relative}")
        return self.git(root, "rev-parse", "HEAD")

    def test_exact_head_is_accepted_without_ancestor_exception(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            head = self.initialize(root)
            details, errors = matrix.validate_source_commit(head, head, root)
        self.assertEqual(errors, [])
        self.assertEqual(details["relationship"], "HEAD")

    def test_ancestor_with_only_closure_metadata_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = self.initialize(root)
            head = self.commit_file(root, ".continuity/STATE.yaml", "status: READY\n")
            details, errors = matrix.validate_source_commit(source, head, root)
        self.assertEqual(errors, [])
        self.assertEqual(details["relationship"], "ANCESTOR_CLOSURE_ONLY")
        self.assertEqual(details["post_test_changes"], [".continuity/STATE.yaml"])

    def test_ancestor_with_business_change_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = self.initialize(root)
            head = self.commit_file(root, "services/backend/Changed.java", "class Changed {}\n")
            details, errors = matrix.validate_source_commit(source, head, root)
        self.assertTrue(errors)
        self.assertEqual(details["relationship"], "ANCESTOR_WITH_FORBIDDEN_CHANGES")
        self.assertTrue(any("services/backend/Changed.java" in error for error in errors))

    def test_non_ancestor_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            base = self.initialize(root)
            self.git(root, "checkout", "-b", "left")
            left = self.commit_file(root, ".continuity/LEFT.yaml", "left: true\n")
            self.git(root, "checkout", "-b", "right", base)
            right = self.commit_file(root, ".continuity/RIGHT.yaml", "right: true\n")
            details, errors = matrix.validate_source_commit(left, right, root)
        self.assertTrue(errors)
        self.assertEqual(details["relationship"], "NON_ANCESTOR_REJECTED")

    def test_shallow_history_rejects_ancestor_exception(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            source_root = Path(temporary) / "source"
            clone_root = Path(temporary) / "clone"
            source_root.mkdir()
            source = self.initialize(source_root)
            self.commit_file(source_root, ".continuity/STATE.yaml", "status: READY\n")
            completed = subprocess.run(
                ["git", "clone", "--depth", "1", source_root.as_uri(), str(clone_root)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
            head = self.git(clone_root, "rev-parse", "HEAD")
            self.assertEqual(self.git(clone_root, "rev-parse", "--is-shallow-repository"), "true")
            details, errors = matrix.validate_source_commit(source, head, clone_root)
        self.assertTrue(errors)
        self.assertEqual(details["relationship"], "SHALLOW_REJECTED")

    def test_allowlist_does_not_cover_business_or_traversal_paths(self) -> None:
        self.assertTrue(matrix.post_test_path_allowed(".continuity/STATE.yaml"))
        self.assertTrue(
            matrix.post_test_path_allowed(
                "artifacts/validation/p00-test-evidence/backend/run.evidence.json"
            )
        )
        self.assertTrue(matrix.post_test_path_allowed("releases/P00/ACCEPTANCE_MATRIX.csv"))
        for path in (
            "services/backend/pom.xml",
            "database/migrations/V999.sql",
            "contracts/openapi.yaml",
            "config/CONFIG_REGISTRY.yaml",
            "scripts/run_p00_test_matrix.py",
            "../services/backend/Changed.java",
        ):
            self.assertFalse(matrix.post_test_path_allowed(path), path)


if __name__ == "__main__":
    unittest.main()
