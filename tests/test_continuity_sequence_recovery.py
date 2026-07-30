from __future__ import annotations

import inspect
import os
from pathlib import Path
import sys
import tempfile
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import continuity  # noqa: E402
import continuity_gate  # noqa: E402
from continuity_lib import ContinuityError, git_info, load_policy  # noqa: E402


OLD_SESSION = "SES-20260728T125934Z-F3DED330"
NEW_SESSION = "SES-20260728T154000Z-0458A14C"
HEAD = "c47d19064bec57bd751f1cfa06a0fa7a75844790"
STASH_OID = "c85e7088464eb2c0d12885aa8a39e2cd10c53d9b"
STASH_SUBJECT = "On task/TASK-R03-001: WIP CR-0449 R16 Android order detail before R14 urgent hotfix"
REASON = "R14未完成且R15未开发时错误进入R16；事务退回R14并保留R16实现证据"


def managed_snapshot(root: Path) -> dict[str, bytes]:
    recovery_id = f"SEQREC-{OLD_SESSION}-R14"
    snapshot: dict[str, bytes] = {}
    for relative in continuity.sequence_recovery_paths(OLD_SESSION, NEW_SESSION, recovery_id):
        path = root / relative
        if path.is_file():
            snapshot[relative] = path.read_bytes()
        elif path.is_dir():
            for item in path.rglob("*"):
                if item.is_file():
                    snapshot[item.relative_to(root).as_posix()] = item.read_bytes()
    return snapshot


def invoke_live(*, fault_after: str | None = None, **overrides: str):
    values = {
        "actor": "codex-r16-client-admin-20260728",
        "old_session_id": OLD_SESSION,
        "new_session_id": NEW_SESSION,
        "target_release": "R14",
        "target_task": "TASK-R14-004",
        "target_story": "STORY-R14-004",
        "cr_id": "CR-0458",
        "expected_head": HEAD,
        "stash_ref": "stash@{0}",
        "expected_stash_oid": STASH_OID,
        "expected_stash_subject": STASH_SUBJECT,
        "reason": REASON,
    }
    values.update(overrides)
    return continuity.perform_sequence_recovery(
        ROOT,
        load_policy(ROOT),
        fault_after=fault_after,
        **values,
    )


