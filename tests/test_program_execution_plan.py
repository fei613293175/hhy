from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from check_program_execution_plan import ancestors, validate_plan  # noqa: E402
from generate_program_execution_plan import RELEASES, build_plan  # noqa: E402


class ProgramExecutionPlanTest(unittest.TestCase):
    def setUp(self) -> None:
        self.plan = build_plan(ROOT)

    def test_repository_plan_is_valid(self) -> None:
        actual = yaml.safe_load(
            (ROOT / "releases" / "PROGRAM_EXECUTION_PLAN.yaml").read_text(encoding="utf-8")
        )
        self.assertEqual([], validate_plan(ROOT, actual))

    def test_rejects_extra_worker_slot(self) -> None:
        changed = copy.deepcopy(self.plan)
        changed["operating_model"]["delegated_worker_slots"] = 4
        self.assertTrue(any("three delegated" in item for item in validate_plan(ROOT, changed)))

    def test_rejects_default_delegation_drift(self) -> None:
        changed = copy.deepcopy(self.plan)
        changed["operating_model"]["default_delegation_mode"] = "MANUAL"
        self.assertTrue(any("default_delegation_mode" in item for item in validate_plan(ROOT, changed)))

    def test_rejects_missing_near_term_parallel_capacity(self) -> None:
        path = ROOT / "releases/R02/PARALLEL_EXECUTION_PLAN.yaml"
        original = path.read_text(encoding="utf-8")
        try:
            data = yaml.safe_load(original)
            data["max_parallel_workers"] = 2
            path.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False), encoding="utf-8")
            self.assertTrue(any("exactly three" in item for item in validate_plan(ROOT, self.plan)))
        finally:
            path.write_text(original, encoding="utf-8")

    def test_rejects_missing_release(self) -> None:
        changed = copy.deepcopy(self.plan)
        changed["release_plan"] = changed["release_plan"][:-1]
        self.assertTrue(any("R02-R32" in item for item in validate_plan(ROOT, changed)))

    def test_r32_transitively_requires_every_prior_release(self) -> None:
        dependencies = yaml.safe_load(
            (ROOT / "releases" / "RELEASE_DEPENDENCIES.yaml").read_text(encoding="utf-8")
        )["dependencies"]
        required = {"P00", "R01", *RELEASES[:-1]}
        self.assertEqual(set(), required - ancestors("R32", dependencies))

    def test_r06_cannot_start_before_r05_is_closed(self) -> None:
        dependencies = yaml.safe_load(
            (ROOT / "releases" / "RELEASE_DEPENDENCIES.yaml").read_text(encoding="utf-8")
        )["dependencies"]
        self.assertIn("R05", dependencies["R06"])


if __name__ == "__main__":
    unittest.main()
