#!/usr/bin/env python3
"""Regression tests for the R14 chat implementation entry gate."""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from check_r14_entry_contract import (  # noqa: E402
    ENTRY_VISUAL_STATUSES,
    PAGES,
    read_csv,
    validate,
)


class R14EntryContractTest(unittest.TestCase):
    def test_repository_entry_contract_passes(self) -> None:
        self.assertEqual([], validate(ROOT))

    def test_entry_contract_accepts_completed_visual_lifecycle(self) -> None:
        def read_with_visual_pass(root: Path, relative: str) -> list[dict[str, str]]:
            rows = deepcopy(read_csv(root, relative))
            if relative == "catalogs/ui_visual_acceptance.csv":
                for row in rows:
                    if row.get("计划版本") == "R14":
                        row["验收状态"] = "PASS"
            return rows

        with patch("check_r14_entry_contract.read_csv", side_effect=read_with_visual_pass):
            self.assertEqual([], validate(ROOT))

    def test_entry_contract_rejects_non_progress_visual_status(self) -> None:
        def read_with_blocked_visual(root: Path, relative: str) -> list[dict[str, str]]:
            rows = deepcopy(read_csv(root, relative))
            if relative == "catalogs/ui_visual_acceptance.csv":
                for row in rows:
                    if row.get("计划版本") == "R14" and row.get("页面ID") == "SCR-CHAT-001":
                        row["验收状态"] = "BLOCKED_REDESIGN"
            return rows

        with patch("check_r14_entry_contract.read_csv", side_effect=read_with_blocked_visual):
            self.assertIn(
                "R14_VISUAL_LIFECYCLE_STATUS SCR-CHAT-001 BLOCKED_REDESIGN",
                validate(ROOT),
            )

    def test_entry_visual_statuses_only_allow_forward_progress(self) -> None:
        self.assertEqual({"IN_REVIEW", "PASS"}, ENTRY_VISUAL_STATUSES)

    def test_expected_page_set_is_exact(self) -> None:
        self.assertEqual(
            {
                "SCR-CHAT-001", "SCR-CHAT-002", "SHEET-CHAT-001",
                "SHEET-CHAT-002", "DIALOG-CHAT-BLOCK-001",
                "DIALOG-CHAT-DELETE-001",
            },
            set(deepcopy(PAGES)),
        )


if __name__ == "__main__":
    unittest.main()
