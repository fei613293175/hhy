from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "run_r02_test_matrix", ROOT / "scripts/run_r02_test_matrix.py"
)
assert SPEC and SPEC.loader
MATRIX = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MATRIX
SPEC.loader.exec_module(MATRIX)


class R02TestMatrixTest(unittest.TestCase):
    def test_inventory_is_exact_and_all_adapters_exist(self) -> None:
        inventory, errors = MATRIX.load_inventory()
        self.assertEqual([], errors)
        self.assertEqual(25, len(inventory))
        self.assertEqual(25, len(set(inventory)))
        self.assertEqual(set(inventory), set(MATRIX.assignments()))

    def test_list_mode_never_claims_execution(self) -> None:
        inventory, errors = MATRIX.load_inventory()
        report = MATRIX.build_result(inventory, errors, None, False)
        self.assertEqual("PASS", report["status"])
        self.assertEqual(25, report["summary"]["not_run"])
        self.assertEqual(0, report["summary"]["passed"])

    def test_check_rejects_missing_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, errors = MATRIX.validate_evidence(Path(directory), "1" * 40)
        self.assertTrue(any("exactly one" in error for error in errors))

    def test_evidence_rejects_wrong_source_commit_before_results(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = {
                "schema": MATRIX.EVIDENCE_SCHEMA,
                "suite": "r02-java21-pg17-android-real-api",
                "source_commit": "0" * 40,
                "command": ["mvn verify"],
                "generated_at": "2026-07-18T00:00:00Z",
                "environment": {
                    "java": "21",
                    "maven": "3.9.11",
                    "postgresql": "17.10",
                    "android_image": "sha256:test",
                    "api_base_url": "https://api.orbexa.cc",
                },
                "results": [],
            }
            (root / "wrong.evidence.json").write_text(json.dumps(report), encoding="utf-8")
            _, errors = MATRIX.validate_evidence(root, "1" * 40)
        self.assertTrue(any("source_commit" in error for error in errors))
        self.assertTrue(any("missing test IDs" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
