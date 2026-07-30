#!/usr/bin/env python3
"""Local loopback control center for the Governance V5.0 Supervisor."""
from __future__ import annotations

import argparse
import copy
import json
import os
import subprocess
import sys
import threading
import urllib.parse
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

import yaml
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.supervisor.hhy_supervisor import (  # noqa: E402
    AUTO_CONTINUE,
    CONFIG_SCHEMA,
    PROGRAM_COMPLETE,
    RUNTIME_DIR,
    STOP_REQUEST,
    atomic_text,
    load_config,
    redact,
    read_active_state,
    resolve_repo,
)
from tools.governance.gov50.tasks import load_task_specs  # noqa: E402

CONTROL_CENTER_DIR = Path(__file__).resolve().parent / "control_center"
TASK_NAME = "HHY-Governance-V5-Supervisor"
LOG_NAMES = {
    "events": "events.jsonl",
    "batches": "batches.jsonl",
    "stop-report": "SUPERVISOR_STOP_REPORT.md",
}
ACTION_NAMES = {
    "dry-run",
    "simulate",
    "trial",
    "start",
    "stop",
    "install-task",
    "uninstall-task",
    "clear-recoverable",
}
CONFIG_FIELDS = {
    "batch": {
        "max_runs_per_batch": {"label": "每批最多执行次数", "unit": "次", "type": "integer", "min": 1, "max": 20},
        "max_batches_per_session": {"label": "单次启动最多批次", "unit": "批", "type": "integer", "min": 1, "max": 10},
        "cooldown_seconds": {"label": "批次之间等待", "unit": "秒", "type": "integer", "min": 60, "max": 86400},
    },
    "runtime": {
        "max_total_hours": {"label": "本次启动最长运行", "unit": "小时", "type": "number", "min": 0.25, "max": 8},
        "max_task_minutes": {"label": "单次任务最长运行", "unit": "分钟", "type": "integer", "min": 1, "max": 480},
        "heartbeat_seconds": {"label": "状态检查间隔", "unit": "秒", "type": "integer", "min": 1, "max": 3600},
        "process_shutdown_grace_seconds": {"label": "停止前等待任务收尾", "unit": "秒", "type": "integer", "min": 1, "max": 3600},
    },
    "no_progress": {
        "repeated_fingerprint_limit": {"label": "相同进展连续几次后停止", "unit": "次", "type": "integer", "min": 1, "max": 10},
        "repeated_stop_status_limit": {"label": "相同停止结果连续几次后停止", "unit": "次", "type": "integer", "min": 2, "max": 10},
    },
    "recovery": {
        "restart_after_windows_login": {"label": "Windows 登录后自动恢复", "unit": "", "type": "boolean"},
        "startup_delay_seconds": {"label": "登录后等待再启动", "unit": "秒", "type": "integer", "min": 0, "max": 86400},
    },
}
CONFIG_GROUP_LABELS = {
    "batch": "任务批次",
    "runtime": "运行时间",
    "no_progress": "安全停止",
    "recovery": "Windows 自动恢复",
}
STATUS_LABELS = {
    "PASS": "检查通过",
    "DRY_RUN": "检查通过，未启动任务",
    "RUNNING": "正在运行",
    "STOPPED": "已停止",
    "READY": "已准备",
    "ACTIVE": "当前生效",
    "MAX_RUNS_REACHED": "达到批次次数上限",
    "NEXT_TASK_READY": "下一步任务已准备",
    "OWNER_ACTION_REQUIRED": "需要项目所有者处理",
    "CANDIDATE_REQUIRED": "需要候选证据",
    "FAILED_BOUNDED": "已停止，达到失败边界",
    "DOCTOR_FAILED": "环境检查未通过",
    "WORKTREE_UNSAFE": "工作区不安全",
    "UNKNOWN_STATUS": "未知状态，已停止",
    "NO_PROGRESS_DETECTED": "检测到没有进展",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def tail_text(path: Path, limit: int = 16000) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")[-limit:]
    except OSError:
        return ""


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


def status_label(value: Any) -> str:
    text = str(value or "UNKNOWN_STATUS")
    return STATUS_LABELS.get(text, text.replace("_", " ").title())


def status_level(value: Any) -> str:
    text = str(value or "")
    if text in {"PASS", "DRY_RUN", "READY", "ACTIVE", "RUNNING", "TASK_DONE"}:
        return "good"
    if text in {"STOPPED", "MAX_RUNS_REACHED", "NEXT_TASK_READY", "INTERRUPTED"}:
        return "warn"
    if text in {"FAILED_BOUNDED", "PROGRAM_COMPLETE", "UNKNOWN_STATUS", "DOCTOR_FAILED", "WORKTREE_UNSAFE", "NO_PROGRESS_DETECTED"}:
        return "stop"
    return "info"


def friendly_item(*, at: Any, title: str, detail: str, status: Any = None, level: str | None = None, actions: list[str] | None = None) -> dict[str, Any]:
    return {
        "at": at or "-",
        "title": title,
        "detail": detail,
        "status": status_label(status) if status else "",
        "level": level or status_level(status),
        "actions": actions or [],
    }


def editable_fields() -> list[dict[str, Any]]:
    rows = []
    for group, fields in CONFIG_FIELDS.items():
        for key, definition in fields.items():
            rows.append({"group": group, "group_label": CONFIG_GROUP_LABELS[group], "key": key, **definition})
    return rows


class ControlCenter:
    def __init__(self, repo: Path, *, python_executable: str | None = None):
        self.repo = resolve_repo(str(repo))
        self.python_executable = python_executable or sys.executable
        self.runtime = self.repo / RUNTIME_DIR
        self.supervisor_script = self.repo / "tools" / "supervisor" / "hhy_supervisor.py"
        self.status_script = self.repo / "tools" / "supervisor" / "supervisor_status.py"
        self.windows_dir = self.repo / "tools" / "supervisor" / "windows"
        self.config_path = self.repo / "tools" / "supervisor" / "supervisor_config.yaml"

    def configuration(self) -> dict[str, Any]:
        try:
            values = load_config(self.repo)
        except Exception as exc:
            return {"status": "FAIL", "error": str(exc), "editable": editable_fields()}
        return {
            "status": "PASS",
            "schema": "hhy.supervisor-control-center-config/v5.0",
            "values": redact(values),
            "editable": editable_fields(),
            "applies": "保存后下一次启动 Supervisor 生效；当前运行中的进程不改变。",
        }

    def update_configuration(self, values: Any) -> dict[str, Any]:
        if not isinstance(values, dict):
            return {"status": "FAIL", "error": "配置格式无效"}
        try:
            current = load_config(self.repo)
        except Exception as exc:
            return {"status": "FAIL", "error": str(exc)}
        candidate = copy.deepcopy(current)
        for group, fields in CONFIG_FIELDS.items():
            incoming = values.get(group, {})
            if not isinstance(incoming, dict):
                return {"status": "FAIL", "error": f"{CONFIG_GROUP_LABELS[group]}配置格式无效"}
            for key, definition in fields.items():
                if key not in incoming:
                    continue
                value = incoming[key]
                kind = definition["type"]
                if kind == "boolean":
                    valid_type = type(value) is bool
                elif kind == "integer":
                    valid_type = type(value) is int
                else:
                    valid_type = type(value) in {int, float} and not isinstance(value, bool)
                if not valid_type:
                    return {"status": "FAIL", "error": f"{definition['label']}必须是{('整数' if kind == 'integer' else '数字')}"}
                if kind != "boolean" and not (definition["min"] <= value <= definition["max"]):
                    return {"status": "FAIL", "error": f"{definition['label']}必须在 {definition['min']} 到 {definition['max']} 之间"}
                candidate.setdefault(group, {})[key] = value
        max_task = int(candidate["runtime"]["max_task_minutes"])
        max_total = float(candidate["runtime"]["max_total_hours"])
        if max_task > max_total * 60:
            return {"status": "FAIL", "error": "单次任务最长运行时间不能超过本次启动最长运行时间"}
        schema = json.loads((self.repo / CONFIG_SCHEMA).read_text(encoding="utf-8"))
        errors = sorted(Draft202012Validator(schema).iter_errors(candidate), key=lambda error: list(error.path))
        if errors:
            return {"status": "FAIL", "error": "配置校验失败：" + "; ".join(error.message for error in errors)}
        atomic_text(self.config_path, yaml.safe_dump(candidate, allow_unicode=True, sort_keys=False))
        return self.configuration()

    def _powershell(self, script: Path, *extra: str) -> dict[str, Any]:
        command = [
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(script),
            *extra,
        ]
        try:
            proc = subprocess.run(
                command,
                cwd=self.repo,
                text=True,
                capture_output=True,
                timeout=30,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            return {"status": "FAIL", "exit_code": 1, "error": str(exc)}
        return {
            "status": "PASS" if proc.returncode == 0 else "FAIL",
            "exit_code": proc.returncode,
            "stdout": proc.stdout[-4000:],
            "stderr": proc.stderr[-4000:],
        }

    def _task_scheduler(self) -> dict[str, Any]:
        if os.name != "nt":
            return {"installed": False, "status": "UNSUPPORTED_PLATFORM"}
        command = (
            "$t=Get-ScheduledTask -TaskName 'HHY-Governance-V5-Supervisor' "
            "-TaskPath '\\' -ErrorAction SilentlyContinue; "
            "if($null -eq $t){ @{installed=$false} | ConvertTo-Json -Compress } "
            "else { $i=Get-ScheduledTaskInfo -TaskName 'HHY-Governance-V5-Supervisor' "
            "-TaskPath '\\'; @{installed=$true;state=[string]$t.State;"
            "last_run=[string]$i.LastRunTime;last_result=[int]$i.LastTaskResult} | ConvertTo-Json -Compress }"
        )
        try:
            proc = subprocess.run(
                ["powershell.exe", "-NoProfile", "-Command", command],
                cwd=self.repo,
                text=True,
                capture_output=True,
                timeout=10,
                check=False,
            )
            payload = json.loads(proc.stdout.strip() or "{}")
            return redact(payload) if isinstance(payload, dict) else {"installed": False, "status": "INVALID"}
        except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError) as exc:
            return {"installed": False, "status": "UNAVAILABLE", "error": str(exc)}

    def _friendly_event(self, entry: dict[str, Any]) -> dict[str, Any]:
        event = str(entry.get("event") or "")
        batch = entry.get("batch")
        if event == "BATCH_STARTED":
            return friendly_item(
                at=entry.get("at"),
                title="开始执行一批任务",
                detail=f"第 {batch or '-'} 批已开始，Supervisor 正在等待治理控制器结果。",
                status="RUNNING",
            )
        if event == "BATCH_FINISHED":
            mapped = entry.get("mapped_status")
            return friendly_item(
                at=entry.get("at"),
                title="一批任务执行完成",
                detail=f"第 {batch or '-'} 批已完成；下一步：{status_label(mapped)}。",
                status=mapped,
            )
        if event == "BATCH_INTERRUPTED":
            return friendly_item(
                at=entry.get("at"),
                title="任务批次被中断",
                detail="本批次没有被确认完成，Supervisor 会保持停止并等待重新检查。",
                status="INTERRUPTED",
            )
        if event == "STOPPED":
            status = entry.get("status")
            detail = "请查看停止状态中的处理建议，不要直接重复启动。"
            if status == "WORKTREE_UNSAFE":
                detail = "权威工作区有未提交改动，治理系统拒绝进入真实开发。"
            return friendly_item(
                at=entry.get("at"),
                title=f"Supervisor 已停止：{status_label(status)}",
                detail=detail,
                status=status,
            )
        if event == "HEARTBEAT_WRITE_FAILED":
            return friendly_item(
                at=entry.get("at"),
                title="状态心跳写入失败",
                detail="Supervisor 无法正常记录心跳，属于需要检查的安全信号。",
                status="UNKNOWN_STATUS",
            )
        return friendly_item(
            at=entry.get("at"),
            title="Supervisor 记录了一条事件",
            detail=f"事件类型：{event or '未知'}。",
            level="info",
        )

    def _friendly_batch(self, entry: dict[str, Any]) -> dict[str, Any]:
        mapped = entry.get("mapped_status")
        runs = entry.get("controller", {}).get("runs") if isinstance(entry.get("controller"), dict) else None
        before = entry.get("before") or {}
        after = entry.get("after") or {}
        detail = f"第 {entry.get('batch') or '-'} 批；本批执行 {runs if runs is not None else '-'} 次。"
        if mapped == "WORKTREE_UNSAFE":
            detail = "权威工作区存在未提交改动，治理控制器为保护项目安全拒绝启动真实开发。"
        if before.get("task_id") and after.get("task_id") and before.get("task_id") != after.get("task_id"):
            detail += f"任务从 {before['task_id']} 进入 {after['task_id']}。"
        else:
            detail += f"当前任务：{after.get('task_id') or before.get('task_id') or '未知'}。"
        return friendly_item(
            at=entry.get("at"),
            title=f"批次结果：{status_label(mapped)}",
            detail=detail,
            status=mapped,
        )

    def _friendly_stop_report(self, report: dict[str, Any]) -> dict[str, Any]:
        actions = report.get("owner_actions") if isinstance(report.get("owner_actions"), list) else []
        stop_status = report.get("stop_status")
        if stop_status == "WORKTREE_UNSAFE":
            detail = "权威工作区存在未提交改动。先提交或整理本次 Supervisor 控制中心文件，再重新执行 Dry-run。"
            actions = actions or ["不要清除停止报告；先让仓库回到干净状态，再重新执行 Dry-run"]
        else:
            detail = str(report.get("resolution") or "当前没有额外处理说明。")
        return friendly_item(
            at=report.get("stopped_at"),
            title=f"停止报告：{status_label(stop_status)}",
            detail=detail,
            status=stop_status,
            actions=[str(action) for action in actions],
        )

    def _log_summary(self, items: list[dict[str, Any]], name: str) -> dict[str, Any]:
        last = items[-1] if items else None
        attention = sum(1 for item in items if item.get("level") in {"warn", "stop"})
        return {
            "name": name,
            "count": len(items),
            "attention_count": attention,
            "last_at": last.get("at") if last else None,
            "headline": last.get("title") if last else "暂无运行记录",
        }

    def status(self) -> dict[str, Any]:
        runtime_state = read_json(self.runtime / "runtime_state.json")
        heartbeat = read_json(self.runtime / "heartbeat.json")
        pid_file = read_json(self.runtime / "supervisor.pid")
        stop_report = read_json(self.runtime / "SUPERVISOR_STOP_REPORT.json")
        try:
            state = read_active_state(self.repo)
            project = state.get("project") or {}
            project = dict(project)
            project["revision"] = state.get("revision")
            task_id = project.get("active_task")
            task = dict((state.get("tasks") or {}).get(task_id) or {}) if task_id else {}
            task.update(load_task_specs(self.repo).get(task_id) or {})
            state_view = {
                "project": redact(project),
                "task": redact(task or {}),
            }
            state_error = None
        except Exception as exc:
            state_view = None
            state_error = str(exc)
        pid = (pid_file or {}).get("pid") if isinstance(pid_file, dict) else None
        return {
            "schema": "hhy.supervisor-control-center-status/v5.0",
            "at": utc_now(),
            "repo": str(self.repo),
            "runtime": {
                "state": redact(runtime_state),
                "heartbeat": redact(heartbeat),
                "pid": redact(pid_file),
                "pid_alive": process_alive(pid),
                "stop_requested": (self.runtime / STOP_REQUEST).is_file(),
                "program_complete": (self.repo / PROGRAM_COMPLETE).is_file(),
                "stop_report": redact(stop_report),
            },
            "governance": state_view,
            "state_error": state_error,
            "task_scheduler": self._task_scheduler(),
            "configuration": self.configuration(),
        }

    def _run_controller(self, *args: str) -> dict[str, Any]:
        try:
            proc = subprocess.run(
                [self.python_executable, str(self.supervisor_script), "--repo", str(self.repo), *args],
                cwd=self.repo,
                text=True,
                capture_output=True,
                timeout=120,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            return {"status": "FAIL", "exit_code": 1, "error": str(exc)}
        output = proc.stdout.strip()
        try:
            payload = json.loads(output)
        except json.JSONDecodeError:
            payload = {"status": "FAIL", "stdout": output[-4000:], "stderr": proc.stderr[-4000:]}
        payload["process_exit_code"] = proc.returncode
        payload = redact(payload)
        if args and args[0] == "--dry-run":
            payload["friendly"] = {
                "title": "Dry-run 检查完成",
                "detail": "只检查环境和治理状态，没有启动真实任务。" if payload.get("status") == "DRY_RUN" else "Dry-run 未通过，请先处理错误。",
                "level": "good" if payload.get("status") == "DRY_RUN" else "stop",
            }
        elif args and args[0] == "--simulate":
            payload["friendly"] = {
                "title": "Simulate 检查完成",
                "detail": "状态映射检查完成，没有调用 Codex，也没有修改治理状态。" if payload.get("status") == "PASS" else "Simulate 未通过，请先处理错误。",
                "level": "good" if payload.get("status") == "PASS" else "stop",
            }
        return payload

    def _spawn_controller(self, *, trial: bool) -> dict[str, Any]:
        self.runtime.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        log_path = self.runtime / f"control-center-{stamp}.log"
        args = [self.python_executable, str(self.supervisor_script), "--repo", str(self.repo)]
        if trial:
            args.append("--trial")
        try:
            handle = log_path.open("a", encoding="utf-8")
            process = subprocess.Popen(
                args,
                cwd=self.repo,
                stdout=handle,
                stderr=subprocess.STDOUT,
                creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
            )
            handle.close()
        except OSError as exc:
            try:
                handle.close()
            except (UnboundLocalError, OSError):
                pass
            return {"status": "FAIL", "error": str(exc)}
        return {
            "status": "STARTED",
            "pid": process.pid,
            "mode": "trial" if trial else "bounded",
            "log": str(log_path.relative_to(self.repo)),
        }

    def action(self, action: str, *, confirmed: bool = False, authorization: str | None = None) -> dict[str, Any]:
        if action not in ACTION_NAMES:
            return {"status": "FAIL", "error": f"unsupported action: {action}"}
        if action in {"trial", "start", "install-task", "uninstall-task", "clear-recoverable"} and not confirmed:
            return {"status": "CONFIRMATION_REQUIRED", "action": action}
        if action == "dry-run":
            return self._run_controller("--dry-run")
        if action == "simulate":
            return self._run_controller("--simulate")
        if action == "trial":
            return self._spawn_controller(trial=True)
        if action == "start":
            return self._spawn_controller(trial=False)
        if action == "stop":
            return self._powershell(self.windows_dir / "stop_supervisor.ps1", "-RepoPath", str(self.repo))
        if action == "install-task":
            return self._powershell(self.windows_dir / "install_supervisor_task.ps1", "-RepoPath", str(self.repo))
        if action == "uninstall-task":
            return self._powershell(self.windows_dir / "uninstall_supervisor_task.ps1")
        if action == "clear-recoverable":
            if not authorization:
                return {"status": "FAIL", "error": "authorization path is required"}
            auth_path = Path(authorization).expanduser().resolve()
            if not auth_path.is_file():
                return {"status": "FAIL", "error": "authorization file does not exist"}
            try:
                proc = subprocess.run(
                    [
                        self.python_executable,
                        str(self.status_script),
                        "--repo",
                        str(self.repo),
                        "--clear-recoverable",
                        "--authorization",
                        str(auth_path),
                    ],
                    cwd=self.repo,
                    text=True,
                    capture_output=True,
                    timeout=30,
                    check=False,
                )
                payload = json.loads(proc.stdout or "{}")
                payload["process_exit_code"] = proc.returncode
                return redact(payload)
            except (OSError, json.JSONDecodeError) as exc:
                return {"status": "FAIL", "error": str(exc)}
        return {"status": "FAIL", "error": "unreachable action"}

    def logs(self, name: str, tail: int = 200) -> dict[str, Any]:
        filename = LOG_NAMES.get(name)
        if not filename:
            return {"status": "FAIL", "error": "unsupported log name"}
        path = self.runtime / filename
        if name == "stop-report":
            report = read_json(self.runtime / "SUPERVISOR_STOP_REPORT.json")
            items = [self._friendly_stop_report(report)] if isinstance(report, dict) else []
            return {
                "status": "PASS",
                "name": name,
                "summary": self._log_summary(items, name),
                "items": items,
            }
        lines = tail_text(path, 32000).splitlines()[-max(1, min(tail, 500)):]
        parsed: list[dict[str, Any]] = []
        for line in lines:
            try:
                entry = json.loads(line)
                if isinstance(entry, dict):
                    parsed.append(self._friendly_event(entry) if name == "events" else self._friendly_batch(entry))
            except json.JSONDecodeError:
                continue
        return {
            "status": "PASS",
            "name": name,
            "summary": self._log_summary(parsed, name),
            "items": parsed,
        }

    def chat(self) -> dict[str, Any]:
        runtime_state = read_json(self.runtime / "runtime_state.json") or {}
        events = self.logs("events", 200).get("items") or []
        messages: list[dict[str, Any]] = []
        for item in events:
            title = item.get("title") or "Supervisor 事件"
            detail = item.get("detail") or ""
            role = "Supervisor"
            if "批次" in title or "任务" in title:
                role = "治理控制器"
            if item.get("level") == "stop":
                role = "安全守卫"
            messages.append({
                "at": item.get("at"),
                "role": role,
                "title": title,
                "detail": detail,
                "level": item.get("level", "info"),
            })
        if runtime_state.get("status") == "RUNNING":
            messages.append({
                "at": runtime_state.get("updated_at") or utc_now(),
                "role": "Supervisor",
                "title": "正在观察开发过程",
                "detail": f"当前第 {runtime_state.get('batch_index') or 1} 批；控制中心会持续刷新状态。",
                "level": "good",
            })
        if not messages:
            messages = [{
                "at": utc_now(),
                "role": "控制中心",
                "title": "等待开始",
                "detail": "尚未启动真实开发。Dry-run 和 Simulate 不会产生 Codex Worker 对话。",
                "level": "info",
            }]
        return {
            "status": "PASS",
            "mode": "lifecycle-observer",
            "notice": "这是开发过程观摩视图，不替代 Codex 原生聊天窗口。",
            "messages": messages,
        }


class Handler(BaseHTTPRequestHandler):
    server_version = "HHY-Supervisor-Control-Center/5.0"

    @property
    def center(self) -> ControlCenter:
        return self.server.control_center  # type: ignore[attr-defined]

    def _send(self, status: int, payload: Any, content_type: str = "application/json; charset=utf-8") -> None:
        body = payload if isinstance(payload, bytes) else (
            json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
            if content_type.startswith("application/json")
            else str(payload).encode("utf-8")
        )
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path in {"/", "/index.html"}:
            self._send(200, (CONTROL_CENTER_DIR / "index.html").read_bytes(), "text/html; charset=utf-8")
            return
        if parsed.path == "/assets/control-center.css":
            self._send(200, (CONTROL_CENTER_DIR / "control-center.css").read_bytes(), "text/css; charset=utf-8")
            return
        if parsed.path == "/assets/control-center.js":
            self._send(200, (CONTROL_CENTER_DIR / "control-center.js").read_bytes(), "text/javascript; charset=utf-8")
            return
        if parsed.path == "/api/status":
            self._send(200, self.center.status())
            return
        if parsed.path == "/api/config":
            self._send(200, self.center.configuration())
            return
        if parsed.path == "/api/chat":
            self._send(200, self.center.chat())
            return
        if parsed.path == "/api/logs":
            params = urllib.parse.parse_qs(parsed.query)
            try:
                tail = int(params.get("tail", ["200"])[0])
            except ValueError:
                tail = 200
            self._send(200, self.center.logs(params.get("name", ["events"])[0], tail))
            return
        self._send(404, {"status": "FAIL", "error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path not in {"/api/action", "/api/config"}:
            self._send(404, {"status": "FAIL", "error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length) or b"{}")
            if parsed.path == "/api/config":
                result = self.center.update_configuration(payload.get("values"))
                self._send(200 if result.get("status") != "FAIL" else 400, result)
                return
            result = self.center.action(
                str(payload.get("action", "")),
                confirmed=bool(payload.get("confirmed")),
                authorization=payload.get("authorization"),
            )
            self._send(200 if result.get("status") != "FAIL" else 400, result)
        except (ValueError, json.JSONDecodeError) as exc:
            self._send(400, {"status": "FAIL", "error": str(exc)})

    def log_message(self, format: str, *args: Any) -> None:
        return


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address: tuple[str, int], center: ControlCenter):
        super().__init__(address, Handler)
        self.control_center = center


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Local HHY Supervisor control center")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--bind", default="127.0.0.1", choices=["127.0.0.1", "localhost", "::1"])
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--open", action="store_true")
    args = parser.parse_args(argv)
    center = ControlCenter(Path(args.repo))
    server = Server((args.bind, args.port), center)
    url = f"http://{args.bind}:{args.port}/"
    print(json.dumps({"status": "READY", "url": url, "repo": str(center.repo)}, ensure_ascii=False), flush=True)
    if args.open:
        import webbrowser
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
