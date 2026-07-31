#!/usr/bin/env python3
"""Always-on local Guardian for the Governance V5.0 Supervisor.

The Guardian owns liveness and bounded recovery. It never edits product code,
selects a task, changes Attempt counts, or approves a governance gate.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.supervisor.hhy_supervisor import (  # noqa: E402
    AUTO_CONTINUE,
    PROGRAM_COMPLETE,
    RUNTIME_DIR,
    atomic_json,
    classify_error_text,
    load_config,
    redact,
    read_active_state,
    resolve_repo,
    utc_now,
)
from tools.governance.gov50.util import resolve_codex_executable  # noqa: E402

GUARDIAN_STATE = "GUARDIAN_STATE.json"
GUARDIAN_EVENTS = "GUARDIAN_EVENTS.jsonl"
GUARDIAN_STOP_REQUEST = "GUARDIAN_STOP_REQUESTED"
STOP_REPORT = "SUPERVISOR_STOP_REPORT.json"
SUPERVISOR_STOP_REQUEST = "STOP_REQUESTED"
SUPERVISOR_PID = "supervisor.pid"
RUNTIME_STATE = "runtime_state.json"
HEARTBEAT = "heartbeat.json"
AUTO_RESUMABLE_STATUSES = {
    "MAX_RUNS_REACHED",
    "NEXT_TASK_READY",
    "RETRY_REQUIRED",
    "RETRYABLE_INFRASTRUCTURE",
    "SUPERVISOR_BUDGET_EXHAUSTED",
}
AUTO_RESUMABLE_ERRORS = {
    "WINDOWS_ACCESS_DENIED",
    "GOVERNANCE_LOCK_BUSY",
    "CONTROLLER_TIMEOUT",
    "GOVERNANCE_COMMIT_FAILURE",
}
MANUAL_INTERVENTION_STATUSES = {
    "EXTERNAL_BLOCKED",
    "POLICY_VIOLATION",
    "WORKTREE_UNSAFE",
    "CODEX_AUTH_REQUIRED",
    "PROGRAM_COMPLETE",
}


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def append_event(events_path: Path, event: str, **data: Any) -> None:
    events_path.parent.mkdir(parents=True, exist_ok=True)
    with events_path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(redact({"at": utc_now(), "event": event, **data}), ensure_ascii=False, separators=(",", ":")) + "\n")


def process_alive(pid: Any) -> bool:
    if not isinstance(pid, int) or pid <= 0:
        return False
    try:
        os.kill(pid, 0)
        return True
    except PermissionError:
        return True
    except (OSError, ProcessLookupError):
        return False


def age_seconds(value: Any) -> float | None:
    if not value:
        return None
    try:
        parsed = dt.datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        return max(0.0, (dt.datetime.now(dt.timezone.utc) - parsed).total_seconds())
    except ValueError:
        return None


def progress_signature(runtime_state: dict[str, Any] | None) -> str | None:
    progress = (runtime_state or {}).get("last_progress")
    if not progress:
        return None
    return hashlib.sha256(json.dumps(progress, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()


def report_error_category(report: dict[str, Any] | None) -> str | None:
    if not isinstance(report, dict):
        return None
    values: list[str] = []
    result = report.get("controller_result")
    if isinstance(result, dict):
        values.extend(str(result.get(key) or "") for key in ("error", "stderr", "stdout", "error_category"))
        execution = result.get("_execution")
        if isinstance(execution, dict):
            values.extend(str(execution.get(key) or "") for key in ("stderr", "stdout", "error_category"))
    values.extend(str(report.get(key) or "") for key in ("resolution", "error_fingerprint"))
    for value in values:
        category = classify_error_text(value)
        if category:
            return category
    return None


class Guardian:
    def __init__(self, repo: Path, *, python_executable: str | None = None):
        self.repo = resolve_repo(str(repo))
        self.runtime = self.repo / RUNTIME_DIR
        self.runtime.mkdir(parents=True, exist_ok=True)
        self.python_executable = python_executable or sys.executable
        self.supervisor_script = self.repo / "tools" / "supervisor" / "hhy_supervisor.py"
        self.events_path = self.runtime / GUARDIAN_EVENTS
        self.state_path = self.runtime / GUARDIAN_STATE

    def _runtime_files(self) -> dict[str, Any]:
        return {
            "runtime": read_json(self.runtime / RUNTIME_STATE) or {},
            "heartbeat": read_json(self.runtime / HEARTBEAT) or {},
            "pid": read_json(self.runtime / SUPERVISOR_PID) or {},
            "stop_report": read_json(self.runtime / STOP_REPORT),
        }

    def diagnose(self) -> dict[str, Any]:
        files = self._runtime_files()
        runtime_state = files["runtime"]
        pid_payload = files["pid"]
        pid = pid_payload.get("pid") or runtime_state.get("pid")
        heartbeat = files["heartbeat"]
        git_lock = self.repo / ".git" / "hhy-governance-v50.lock"
        git_status = None
        git_error = None
        try:
            proc = subprocess.run(
                ["git", "status", "--porcelain=v1", "-uall"],
                cwd=self.repo,
                text=True,
                capture_output=True,
                timeout=20,
                check=False,
            )
            git_status = proc.stdout.strip()
            git_error = proc.stderr.strip() or None
        except (OSError, subprocess.TimeoutExpired) as exc:
            git_error = str(exc)
        stop_report = files["stop_report"] if isinstance(files["stop_report"], dict) else None
        category = report_error_category(stop_report)
        governance: dict[str, Any] = {
            "project_status": None,
            "active_task": None,
            "active_task_status": None,
            "failed_bounded": False,
            "failed_tasks": [],
        }
        try:
            state = read_active_state(self.repo)
            project = state.get("project") or {}
            active_task = project.get("active_task")
            rows = state.get("tasks") or {}
            failed_tasks = [
                task_id for task_id, row in rows.items()
                if isinstance(row, dict) and row.get("status") == "FAILED_BOUNDED"
            ]
            governance = {
                "project_status": project.get("status"),
                "active_task": active_task,
                "active_task_status": (rows.get(active_task) or {}).get("status") if active_task else None,
                "failed_bounded": project.get("status") == "FAILED_BOUNDED" or bool(
                    active_task and (rows.get(active_task) or {}).get("status") == "FAILED_BOUNDED"
                ),
                "failed_tasks": failed_tasks[-20:],
            }
        except Exception as exc:
            governance["state_error"] = str(exc)
        runtime_status = runtime_state.get("status")
        supervisor_running = process_alive(pid) and runtime_status not in {"STOPPED", "IDLE"}
        return {
            "at": utc_now(),
            "supervisor": {
                "pid": pid,
                "running": supervisor_running,
                "stop_requested": (self.runtime / SUPERVISOR_STOP_REQUEST).is_file(),
                "runtime_status": runtime_status,
                "heartbeat_age_seconds": age_seconds(heartbeat.get("at")),
                "heartbeat_stale": bool(age_seconds(heartbeat.get("at")) is not None and age_seconds(heartbeat.get("at")) > 120),
            },
            "governance": governance,
            "stop_report": {
                "present": stop_report is not None,
                "status": stop_report.get("stop_status") if stop_report else None,
                "error_category": category,
                "auto_resumable": bool(
                    stop_report
                    and not governance.get("failed_bounded")
                    and (
                        stop_report.get("stop_status") in AUTO_RESUMABLE_STATUSES
                        or category in AUTO_RESUMABLE_ERRORS
                    )
                ),
            },
            "environment": {
                "git_lock_present": git_lock.is_file(),
                "git_worktree_dirty": bool(git_status),
                "git_error": git_error,
                "python": str(self.python_executable),
                "python_exists": Path(self.python_executable).is_file(),
                "codex_on_path": resolve_codex_executable() is not None,
                "codex_executable": resolve_codex_executable(),
            },
        }

    def _write_state(self, state: dict[str, Any]) -> dict[str, Any]:
        atomic_json(self.state_path, {"schema": "hhy.supervisor-guardian/v5.0", **state})
        return state

    def _current_state(self) -> dict[str, Any]:
        current = read_json(self.state_path)
        return current if isinstance(current, dict) else {
            "status": "STARTING",
            "retry_count": 0,
            "last_progress_signature": None,
            "last_action": None,
            "next_action": "正在进行首次诊断",
        }

    def _archive_stop_report(self, report: dict[str, Any]) -> str:
        history = self.runtime / "guardian-history"
        history.mkdir(parents=True, exist_ok=True)
        stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
        target = history / f"{stamp}-{report.get('stop_status', 'STOPPED')}.json"
        shutil.copy2(self.runtime / STOP_REPORT, target)
        markdown = self.runtime / "SUPERVISOR_STOP_REPORT.md"
        if markdown.is_file():
            shutil.copy2(markdown, target.with_suffix(".md"))
        (self.runtime / STOP_REPORT).unlink(missing_ok=True)
        markdown.unlink(missing_ok=True)
        append_event(self.events_path, "STOP_REPORT_ARCHIVED", status=report.get("stop_status"), path=str(target.relative_to(self.repo)))
        return str(target.relative_to(self.repo))

    def _start_supervisor(self, reason: str) -> dict[str, Any]:
        log_path = self.runtime / f"guardian-supervisor-{dt.datetime.now().strftime('%Y%m%d-%H%M%S')}.log"
        env = os.environ.copy()
        env["HHY_GOVERNANCE_ROLE"] = "ORCHESTRATOR"
        env["HHY_SUPERVISOR_GUARDIAN"] = "1"
        env["PYTHON"] = self.python_executable
        env["HHY_PYTHON"] = self.python_executable
        handle = log_path.open("a", encoding="utf-8")
        try:
            process = subprocess.Popen(
                [self.python_executable, str(self.supervisor_script), "--repo", str(self.repo)],
                cwd=self.repo,
                stdout=handle,
                stderr=subprocess.STDOUT,
                stdin=subprocess.DEVNULL,
                env=env,
                creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
            )
        finally:
            handle.close()
        append_event(self.events_path, "SUPERVISOR_AUTO_STARTED", pid=process.pid, reason=reason, log=str(log_path.relative_to(self.repo)))
        return {"pid": process.pid, "reason": reason, "log": str(log_path.relative_to(self.repo))}

    def _run_recovery_planner(self) -> dict[str, Any]:
        policy = self.repo / "governance" / "evidence" / "recovery" / "PROJECT_OWNER_STANDING_AUTHORIZATION.json"
        command = [
            self.python_executable,
            str(self.repo / "tools" / "governance" / "hhy_governance.py"),
            "auto-recover-failed",
            "--policy",
            str(policy),
        ]
        env = os.environ.copy()
        env["HHY_GOVERNANCE_ROLE"] = "ORCHESTRATOR"
        env["PYTHON"] = self.python_executable
        env["HHY_PYTHON"] = self.python_executable
        try:
            proc = subprocess.run(
                command,
                cwd=self.repo,
                text=True,
                capture_output=True,
                timeout=180,
                env=env,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            return {"status": "FAIL", "error": str(exc)}
        try:
            payload = json.loads(proc.stdout)
            if isinstance(payload, dict):
                payload.setdefault("process_exit_code", proc.returncode)
                return payload
        except json.JSONDecodeError:
            pass
        for line in reversed([line.strip() for line in proc.stdout.splitlines() if line.strip()]):
            try:
                payload = json.loads(line)
                if isinstance(payload, dict):
                    payload.setdefault("process_exit_code", proc.returncode)
                    return payload
            except json.JSONDecodeError:
                continue
        return {
            "status": "FAIL",
            "error": (proc.stderr or proc.stdout or "recovery planner returned no result")[-4000:],
            "process_exit_code": proc.returncode,
        }

    def once(self) -> dict[str, Any]:
        config = load_config(self.repo)
        guardian_config = config["guardian"]
        current = self._current_state()
        diagnosis = self.diagnose()
        signature = progress_signature(read_json(self.runtime / RUNTIME_STATE))
        if signature and signature != current.get("last_progress_signature"):
            current["retry_count"] = 0
        current["last_progress_signature"] = signature
        current["diagnosis"] = diagnosis
        current["at"] = utc_now()

        if not guardian_config.get("enabled", True):
            current.update({"status": "DISABLED", "next_action": "Guardian 已关闭，不会自动启动 Supervisor"})
            return self._write_state(current)
        if (self.repo / PROGRAM_COMPLETE).is_file():
            current.update({"status": "COMPLETE", "next_action": "项目已完成，不再启动开发"})
            return self._write_state(current)
        if diagnosis["supervisor"]["running"]:
            current.pop("error", None)
            current.pop("blocked_reason", None)
            current.update({"status": "ONLINE", "next_action": "持续监控 Supervisor 心跳和运行结果"})
            return self._write_state(current)
        if not guardian_config.get("auto_start_supervisor", True):
            current.update({"status": "WAITING", "next_action": "自动启动已关闭，等待控制中心手动启动"})
            return self._write_state(current)

        stop_report = read_json(self.runtime / STOP_REPORT)
        stop_status = stop_report.get("stop_status") if isinstance(stop_report, dict) else None
        error_category = diagnosis["stop_report"].get("error_category")
        auto_resumable = diagnosis["stop_report"].get("auto_resumable")
        manual_boundary = bool(diagnosis["supervisor"].get("stop_requested")) or stop_status in MANUAL_INTERVENTION_STATUSES
        internal_auto = bool(guardian_config.get("auto_handle_internal_failures", True)) and not manual_boundary
        if diagnosis["governance"].get("failed_bounded"):
            if guardian_config.get("auto_create_recovery_task", True):
                planned = self._run_recovery_planner()
                if planned.get("status") == "READY":
                    archived = self._archive_stop_report(stop_report) if stop_report else None
                    current.update({
                        "status": "RECOVERY_PLANNED",
                        "last_action": f"已自动创建修复任务 {planned.get('task_id')}",
                        "next_action": "等待冷却后自动启动 Supervisor",
                        "recovery": planned,
                        "archived_stop_report": archived,
                    })
                    self._write_state(current)
                    delay = int(guardian_config.get("retry_cooldown_seconds", 60))
                    if delay:
                        time.sleep(delay)
                    try:
                        started = self._start_supervisor(f"自动恢复任务 {planned.get('task_id')}")
                        current.update({
                            "status": "STARTED",
                            "next_action": "自动修复任务已启动，Guardian 将继续监控",
                            "started": started,
                        })
                        append_event(self.events_path, "AUTOMATIC_RECOVERY_TASK_STARTED", task_id=planned.get("task_id"))
                    except Exception as exc:
                        current.update({"status": "START_FAILED", "next_action": "修复任务已创建，但 Supervisor 启动失败", "error": str(exc)})
                        append_event(self.events_path, "AUTOMATIC_RECOVERY_START_FAILED", error=str(exc), task_id=planned.get("task_id"))
                    return self._write_state(current)
                planner_status = planned.get("status") or "RECOVERY_PLANNER_FAILED"
                if planner_status == "RECOVERY_LIMIT_REACHED":
                    current.update({
                        "status": "ATTENTION_REQUIRED",
                        "next_action": "自动修复代数达到安全上限，已保留全部证据等待项目所有者决定",
                        "blocked_reason": planner_status,
                        "recovery": planned,
                    })
                    append_event(self.events_path, "OWNER_ATTENTION_REQUIRED", status=planner_status, error_category=error_category)
                else:
                    current.update({
                        "status": "RECOVERY_PLANNER_RETRYING",
                        "next_action": "修复任务规划暂时失败，Guardian 将自动重试，不需要手动授权",
                        "blocked_reason": planned.get("error") or planned.get("reason") or planner_status,
                        "recovery": planned,
                        "last_action": "RECOVERY_PLANNER_RETRYING",
                    })
                    append_event(self.events_path, "RECOVERY_PLANNER_RETRYING", status=planner_status, error_category=error_category)
                return self._write_state(current)
            current.update({
                "status": "ATTENTION_REQUIRED",
                "next_action": "已达到失败边界，自动修复任务功能已关闭",
                "blocked_reason": "FAILED_BOUNDED",
            })
            return self._write_state(current)
        if (
            stop_status == "FAILED_BOUNDED"
            and diagnosis["governance"].get("active_task")
            and diagnosis["governance"].get("active_task_status") == "READY"
        ):
            archived = self._archive_stop_report(stop_report) if stop_report else None
            current.update({
                "status": "RECOVERY_PLANNED",
                "last_action": f"检测到新修复任务 {diagnosis['governance']['active_task']}",
                "next_action": "等待冷却后自动启动 Supervisor",
                "archived_stop_report": archived,
            })
            self._write_state(current)
            delay = int(guardian_config.get("retry_cooldown_seconds", 60))
            if delay:
                time.sleep(delay)
            try:
                started = self._start_supervisor(f"自动恢复任务 {diagnosis['governance']['active_task']}")
                current.update({
                    "status": "STARTED",
                    "next_action": "自动修复任务已启动，Guardian 将继续监控",
                    "started": started,
                })
                append_event(
                    self.events_path,
                    "AUTOMATIC_RECOVERY_TASK_STARTED",
                    task_id=diagnosis["governance"]["active_task"],
                )
            except Exception as exc:
                current.update({"status": "START_FAILED", "next_action": "修复任务已存在，但 Supervisor 启动失败", "error": str(exc)})
                append_event(self.events_path, "AUTOMATIC_RECOVERY_START_FAILED", error=str(exc))
            return self._write_state(current)
        if stop_report and not (auto_resumable or internal_auto):
            current.update({
                "status": "ATTENTION_REQUIRED",
                "next_action": "当前问题涉及外部依赖或安全边界，已保留停止证据等待处理",
                "blocked_reason": stop_status or "UNKNOWN_STATUS",
            })
            append_event(self.events_path, "OWNER_ATTENTION_REQUIRED", status=stop_status, error_category=error_category)
            return self._write_state(current)
        limit = int(guardian_config.get("auto_retry_limit", 20))
        retries = int(current.get("retry_count") or 0)
        if retries >= limit:
            current.update({
                "status": "RETRY_LIMIT_REACHED",
                "next_action": f"自动恢复已达到 {limit} 次上限，保留证据等待处理",
            })
            append_event(self.events_path, "AUTO_RETRY_LIMIT_REACHED", limit=limit, status=stop_status, error_category=error_category)
            return self._write_state(current)
        if stop_report and guardian_config.get("auto_archive_recoverable_stop", True):
            archived = self._archive_stop_report(stop_report)
        else:
            archived = None
        delay = int(guardian_config.get("retry_cooldown_seconds", 60))
        retries += 1
        reason = f"自动恢复第 {retries}/{limit} 次"
        if stop_status:
            reason += f"：{stop_status}"
        if error_category:
            reason += f"（{error_category}）"
        current.update({
            "status": "RECOVERING",
            "retry_count": retries,
            "last_action": reason,
            "next_action": f"{delay} 秒后自动启动 Supervisor",
            "archived_stop_report": archived,
        })
        self._write_state(current)
        if delay:
            time.sleep(delay)
        try:
            started = self._start_supervisor(reason)
            current.pop("error", None)
            current.update({"status": "STARTED", "next_action": "Supervisor 已自动启动，Guardian 将继续监控", "started": started})
        except Exception as exc:
            current.update({"status": "START_FAILED", "next_action": "自动启动失败，保留诊断结果等待处理", "error": str(exc)})
            append_event(self.events_path, "SUPERVISOR_AUTO_START_FAILED", error=str(exc))
        return self._write_state(current)

    def daemon(self) -> int:
        while True:
            try:
                if (self.runtime / GUARDIAN_STOP_REQUEST).is_file():
                    (self.runtime / GUARDIAN_STOP_REQUEST).unlink(missing_ok=True)
                    self._write_state({"status": "STOPPED", "at": utc_now(), "next_action": "Guardian 已按控制中心请求停止"})
                    append_event(self.events_path, "GUARDIAN_STOPPED")
                    return 0
                result = self.once()
                poll = max(3, int(load_config(self.repo)["guardian"]["poll_seconds"]))
                print(json.dumps(redact(result), ensure_ascii=False), flush=True)
                time.sleep(poll)
            except KeyboardInterrupt:
                return 0
            except Exception as exc:
                state = self._write_state({
                    "status": "GUARDIAN_ERROR",
                    "at": utc_now(),
                    "next_action": "Guardian 自身发生错误，等待下一次检查",
                    "error": str(exc),
                })
                append_event(self.events_path, "GUARDIAN_ERROR", error=str(exc))
                print(json.dumps(redact(state), ensure_ascii=False), flush=True)
                time.sleep(10)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="HHY Governance V5.0 always-on Guardian")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--daemon", action="store_true")
    parser.add_argument("--diagnose", action="store_true")
    args = parser.parse_args(argv)
    try:
        guardian = Guardian(Path(args.repo))
        if args.diagnose:
            print(json.dumps(redact(guardian.diagnose()), ensure_ascii=False, indent=2))
            return 0
        if args.daemon:
            return guardian.daemon()
        result = guardian.once()
        print(json.dumps(redact(result), ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(json.dumps({"schema": "hhy.supervisor-guardian-error/v5.0", "status": "FAIL", "error": str(exc)}, ensure_ascii=False, indent=2))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
