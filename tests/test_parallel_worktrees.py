from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import json
import os
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/prepare_parallel_worktrees.ps1"


def find_powershell() -> str | None:
    return shutil.which("pwsh") or shutil.which("powershell")


def find_git() -> str | None:
    configured = os.environ.get("HHY_GIT")
    return configured if configured and Path(configured).is_file() else shutil.which("git")


class TestParallelWorktreePreparation(TestCase):
    def setUp(self) -> None:
        self.powershell = find_powershell()
        self.git = find_git()
        if not self.powershell or not self.git:
            self.skipTest("PowerShell and Git are required")

    def run_script(self, repository: Path, root: Path, worker: str, claim: str, *, execute: bool = False):
        command = [
            self.powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(SCRIPT),
            "-RepositoryRoot", str(repository), "-WorktreeRoot", str(root),
            "-WorkerId", worker, "-AllowedPath", claim, "-GitExecutable", str(self.git),
        ]
        if execute:
            command.append("-Execute")
        return subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def make_repository(self, root: Path) -> Path:
        repository = root / "repo"
        repository.mkdir()
        subprocess.run([self.git, "init", str(repository)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run([self.git, "-C", str(repository), "config", "user.name", "test"], check=True)
        subprocess.run([self.git, "-C", str(repository), "config", "user.email", "test@example.invalid"], check=True)
        (repository / "README.md").write_text("baseline\n", encoding="utf-8")
        policy = repository / ".continuity/CONTINUITY_POLICY.yaml"
        policy.parent.mkdir()
        policy.write_text(
            "parallel_development:\n  authoritative_active_sessions: 1\n  max_delegated_workers: 3\n",
            encoding="utf-8",
        )
        subprocess.run([self.git, "-C", str(repository), "add", "README.md"], check=True)
        subprocess.run([self.git, "-C", str(repository), "commit", "-m", "baseline"], check=True, stdout=subprocess.PIPE)
        return repository

    def test_dry_run_does_not_create_worktree_or_registry(self) -> None:
        with TemporaryDirectory() as temporary:
            base = Path(temporary)
            repository = self.make_repository(base)
            worktrees = base / "workers"
            result = self.run_script(repository, worktrees, "android-1", "apps/android/**")
            self.assertEqual(0, result.returncode, result.stderr)
            payload = json.loads(result.stdout)
            self.assertEqual("PLANNED", payload["status"])
            self.assertFalse((worktrees / "android-1").exists())
            self.assertFalse((worktrees / ".assignments").exists())

    def test_forbidden_coordinator_path_is_rejected(self) -> None:
        with TemporaryDirectory() as temporary:
            base = Path(temporary)
            repository = self.make_repository(base)
            result = self.run_script(repository, base / "workers", "unsafe-1", ".continuity/**")
            self.assertNotEqual(0, result.returncode)
            self.assertIn("coordinator-owned", result.stderr)

    def test_execute_creates_detached_worktree_and_rejects_overlap(self) -> None:
        with TemporaryDirectory() as temporary:
            base = Path(temporary)
            repository = self.make_repository(base)
            worktrees = base / "workers"
            first = self.run_script(repository, worktrees, "backend-1", "services/backend/**", execute=True)
            self.assertEqual(0, first.returncode, first.stderr)
            payload = json.loads(first.stdout)
            self.assertEqual("ACTIVE", payload["status"])
            self.assertTrue((worktrees / "backend-1" / ".git").is_file())
            self.assertTrue((repository / ".git/hhy-parallel-assignments/backend-1.json").is_file())
            blocked_commit = subprocess.run(
                [self.git, "-C", str(worktrees / "backend-1"), "commit", "--allow-empty", "-m", "blocked"],
                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            self.assertNotEqual(0, blocked_commit.returncode)
            second = self.run_script(repository, worktrees, "backend-2", "services/backend/access/**")
            self.assertNotEqual(0, second.returncode)
            self.assertIn("overlaps active worker", second.stderr)

    def test_three_active_workers_succeed_and_fourth_is_rejected(self) -> None:
        with TemporaryDirectory() as temporary:
            base = Path(temporary)
            repository = self.make_repository(base)
            worktrees = base / "workers"
            for index, claim in enumerate(["apps/android/**", "services/backend/**", "tests/**"], start=1):
                result = self.run_script(repository, worktrees, f"worker-{index}", claim, execute=True)
                self.assertEqual(0, result.returncode, result.stderr)
            fourth = self.run_script(repository, base / "other-workers", "worker-4", "packages/**", execute=True)
            self.assertNotEqual(0, fourth.returncode)
            self.assertIn("capacity reached", fourth.stderr)

    def test_duplicate_worker_id_is_rejected(self) -> None:
        with TemporaryDirectory() as temporary:
            base = Path(temporary)
            repository = self.make_repository(base)
            first = self.run_script(repository, base / "workers", "same-worker", "apps/android/**", execute=True)
            self.assertEqual(0, first.returncode, first.stderr)
            duplicate = self.run_script(repository, base / "other-workers", "same-worker", "services/backend/**")
            self.assertNotEqual(0, duplicate.returncode)
            self.assertIn("already has an active assignment", duplicate.stderr)
