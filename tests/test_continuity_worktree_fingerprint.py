from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import continuity_gate as gate  # noqa: E402
import continuity_lib as lib  # noqa: E402


class WorktreeProjectFingerprintTest(unittest.TestCase):
    def make_repo(self) -> tuple[tempfile.TemporaryDirectory[str], Path, str]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        lib.git(root, "init", check=True)
        lib.git(root, "config", "user.name", "Continuity Test", check=True)
        lib.git(root, "config", "user.email", "continuity-test@orbexa.cc", check=True)
        (root / "tracked.txt").write_text("base\n", encoding="utf-8")
        (root / "deleted.txt").write_text("delete-me\n", encoding="utf-8")
        lib.git(root, "add", "-A", check=True)
        lib.git(root, "commit", "-m", "baseline", check=True)
        return temporary, root, lib.git(root, "rev-parse", "HEAD")

    def test_net_revert_matches_final_commit_fingerprint(self) -> None:
        temporary, root, base = self.make_repo()
        self.addCleanup(temporary.cleanup)

        (root / "tracked.txt").write_text("intermediate\n", encoding="utf-8")
        lib.git(root, "add", "-A", check=True)
        lib.git(root, "commit", "-m", "intermediate", check=True)

        (root / "tracked.txt").write_text("base\n", encoding="utf-8")
        (root / "deleted.txt").unlink()
        (root / "new.txt").write_text("new\n", encoding="utf-8")
        session = {"git": {"base_commit": base}}

        worktree = lib.project_fingerprint(root, session)
        self.assertEqual(["deleted.txt", "new.txt"], worktree["files"])

        lib.git(root, "add", "-A", check=True)
        self.assertEqual([], lib.git_index_worktree_divergence(root))
        lib.git(root, "commit", "-m", "final", check=True)
        final_commit = lib.git(root, "rev-parse", "HEAD")
        with mock.patch.object(gate, "ROOT", root):
            historical = gate.project_fingerprint_at_commit(session, final_commit)

        self.assertEqual(worktree["sha256"], historical["sha256"])
        self.assertEqual(worktree["files"], historical["files"])

    def test_aligned_staged_add_modify_and_delete_are_preserved(self) -> None:
        temporary, root, base = self.make_repo()
        self.addCleanup(temporary.cleanup)

        (root / "tracked.txt").write_text("modified\n", encoding="utf-8")
        (root / "deleted.txt").unlink()
        (root / "added.txt").write_text("added\n", encoding="utf-8")
        lib.git(root, "add", "-A", check=True)

        self.assertEqual([], lib.git_index_worktree_divergence(root))
        self.assertEqual(
            ["added.txt", "deleted.txt", "tracked.txt"],
            lib.changed_since(root, base),
        )

    def test_index_worktree_divergence_and_untracked_are_reported(self) -> None:
        temporary, root, _base = self.make_repo()
        self.addCleanup(temporary.cleanup)

        (root / "tracked.txt").write_text("staged\n", encoding="utf-8")
        lib.git(root, "add", "tracked.txt", check=True)
        (root / "tracked.txt").write_text("unstaged\n", encoding="utf-8")
        (root / "untracked.txt").write_text("untracked\n", encoding="utf-8")

        self.assertEqual(
            ["tracked.txt", "untracked.txt"],
            lib.git_index_worktree_divergence(root),
        )


if __name__ == "__main__":
    unittest.main()
