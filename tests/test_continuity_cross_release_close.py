#!/usr/bin/env python3
"""Isolated regression coverage for a close-only cross-release handoff."""
from __future__ import annotations

from hashlib import sha256
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
ACTOR = "cross-release-close-test"


def dump_yaml(path: Path, payload: dict) -> None:
    path.write_text(
        yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )


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
        dump_yaml(target / relative, payload)

    p00_path = target / "releases/P00/TASKS.yaml"
    p00 = yaml.safe_load(p00_path.read_text(encoding="utf-8")) or {}
    for task in p00.get("tasks", []):
        if task.get("id") == "TASK-P00-008":
            task["status"] = "READY"
            task.pop("completed_at", None)
        else:
            task["status"] = "DONE"
            task["completed_at"] = "2026-07-16T00:00:00Z"
    dump_yaml(p00_path, p00)

    p00_last = p00["tasks"][-1]
    next_path = target / "NEXT_TASK.yaml"
    next_task = yaml.safe_load(next_path.read_text(encoding="utf-8")) or {}
    next_task.update({
        "id": p00_last["id"],
        "title": p00_last["title"],
        "status": "READY",
        "release": "P00",
        "definition_of_ready": "releases/P00/DEFINITION_OF_READY.yaml",
        "stories": "releases/P00/STORIES.yaml",
        "requirements": p00_last.get("requirements", []),
        "steps": p00_last.get("deliverables", []),
        "acceptance": p00_last.get("acceptance", []),
        "claim_required": True,
        "start_command": (
            "python3 scripts/continuity.py start --actor <ACTOR_ID> "
            "--task TASK-P00-008"
        ),
    })
    dump_yaml(next_path, next_task)

    status_path = target / "CURRENT_STATUS.yaml"
    status = yaml.safe_load(status_path.read_text(encoding="utf-8")) or {}
    status.update({
        "phase": "P00",
        "active_release": "P00",
        "status": "READY",
        "active_task": None,
        "next_task": "TASK-P00-008",
        "completed_tasks": [f"TASK-P00-{number:03d}" for number in range(1, 8)],
        "in_progress_tasks": [],
    })
    dump_yaml(status_path, status)


def repository_snapshot(root: Path) -> dict[str, str]:
    excluded_dirs = {".git", "runtime", "__pycache__", "node_modules", "dist", "target", ".gradle"}
    result: dict[str, str] = {}
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in excluded_dirs for part in relative.parts):
            continue
        if path.suffix in {".pyc", ".pyo"}:
            continue
        result[relative.as_posix()] = sha256(path.read_bytes()).hexdigest()
    return result


