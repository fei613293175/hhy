#!/usr/bin/env python3
"""Regression tests for the narrowly authorized BLOCKED resume path."""
from __future__ import annotations

from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from continuity import blocked_resume_command, blocked_resume_is_authorized  # noqa: E402


class BlockedResumeAuthorizationTest(unittest.TestCase):
    TASK = "TASK-R03-007"

    def task(self, status: str = "BLOCKED", task_id: str | None = None) -> dict:
        return {"id": task_id or self.TASK, "status": status}

    def next_task(
        self,
        status: str = "BLOCKED",
        task_id: str | None = None,
        command: str | None = None,
    ) -> dict:
        return {
            "id": task_id or self.TASK,
            "status": status,
            "resume_command": command or blocked_resume_command(self.TASK),
        }

    def test_exact_close_generated_blocked_resume_is_allowed(self) -> None:
        self.assertTrue(
            blocked_resume_is_authorized(self.TASK, self.task(), self.next_task())
        )

    def test_missing_or_mismatched_resume_command_is_rejected(self) -> None:
        missing = self.next_task()
        missing.pop("resume_command")
        self.assertFalse(blocked_resume_is_authorized(self.TASK, self.task(), missing))
        self.assertFalse(
            blocked_resume_is_authorized(
                self.TASK,
                self.task(),
                self.next_task(command="python3 scripts/continuity.py start --actor x --task TASK-R03-007"),
            )
        )

    def test_different_task_or_single_sided_block_is_rejected(self) -> None:
        self.assertFalse(
            blocked_resume_is_authorized(
                self.TASK, self.task(task_id="TASK-R03-006"), self.next_task()
            )
        )
        self.assertFalse(
            blocked_resume_is_authorized(
                self.TASK, self.task(), self.next_task(status="READY")
            )
        )

    def test_ready_and_in_progress_are_not_misclassified_as_blocked_resume(self) -> None:
        for status in ("READY", "IN_PROGRESS"):
            with self.subTest(status=status):
                self.assertFalse(
                    blocked_resume_is_authorized(
                        self.TASK, self.task(status=status), self.next_task(status=status)
                    )
                )


if __name__ == "__main__":
    unittest.main()
