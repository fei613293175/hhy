from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "run_r13_specialized_matrix", ROOT / "scripts/run_r13_specialized_matrix.py"
)
assert SPEC and SPEC.loader
MATRIX = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MATRIX
SPEC.loader.exec_module(MATRIX)


class R13SpecializedMatrixTest(unittest.TestCase):
    def test_inventory_is_exact_and_all_selectors_exist(self) -> None:
        inventory, errors = MATRIX.load_inventory()
        self.assertEqual([], errors)
        self.assertEqual(MATRIX.EXPECTED_IDS, set(inventory))

    def test_missing_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors = MATRIX.validate_evidence(
                Path(directory) / "missing.json", sorted(MATRIX.EXPECTED_IDS)
            )
        self.assertTrue(any("cannot load evidence" in error for error in errors))

    def test_wrong_environment_and_results_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text(json.dumps({
                "schema": MATRIX.EVIDENCE_SCHEMA,
                "suite": MATRIX.RUNNER,
                "source_commit": "0" * 40,
                "environment": {"java": "17", "postgresql": "16", "android_gradle_jdk": "17"},
                "results": [],
            }), encoding="utf-8")
            errors = MATRIX.validate_evidence(path, sorted(MATRIX.EXPECTED_IDS))
        self.assertTrue(any("Java 21" in error for error in errors))
        self.assertTrue(any("PostgreSQL 17" in error for error in errors))
        self.assertTrue(any("Android Gradle JDK 21" in error for error in errors))
        self.assertTrue(any("exactly match" in error for error in errors))

    def test_artifact_path_escape_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            outside = root.parent / "outside-r13.log"
            outside.write_text("not trusted", encoding="utf-8")
            path = root / "bad-path.json"
            path.write_text(json.dumps({
                "schema": MATRIX.EVIDENCE_SCHEMA,
                "suite": MATRIX.RUNNER,
                "source_commit": "0" * 40,
                "environment": {"java": "21", "postgresql": "17", "android_gradle_jdk": "21"},
                "results": [{
                    "test_id": test_id,
                    "status": "PASS",
                    "exit_code": 0,
                    "assertions": ["assertion"],
                    "artifacts": [{"path": "../outside-r13.log", "sha256": "0" * 64}],
                } for test_id in sorted(MATRIX.EXPECTED_IDS)],
            }), encoding="utf-8")
            errors = MATRIX.validate_evidence(path, sorted(MATRIX.EXPECTED_IDS))
        self.assertTrue(any("artifact is missing" in error for error in errors))
        outside.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
