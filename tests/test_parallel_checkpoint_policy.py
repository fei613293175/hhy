from __future__ import annotations

from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from continuity_lib import ContinuityError, normalize_parallel_execution  # noqa: E402


class ParallelCheckpointPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = {
            "parallel_development": {
                "max_delegated_workers": 3,
                "non_delegation_requires_checkpoint_reason": True,
            }
        }

    def test_delegated_workers_are_normalized(self) -> None:
        result = normalize_parallel_execution(self.policy, {
            "assessment": "DELEGATED",
            "workers": [
                {"worker_id": "a", "responsibility": "backend", "allowed_paths": ["services/backend/**"]},
                {"worker_id": "b", "responsibility": "android", "allowed_paths": ["apps/android/**"]},
            ],
        })
        self.assertEqual(result["delegated_workers"], 2)

    def test_fourth_worker_is_rejected(self) -> None:
        workers = [
            {"worker_id": str(index), "responsibility": "test", "allowed_paths": [f"tests/lane-{index}/**"]}
            for index in range(4)
        ]
        with self.assertRaises(ContinuityError):
            normalize_parallel_execution(self.policy, {"assessment": "DELEGATED", "workers": workers})

    def test_non_delegation_requires_reason(self) -> None:
        with self.assertRaises(ContinuityError):
            normalize_parallel_execution(self.policy, {"assessment": "NO_SAFE_PARALLEL", "workers": []})

    def test_overlapping_path_leases_are_rejected(self) -> None:
        with self.assertRaises(ContinuityError):
            normalize_parallel_execution(self.policy, {
                "assessment": "DELEGATED",
                "workers": [
                    {"worker_id": "a", "responsibility": "all tests", "allowed_paths": ["tests/**"]},
                    {"worker_id": "b", "responsibility": "unit tests", "allowed_paths": ["tests/unit/**"]},
                ],
            })

    def test_capability_fallback_is_auditable(self) -> None:
        result = normalize_parallel_execution(self.policy, {
            "assessment": "CAPABILITY_UNAVAILABLE",
            "workers": [],
            "reason": "当前AI运行时不支持执行代理",
        })
        self.assertEqual(result["delegated_workers"], 0)
        self.assertTrue(result["reason"])


if __name__ == "__main__":
    unittest.main()
