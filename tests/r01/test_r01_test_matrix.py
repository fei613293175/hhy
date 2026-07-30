#!/usr/bin/env python3
from __future__ import annotations

import csv
import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/run_r01_test_matrix.py"
SPEC = importlib.util.spec_from_file_location("run_r01_test_matrix", SCRIPT)
assert SPEC and SPEC.loader
matrix = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = matrix
SPEC.loader.exec_module(matrix)


class R01TestMatrixTest(unittest.TestCase):
    def _copy_adapters(self, target_root: Path) -> list[dict[str, str]]:
        with matrix.CATALOG_PATH.open(encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
        applicable = [row for row in rows if matrix._release_applies(row["计划版本"])]
        for row in applicable:
            relative = row["自动化路径"]
            target = target_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / relative).read_bytes())
        return rows

    def test_repository_inventory_is_exact_catalog_one_to_one_and_closed(self) -> None:
        manifest_ids, catalog, errors = matrix.load_inventory()
        self.assertEqual(errors, [])
        self.assertEqual(len(manifest_ids), 27)
        self.assertEqual(len(set(manifest_ids)), 27)
        self.assertEqual(set(matrix.build_assignments()), set(manifest_ids))
        self.assertEqual(
            {test_id for test_id, row in catalog.items() if matrix._release_applies(row["计划版本"])},
            set(manifest_ids),
        )
        self.assertEqual(
            sum(item.source == "local" for item in matrix.build_assignments().values()), 24
        )
        self.assertEqual(
            sum(item.source == "external" for item in matrix.build_assignments().values()), 3
        )

    def test_inventory_rejects_manifest_duplicate_missing_and_extra(self) -> None:
        original = yaml.safe_load(matrix.MANIFEST_PATH.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            catalog = root / "catalog.csv"
            catalog.write_bytes(matrix.CATALOG_PATH.read_bytes())
            for name, tests in (
                ("duplicate", original["tests"][:-1] + [original["tests"][0]]),
                ("missing", original["tests"][:-1]),
                ("extra", original["tests"] + ["TST-NOT-R01"]),
            ):
                manifest = root / f"{name}.yaml"
                payload = dict(original)
                payload["tests"] = tests
                manifest.write_text(yaml.safe_dump(payload, allow_unicode=True), encoding="utf-8")
                _, _, errors = matrix.load_inventory(manifest, catalog)
                self.assertTrue(errors, name)

    def test_catalog_duplicate_and_manifest_catalog_drift_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with matrix.CATALOG_PATH.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.reader(handle))
            duplicate_catalog = root / "duplicate.csv"
            with duplicate_catalog.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerows(rows + [rows[1]])
            _, _, duplicate_errors = matrix.load_inventory(matrix.MANIFEST_PATH, duplicate_catalog)
            self.assertTrue(any("catalog duplicate" in item for item in duplicate_errors))

            drift_catalog = root / "drift.csv"
            rows[1][6] = "R01"
            with drift_catalog.open("w", encoding="utf-8-sig", newline="") as handle:
                csv.writer(handle).writerows(rows)
            _, _, drift_errors = matrix.load_inventory(matrix.MANIFEST_PATH, drift_catalog)
            self.assertTrue(any("must match 1:1" in item for item in drift_errors))

    def test_missing_adapter_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo_root = Path(temporary)
            rows = self._copy_adapters(repo_root)
            applicable = next(row for row in rows if matrix._release_applies(row["计划版本"]))
            (repo_root / applicable["自动化路径"]).unlink()
            _, _, errors = matrix.load_inventory(
                matrix.MANIFEST_PATH, matrix.CATALOG_PATH, repo_root
            )
        self.assertTrue(any("adapter file is missing" in item for item in errors))

    def test_adapter_test_id_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo_root = Path(temporary)
            rows = self._copy_adapters(repo_root)
            applicable = next(row for row in rows if matrix._release_applies(row["计划版本"]))
            adapter_path = repo_root / applicable["自动化路径"]
            adapter = json.loads(adapter_path.read_text(encoding="utf-8"))
            adapter["test_id"] = "TST-WRONG-ID"
            adapter_path.write_text(json.dumps(adapter), encoding="utf-8")
            _, _, errors = matrix.load_inventory(
                matrix.MANIFEST_PATH, matrix.CATALOG_PATH, repo_root
            )
        self.assertTrue(any("test_id expected=" in item for item in errors))

    def test_adapter_path_escape_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo_root = Path(temporary) / "repo"
            repo_root.mkdir()
            rows = self._copy_adapters(repo_root)
            for row in rows:
                if matrix._release_applies(row["计划版本"]):
                    row["自动化路径"] = "../outside/escape-adapter"
                    break
            catalog_path = Path(temporary) / "catalog.csv"
            with catalog_path.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            _, _, errors = matrix.load_inventory(matrix.MANIFEST_PATH, catalog_path, repo_root)
        self.assertTrue(any("must stay inside repository" in item for item in errors))

    def test_list_mode_executes_nothing(self) -> None:
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
            self.assertEqual(payload["summary"]["manifest"], 27)
            self.assertEqual(payload["summary"]["executed"], 0)
            self.assertEqual({row["status"] for row in payload["results"]}, {"NOT_RUN"})

    def _write_report(
        self,
        root: Path,
        test_ids: list[str],
        *,
        commit: str = "a" * 40,
        status: str = "PASS",
        executed: bool = True,
        artifact_path: str = "logs/backend.log",
        artifact_hash: str | None = None,
        report_name: str = "backend.evidence.json",
        report_id: str = "r01-backend-run",
    ) -> None:
        target = (root / artifact_path).resolve()
        if matrix._inside(target, root.resolve()):
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("maven + pg17 + real api output\n", encoding="utf-8")
            digest = artifact_hash or hashlib.sha256(target.read_bytes()).hexdigest()
        else:
            digest = artifact_hash or "0" * 64
        now = datetime.now(timezone.utc).isoformat()
        payload = {
            "schema": matrix.EVIDENCE_SCHEMA,
            "report_id": report_id,
            "suite": "r01-backend-maven-pg17-real-api",
            "producer": "ci-r01",
            "generated_at": now,
            "source_commit": commit,
            "command": ["mvn", "verify", "-Pr01-real-api"],
            "environment": {
                "java": "21",
                "maven": "3.9.11",
                "postgresql": "17.5",
                "api_base_url": "http://127.0.0.1:8080",
            },
            "results": [
                {
                    "test_id": test_id,
                    "status": status,
                    "executed": executed,
                    "exit_code": 0,
                    "started_at": now,
                    "finished_at": now,
                    "assertions": [{"name": "real API assertion", "status": "PASS"}],
                    "artifacts": [{"path": artifact_path, "sha256": digest}],
                    "details": "Maven integration tests against PostgreSQL 17 and live backend HTTP",
                }
                for test_id in test_ids
            ],
        }
        (root / report_name).write_text(json.dumps(payload), encoding="utf-8")

    def external_assignments(self) -> dict[str, matrix.Assignment]:
        return {
            test_id: assignment
            for test_id, assignment in matrix.build_assignments().items()
            if assignment.source == "external"
        }

    def test_valid_exact_external_evidence_is_accepted(self) -> None:
        assignments = self.external_assignments()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(root, sorted(assignments))
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertEqual(errors, [])
        self.assertEqual(len(results), 3)
        self.assertEqual({row["status"] for row in results.values()}, {"PASS"})
        self.assertEqual(
            {row["source_commit_validation"]["relationship"] for row in results.values()},
            {"HEAD"},
        )

    def test_na_not_run_unexecuted_and_stale_commit_are_rejected(self) -> None:
        test_id = "TST-ADMIN_SECURITY_001-HAPPY"
        assignments = {test_id: matrix.Assignment("external", "r01-backend-maven-pg17-real-api")}
        for status, executed, commit in (
            ("N/A", True, "a" * 40),
            ("NOT_RUN", False, "a" * 40),
            ("PASS", False, "a" * 40),
            ("PASS", True, "b" * 40),
        ):
            with self.subTest(status=status, executed=executed, commit=commit[0]):
                with tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    self._write_report(root, [test_id], status=status, executed=executed, commit=commit)
                    results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
                self.assertTrue(errors)
                self.assertEqual(results[test_id]["status"], "FAIL")

    def test_artifact_escape_missing_empty_and_sha_tampering_are_rejected(self) -> None:
        test_id = "TST-ADMIN_SECURITY_001-HAPPY"
        assignments = {test_id: matrix.Assignment("external", "r01-backend-maven-pg17-real-api")}
        fixtures = (
            ("../outside.log", None),
            ("logs/backend.log", "0" * 64),
        )
        for artifact_path, digest in fixtures:
            with self.subTest(path=artifact_path):
                with tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    self._write_report(root, [test_id], artifact_path=artifact_path, artifact_hash=digest)
                    results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
                self.assertTrue(errors)
                self.assertEqual(results[test_id]["status"], "FAIL")

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(root, [test_id])
            (root / "logs/backend.log").write_text("", encoding="utf-8")
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertTrue(errors)
        self.assertEqual(results[test_id]["status"], "FAIL")

    def test_duplicate_and_missing_external_evidence_are_rejected(self) -> None:
        assignments = self.external_assignments()
        test_id = sorted(assignments)[0]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(root, [test_id], report_name="one.evidence.json", report_id="one")
            self._write_report(root, [test_id], report_name="two.evidence.json", report_id="two")
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertIn(test_id, results)
        self.assertTrue(any("duplicate external evidence" in item for item in errors))
        self.assertTrue(any("missing external evidence" in item for item in errors))

    def test_extra_test_and_non_pg17_environment_are_rejected(self) -> None:
        assignments = self.external_assignments()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(root, ["TST-NOT-IN-R01"])
            report_path = root / "backend.evidence.json"
            payload = json.loads(report_path.read_text(encoding="utf-8"))
            payload["environment"]["postgresql"] = "16.9"
            report_path.write_text(json.dumps(payload), encoding="utf-8")
            results, errors = matrix.validate_evidence(root, assignments, "a" * 40)
        self.assertEqual(results, {})
        self.assertTrue(any("unexpected" in item for item in errors))
        self.assertTrue(any("version 17" in item for item in errors))


