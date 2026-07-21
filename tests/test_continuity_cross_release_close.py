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
sys.path.insert(0, str(ROOT / "scripts"))
from continuity import (  # noqa: E402
    ContinuityError,
    release_has_async_owner_gate,
    validate_independent_release_start,
)
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
    def test_current_async_owner_close_task_satisfies_only_its_release_dependency(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-async-owner-dependency-") as directory:
            root = Path(directory)
            (root / "releases/R06").mkdir(parents=True)
            (root / "releases/R07").mkdir(parents=True)
            dump_yaml(root / "releases/R06/TASKS.yaml", {
                "release": "R06",
                "tasks": [
                    {"id": "TASK-R06-007", "status": "DONE"},
                    {"id": "TASK-R06-008", "status": "READY", "title": "版本关闭与无状态交接"},
                ],
            })
            dump_yaml(root / "releases/R06/RELEASE_MANIFEST.yaml", {
                "android_delivery": {"machine_delivery": "PASS", "owner_physical_test": "PENDING"},
                "machine_completion": {
                    "status": "PASS", "owner_feedback_mode": "ASYNC_NON_BLOCKING",
                    "formal_release_acceptance": "PENDING_OWNER_PHYSICAL_TEST",
                    "production_activation": "BLOCKED_OWNER_PHYSICAL_TEST",
                    "next_release_development": "ALLOWED",
                },
            })
            dump_yaml(root / "releases/R07/TASKS.yaml", {
                "release": "R07",
                "tasks": [{"id": "TASK-R07-001", "status": "READY", "title": "R07开发就绪核验"}],
            })
            dump_yaml(root / "releases/RELEASE_DEPENDENCIES.yaml", {
                "dependencies": {"R07": ["R06"]},
            })

            next_task = validate_independent_release_start(
                root,
                current_release="R06",
                current_task="TASK-R06-008",
                next_release="R07",
                next_task="TASK-R07-001",
            )
            self.assertEqual((next_task["release"], next_task["id"]), ("R07", "TASK-R07-001"))

            r06_path = root / "releases/R06/TASKS.yaml"
            r06 = yaml.safe_load(r06_path.read_text(encoding="utf-8")) or {}
            r06["tasks"][0]["status"] = "READY"
            dump_yaml(r06_path, r06)
            with self.assertRaisesRegex(ContinuityError, "TASK-R06-007"):
                validate_independent_release_start(
                    root,
                    current_release="R06",
                    current_task="TASK-R06-008",
                    next_release="R07",
                    next_task="TASK-R07-001",
                )

    def test_version_close_task_requires_complete_async_owner_manifest_facts(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-async-owner-gate-") as directory:
            root = Path(directory)
            manifest_path = root / "releases/R06/RELEASE_MANIFEST.yaml"
            manifest_path.parent.mkdir(parents=True)
            manifest = {
                "android_delivery": {"machine_delivery": "PASS", "owner_physical_test": "PENDING"},
                "machine_completion": {
                    "status": "PASS", "owner_feedback_mode": "ASYNC_NON_BLOCKING",
                    "formal_release_acceptance": "PENDING_OWNER_PHYSICAL_TEST",
                    "production_activation": "BLOCKED_OWNER_PHYSICAL_TEST",
                    "next_release_development": "ALLOWED",
                },
            }
            dump_yaml(manifest_path, manifest)
            self.assertTrue(release_has_async_owner_gate(root, "R06"))
            manifest["machine_completion"]["production_activation"] = "ALLOWED"
            dump_yaml(manifest_path, manifest)
            self.assertFalse(release_has_async_owner_gate(root, "R06"))

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
            r01_plan_path = repo / "releases/R01/TASKS.yaml"
            r01_plan = yaml.safe_load(r01_plan_path.read_text(encoding="utf-8"))
            for index, task in enumerate(r01_plan["tasks"]):
                task["status"] = "READY" if index == 0 else "BLOCKED"
                task.pop("completed_at", None)
            r01_plan_path.write_text(yaml.safe_dump(r01_plan, allow_unicode=True, sort_keys=False), encoding="utf-8")
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
                "--parallel-assessment", "NO_SAFE_PARALLEL", "--parallel-reason",
                "跨Release关闭原子性回归必须串行操作同一状态链",
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
            self.assertEqual(
                set(next_task["commands"]),
                {"resume", "start", "checkpoint", "handoff", "export_clean", "cr_amend"},
            )
            for command in next_task["commands"].values():
                self.assertIn("scripts/continuity.py", command)
            self.assertEqual(next_task["commands"]["start"], next_task["start_command"])
            status = yaml.safe_load((repo / "CURRENT_STATUS.yaml").read_text(encoding="utf-8"))
            self.assertEqual(status["phase"], "R01")
            self.assertEqual(status["active_release"], "R01")
            self.assertEqual(status["active_task"], "TASK-R01-001")
            self.assertEqual(status["next_task"], "TASK-R01-001")
            self.assertEqual(status["status"], "READY")
            # Release-close consumers require the same immutable commit as the APK manifest.
            self.assertEqual(status["last_green_commit"], code_commit)
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

    def test_external_apk_gate_can_wait_while_independent_release_starts(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-external-gate-continue-") as temp:
            repo = Path(temp) / "repository"
            copy_fixture(repo)

            r02_path = repo / "releases/R02/TASKS.yaml"
            r02 = yaml.safe_load(r02_path.read_text(encoding="utf-8")) or {}
            for task in r02["tasks"]:
                if task["id"] == "TASK-R02-007":
                    task["status"] = "READY"
                    task.pop("completed_at", None)
                elif task["id"] == "TASK-R02-008":
                    task["status"] = "BLOCKED"
                    task.pop("completed_at", None)
                else:
                    task["status"] = "DONE"
                    task["completed_at"] = "2026-07-18T00:00:00Z"
            dump_yaml(r02_path, r02)

            r03_path = repo / "releases/R03/TASKS.yaml"
            r03 = yaml.safe_load(r03_path.read_text(encoding="utf-8")) or {}
            for index, task in enumerate(r03["tasks"]):
                task["status"] = "READY" if index == 0 else "BLOCKED"
                task.pop("completed_at", None)
            dump_yaml(r03_path, r03)
            r03_before = r03_path.read_bytes()

            r02_task = next(row for row in r02["tasks"] if row["id"] == "TASK-R02-007")
            next_task = yaml.safe_load((repo / "NEXT_TASK.yaml").read_text(encoding="utf-8")) or {}
            next_task.update({
                "id": "TASK-R02-007",
                "title": r02_task["title"],
                "status": "READY",
                "release": "R02",
                "definition_of_ready": "releases/R02/DEFINITION_OF_READY.yaml",
                "stories": "releases/R02/STORIES.yaml",
                "requirements": r02_task.get("requirements", []),
                "steps": r02_task.get("deliverables", []),
                "acceptance": r02_task.get("acceptance", []),
                "claim_required": True,
                "start_command": (
                    "python3 scripts/continuity.py start --actor <ACTOR_ID> "
                    "--task TASK-R02-007"
                ),
            })
            dump_yaml(repo / "NEXT_TASK.yaml", next_task)
            status = yaml.safe_load((repo / "CURRENT_STATUS.yaml").read_text(encoding="utf-8")) or {}
            status.update({
                "phase": "R02", "active_release": "R02", "status": "READY",
                "active_task": None, "next_task": "TASK-R02-007",
                "in_progress_tasks": [], "blocked_tasks": [],
            })
            dump_yaml(repo / "CURRENT_STATUS.yaml", status)

            self.git(repo, "init")
            self.git(repo, "config", "user.name", "External Gate Test")
            self.git(repo, "config", "user.email", "external-gate@test.invalid")
            self.git(repo, "add", "-A")
            self.git(repo, "commit", "--no-verify", "-m", "fixture baseline")

            started = yaml.safe_load(self.cli(
                repo, "start", "--actor", ACTOR, "--task", "TASK-R02-007",
                "--story", "STORY-R02-009", "--goal", "等待真机时继续独立R03",
                "--scope", "scripts/smoke/external_gate_probe.txt",
            ).stdout)
            probe = repo / "scripts/smoke/external_gate_probe.txt"
            probe.parent.mkdir(parents=True, exist_ok=True)
            probe.write_text("R02 waits for owner device; R03 depends only on green R01.\n", encoding="utf-8")
            self.cli(
                repo, "checkpoint", "--summary", "记录R02机器交付完成",
                "--next-step", "挂起真机门禁并继续R03", "--test",
                "machine-delivery|PASS|fixture-apk-evidence|机器交付通过",
                "--parallel-assessment", "NO_SAFE_PARALLEL", "--parallel-reason",
                "任务状态原子转换必须串行",
            )
            self.git(repo, "add", "-A")
            self.git(repo, "commit", "--no-verify", "-m", "record machine delivery")
            code_commit = self.git(repo, "rev-parse", "HEAD").stdout.strip()

            before_rejected = repository_snapshot(repo)
            rejected = self.cli(
                repo, "close", "--actor", ACTOR, "--result", "BLOCKED",
                "--summary", "等待项目所有者真机验收", "--next-release", "R03",
                "--next-task", "TASK-R03-001", "--code-commit", code_commit,
                expected=2,
            )
            self.assertIn("--allow-independent-release", rejected.stderr)
            self.assertEqual(repository_snapshot(repo), before_rejected)

            closed = yaml.safe_load(self.cli(
                repo, "close", "--actor", ACTOR, "--result", "BLOCKED",
                "--summary", "等待项目所有者真机验收", "--next-release", "R03",
                "--next-task", "TASK-R03-001", "--code-commit", code_commit,
                "--allow-independent-release", "--user-confirmation",
                "项目所有者要求APK稍后测试，当前立即继续下一版本",
            ).stdout)
            self.assertEqual(closed["result"], "BLOCKED")
            self.assertEqual(closed["next_release"], "R03")
            self.assertEqual(closed["next_task"], "TASK-R03-001")

            r02_after = yaml.safe_load(r02_path.read_text(encoding="utf-8"))
            deferred = next(row for row in r02_after["tasks"] if row["id"] == "TASK-R02-007")
            self.assertEqual(deferred["status"], "BLOCKED")
            self.assertIn("真机", deferred["blocker"])
            self.assertEqual(r03_path.read_bytes(), r03_before)

            next_after = yaml.safe_load((repo / "NEXT_TASK.yaml").read_text(encoding="utf-8"))
            self.assertEqual(
                (next_after["id"], next_after["release"], next_after["status"]),
                ("TASK-R03-001", "R03", "READY"),
            )
            self.assertEqual(next_after["deferred_task"]["id"], "TASK-R02-007")
            current = yaml.safe_load((repo / "CURRENT_STATUS.yaml").read_text(encoding="utf-8"))
            self.assertEqual(current["status"], "READY")
            self.assertEqual(current["active_release"], "R03")
            self.assertIn("TASK-R02-007", current["blocked_tasks"])
            session = yaml.safe_load(
                (repo / f".continuity/sessions/{started['session_id']}.yaml").read_text(encoding="utf-8")
            )
            self.assertEqual(session["closure"]["result"], "BLOCKED")
            self.assertTrue(session["closure"]["blocked_advance"])


if __name__ == "__main__":
    unittest.main()
