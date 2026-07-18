from __future__ import annotations

from pathlib import Path
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/restore_git_transport.py"
SPEC = importlib.util.spec_from_file_location("restore_git_transport", SCRIPT)
assert SPEC and SPEC.loader
transport = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(transport)


def locate_git() -> str:
    override = os.environ.get("HHY_GIT_BIN")
    if override and Path(override).is_file():
        return override
    discovered = shutil.which("git")
    if discovered:
        return discovered
    bundled = Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe"
    if bundled.is_file():
        return str(bundled)
    raise unittest.SkipTest("Git executable is unavailable")


GIT = locate_git()


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        [GIT, *args],
        cwd=str(root),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and result.returncode != 0:
        raise AssertionError(f"git {' '.join(args)} failed: {result.stdout}\n{result.stderr}")
    return result.stdout.strip()


class GitTransportRecoveryTest(unittest.TestCase):
    branch = "task/TASK-TRANSPORT-TEST"

    def setUp(self) -> None:
        self.previous_git_bin = os.environ.get("HHY_GIT_BIN")
        os.environ["HHY_GIT_BIN"] = GIT
        self.tempdir = tempfile.TemporaryDirectory(prefix="hhy-git-transport-test-")
        self.base = Path(self.tempdir.name)
        self.remote = self.base / "remote.git"
        self.seed = self.base / "seed"
        self.work = self.base / "work"
        self.descriptor_path = self.base / "REPOSITORY_TRANSPORT.yaml"

        self.remote.mkdir()
        git(self.remote, "init", "--bare", "--quiet")
        self.seed.mkdir()
        git(self.seed, "init", "--quiet", "-b", self.branch)
        self.configure_identity(self.seed)
        (self.seed / "source.txt").write_text("baseline\n", encoding="utf-8")
        git(self.seed, "add", "source.txt")
        git(self.seed, "commit", "--quiet", "-m", "baseline")
        git(self.seed, "remote", "add", "origin", self.remote.as_uri())
        git(self.seed, "push", "--quiet", "-u", "origin", self.branch)
        git(self.base, "clone", "--quiet", "--branch", self.branch, self.remote.as_uri(), str(self.work))
        self.configure_identity(self.work)

        self.descriptor_path.write_text(
            yaml.safe_dump(
                {
                    "schema_version": 1,
                    "repository": {
                        "name": "transport-test",
                        "canonical_url": self.remote.as_uri(),
                        "remote_name": "origin",
                        "default_branch": "main",
                    },
                    "security": {
                        "prohibit_url_credentials": True,
                        "prohibit_force_push": True,
                    },
                    "branch_policy": {
                        "allowed_patterns": ["task/*", "main"],
                        "upstream_remote": "origin",
                        "upstream_merge_template": "refs/heads/{branch}",
                    },
                    "recovery": {
                        "bundle_filename": "repository.bundle",
                        "require_bundle_verification": True,
                        "raw_source_without_git_or_bundle": "BLOCK",
                    },
                    "push_preflight": {
                        "fetch_before_compare": True,
                        "require_clean_worktree": True,
                        "require_exact_remote_url": True,
                        "require_exact_upstream": True,
                        "reject_behind": True,
                        "reject_diverged": True,
                        "allow_new_remote_branch": True,
                    },
                },
                sort_keys=False,
            ),
            encoding="utf-8",
        )
        self.descriptor = transport.load_descriptor(self.descriptor_path)

    def tearDown(self) -> None:
        if self.previous_git_bin is None:
            os.environ.pop("HHY_GIT_BIN", None)
        else:
            os.environ["HHY_GIT_BIN"] = self.previous_git_bin
        self.tempdir.cleanup()

    @staticmethod
    def configure_identity(repo: Path) -> None:
        git(repo, "config", "user.name", "Git Transport Test")
        git(repo, "config", "user.email", "git-transport@example.invalid")

    def test_existing_clone_restores_origin_and_upstream(self) -> None:
        git(self.work, "remote", "remove", "origin")
        before = transport.transport_status(self.work, self.descriptor)
        self.assertEqual("BLOCKED", before["status"])

        restored = transport.configure_existing_repository(self.work, self.descriptor)

        self.assertEqual("READY", restored["status"])
        self.assertEqual(self.remote.as_uri(), git(self.work, "config", "--get", "remote.origin.url"))
        self.assertEqual("origin", git(self.work, "config", "--get", f"branch.{self.branch}.remote"))
        self.assertEqual(
            f"refs/heads/{self.branch}",
            git(self.work, "config", "--get", f"branch.{self.branch}.merge"),
        )

    def test_mismatched_existing_remote_requires_explicit_repair(self) -> None:
        git(self.work, "remote", "set-url", "origin", "https://example.invalid/wrong.git")
        with self.assertRaisesRegex(transport.TransportError, "allow-remote-repair"):
            transport.configure_existing_repository(self.work, self.descriptor, fetch=False)

        restored = transport.configure_existing_repository(
            self.work,
            self.descriptor,
            allow_remote_repair=True,
        )
        self.assertEqual("READY", restored["status"])

    def test_verified_bundle_restores_history_remote_and_upstream(self) -> None:
        bundle = self.base / "repository.bundle"
        git(self.work, "bundle", "create", str(bundle), "--all")
        target = self.base / "restored"

        restored = transport.restore_from_bundle(
            bundle,
            target,
            self.descriptor,
            branch=self.branch,
        )

        self.assertEqual("RESTORED_VERIFIED_BUNDLE", restored["operation"])
        self.assertEqual(git(self.work, "rev-parse", "HEAD"), git(target, "rev-parse", "HEAD"))
        self.assertEqual(self.remote.as_uri(), git(target, "config", "--get", "remote.origin.url"))
        self.assertEqual("origin", git(target, "config", "--get", f"branch.{self.branch}.remote"))

    def test_raw_source_without_git_or_bundle_never_fabricates_history(self) -> None:
        raw = self.base / "raw-source"
        raw.mkdir()
        (raw / "source.txt").write_text("no history\n", encoding="utf-8")

        with self.assertRaisesRegex(transport.TransportError, "refusing to initialize or fabricate"):
            transport.configure_existing_repository(raw, self.descriptor)

        self.assertFalse((raw / ".git").exists())
        status = transport.transport_status(raw, self.descriptor)
        self.assertEqual("RAW_SOURCE_NO_GIT_HISTORY", status["mode"])

    def test_push_preflight_rejects_force_wrong_remote_upstream_and_credentials(self) -> None:
        passed = transport.push_preflight(self.work, self.descriptor)
        self.assertEqual("PASS", passed["status"])

        with self.assertRaisesRegex(transport.TransportError, "force push"):
            transport.push_preflight(self.work, self.descriptor, push_arguments=["--force-with-lease"])
        with self.assertRaisesRegex(transport.TransportError, "wrong push remote"):
            transport.push_preflight(self.work, self.descriptor, remote_name="backup")
        with self.assertRaisesRegex(transport.TransportError, "actual push URL differs"):
            transport.push_preflight(self.work, self.descriptor, push_url="https://example.invalid/other.git")

        git(self.work, "config", f"branch.{self.branch}.remote", "backup")
        with self.assertRaisesRegex(transport.TransportError, "wrong upstream"):
            transport.push_preflight(self.work, self.descriptor, fetch=False)
        git(self.work, "config", f"branch.{self.branch}.remote", "origin")

        git(self.work, "remote", "set-url", "origin", "https://token@example.invalid/hhy.git")
        with self.assertRaisesRegex(transport.TransportError, "credentials"):
            transport.push_preflight(self.work, self.descriptor, fetch=False)

    def test_push_preflight_rejects_behind_and_diverged_history(self) -> None:
        other = self.base / "other"
        git(self.base, "clone", "--quiet", "--branch", self.branch, self.remote.as_uri(), str(other))
        self.configure_identity(other)
        (other / "remote.txt").write_text("remote\n", encoding="utf-8")
        git(other, "add", "remote.txt")
        git(other, "commit", "--quiet", "-m", "remote advance")
        git(other, "push", "--quiet", "origin", self.branch)

        with self.assertRaisesRegex(transport.TransportError, "branch is behind"):
            transport.push_preflight(self.work, self.descriptor)

        (self.work / "local.txt").write_text("local\n", encoding="utf-8")
        git(self.work, "add", "local.txt")
        git(self.work, "commit", "--quiet", "-m", "local advance")
        with self.assertRaisesRegex(transport.TransportError, "branch diverged"):
            transport.push_preflight(self.work, self.descriptor)

    def test_cli_status_is_machine_readable_and_does_not_contact_github(self) -> None:
        result = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--config",
                str(self.descriptor_path),
                "--json",
                "status",
                "--root",
                str(self.work),
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            env={**os.environ, "HHY_GIT_BIN": GIT},
        )
        self.assertEqual(0, result.returncode, result.stderr or result.stdout)
        self.assertEqual("READY", json.loads(result.stdout)["status"])

    def test_pre_push_hook_binds_actual_git_remote_and_url(self) -> None:
        hook = (ROOT / ".githooks/pre-push").read_text(encoding="utf-8")
        self.assertIn('--push-remote "${1:-}"', hook)
        self.assertIn('--push-url "${2:-}"', hook)


if __name__ == "__main__":
    unittest.main()
