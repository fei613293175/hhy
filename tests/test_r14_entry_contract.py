#!/usr/bin/env python3
"""Regression tests for the R14 chat implementation entry gate."""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from check_r14_entry_contract import PAGES, validate  # noqa: E402


class R14EntryContractTest(unittest.TestCase):
    def test_repository_entry_contract_passes(self) -> None:
        self.assertEqual([], validate(ROOT))

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
