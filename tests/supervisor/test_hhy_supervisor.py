from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

import pytest
import yaml
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
SUPERVISOR = ROOT / "tools" / "supervisor" / "hhy_supervisor.py"
STATUS = ROOT / "tools" / "supervisor" / "supervisor_status.py"

sys.path.insert(0, str(ROOT))
from tools.supervisor.hhy_supervisor import (  # noqa: E402
    AUTO_CONTINUE,
    MANDATORY_STOP,
    SingleInstanceLock,
    atomic_text,
    map_controller_status,
    simulation,
)
import tools.supervisor.hhy_supervisor as supervisor_module


def run_python(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def test_config_and_generated_audit_are_schema_valid() -> None:
    config = yaml.safe_load((ROOT / "tools/supervisor/supervisor_config.yaml").read_text(encoding="utf-8"))
    schema = json.loads((ROOT / "governance/schemas/supervisor-config.schema.json").read_text(encoding="utf-8"))
    errors = list(Draft202012Validator(schema).iter_errors(config))
    assert errors == []
    audit = json.loads((ROOT / "governance/runtime/supervisor/SUPERVISOR_COMPATIBILITY_AUDIT.json").read_text(encoding="utf-8"))
    assert audit["status"] == "PASS_WITH_COMPATIBILITY_NOTES"
    assert audit["controller"]["starts_codex_worker"] is True
    assert audit["controller"]["supervisor_calls_codex_cli"] is False


@pytest.mark.parametrize("status", sorted(AUTO_CONTINUE))
def test_only_two_statuses_auto_continue(status: str) -> None:
    mapped = map_controller_status({"status": status}, 20, {"project": {"goal_complete": False}})
    assert mapped == status


@pytest.mark.parametrize("status", sorted(MANDATORY_STOP))
def test_mandatory_statuses_stop(status: str) -> None:
    mapped = map_controller_status({"status": status}, 20, {"project": {"goal_complete": False}})
    assert mapped == status


def test_controller_compatibility_mapping() -> None:
    assert map_controller_status({"status": "TASK_DONE", "runs": 20}, 20, {}) == "MAX_RUNS_REACHED"
    assert map_controller_status({"status": "TASK_DONE", "runs": 1}, 20, {}) == "NEXT_TASK_READY"
    assert map_controller_status({"status": "CANDIDATE_EVIDENCE_REQUIRED"}, 20, {}) == "CANDIDATE_REQUIRED"
    assert map_controller_status({"status": "DIRTY_AUTHORITY_WORKTREE"}, 20, {}) == "WORKTREE_UNSAFE"
    assert map_controller_status({"status": "not-known"}, 20, {}) == "UNKNOWN_STATUS"


def test_simulate_never_calls_codex_or_writes_state() -> None:
    result = simulation()
    assert result["status"] == "PASS"
    assert result["codex_called"] is False
    assert result["state_written"] is False
    assert {row["input"] for row in result["cases"]} == AUTO_CONTINUE | MANDATORY_STOP


def test_single_instance_lock_rejects_second_holder(tmp_path: Path) -> None:
    metadata = {"pid": os.getpid(), "instance_uuid": "first", "repo": str(ROOT)}
    first = SingleInstanceLock(tmp_path / "supervisor.lock", metadata)
    second = SingleInstanceLock(tmp_path / "supervisor.lock", {**metadata, "instance_uuid": "second"})
    first.acquire()
    try:
        with pytest.raises(RuntimeError, match="SUPERVISOR_ALREADY_RUNNING"):
            second.acquire()
    finally:
        first.release()


def test_dry_run_does_not_start_run_loop_or_modify_authority() -> None:
    before = subprocess.run(["git", "status", "--porcelain=v1", "-uall"], cwd=ROOT, text=True, capture_output=True, check=True).stdout
    result = run_python(str(SUPERVISOR), "--repo", str(ROOT), "--dry-run")
    after = subprocess.run(["git", "status", "--porcelain=v1", "-uall"], cwd=ROOT, text=True, capture_output=True, check=True).stdout
    assert result.returncode == 0, result.stdout + result.stderr
    payload = json.loads(result.stdout)
    assert payload["status"] == "DRY_RUN"
    assert "run-loop" in payload["would_execute"]
    assert before == after


def test_simulate_cli_has_no_codex_invocation() -> None:
    result = run_python(str(SUPERVISOR), "--repo", str(ROOT), "--simulate")
    assert result.returncode == 0, result.stdout + result.stderr
    payload = json.loads(result.stdout)
    assert payload["codex_called"] is False
    assert payload["state_written"] is False


def test_status_cli_is_read_only() -> None:
    result = run_python(str(STATUS), "--repo", str(ROOT))
    assert result.returncode == 0, result.stdout + result.stderr
    payload = json.loads(result.stdout)
    assert payload["schema"] == "hhy.supervisor-status/v5.0"
    assert payload["state"]["project"]["status"] == "FAILED_BOUNDED"
    assert payload["state"]["project"]["active_task"] is None


def test_atomic_text_retries_transient_windows_access_denied(tmp_path: Path, monkeypatch) -> None:
    target = tmp_path / "runtime.json"
    original_replace = supervisor_module.os.replace
    calls = {"count": 0}

    def flaky_replace(source, destination):
        calls["count"] += 1
        if calls["count"] < 3:
            raise PermissionError(5, "Access is denied")
        return original_replace(source, destination)

    monkeypatch.setattr(supervisor_module.os, "replace", flaky_replace)
    atomic_text(target, "ok\n")
    assert target.read_text(encoding="utf-8") == "ok\n"
    assert calls["count"] == 3
    assert list(tmp_path.glob(".*.tmp")) == []


def test_windows_scripts_cover_required_operations() -> None:
    windows = ROOT / "tools/supervisor/windows"
    names = {
        "install_supervisor_task.ps1",
        "uninstall_supervisor_task.ps1",
        "start_supervisor.ps1",
        "stop_supervisor.ps1",
        "status_supervisor.ps1",
        "install_autonomous_mode.ps1",
        "install_guardian_task.ps1",
        "uninstall_guardian_task.ps1",
        "start_guardian.ps1",
        "status_guardian.ps1",
    }
    assert names == {path.name for path in windows.glob("*.ps1")}
    install = (windows / "install_supervisor_task.ps1").read_text(encoding="utf-8")
    start = (windows / "start_supervisor.ps1").read_text(encoding="utf-8")
    stop = (windows / "stop_supervisor.ps1").read_text(encoding="utf-8")
    assert "Register-ScheduledTask" in install
    assert "MultipleInstances IgnoreNew" in install
    assert "py.exe" in start and "python.exe" in start
    assert "PROGRAM_COMPLETE.lock" in start
    assert "STOP_REQUESTED" in stop
    guardian_start = (windows / "start_guardian.ps1").read_text(encoding="utf-8")
    guardian_install = (windows / "install_guardian_task.ps1").read_text(encoding="utf-8")
    autonomous = (windows / "install_autonomous_mode.ps1").read_text(encoding="utf-8")
    assert "--daemon" in guardian_start
    assert "Register-ScheduledTask" in guardian_install
    assert "install_guardian_task.ps1" in autonomous
