from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("run_r04_test_matrix", ROOT / "scripts/run_r04_test_matrix.py")
assert SPEC and SPEC.loader
MATRIX = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MATRIX
SPEC.loader.exec_module(MATRIX)


class R04TestMatrixTest(unittest.TestCase):
    def test_inventory_is_exact_and_all_adapters_exist(self) -> None:
        inventory, errors = MATRIX.load_inventory()
        self.assertEqual([], errors)
        self.assertEqual(6, len(inventory))
        self.assertEqual(6, len(set(inventory)))

    def test_list_mode_never_claims_execution(self) -> None:
        inventory, errors = MATRIX.load_inventory()
        report = MATRIX.build_result(inventory, errors, None, False)
        self.assertEqual("PASS", report["status"])
        self.assertEqual(0, report["summary"]["passed"])
        self.assertEqual(6, report["summary"]["not_run"])

    def test_check_rejects_missing_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, errors = MATRIX.validate_evidence(Path(directory), "1" * 40, MATRIX.load_inventory()[0])
        self.assertTrue(any("exactly one" in error for error in errors))

    def test_evidence_rejects_wrong_environment_and_missing_results(self) -> None:
        inventory = MATRIX.load_inventory()[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = {
                "schema": MATRIX.EVIDENCE_SCHEMA,
                "suite": MATRIX.RUNNER,
                "source_commit": "1" * 40,
                "command": ["verify"],
                "generated_at": "2026-07-19T00:00:00Z",
                "environment": {
                    "java": "17",
                    "maven": "3.9.11",
                    "postgresql": "16",
                    "android_image": "wrong",
                    "api_base_url": "http://example.invalid",
                },
                "results": [],
            }
            (root / "bad.evidence.json").write_text(json.dumps(report), encoding="utf-8")
            _, errors = MATRIX.validate_evidence(root, "1" * 40, inventory)
        self.assertTrue(any("Java 21" in error for error in errors))
        self.assertTrue(any("PostgreSQL 17" in error for error in errors))
        self.assertTrue(any("android_image" in error for error in errors))
        self.assertTrue(any("api_base_url" in error for error in errors))
        self.assertTrue(any("missing test IDs" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
