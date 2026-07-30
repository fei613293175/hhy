from __future__ import annotations

import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import continuity_gate as gate  # noqa: E402


class HistoricalProjectFingerprintTest(unittest.TestCase):
    def test_rename_keeps_deleted_and_added_paths_in_historical_fingerprint(self) -> None:
        diff_result = SimpleNamespace(
            returncode=0,
            stdout="apps/android/old-manifest.yaml\napps/android/new-manifest.yaml\n",
            stderr="",
        )

        def blob_at(_commit: str, relative: str):
            if relative.endswith("old-manifest.yaml"):
                return "DELETED", None
            return "FILE", b"manifest_schema: 2\n"

        with (
            mock.patch.object(gate, "run_command", return_value=diff_result) as run_command,
            mock.patch.object(gate, "git_blob_at", side_effect=blob_at),
        ):
            result = gate.project_fingerprint_at_commit(
                {"git": {"base_commit": "base-commit"}},
                "delivery-commit",
            )

        diff_command = run_command.call_args.args[0]
        self.assertIn("--no-renames", diff_command)
        self.assertEqual(
            ["apps/android/new-manifest.yaml", "apps/android/old-manifest.yaml"],
            result["files"],
        )
        states = {entry["path"]: entry["state"] for entry in result["payload"]["files"]}
        self.assertEqual("DELETED", states["apps/android/old-manifest.yaml"])
        self.assertEqual("FILE", states["apps/android/new-manifest.yaml"])


if __name__ == "__main__":
    unittest.main()
