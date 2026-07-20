from __future__ import annotations

import unittest

from scripts.continuity_ci_scope import full_lifecycle_reasons


class ContinuityCiScopeTest(unittest.TestCase):
    def test_product_and_session_records_use_fast_path(self) -> None:
        reasons = full_lifecycle_reasons([
            "apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt",
            ".continuity/checkpoints/SES/example.yaml",
            "CURRENT_STATUS.yaml",
        ])
        self.assertEqual([], reasons)

    def test_core_scripts_hooks_and_policy_require_full_lifecycle(self) -> None:
        reasons = full_lifecycle_reasons([
            "scripts/continuity_lib.py",
            ".githooks/pre-push",
            ".continuity/CONTINUITY_POLICY.yaml",
        ])
        self.assertEqual([
            ".continuity/CONTINUITY_POLICY.yaml",
            ".githooks/pre-push",
            "scripts/continuity_lib.py",
        ], reasons)

    def test_scope_workflow_and_regression_change_require_full_lifecycle(self) -> None:
        reasons = full_lifecycle_reasons([
            ".github/workflows/continuity-gate.yml",
            "scripts/continuity_ci_scope.py",
            "tests/test_continuity_bootstrap_recovery.py",
        ])
        self.assertEqual(3, len(reasons))

    def test_initial_history_requires_full_lifecycle(self) -> None:
        self.assertEqual(["INITIAL_HISTORY"], full_lifecycle_reasons([], initial_history=True))


if __name__ == "__main__":
    unittest.main()
