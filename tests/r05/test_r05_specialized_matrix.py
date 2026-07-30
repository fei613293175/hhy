from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "run_r05_specialized_matrix", ROOT / "scripts/run_r05_specialized_matrix.py"
)
assert SPEC and SPEC.loader
MATRIX = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MATRIX
SPEC.loader.exec_module(MATRIX)


class R05SpecializedMatrixTest(unittest.TestCase):
    def test_inventory_is_exact_and_all_methods_exist(self) -> None:
        inventory, errors = MATRIX.load_inventory()
        self.assertEqual([], errors)
        self.assertEqual(8, len(inventory))
        self.assertEqual(8, len(set(inventory)))

    def test_missing_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "missing.json"
            errors = MATRIX.validate_evidence(path, MATRIX.load_inventory()[0])
        self.assertTrue(any("cannot load evidence" in error for error in errors))

    def test_wrong_environment_and_missing_results_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text(json.dumps({
                "schema": MATRIX.EVIDENCE_SCHEMA,
                "suite": MATRIX.RUNNER,
                "source_commit": "0" * 40,
                "environment": {"java": "17", "postgresql": "16"},
                "results": [],
            }), encoding="utf-8")
            errors = MATRIX.validate_evidence(path, MATRIX.load_inventory()[0])
        self.assertTrue(any("Java 21" in error for error in errors))
        self.assertTrue(any("PostgreSQL 17" in error for error in errors))
        self.assertTrue(any("exactly match" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