class CrossReleaseCloseTest(unittest.TestCase):
    def cli(
        self, repo: Path, *args: str, expected: int = 0
    ) -> subprocess.CompletedProcess[str]:
        env = dict(os.environ)
        env.update({
            "PYTHONDONTWRITEBYTECODE": "1",
            "GIT_TERMINAL_PROMPT": "0",
            "HHY_PYTHON": sys.executable,
        })
        result = subprocess.run(
            [sys.executable, "scripts/continuity.py", *args],
            cwd=repo,
            env=env,
            text=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=240,
        )
        self.assertEqual(result.returncode, expected, result.stdout + "\n" + result.stderr)
        return result

    def git(self, repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
        env = dict(os.environ)
        env.update({
            "PYTHONDONTWRITEBYTECODE": "1",
            "GIT_TERMINAL_PROMPT": "0",
            "HHY_PYTHON": sys.executable,
        })
        result = subprocess.run(
            ["git", *args], cwd=repo, env=env, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=240,
        )
        self.assertEqual(result.returncode, 0, result.stdout + "\n" + result.stderr)
        return result

    def test_p00_008_hands_off_to_r01_without_starting_it(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-cross-release-close-") as temp:
            repo = Path(temp) / "repository"
            copy_fixture(repo)
            r01_tasks_before = (repo / "releases/R01/TASKS.yaml").read_bytes()

            self.cli(
                repo, "bootstrap", "--actor", ACTOR, "--init-git", "--initial-commit",
                "--task", "TASK-P00-008", "--branch", "task/TASK-P00-008",
            )
            started = yaml.safe_load(self.cli(
                repo, "start", "--actor", ACTOR, "--task", "TASK-P00-008",
                "--story", "STORY-P00-001", "--goal", "验证跨Release只交接不启动",
                "--scope", "scripts/smoke/cross_release_close_probe.txt",
            ).stdout)
            session_id = started["session_id"]

            probe = repo / "scripts/smoke/cross_release_close_probe.txt"
            probe.parent.mkdir(parents=True, exist_ok=True)
            probe.write_text("P00-008 closes; R01-001 remains unclaimed.\n", encoding="utf-8")
            self.cli(
                repo, "checkpoint", "--summary", "实现跨Release关闭隔离验证",
                "--next-step", "提交实现后关闭P00-008", "--test",
                "cross-release-close|PASS|tests/test_continuity_cross_release_close.py|隔离回归通过",
            )
            self.git(repo, "add", "-A")
            self.git(
                repo, "commit", "-m",
                "[STORY-P00-001] test(continuity): prepare cross release close probe",
            )
            code_commit = self.git(repo, "rev-parse", "HEAD").stdout.strip()

            before_invalid = repository_snapshot(repo)
            missing_release = self.cli(
                repo, "close", "--actor", ACTOR, "--result", "COMPLETED",
                "--summary", "非法目标必须原子拒绝", "--next-release", "R99",
                "--next-task", "TASK-R99-001", "--code-commit", code_commit,
                expected=2,
            )
            self.assertIn("下一Release不存在", missing_release.stderr)
            self.assertEqual(repository_snapshot(repo), before_invalid)

            non_first_task = self.cli(
                repo, "close", "--actor", ACTOR, "--result", "COMPLETED",
                "--summary", "非首任务必须原子拒绝", "--next-release", "R01",
                "--next-task", "TASK-R01-002", "--code-commit", code_commit,
                expected=2,
            )
            self.assertIn("只能指向下一Release首个任务", non_first_task.stderr)
            self.assertEqual(repository_snapshot(repo), before_invalid)

            closed = yaml.safe_load(self.cli(
                repo, "close", "--actor", ACTOR, "--result", "COMPLETED",
                "--summary", "P00关闭并只交接到R01", "--next-release", "R01",
                "--next-task", "TASK-R01-001", "--code-commit", code_commit,
            ).stdout)
            self.assertEqual(closed["status"], "SESSION_CLOSED")
            self.assertEqual(closed["next_release"], "R01")
            self.assertEqual(closed["next_task"], "TASK-R01-001")
            self.assertTrue(closed["final_metadata_commit_required"])
            self.assertFalse(any(" start " in f" {command} " for command in closed["next_commands"]))

            p00 = yaml.safe_load((repo / "releases/P00/TASKS.yaml").read_text(encoding="utf-8"))
            self.assertTrue(all(task["status"] == "DONE" for task in p00["tasks"]))
            self.assertEqual((repo / "releases/R01/TASKS.yaml").read_bytes(), r01_tasks_before)

            next_task = yaml.safe_load((repo / "NEXT_TASK.yaml").read_text(encoding="utf-8"))
            self.assertEqual(
                (next_task["id"], next_task["release"], next_task["status"]),
                ("TASK-R01-001", "R01", "READY"),
            )
            status = yaml.safe_load((repo / "CURRENT_STATUS.yaml").read_text(encoding="utf-8"))
            self.assertEqual(status["phase"], "R01")
            self.assertEqual(status["active_release"], "R01")
            self.assertEqual(status["active_task"], "TASK-R01-001")
            self.assertEqual(status["next_task"], "TASK-R01-001")
            self.assertEqual(status["status"], "READY")
            self.assertIsNone(status["continuity"]["active_session_id"])

            active = yaml.safe_load(
                (repo / ".continuity/ACTIVE_SESSION.yaml").read_text(encoding="utf-8")
            )
            self.assertIsNone(active["active_session_id"])
            session = yaml.safe_load(
                (repo / f".continuity/sessions/{session_id}.yaml").read_text(encoding="utf-8")
            )
            self.assertEqual(session["status"], "CLOSED")
            self.assertEqual(session["closure"]["next_release"], "R01")

            claims = yaml.safe_load(
                (repo / ".continuity/TASK_CLAIMS.yaml").read_text(encoding="utf-8")
            )
            self.assertFalse(any(
                row.get("task_id") == "TASK-R01-001" and row.get("status") == "ACTIVE"
                for row in claims.get("claims", [])
            ))
            events = [
                json.loads(line)
                for line in (repo / ".continuity/EVENT_LOG.jsonl").read_text(encoding="utf-8").splitlines()
                if line.strip()
            ]
            self.assertFalse(any(
                event.get("event_type") == "SESSION_STARTED"
                and event.get("payload", {}).get("release") == "R01"
                for event in events
            ))
            closed_event = next(event for event in reversed(events) if event["event_type"] == "SESSION_CLOSED")
            self.assertEqual(closed_event["payload"]["next_release"], "R01")


if __name__ == "__main__":
    unittest.main()
