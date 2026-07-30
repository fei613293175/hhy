#!/usr/bin/env python3
"""Read-only Supervisor status and explicit owner-authorized stop clearing."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys

import yaml

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.supervisor.hhy_supervisor import RUNTIME_DIR, redact, resolve_repo, read_active_state  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Read HHY Supervisor status")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--clear-recoverable", action="store_true")
    parser.add_argument("--authorization")
    args = parser.parse_args(argv)
    repo = resolve_repo(args.repo)
    runtime = repo / RUNTIME_DIR
    if args.clear_recoverable:
        if not args.authorization:
            raise SystemExit("--authorization is required")
        auth_path = Path(args.authorization).resolve()
        data = json.loads(auth_path.read_text(encoding="utf-8"))
        if data.get("status") != "APPROVED" or data.get("authorized_by") != "PROJECT_OWNER":
            raise SystemExit("owner authorization is required")
        stop = runtime / "SUPERVISOR_STOP_REPORT.json"
        if stop.is_file():
            report = json.loads(stop.read_text(encoding="utf-8"))
            if report.get("stop_status") in {"PROGRAM_COMPLETE", "FAILED_BOUNDED", "POLICY_VIOLATION"}:
                raise SystemExit("permanent stop status cannot be cleared")
            stop.unlink()
        (runtime / "STOP_REQUESTED").unlink(missing_ok=True)
        print(json.dumps({"schema": "hhy.supervisor-clear/v5.0", "status": "CLEARED", "authorized_by": "PROJECT_OWNER"}, ensure_ascii=False, indent=2))
        return 0
    result: dict[str, object] = {"schema": "hhy.supervisor-status/v5.0", "pid": os.getpid(), "runtime": {}}
    for name in ("supervisor.pid", "runtime_state.json", "heartbeat.json", "SUPERVISOR_STOP_REPORT.json"):
        path = runtime / name
        if path.is_file():
            try: result[Path(name).stem] = redact(json.loads(path.read_text(encoding="utf-8")))
            except Exception: result[Path(name).stem] = {"status": "INVALID_JSON"}
    result["program_complete"] = (runtime / "PROGRAM_COMPLETE.lock").is_file()
    result["stop_requested"] = (runtime / "STOP_REQUESTED").is_file()
    try:
        result["state"] = {"project": read_active_state(repo).get("project")}
    except Exception as exc:
        result["state_error"] = str(exc)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
