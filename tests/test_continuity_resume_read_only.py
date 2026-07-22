#!/usr/bin/env python3
"""Regression coverage for the read-only no-session resume boundary."""
from __future__ import annotations

from argparse import Namespace
from pathlib import Path
import sys
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import continuity  # noqa: E402


class ReadOnlyResumeTest(unittest.TestCase):
    def test_no_session_resume_never_calls_writing_context_builder(self) -> None:
        payload = {
            "bootstrap_required": False,
            "next_task": {"id": "TASK-R08-007"},
            "resume_command": "python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-R08-007",
            "rule_readiness": {"status": "PASS"},
            "context_fresh": True,
            "context_reason": "PASS",
        }
        with (
            patch.object(continuity, "initialize_continuity_files"),
            patch.object(continuity, "validate_event_chain", return_value={"valid": True, "errors": []}),
            patch.object(continuity, "current_session", return_value=None),
            patch.object(continuity, "read_only_no_session_resume_payload", return_value=payload),
            patch.object(continuity, "build_context_pack") as writing_builder,
            patch.object(continuity, "print_yaml") as output,
        ):
            continuity.command_resume(Namespace())
            continuity.command_resume(Namespace())

        writing_builder.assert_not_called()
        self.assertEqual(output.call_count, 2)
        for call in output.call_args_list:
            result = call.args[0]
            self.assertEqual(result["status"], "READY_TO_START")
            self.assertEqual(result["rule_readiness"], "PASS")
            self.assertIs(result["repository_mutated"], False)

    def test_live_rule_manifest_is_required_but_tree_only_staleness_is_non_mutating(self) -> None:
        policy = {"bootstrap": {"allow_without_git_task_ids": []}}
        with (
            patch.object(continuity, "read_current_status", return_value={"active_release": "R08"}),
            patch.object(continuity, "read_next_task", return_value={"id": "TASK-R08-007", "release": "R08"}),
            patch.object(continuity, "git_info", return_value={"head": "a" * 40}),
            patch.object(continuity, "load_policy", return_value=policy),
            patch.object(continuity, "context_source_paths", return_value=[Path("AGENTS.md")]),
            patch.object(continuity, "portable_source_record", return_value={"path": "AGENTS.md", "sha256": "b" * 64}),
            patch.object(continuity, "rule_readiness_payload", return_value={"status": "PASS", "missing_sources": []}) as readiness,
            patch.object(continuity, "git_has_concrete_head", return_value=True),
            patch.object(continuity, "context_is_fresh", return_value=(True, "PASS")) as freshness,
        ):
            result = continuity.read_only_no_session_resume_payload(Path("repo"))

        self.assertEqual(result["resume_command"], "python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-R08-007")
        readiness.assert_called_once()
        freshness.assert_called_once_with(Path("repo"), None, verify_tree=False)

    def test_missing_required_rule_source_blocks_resume(self) -> None:
        with (
            patch.object(continuity, "read_current_status", return_value={"active_release": "R08"}),
            patch.object(continuity, "read_next_task", return_value={"id": "TASK-R08-007", "release": "R08"}),
            patch.object(continuity, "git_info", return_value={"head": "a" * 40}),
            patch.object(continuity, "load_policy", return_value={}),
            patch.object(continuity, "context_source_paths", return_value=[]),
            patch.object(
                continuity,
                "rule_readiness_payload",
                return_value={"status": "FAIL", "missing_sources": ["AGENTS.md"]},
            ),
        ):
            with self.assertRaisesRegex(continuity.ContinuityError, "AGENTS.md"):
                continuity.read_only_no_session_resume_payload(Path("repo"))


if __name__ == "__main__":
    unittest.main()
