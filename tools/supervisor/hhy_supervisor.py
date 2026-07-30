#!/usr/bin/env python3
"""Bounded external supervisor for the Governance V5.0 Orchestrator.

This module observes and calls the existing controller. It never selects a task,
writes governance state, changes attempt counters, or invokes Codex directly.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Callable, Iterator

import yaml
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.governance.gov50.state import assert_valid_state, read_state  # noqa: E402
from tools.governance.gov50.tasks import load_task_specs  # noqa: E402

CONFIG_SCHEMA = "governance/schemas/supervisor-config.schema.json"
RUNTIME_SCHEMA = "governance/schemas/supervisor-runtime.schema.json"
STOP_SCHEMA = "governance/schemas/supervisor-stop-report.schema.json"
RUNTIME_DIR = Path("governance/runtime/supervisor")
PROGRAM_COMPLETE = RUNTIME_DIR / "PROGRAM_COMPLETE.lock"
STOP_REQUEST = RUNTIME_DIR / "STOP_REQUESTED"

AUTO_CONTINUE = {"MAX_RUNS_REACHED", "NEXT_TASK_READY"}
MANDATORY_STOP = {
    "CANDIDATE_REQUIRED", "EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED", "FAILED_BOUNDED",
    "POLICY_VIOLATION", "OWNER_ACTION_REQUIRED", "PROGRAM_COMPLETE", "UNKNOWN_STATUS",
    "STATE_INVALID", "DOCTOR_FAILED", "WORKTREE_UNSAFE", "CODEX_AUTH_REQUIRED",
    "SUPERVISOR_BUDGET_EXHAUSTED", "NO_PROGRESS_DETECTED",
}
CONTROLLER_MAP = {
    "CANDIDATE_EVIDENCE_REQUIRED": "CANDIDATE_REQUIRED",
    "DIRTY_AUTHORITY_WORKTREE": "WORKTREE_UNSAFE",
    "MIGRATION_NOT_ACTIVE": "STATE_INVALID",
    "CODEX_AUTH_REQUIRED": "CODEX_AUTH_REQUIRED",
}
SECRET_KEY = re.compile(r"token|secret|password|passwd|authorization|private[_-]?key|credential|api[_-]?key", re.I)


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical_hash(value: Any) -> str:
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return sha256_bytes(raw)


def redact(value: Any, depth: int = 0) -> Any:
    """Keep reports useful while removing secret-bearing fields and huge output."""
    if depth > 8:
        return "<truncated>"
    if isinstance(value, dict):
        return {str(k): ("<redacted>" if SECRET_KEY.search(str(k)) else redact(v, depth + 1)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v, depth + 1) for v in value[:100]]
    if isinstance(value, str):
        return value[-4000:]
    return value


def atomic_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, path)


def atomic_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    tmp.write_text(value, encoding="utf-8")
    os.replace(tmp, path)


def append_jsonl(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(redact(value), ensure_ascii=False, separators=(",", ":")) + "\n")


def resolve_repo(value: str | None) -> Path:
    repo = Path(value or ROOT).expanduser().resolve()
    if not (repo / ".git").exists():
        raise RuntimeError(f"not a Git repository: {repo}")
    if not (repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml").is_file():
        raise RuntimeError("Governance V5.0 constitution is missing")
    constitution = yaml.safe_load((repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml").read_text(encoding="utf-8")) or {}
    if constitution.get("status") != "ENFORCED" or constitution.get("project") != "hhy-pro-platform":
        raise RuntimeError("repository is not an enforced hhy-pro-platform Governance V5.0 checkout")
    return repo


def load_config(repo: Path, path: Path | None = None) -> dict[str, Any]:
    config_path = (path or (repo / "tools" / "supervisor" / "supervisor_config.yaml")).resolve()
    if not config_path.is_file():
        raise RuntimeError(f"supervisor config missing: {config_path}")
    data = yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}
    schema = json.loads((repo / CONFIG_SCHEMA).read_text(encoding="utf-8"))
    errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda e: list(e.path))
    if errors:
        raise RuntimeError("invalid supervisor config: " + "; ".join(e.message for e in errors))
    configured_root = data.get("repo_root")
    if configured_root and Path(configured_root).expanduser().resolve() != repo:
        raise RuntimeError("supervisor config repo_root does not match --repo")
    return data


def run_command(repo: Path, argv: list[str], timeout: int = 120) -> dict[str, Any]:
    env = os.environ.copy()
    # Existing Gates honor PYTHON; this also avoids the WindowsApps python stub.
    env["PYTHON"] = sys.executable
    started = time.monotonic()
    try:
        proc = subprocess.run(argv, cwd=repo, text=True, capture_output=True, timeout=timeout, env=env)
    except FileNotFoundError as exc:
        return {"status": "FAIL", "exit_code": 127, "command": argv, "stdout": "", "stderr": str(exc), "elapsed_seconds": round(time.monotonic() - started, 3)}
    except subprocess.TimeoutExpired as exc:
        return {"status": "TIMEOUT", "exit_code": 124, "command": argv, "stdout": (exc.stdout or "")[-4000:], "stderr": (exc.stderr or "")[-4000:], "elapsed_seconds": round(time.monotonic() - started, 3)}
    return {"status": "PASS" if proc.returncode == 0 else "FAIL", "exit_code": proc.returncode, "command": argv, "stdout": proc.stdout[-12000:], "stderr": proc.stderr[-12000:], "elapsed_seconds": round(time.monotonic() - started, 3)}


def parse_json_output(text: str) -> dict[str, Any] | None:
    for line in reversed([line.strip() for line in text.splitlines() if line.strip()]):
        try:
            value = json.loads(line)
            if isinstance(value, dict):
                return value
        except json.JSONDecodeError:
            continue
    # Some tools pretty-print a single JSON object.
    try:
        value = json.loads(text)
        return value if isinstance(value, dict) else None
    except json.JSONDecodeError:
        return None


def git(repo: Path, *args: str) -> str:
    proc = subprocess.run(["git", *args], cwd=repo, text=True, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or f"git {' '.join(args)} failed")
    return proc.stdout.strip()


def read_active_state(repo: Path) -> dict[str, Any]:
    specs = load_task_specs(repo)
    state = read_state(repo)
    assert_valid_state(state, specs)
    return state


def state_summary(state: dict[str, Any]) -> dict[str, Any]:
    project = state.get("project") or {}
    task_id = project.get("active_task")
    row = (state.get("tasks") or {}).get(task_id) if task_id else None
    return {
        "release": project.get("active_release"), "task": task_id,
        "task_status": (row or {}).get("status"), "attempt": (row or {}).get("attempts_used"),
        "revision": state.get("revision"), "authoritative_commit": project.get("authoritative_commit"),
        "project_status": project.get("status"), "goal_complete": bool(project.get("goal_complete")),
    }


def failed_set_hash(result: dict[str, Any]) -> str:
    rows: list[Any] = []
    def walk(value: Any, key: str = "") -> None:
        if isinstance(value, dict):
            for child_key, child in value.items():
                if str(child_key).lower() in {"failed_tests", "failures", "errors", "findings", "violations"}:
                    rows.append({str(child_key): redact(child)})
                walk(child, str(child_key))
        elif isinstance(value, list):
            for child in value:
                walk(child, key)
    walk(result)
    return canonical_hash(rows)


def diff_hash(repo: Path) -> str:
    try:
        return canonical_hash(git(repo, "-c", "core.quotepath=false", "status", "--porcelain=v1", "-uall").splitlines())
    except Exception:
        return canonical_hash(["GIT_UNAVAILABLE"])


def progress_fingerprint(repo: Path, before: dict[str, Any], after: dict[str, Any], result: dict[str, Any]) -> dict[str, Any]:
    task = after.get("task") or before.get("task")
    return {
        "release_id": after.get("release") or before.get("release"),
        "task_id": task,
        "attempt": after.get("attempt"),
        "baseline_commit": before.get("authoritative_commit"),
        "ending_commit": git(repo, "rev-parse", "HEAD"),
        "state_revision": after.get("revision"),
        "controller_status": result.get("status"),
        "failed_test_set_hash": failed_set_hash(result),
        "git_diff_hash": diff_hash(repo),
        "error_fingerprint": result.get("error_fingerprint") or result.get("reason") or result.get("blocker"),
    }


def real_progress(previous: dict[str, Any] | None, current: dict[str, Any]) -> bool:
    if not previous:
        return True
    for key in ("state_revision", "ending_commit", "task_id", "attempt", "failed_test_set_hash", "git_diff_hash"):
        if previous.get(key) != current.get(key):
            return True
    return False


def map_controller_status(result: dict[str, Any], max_runs: int, state: dict[str, Any]) -> str:
    raw = str(result.get("status") or "")
    if raw in AUTO_CONTINUE:
        return raw
    if raw == "INFRASTRUCTURE_BLOCKED":
        blocker = result.get("blocker") or {}
        if isinstance(blocker, dict) and blocker.get("code") in {"CODEX_AUTHENTICATION_FAILED", "CODEX_AUTH_REQUIRED"}:
            return "CODEX_AUTH_REQUIRED"
    if raw in MANDATORY_STOP:
        return raw
    if raw in CONTROLLER_MAP:
        return CONTROLLER_MAP[raw]
    if raw == "TASK_DONE":
        runs = int(result.get("runs") or 1)
        if runs >= max_runs:
            return "MAX_RUNS_REACHED"
        return "NEXT_TASK_READY"
    if raw == "PROGRAM_COMPLETE" or bool((state.get("project") or {}).get("goal_complete")):
        return "PROGRAM_COMPLETE"
    if raw == "RETRY_REQUIRED":
        return "NO_PROGRESS_DETECTED"
    return "UNKNOWN_STATUS"


class SingleInstanceLock:
    """OS-level lock; the metadata file is diagnostic only."""
    def __init__(self, path: Path, metadata: dict[str, Any]):
        self.path, self.metadata, self.handle = path, metadata, None

    def acquire(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.handle = self.path.open("a+b")
        try:
            if os.name == "nt":
                import msvcrt
                if self.handle.seek(0, os.SEEK_END) == 0:
                    self.handle.write(b"0")
                    self.handle.flush()
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except (OSError, IOError):
            self.handle.close()
            self.handle = None
            raise RuntimeError("SUPERVISOR_ALREADY_RUNNING")
        self.handle.seek(0)
        self.handle.truncate()
        self.handle.write((json.dumps(redact(self.metadata), ensure_ascii=False) + "\n").encode("utf-8"))
        self.handle.flush()

    def release(self) -> None:
        if not self.handle:
            return
        try:
            if os.name == "nt":
                import msvcrt
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_UN)
        finally:
            self.handle.close()
            self.handle = None

    def __enter__(self) -> "SingleInstanceLock":
        self.acquire()
        return self

    def __exit__(self, *_: Any) -> None:
        self.release()


class Supervisor:
    def __init__(self, repo: Path, config: dict[str, Any], *, sleep_fn: Callable[[float], None] = time.sleep, command_runner: Callable[..., dict[str, Any]] | None = None):
        self.repo = repo
        self.config = config
        self.sleep_fn = sleep_fn
        self.command_runner = command_runner or run_command
        self.runtime = repo / RUNTIME_DIR
        self.instance_uuid = str(uuid.uuid4())
        self.process_started_at = utc_now()
        self.started_monotonic = time.monotonic()
        self.start_commit = self._git_head()
        self._runtime_lock = threading.RLock()
        self._heartbeat_stop = threading.Event()
        self._heartbeat_thread: threading.Thread | None = None
        self._active_batch: dict[str, Any] | None = None
        self.runtime_state: dict[str, Any] = {
            "schema": "hhy.supervisor-runtime/v5.0", "status": "IDLE", "instance_uuid": self.instance_uuid,
            "pid": os.getpid(), "process_started_at": self.process_started_at, "repo": str(repo),
            "started_at": utc_now(), "updated_at": utc_now(), "batch_index": 0, "total_runs": 0,
            "active_release": None, "active_task": None, "state_revision": None, "last_progress": None,
            "progress_history": [], "last_stop_status": None, "repeated_stop_status_count": 0,
            "budget_hours": float(config["runtime"]["max_total_hours"]), "max_batches": int(config["batch"]["max_batches_per_session"]),
            "max_runs_per_batch": int(config["batch"]["max_runs_per_batch"]),
            "max_task_minutes": int(config["runtime"]["max_task_minutes"]),
        }

    def _git_head(self) -> str | None:
        try:
            return git(self.repo, "rev-parse", "HEAD")
        except Exception:
            return None

    def _path(self, name: str) -> Path:
        return self.runtime / name

    def _write_pid(self) -> None:
        atomic_json(self._path("supervisor.pid"), {
            "schema": "hhy.supervisor-pid/v5.0",
            "pid": os.getpid(),
            "process_started_at": self.process_started_at,
            "repo": str(self.repo),
            "instance_uuid": self.instance_uuid,
        })

    def _remove_pid(self) -> None:
        path = self._path("supervisor.pid")
        if not path.is_file():
            return
        try:
            current = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        if current.get("instance_uuid") == self.instance_uuid:
            path.unlink(missing_ok=True)

    def _heartbeat_loop(self) -> None:
        interval = max(1, int(self.config["runtime"]["heartbeat_seconds"]))
        while not self._heartbeat_stop.wait(interval):
            try:
                self.persist_runtime()
            except Exception as exc:
                self.event("HEARTBEAT_WRITE_FAILED", error_fingerprint=canonical_hash(str(exc)))

    def _start_heartbeat(self) -> None:
        self._heartbeat_stop.clear()
        self._heartbeat_thread = threading.Thread(target=self._heartbeat_loop, name="hhy-supervisor-heartbeat", daemon=True)
        self._heartbeat_thread.start()

    def _stop_heartbeat(self) -> None:
        self._heartbeat_stop.set()
        if self._heartbeat_thread:
            self._heartbeat_thread.join(timeout=2)
            self._heartbeat_thread = None

    def event(self, event: str, **data: Any) -> None:
        append_jsonl(self._path("events.jsonl"), {"at": utc_now(), "event": event, **data})

    def persist_runtime(self, status: str | None = None) -> None:
        with self._runtime_lock:
            if status:
                self.runtime_state["status"] = status
            self.runtime_state["updated_at"] = utc_now()
            schema = json.loads((self.repo / RUNTIME_SCHEMA).read_text(encoding="utf-8"))
            errors = sorted(Draft202012Validator(schema).iter_errors(self.runtime_state), key=lambda e: list(e.path))
            if errors:
                raise RuntimeError("runtime state does not satisfy schema: " + "; ".join(e.message for e in errors))
            atomic_json(self._path("runtime_state.json"), redact(self.runtime_state))
            atomic_json(self._path("heartbeat.json"), {
                "schema": "hhy.supervisor-heartbeat/v5.0",
                "at": utc_now(),
                "instance_uuid": self.instance_uuid,
                "pid": os.getpid(),
                "status": self.runtime_state["status"],
                "batch_index": self.runtime_state["batch_index"],
            })

    def write_stop_report(self, status: str, state: dict[str, Any] | None, result: dict[str, Any] | None = None, *, allow_auto_continue: bool = False, resolution: str = "") -> dict[str, Any]:
        summary = state_summary(state) if state else {"release": None, "task": None, "attempt": None, "revision": None}
        fingerprint = (self.runtime_state.get("last_progress") or {}).get("error_fingerprint") if self.runtime_state.get("last_progress") else None
        owner_actions: list[str] = []
        if status == "CANDIDATE_REQUIRED":
            owner_actions.append("冻结 Commit，完成完整编译、全量测试、APK、模拟器、Runtime 截图和 Candidate Gate")
        if status in {"EXTERNAL_BLOCKED", "OWNER_ACTION_REQUIRED"}:
            owner_actions.append("按停止报告中的解除条件提供证据或执行明确的项目所有者操作")
        if status in {"FAILED_BOUNDED", "PROGRAM_COMPLETE"}:
            owner_actions.append("不得自动重试；只能按治理正式流程处理")
        report = {
            "schema": "hhy.supervisor-stop-report/v5.0", "stopped_at": utc_now(), "stop_status": status,
            "release": summary.get("release"), "task": summary.get("task"), "attempt": summary.get("attempt"),
            "start_commit": self.start_commit, "end_commit": self._git_head(), "state_revision": summary.get("revision"),
            "last_progress": self.runtime_state.get("last_progress"), "error_fingerprint": fingerprint,
            "allow_auto_continue": allow_auto_continue, "owner_actions": owner_actions,
            "resolution": resolution or ("Supervisor will remain stopped until the documented condition is satisfied."),
            "recovery_command": "python3 tools/supervisor/hhy_supervisor.py --repo <path> --dry-run",
            "log_paths": ["governance/runtime/supervisor/events.jsonl", "governance/runtime/supervisor/batches.jsonl"],
            "controller_result": redact(result) if result else None,
        }
        schema = json.loads((self.repo / STOP_SCHEMA).read_text(encoding="utf-8"))
        errors = sorted(Draft202012Validator(schema).iter_errors(report), key=lambda e: list(e.path))
        if errors:
            raise RuntimeError("internal stop report does not satisfy schema: " + "; ".join(e.message for e in errors))
        atomic_json(self._path("SUPERVISOR_STOP_REPORT.json"), report)
        md = ["# Supervisor Stop Report", "", f"- Status: `{status}`", f"- Stopped: `{report['stopped_at']}`", f"- Release: `{report['release']}`", f"- Task: `{report['task']}`", f"- Attempt: `{report['attempt']}`", f"- STATE Revision: `{report['state_revision']}`", f"- Start commit: `{report['start_commit']}`", f"- End commit: `{report['end_commit']}`", f"- Auto continue: `{report['allow_auto_continue']}`", "", "## Required action", "", *[f"- {item}" for item in owner_actions], f"- {report['resolution']}", "", "## Recovery", "", f"`{report['recovery_command']}`", "", "## Logs", "", *[f"- `{path}`" for path in report["log_paths"]], ""]
        self._path("SUPERVISOR_STOP_REPORT.md").write_text("\n".join(md), encoding="utf-8")
        return report

    def preflight(self, *, dry_run: bool = False) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
        if (self.repo / PROGRAM_COMPLETE).is_file():
            try:
                state = read_active_state(self.repo)
            except Exception as exc:
                raise RuntimeError("STATE_INVALID") from exc
            return state, {}, {"status": "PROGRAM_COMPLETE"}
        if self._path("SUPERVISOR_STOP_REPORT.json").is_file() and not dry_run:
            raise RuntimeError("SUPERVISOR_STOP_REPORT_PENDING")
        try:
            state = read_active_state(self.repo)
        except Exception as exc:
            raise RuntimeError("STATE_INVALID") from exc
        if bool((state.get("project") or {}).get("goal_complete")):
            return state, {}, {"status": "PROGRAM_COMPLETE"}
        if (self.repo / "governance" / "runtime" / "ACTIVE_TASK.json").is_file():
            raise RuntimeError("WORKTREE_UNSAFE")
        for worktree in git(self.repo, "worktree", "list", "--porcelain").split("\n\n"):
            path_line = next((line for line in worktree.splitlines() if line.startswith("worktree ")), "")
            path = Path(path_line[9:]).resolve() if path_line else None
            if path and path != self.repo and (path / "governance" / "runtime" / "ACTIVE_TASK.json").is_file():
                raise RuntimeError("WORKTREE_UNSAFE")
        worktree = git(self.repo, "status", "--porcelain=v1", "-uall",).splitlines()
        conflict = [line for line in worktree if line[:2] in {"UU", "AA", "DD", "AU", "UD", "UA", "DU"}]
        if conflict or (self.repo / ".git" / "MERGE_HEAD").exists() or (self.repo / ".git" / "REBASE_HEAD").exists():
            raise RuntimeError("WORKTREE_UNSAFE")
        doctor = self._controller_json(["doctor", "--ci"], timeout=1800)
        if doctor.get("status") != "PASS":
            raise RuntimeError("DOCTOR_FAILED")
        active = self._controller_json(["active-task", "--format", "json"], timeout=120)
        active_state = active.get("state") or {}
        if (
            active_state.get("active_release") != state.get("project", {}).get("active_release")
            or active_state.get("active_task") != state.get("project", {}).get("active_task")
        ):
            raise RuntimeError("STATE_INVALID")
        return state, active, doctor

    def _controller_json(self, args: list[str], timeout: int) -> dict[str, Any]:
        command = [sys.executable, "tools/governance/hhy_governance.py", *args]
        result = self.command_runner(self.repo, command, timeout)
        payload = parse_json_output(result.get("stdout", ""))
        if payload is None:
            if result.get("exit_code") == 0:
                raise RuntimeError("UNKNOWN_STATUS")
            raise RuntimeError("DOCTOR_FAILED" if args[:1] == ["doctor"] else "UNKNOWN_STATUS")
        payload.setdefault("_execution", result)
        return payload

    def batch_once(self, max_runs: int) -> tuple[str, dict[str, Any], dict[str, Any]]:
        try:
            before_state = read_active_state(self.repo)
        except Exception as exc:
            raise RuntimeError("STATE_INVALID") from exc
        before = state_summary(before_state)
        self._active_batch = {"batch": self.runtime_state["batch_index"], "before": before, "started_at": utc_now()}
        self.event("BATCH_STARTED", **self._active_batch)
        try:
            task_timeout = int(self.config["runtime"]["max_task_minutes"]) * 60
            result = self._controller_json(["run-loop", "--max-runs", str(max_runs)], timeout=max(60, task_timeout))
        except Exception:
            self.event("BATCH_INTERRUPTED", **(self._active_batch or {}))
            raise
        try:
            after_state = read_active_state(self.repo)
        except Exception as exc:
            raise RuntimeError("STATE_INVALID") from exc
        after = state_summary(after_state)
        status = map_controller_status(result, max_runs, after_state)
        fingerprint = progress_fingerprint(self.repo, before, after, result)
        self.runtime_state["last_progress"] = fingerprint
        self.runtime_state["progress_history"] = (self.runtime_state.get("progress_history") or [])[-49:] + [fingerprint]
        self.runtime_state["active_release"] = after.get("release")
        self.runtime_state["active_task"] = after.get("task")
        self.runtime_state["state_revision"] = after.get("revision")
        self.runtime_state["total_runs"] += int(result.get("runs") or 0)
        append_jsonl(self._path("batches.jsonl"), {"at": utc_now(), "batch": self.runtime_state["batch_index"], "mapped_status": status, "controller": result, "before": before, "after": after, "progress": fingerprint})
        self.event("BATCH_FINISHED", batch=self.runtime_state["batch_index"], mapped_status=status, runs=result.get("runs"))
        self._active_batch = None
        return status, result, after_state

    def run(self, *, trial: bool = False) -> dict[str, Any]:
        max_runs = 1 if trial else int(self.config["batch"]["max_runs_per_batch"])
        max_batches = 1 if trial else int(self.config["batch"]["max_batches_per_session"])
        budget_hours = float(self.config["runtime"]["max_total_hours"])
        metadata = {"schema": "hhy.supervisor-lock/v5.0", "instance_uuid": self.instance_uuid, "pid": os.getpid(), "process_started_at": self.process_started_at, "repo": str(self.repo)}
        lock = SingleInstanceLock(self._path("supervisor.lock"), metadata)
        with lock:
            self._write_pid()
            self._start_heartbeat()
            try:
                self.runtime_state["max_batches"] = max_batches
                self.runtime_state["max_runs_per_batch"] = max_runs
                existing = None
                if self._path("runtime_state.json").is_file():
                    try: existing = json.loads(self._path("runtime_state.json").read_text(encoding="utf-8"))
                    except Exception: existing = None
                if existing and existing.get("status") == "RUNNING":
                    self.event("INTERRUPTED", previous_instance=existing.get("instance_uuid"), previous_batch=existing.get("batch_index"))
                    append_jsonl(self._path("batches.jsonl"), {
                        "at": utc_now(),
                        "batch": existing.get("batch_index"),
                        "mapped_status": "INTERRUPTED",
                        "interrupted": True,
                        "previous_instance": existing.get("instance_uuid"),
                    })
                    self.runtime_state["status"] = "INTERRUPTED"
                if existing:
                    for key in (
                        "last_progress",
                        "progress_history",
                        "last_stop_status",
                        "repeated_stop_status_count",
                        "repeated_fingerprint_count",
                    ):
                        if key in existing:
                            self.runtime_state[key] = existing[key]
                self.persist_runtime("RUNNING")
                state, active, doctor = self.preflight()
                if doctor.get("status") == "PROGRAM_COMPLETE" or bool((state.get("project") or {}).get("goal_complete")):
                    self.persist_runtime("STOPPED")
                    return self._finish("PROGRAM_COMPLETE", state, None, "R32 completion marker or authoritative goal_complete is set.")
                previous_progress = self.runtime_state.get("last_progress")
                for batch in range(1, max_batches + 1):
                    if (self.repo / STOP_REQUEST).is_file():
                        return self._finish("OWNER_ACTION_REQUIRED", state, None, "STOP_REQUESTED marker is present; remove it only through the documented owner operation.")
                    if time.monotonic() - self.started_monotonic >= budget_hours * 3600:
                        return self._finish("SUPERVISOR_BUDGET_EXHAUSTED", state, None, "The configured total runtime budget was reached.")
                    self.runtime_state["batch_index"] = batch
                    self.persist_runtime("RUNNING")
                    status, result, state = self.batch_once(max_runs)
                    progress = self.runtime_state.get("last_progress") or {}
                    same_progress = previous_progress is not None and not real_progress(previous_progress, progress)
                    previous_progress = progress
                    if same_progress:
                        repeats = int(self.runtime_state.get("repeated_fingerprint_count") or 0) + 1
                        self.runtime_state["repeated_fingerprint_count"] = repeats
                        self.runtime_state["last_stop_status"] = "NO_PROGRESS_DETECTED"
                        if repeats >= int(self.config["no_progress"]["repeated_fingerprint_limit"]):
                            return self._finish("NO_PROGRESS_DETECTED", state, result, "The same machine progress fingerprint repeated without a valid state, code, test, candidate, or commit change.")
                    else:
                        self.runtime_state["repeated_fingerprint_count"] = 0
                    prior_status = self.runtime_state.get("last_stop_status")
                    if prior_status == status:
                        self.runtime_state["repeated_stop_status_count"] = int(self.runtime_state.get("repeated_stop_status_count") or 0) + 1
                    else:
                        self.runtime_state["repeated_stop_status_count"] = 1
                    self.runtime_state["last_stop_status"] = status
                    if self.runtime_state["repeated_stop_status_count"] >= int(self.config["no_progress"]["repeated_stop_status_limit"]):
                        return self._finish("NO_PROGRESS_DETECTED", state, result, "The same stop status reached the configured consecutive limit.")
                    self.persist_runtime("RUNNING")
                    if status not in AUTO_CONTINUE:
                        return self._finish(status, state, result, "Controller returned a mandatory-stop or unknown status.")
                    if batch >= max_batches:
                        return self._finish("SUPERVISOR_BUDGET_EXHAUSTED", state, result, "The configured batch budget was reached.")
                    self.sleep_fn(int(self.config["batch"]["cooldown_seconds"]))
                    state, active, doctor = self.preflight()
                return self._finish("SUPERVISOR_BUDGET_EXHAUSTED", state, None, "The configured batch budget was reached.")
            except RuntimeError as exc:
                text = str(exc)
                if self._active_batch:
                    self.event("BATCH_INTERRUPTED", **self._active_batch)
                status = text if text in MANDATORY_STOP else ("OWNER_ACTION_REQUIRED" if text == "SUPERVISOR_STOP_REPORT_PENDING" else "UNKNOWN_STATUS")
                try: state = read_active_state(self.repo)
                except Exception: state = None
                return self._finish(status, state, None, text)
            except Exception as exc:
                if self._active_batch:
                    self.event("BATCH_INTERRUPTED", **self._active_batch)
                try: state = read_active_state(self.repo)
                except Exception: state = None
                return self._finish("UNKNOWN_STATUS", state, None, f"unexpected supervisor error: {exc}")
            finally:
                self._stop_heartbeat()
                self._remove_pid()

    def _finish(self, status: str, state: dict[str, Any] | None, result: dict[str, Any] | None, resolution: str) -> dict[str, Any]:
        self.runtime_state["last_stop_status"] = status
        self.persist_runtime("STOPPED")
        if status == "PROGRAM_COMPLETE":
            atomic_text(self.repo / PROGRAM_COMPLETE, "PROGRAM_COMPLETE\n")
        report = self.write_stop_report(status, state, result, allow_auto_continue=status in AUTO_CONTINUE, resolution=resolution)
        self.event("STOPPED", status=status, report="governance/runtime/supervisor/SUPERVISOR_STOP_REPORT.json")
        return {"schema": "hhy.supervisor-result/v5.0", "status": status, "report": report, "runtime": "governance/runtime/supervisor/runtime_state.json"}


def simulation(statuses: list[str] | None = None) -> dict[str, Any]:
    values = statuses or sorted(AUTO_CONTINUE | MANDATORY_STOP)
    rows = []
    for status in values:
        mapped = map_controller_status({"status": status}, 20, {"project": {"goal_complete": status == "PROGRAM_COMPLETE"}})
        rows.append({"input": status, "mapped": mapped, "auto_continue": mapped in AUTO_CONTINUE, "stop": mapped not in AUTO_CONTINUE})
    return {"schema": "hhy.supervisor-simulation/v5.0", "status": "PASS", "cases": rows, "codex_called": False, "state_written": False}


def dry_run(repo: Path, config: dict[str, Any]) -> dict[str, Any]:
    """Run preflight and reject any controller mutation without deleting user files."""
    supervisor = Supervisor(repo, config)
    before = git(repo, "status", "--porcelain=v1", "-uall")
    state, active, doctor = supervisor.preflight(dry_run=True)
    after = git(repo, "status", "--porcelain=v1", "-uall")
    if before != after:
        raise RuntimeError("DRY_RUN_MUTATION_DETECTED")
    return {"schema": "hhy.supervisor-dry-run/v5.0", "status": "DRY_RUN", "doctor": doctor, "active_task": active, "state": state_summary(state), "would_execute": ["run-loop", "--max-runs", str(config["batch"]["max_runs_per_batch"])]}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="HHY Governance V5.0 bounded Supervisor")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--config")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--simulate", action="store_true")
    parser.add_argument("--simulate-status", action="append", default=[])
    parser.add_argument("--trial", action="store_true", help="one batch with one Orchestrator run; never exceeds configured limits")
    args = parser.parse_args(argv)
    try:
        if args.simulate:
            print(json.dumps(simulation(args.simulate_status), ensure_ascii=False, indent=2))
            return 0
        repo = resolve_repo(args.repo)
        config = load_config(repo, Path(args.config).resolve() if args.config else None)
        result = dry_run(repo, config) if args.dry_run else Supervisor(repo, config).run(trial=args.trial)
        print(json.dumps(redact(result), ensure_ascii=False, indent=2))
        return 0 if result.get("status") in {"DRY_RUN", "MAX_RUNS_REACHED", "NEXT_TASK_READY"} else 21
    except Exception as exc:
        payload = {"schema": "hhy.supervisor-error/v5.0", "status": "FAIL", "error": str(exc)}
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
