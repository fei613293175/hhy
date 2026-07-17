#!/usr/bin/env python3
"""Regression test for claiming P00 before the first Git bootstrap."""
from __future__ import annotations

from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
ZERO_HASH = "0" * 64


def copy_fixture(target: Path) -> None:
    ignored = {
        ".git", "__pycache__", "node_modules", "dist", "target", "build", ".gradle"
    }

    def ignore(_directory: str, names: list[str]) -> set[str]:
        return {name for name in names if name in ignored or name.endswith((".pyc", ".pyo"))}

    shutil.copytree(ROOT, target, ignore=ignore, copy_function=shutil.copy2)
    runtime = target / ".continuity/runtime"
    shutil.rmtree(runtime, ignore_errors=True)
    runtime.mkdir(parents=True)
    (runtime / ".gitkeep").touch()

    for directory in [target / ".continuity/sessions", target / ".continuity/checkpoints"]:
        shutil.rmtree(directory, ignore_errors=True)
        directory.mkdir(parents=True)

    (target / ".continuity/EVENT_LOG.jsonl").write_text("", encoding="utf-8")
    records = {
        ".continuity/ACTIVE_SESSION.yaml": {
            "protocol_version": "1.0", "active_session_id": None, "status": "NONE"
        },
        ".continuity/SESSION_INDEX.yaml": {"version": "1.0", "sessions": []},
        ".continuity/TASK_CLAIMS.yaml": {"version": "1.0", "claims": []},
        ".continuity/TASK_TRANSITIONS.yaml": {"version": "1.0", "transitions": []},
        ".continuity/STATE.yaml": {
            "protocol_version": "1.0",
            "package_version": "1.2.3",
            "mode": "ENFORCED",
            "active_session_id": None,
            "event_log": {"sequence": 0, "head_hash": ZERO_HASH},
        },
    }
    for relative, payload in records.items():
        (target / relative).write_text(
            yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8"
        )

    status_path = target / "CURRENT_STATUS.yaml"
    status = yaml.safe_load(status_path.read_text(encoding="utf-8")) or {}
    status.update({
        "phase": "P00",
        "active_release": "P00",
        "active_task": None,
        "status": "READY",
        "in_progress_tasks": [],
        "next_task": "TASK-P00-001",
        "completed_tasks": [
            item for item in status.get("completed_tasks", [])
            if not str(item).startswith("TASK-P00-")
        ],
    })
    status_path.write_text(
        yaml.safe_dump(status, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )

    tasks_path = target / "releases/P00/TASKS.yaml"
    task_document = yaml.safe_load(tasks_path.read_text(encoding="utf-8")) or {}
    tasks = task_document.get("tasks") or []
    for task in tasks:
        task["status"] = "READY" if task.get("id") == "TASK-P00-001" else "BLOCKED"
        task.pop("completed_at", None)
    tasks_path.write_text(
        yaml.safe_dump(task_document, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )

    first_task = next(task for task in tasks if task.get("id") == "TASK-P00-001")
    next_path = target / "NEXT_TASK.yaml"
    next_task = yaml.safe_load(next_path.read_text(encoding="utf-8")) or {}
    next_task.update({
        "id": first_task["id"],
        "title": first_task["title"],
        "status": "READY",
        "release": "P00",
        "requirements": first_task.get("requirements", []),
        "depends_on": first_task.get("depends_on", []),
        "steps": first_task.get("deliverables", []),
        "acceptance": first_task.get("acceptance", []),
        "start_command": "python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-P00-001",
    })
    next_path.write_text(
        yaml.safe_dump(next_task, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )


class BootstrapRecoveryTest(unittest.TestCase):
    def run_cli(self, repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
        env = dict(os.environ)
        env.update({"PYTHONDONTWRITEBYTECODE": "1", "GIT_TERMINAL_PROMPT": "0"})
        result = subprocess.run(
            [sys.executable, "scripts/continuity.py", *args],
            cwd=repo,
            env=env,
            text=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=180,
        )
        self.assertEqual(result.returncode, 0, result.stdout + "\n" + result.stderr)
        return result

    def test_bootstrap_reconciles_pre_git_session(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-bootstrap-recovery-") as temp:
            repo = Path(temp) / "repository"
            copy_fixture(repo)

            subprocess.run(["git", "init"], cwd=repo, check=True, stdout=subprocess.PIPE)

            resume = yaml.safe_load(self.run_cli(repo, "resume").stdout)
            self.assertEqual(resume["status"], "GIT_BOOTSTRAP_REQUIRED")
            self.assertIn("continuity.py bootstrap", resume["resume_command"])

            missing_commit = subprocess.run(
                [sys.executable, "scripts/continuity.py", "bootstrap", "--actor", "bootstrap-test",
                 "--task", "TASK-P00-001", "--branch", "task/TASK-P00-001"],
                cwd=repo, env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}, text=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            self.assertEqual(missing_commit.returncode, 2)
            self.assertIn("--initial-commit", missing_commit.stderr)

            self.run_cli(
                repo, "start", "--actor", "bootstrap-test", "--task", "TASK-P00-001",
                "--story", "STORY-P00-001", "--goal", "验证Git冷启动会话回填",
            )
            session_id = yaml.safe_load(
                (repo / ".continuity/ACTIVE_SESSION.yaml").read_text(encoding="utf-8")
            )["active_session_id"]
            before = yaml.safe_load(
                (repo / f".continuity/sessions/{session_id}.yaml").read_text(encoding="utf-8")
            )
            self.assertIn(before["git"]["base_commit"], {"NOT_INITIALIZED", "UNBORN"})
            before["checkpoint_sequence"] = 1
            (repo / f".continuity/sessions/{session_id}.yaml").write_text(
                yaml.safe_dump(before, allow_unicode=True, sort_keys=False), encoding="utf-8"
            )
            session_log = repo / before["session_log"]
            with session_log.open("a", encoding="utf-8") as handle:
                handle.write("\n### LEGACY-CHECKPOINT-MARKER\n")

            bootstrap = yaml.safe_load(self.run_cli(
                repo, "bootstrap", "--actor", "bootstrap-test", "--init-git",
                "--initial-commit", "--task", "TASK-P00-001",
                "--branch", "task/TASK-P00-001",
            ).stdout)
            self.assertEqual(bootstrap["status"], "BOOTSTRAP_COMPLETED_SESSION_RECONCILED")
            self.assertIn("continuity.py checkpoint", bootstrap["next_command"])
            head = subprocess.run(
                ["git", "rev-parse", "HEAD"], cwd=repo, text=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True,
            ).stdout.strip()
            after = yaml.safe_load(
                (repo / f".continuity/sessions/{session_id}.yaml").read_text(encoding="utf-8")
            )
            self.assertTrue(after["git"]["initialized"])
            self.assertEqual(after["git"]["base_commit"], head)
            self.assertEqual(after["git"]["start_head"], head)
            self.assertIn("bootstrap-reconciled", after["scope"]["source"])
            self.assertIn("LEGACY-CHECKPOINT-MARKER", session_log.read_text(encoding="utf-8"))

            ignored_paths = [
                repo / "scripts/__pycache__/probe.pyc",
                repo / "apps/admin-web/node_modules/probe.txt",
                repo / "services/backend/boot/target/probe.txt",
                repo / "apps/android/.gradle/probe.txt",
                repo / "apps/android/app/build/intermediates/probe.txt",
            ]
            for ignored_path in ignored_paths:
                ignored_path.parent.mkdir(parents=True, exist_ok=True)
                ignored_path.write_bytes(b"ignored")
            status = subprocess.run(
                ["git", "status", "--porcelain=v1", "--untracked-files=all"],
                cwd=repo, text=True, stdout=subprocess.PIPE, check=True,
            ).stdout
            for token in ["__pycache__", "node_modules", "target", ".gradle", "build"]:
                self.assertNotIn(token, status)

            probe = repo / "scripts/smoke/bootstrap_recovery_probe.txt"
            probe.parent.mkdir(parents=True, exist_ok=True)
            probe.write_text("bootstrap-recovered\n", encoding="utf-8")
            checkpoint = yaml.safe_load(self.run_cli(
                repo, "checkpoint", "--summary", "验证bootstrap会话回填",
                "--next-step", "提交回归测试", "--test",
                "bootstrap-recovery|PASS|tests/test_continuity_bootstrap_recovery.py|隔离仓库回归通过",
            ).stdout)
            self.assertEqual(checkpoint["changed_files"], 1)
            machine_checkpoint = yaml.safe_load(
                (repo / f".continuity/checkpoints/{session_id}/0002.yaml").read_text(encoding="utf-8")
            )
            self.assertEqual(
                machine_checkpoint["project_fingerprint"]["files"],
                ["scripts/smoke/bootstrap_recovery_probe.txt"],
            )

    def test_wrong_actor_is_rejected_before_git_initialization(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-bootstrap-actor-") as temp:
            repo = Path(temp) / "repository"
            copy_fixture(repo)
            self.run_cli(
                repo, "start", "--actor", "session-owner", "--task", "TASK-P00-001",
                "--story", "STORY-P00-001", "--goal", "验证bootstrap Actor隔离",
            )
            state_before = (repo / ".continuity/STATE.yaml").read_bytes()
            events_before = (repo / ".continuity/EVENT_LOG.jsonl").read_bytes()
            result = subprocess.run(
                [sys.executable, "scripts/continuity.py", "bootstrap", "--actor", "wrong-actor",
                 "--init-git", "--initial-commit", "--task", "TASK-P00-001"],
                cwd=repo, env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}, text=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            self.assertEqual(result.returncode, 2)
            self.assertIn("其他Actor", result.stderr)
            self.assertFalse((repo / ".git").exists())
            self.assertEqual((repo / ".continuity/STATE.yaml").read_bytes(), state_before)
            self.assertEqual((repo / ".continuity/EVENT_LOG.jsonl").read_bytes(), events_before)


if __name__ == "__main__":
    unittest.main()
