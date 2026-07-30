#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from unittest import mock
import importlib.util
import os
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "check_v123_continuity_under_test", ROOT / "scripts/check_v123_continuity.py"
)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ContinuityGitResolutionTest(unittest.TestCase):
    def test_explicit_git_override_wins_without_path(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-git-resolution-") as directory:
            executable = Path(directory) / "git.exe"
            executable.write_bytes(b"fixture")
            with mock.patch.dict(os.environ, {"HHY_GIT_BIN": str(executable)}, clear=False), mock.patch.object(MODULE.shutil, "which", return_value=None):
                self.assertEqual(str(executable), MODULE.git_executable())

    def test_missing_explicit_override_fails_instead_of_silent_fallback(self) -> None:
        missing = ROOT / "does-not-exist" / "git.exe"
        with mock.patch.dict(os.environ, {"HHY_GIT_BIN": str(missing)}, clear=False):
            with self.assertRaisesRegex(OSError, "HHY_GIT_BIN不存在"):
                MODULE.git_executable()


if __name__ == "__main__":
    unittest.main()