class RecoveryFileTransactionTest(unittest.TestCase):
    def test_restores_changed_bytes_and_removes_new_files_and_directories(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-sequence-recovery-transaction-") as directory:
            root = Path(directory)
            existing = root / "state/current.yaml"
            existing.parent.mkdir(parents=True)
            existing.write_bytes(b"before\r\n")
            with self.assertRaisesRegex(RuntimeError, "fault"):
                with continuity.RecoveryFileTransaction(root, ["state", "new/tree"]):
                    existing.write_bytes(b"after\n")
                    created = root / "new/tree/session.yaml"
                    created.parent.mkdir(parents=True)
                    created.write_bytes(b"new\n")
                    raise RuntimeError("fault")
            self.assertEqual(b"before\r\n", existing.read_bytes())
            self.assertFalse((root / "new").exists())

    def test_recovery_implementation_contains_no_destructive_git_commands(self) -> None:
        source = inspect.getsource(continuity.perform_sequence_recovery)
        for forbidden in ('"checkout"', '"reset"', '"rebase"', '"pop"', '"drop"'):
            self.assertNotIn(forbidden, source)


@unittest.skipUnless(
    os.environ.get("HHY_SEQUENCE_RECOVERY_LIVE_TEST") == "1",
    "只在CR-0458实际恢复前运行仓库级故障注入",
)
class LiveSequenceRecoveryRollbackTest(unittest.TestCase):
    def assert_zero_write_failure(self, **overrides: str) -> None:
        before = managed_snapshot(ROOT)
        head_before = git_info(ROOT)["head"]
        stash_before = continuity.sequence_recovery_stash_facts(ROOT, "stash@{0}")
        with self.assertRaises(ContinuityError):
            invoke_live(**overrides)
        self.assertEqual(before, managed_snapshot(ROOT))
        self.assertEqual(head_before, git_info(ROOT)["head"])
        self.assertEqual(stash_before, continuity.sequence_recovery_stash_facts(ROOT, "stash@{0}"))

    def test_precondition_failures_are_zero_write(self) -> None:
        self.assert_zero_write_failure(target_release="R15")
        self.assert_zero_write_failure(expected_head="f" * 40)
        self.assert_zero_write_failure(expected_stash_oid="e" * 40)
        self.assert_zero_write_failure(expected_stash_subject="wrong subject")

    def test_every_mutation_stage_rolls_back_byte_for_byte(self) -> None:
        stages = (
            "old_session_terminated",
            "task_plans_rewritten",
            "current_next_rewritten",
            "compensation_transitions_event",
            "pointer_state_cleared",
            "new_session_record",
            "new_session_log",
            "new_session_claim",
            "new_session_pointer_state",
            "new_session_index",
            "new_session_current_status",
            "new_session_event_transition",
            "new_session_context",
            "checkpoint_written",
            "completion_event_written",
            "recovery_record_written",
            "final_context_built",
        )
        for stage in stages:
            with self.subTest(stage=stage):
                before = managed_snapshot(ROOT)
                head_before = git_info(ROOT)["head"]
                stash_before = continuity.sequence_recovery_stash_facts(ROOT, "stash@{0}")
                with self.assertRaisesRegex(
                    ContinuityError,
                    f"SEQUENCE_RECOVERY_FAULT_INJECTED:{stage}",
                ):
                    invoke_live(fault_after=stage)
                self.assertEqual(before, managed_snapshot(ROOT), stage)
                self.assertEqual(head_before, git_info(ROOT)["head"], stage)
                self.assertEqual(
                    stash_before,
                    continuity.sequence_recovery_stash_facts(ROOT, "stash@{0}"),
                    stage,
                )


class CompletedSequenceRecoveryInvariantTest(unittest.TestCase):
    def test_sequence_recovered_is_terminal_for_gate_and_never_current_again(self) -> None:
        old = continuity.load_session(ROOT, OLD_SESSION)
        self.assertEqual("SEQUENCE_RECOVERED", old.get("status"))
        report = continuity_gate.Report("test")
        continuity_gate.validate_session_structure(report, old, load_policy(ROOT))
        self.assertNotIn("SESSION_STATUS", {row["code"] for row in report.errors})

        recovered = continuity.load_session(ROOT, NEW_SESSION)
        self.assertEqual(NEW_SESSION, recovered.get("session_id"))
        self.assertEqual(OLD_SESSION, recovered.get("takeover_of"))
        self.assertIn(recovered.get("status"), {"ACTIVE", "HANDED_OFF", "CLOSED"})

        active = continuity.current_session(ROOT)
        self.assertNotEqual(OLD_SESSION, (active or {}).get("session_id"))
        claims = yaml.safe_load((ROOT / ".continuity/TASK_CLAIMS.yaml").read_text(encoding="utf-8")) or {}
        active_claims = [row for row in claims.get("claims", []) if row.get("status") == "ACTIVE"]
        expected_active_sessions = [] if active is None else [active.get("session_id")]
        self.assertEqual(expected_active_sessions, [row.get("session_id") for row in active_claims])
        self.assertNotIn(OLD_SESSION, expected_active_sessions)

    def test_successful_recovery_record_is_fully_consistent_when_present(self) -> None:
        recovery_id = f"SEQREC-{OLD_SESSION}-R14"
        record_path = ROOT / ".continuity/sequence_recoveries" / f"{recovery_id}.yaml"
        if not record_path.is_file():
            self.skipTest("CR-0458实际恢复尚未执行")
        record = yaml.safe_load(record_path.read_text(encoding="utf-8")) or {}
        self.assertEqual("PASS", record.get("status"))
        self.assertEqual(HEAD, record.get("git", {}).get("head_before"))
        self.assertEqual(HEAD, record.get("git", {}).get("head_after"))
        self.assertEqual(STASH_OID, record.get("git", {}).get("stash_before", {}).get("oid"))
        self.assertEqual(
            record.get("git", {}).get("stash_before"),
            record.get("git", {}).get("stash_after"),
        )
        recovered = continuity.load_session(ROOT, NEW_SESSION)
        self.assertEqual(record.get("to", {}).get("session"), recovered.get("session_id"))
        self.assertEqual(OLD_SESSION, recovered.get("takeover_of"))
        self.assertEqual("R14", recovered.get("release"))
        self.assertEqual("TASK-R14-004", recovered.get("task_id"))
        self.assertEqual("STORY-R14-004", recovered.get("story_id"))
        self.assertIn("CR-0458", recovered.get("change_requests") or [])


if __name__ == "__main__":
    unittest.main()
