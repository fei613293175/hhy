from __future__ import annotations

import json
from pathlib import Path

from tools.supervisor.supervisor_guardian import (
    AUTO_RESUMABLE_ERRORS,
    AUTO_RESUMABLE_STATUSES,
    Guardian,
    report_error_category,
)
import tools.supervisor.supervisor_guardian as guardian_module


def test_guardian_classifies_known_access_denied_stop() -> None:
    report = {
        "stop_status": "UNKNOWN_STATUS",
        "controller_result": {
            "status": "FAIL",
            "error": "[WinError 5] 拒绝访问。",
        },
    }
    assert report_error_category(report) == "WINDOWS_ACCESS_DENIED"
    assert "WINDOWS_ACCESS_DENIED" in AUTO_RESUMABLE_ERRORS


def test_guardian_marks_only_bounded_statuses_auto_resumable(tmp_path: Path) -> None:
    repo = tmp_path / "repo"
    runtime = repo / "governance" / "runtime" / "supervisor"
    runtime.mkdir(parents=True)
    (repo / ".git").mkdir()
    (repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml").write_text(
        "status: ENFORCED\nproject: hhy-pro-platform\n", encoding="utf-8"
    )
    report = {"stop_status": "RETRYABLE_INFRASTRUCTURE"}
    assert report["stop_status"] in AUTO_RESUMABLE_STATUSES
    report["stop_status"] = "UNKNOWN_STATUS"
    assert report["stop_status"] not in AUTO_RESUMABLE_STATUSES


def test_guardian_diagnose_is_read_only_for_git_state(tmp_path: Path, monkeypatch) -> None:
    repo = tmp_path / "repo"
    runtime = repo / "governance" / "runtime" / "supervisor"
    runtime.mkdir(parents=True)
    (repo / ".git").mkdir()
    (repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml").write_text(
        "status: ENFORCED\nproject: hhy-pro-platform\n", encoding="utf-8"
    )
    original = Guardian.__init__
    monkeypatch.setattr(Guardian, "__init__", lambda self, repo, **kwargs: (
        setattr(self, "repo", repo),
        setattr(self, "runtime", runtime),
        setattr(self, "python_executable", "python"),
        setattr(self, "supervisor_script", repo / "tools/supervisor/hhy_supervisor.py"),
        setattr(self, "events_path", runtime / "GUARDIAN_EVENTS.jsonl"),
        setattr(self, "state_path", runtime / "GUARDIAN_STATE.json"),
    ) and None)
    guardian = Guardian(repo)
    before = sorted(path.name for path in runtime.iterdir())
    diagnosis = guardian.diagnose()
    after = sorted(path.name for path in runtime.iterdir())
    assert diagnosis["environment"]["git_lock_present"] is False
    assert before == after
    monkeypatch.setattr(Guardian, "__init__", original)


def test_guardian_blocks_access_denied_auto_resume_after_failed_bounded(tmp_path: Path, monkeypatch) -> None:
    repo = tmp_path / "repo"
    runtime = repo / "governance" / "runtime" / "supervisor"
    runtime.mkdir(parents=True)
    (repo / ".git").mkdir()
    (repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml").write_text(
        "status: ENFORCED\nproject: hhy-pro-platform\n", encoding="utf-8"
    )
    (runtime / "runtime_state.json").write_text(
        '{"status":"STOPPED","pid":999999}', encoding="utf-8"
    )
    (runtime / "SUPERVISOR_STOP_REPORT.json").write_text(
        json.dumps({
            "stop_status": "UNKNOWN_STATUS",
            "resolution": "[WinError 5] Access is denied",
        }),
        encoding="utf-8",
    )
    original = Guardian.__init__
    monkeypatch.setattr(Guardian, "__init__", lambda self, repo, **kwargs: (
        setattr(self, "repo", repo),
        setattr(self, "runtime", runtime),
        setattr(self, "python_executable", "python"),
        setattr(self, "supervisor_script", repo / "tools/supervisor/hhy_supervisor.py"),
        setattr(self, "events_path", runtime / "GUARDIAN_EVENTS.jsonl"),
        setattr(self, "state_path", runtime / "GUARDIAN_STATE.json"),
    ) and None)
    monkeypatch.setattr(
        guardian_module,
        "read_active_state",
        lambda _repo: {
            "project": {"status": "FAILED_BOUNDED", "active_task": None},
            "tasks": {"TASK-R14-RECOVERY-002": {"status": "FAILED_BOUNDED", "attempts_used": 3}},
        },
    )
    guardian = Guardian(repo)
    diagnosis = guardian.diagnose()
    assert diagnosis["governance"]["failed_bounded"] is True
    assert diagnosis["stop_report"]["auto_resumable"] is False
    monkeypatch.setattr(Guardian, "__init__", original)