class R01SourceCommitPolicyTest(unittest.TestCase):
    def git(self, root: Path, *arguments: str) -> str:
        completed = subprocess.run(
            ["git", *arguments], cwd=root, text=True, capture_output=True, check=False
        )
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        return completed.stdout.strip()

    def initialize(self, root: Path) -> str:
        self.git(root, "init")
        self.git(root, "config", "user.email", "r01-matrix@example.invalid")
        self.git(root, "config", "user.name", "R01 Matrix Test")
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

    def test_exact_head_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            head = self.initialize(root)
            details, errors = matrix.validate_source_commit(head, head, root)
        self.assertEqual(errors, [])
        self.assertEqual(details["relationship"], "HEAD")
        self.assertEqual(details["post_test_changes"], [])

    def test_ancestor_with_evidence_and_closure_metadata_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = self.initialize(root)
            self.commit_file(
                root,
                "artifacts/validation/r01-test-evidence/backend/run.evidence.json",
                "{}\n",
            )
            head = self.commit_file(root, "CURRENT_STATUS.yaml", "status: READY\n")
            details, errors = matrix.validate_source_commit(source, head, root)
        self.assertEqual(errors, [])
        self.assertEqual(details["relationship"], "ANCESTOR_CLOSURE_ONLY")
        self.assertEqual(
            details["post_test_changes"],
            [
                "CURRENT_STATUS.yaml",
                "artifacts/validation/r01-test-evidence/backend/run.evidence.json",
            ],
        )

    def test_ancestor_with_business_change_is_stale(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = self.initialize(root)
            head = self.commit_file(root, "apps/admin-web/src/Changed.ts", "export const changed = true\n")
            details, errors = matrix.validate_source_commit(source, head, root)
        self.assertTrue(errors)
        self.assertEqual(details["relationship"], "ANCESTOR_WITH_FORBIDDEN_CHANGES")
        self.assertTrue(any("apps/admin-web/src/Changed.ts" in error for error in errors))

    def test_non_ancestor_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            base = self.initialize(root)
            self.git(root, "checkout", "-b", "left")
            left = self.commit_file(root, "CURRENT_STATUS.yaml", "left: true\n")
            self.git(root, "checkout", "-b", "right", base)
            right = self.commit_file(root, "NEXT_TASK.yaml", "right: true\n")
            details, errors = matrix.validate_source_commit(left, right, root)
        self.assertTrue(errors)
        self.assertEqual(details["relationship"], "NON_ANCESTOR_REJECTED")

    def test_shallow_repository_rejects_ancestor_exception(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            source_root = Path(temporary) / "source"
            clone_root = Path(temporary) / "clone"
            source_root.mkdir()
            source = self.initialize(source_root)
            self.commit_file(source_root, "CURRENT_STATUS.yaml", "status: READY\n")
            completed = subprocess.run(
                ["git", "clone", "--depth", "1", source_root.as_uri(), str(clone_root)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
            head = self.git(clone_root, "rev-parse", "HEAD")
            self.assertEqual(
                self.git(clone_root, "rev-parse", "--is-shallow-repository"), "true"
            )
            details, errors = matrix.validate_source_commit(source, head, clone_root)
        self.assertTrue(errors)
        self.assertEqual(details["relationship"], "SHALLOW_REJECTED")

    def test_allowlist_excludes_executable_and_traversal_paths(self) -> None:
        for path in (
            "artifacts/validation/r01-test-evidence/backend/run.evidence.json",
            "artifacts/validation/r01-test-matrix-final.json",
            "artifacts/reports/R01/TASK-R01-005.md",
            ".continuity/sessions/SES-R01.yaml",
            "docs/03-continuity/sessions/2026-07/SES-R01.md",
            "releases/R01/ACCEPTANCE_MATRIX.csv",
        ):
            self.assertTrue(matrix.post_test_path_allowed(path), path)
        for path in (
            "apps/admin-web/src/App.vue",
            "services/backend/pom.xml",
            "packages/api-client/src/admin.generated.ts",
            "contracts/admin-openapi.yaml",
            "database/migrations/V999.sql",
            "config/CONFIG_REGISTRY.yaml",
            "design/tokens.json",
            "scripts/run_r01_test_matrix.py",
            "tests/r01/test_r01_test_matrix.py",
            "../artifacts/validation/r01-test-evidence/escape.json",
            "artifacts/validation/r01-test-matrix/report.json",
        ):
            self.assertFalse(matrix.post_test_path_allowed(path), path)


if __name__ == "__main__":
    unittest.main()
