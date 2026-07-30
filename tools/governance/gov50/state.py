from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
from typing import Any

from .util import read_yaml, utc_now, write_yaml

STATE_PATH = Path("governance/STATE.yaml")
TASK_TERMINAL = {"DONE", "SUPERSEDED", "FAILED_BOUNDED", "POLICY_VIOLATION"}
TASK_STATUSES = {
    "PLANNED", "PENDING_ACTIVATION", "READY", "IN_PROGRESS", "DONE", "SUPERSEDED",
    "FAILED_BOUNDED", "POLICY_VIOLATION", "EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED",
}
RELEASE_STATUSES = {
    "HISTORICAL", "PLANNED", "DEVELOPING", "MODULE_GREEN", "FREEZE_READY", "FROZEN",
    "TEST_APK_READY", "CANDIDATE_GREEN", "MACHINE_CLOSED", "FORMALLY_ACCEPTED",
    "RELEASED", "INVALIDATED", "RECOVERY_REQUIRED",
}


def _state_hash(state: dict[str, Any]) -> str:
    payload = copy.deepcopy(state)
    payload.pop("state_hash", None)
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def bootstrap_state(specs: dict[str, dict[str, Any]], branch: str, head: str) -> dict[str, Any]:
    releases: dict[str, Any] = {}
    for release in ["P00"] + [f"R{i:02d}" for i in range(1, 33)]:
        if release == "R14":
            status = "DEVELOPING"
            owner = "PENDING"
        elif release == "P00" or int(release[1:]) <= 13:
            status = "HISTORICAL"
            owner = "HISTORICAL"
        else:
            status = "PLANNED"
            owner = "NOT_STARTED"
        releases[release] = {
            "status": status,
            "frozen_commit": None,
            "candidate": {"status": "NONE", "commit": None, "evidence": None},
            "owner_verification": {"status": owner, "evidence": None},
            "formal_release": {"status": "NONE", "evidence": None, "tag": None},
        }

    tasks: dict[str, Any] = {}
    for task_id, spec in specs.items():
        release = str(spec.get("release"))
        status = "PLANNED"
        if task_id == "CONTROL-GOV50-MIGRATION":
            status = "DONE"
        elif task_id == "TASK-R14-RECOVERY-001":
            status = "PENDING_ACTIVATION"
        elif release == "P00" or (release.startswith("R") and int(release[1:]) <= 13):
            status = "DONE"
        elif release == "R14":
            status = "SUPERSEDED" if task_id == "TASK-R14-008" else "DONE"
        tasks[task_id] = {
            "status": status,
            "attempts_used": 0,
            "current_attempt": None,
            "last_error_fingerprint": None,
            "last_candidate_commit": None,
            "last_gate_evidence": None,
            "blocker": None,
        }

    state = {
        "schema": "hhy.governance-state/v5.0",
        "revision": 1,
        "updated_at": utc_now(),
        "state_hash": "",
        "project": {
            "status": "INSTALLED_NOT_ACTIVE",
            "branch_at_install": branch,
            "migration_baseline_commit": head,
            "migration_commit": None,
            "authoritative_commit": head,
            "active_release": "R14",
            "active_task": "TASK-R14-RECOVERY-001",
            "goal_complete": False,
        },
        "lease": None,
        "releases": releases,
        "tasks": tasks,
    }
    state["state_hash"] = _state_hash(state)
    return state


def assert_valid_state(state: dict[str, Any], specs: dict[str, dict[str, Any]]) -> None:
    errors: list[str] = []
    if state.get("schema") != "hhy.governance-state/v5.0":
        errors.append("state schema mismatch")
    if not isinstance(state.get("revision"), int) or state.get("revision", 0) < 1:
        errors.append("revision must be a positive integer")
    if state.get("state_hash") != _state_hash(state):
        errors.append("state hash mismatch")
    project = state.get("project") or {}
    active_release = project.get("active_release")
    active_task = project.get("active_task")
    if set(state.get("tasks") or {}) != set(specs):
        missing = sorted(set(specs) - set(state.get("tasks") or {}))
        extra = sorted(set(state.get("tasks") or {}) - set(specs))
        errors.append(f"task state/spec mismatch missing={missing[:5]} extra={extra[:5]}")
    ready_or_active: list[str] = []
    for task_id, row in (state.get("tasks") or {}).items():
        status = row.get("status")
        if status not in TASK_STATUSES:
            errors.append(f"{task_id}: invalid status {status}")
        attempts = row.get("attempts_used")
        if not isinstance(attempts, int) or attempts < 0 or attempts > 3:
            errors.append(f"{task_id}: attempts_used outside 0..3")
        current = row.get("current_attempt")
        if current is not None and (not isinstance(current, int) or current < 1 or current > 3):
            errors.append(f"{task_id}: current_attempt outside 1..3")
        if status in {"READY", "IN_PROGRESS"}:
            ready_or_active.append(task_id)
    for task_id, spec in specs.items():
        supersedes = spec.get("supersedes")
        if not supersedes:
            continue
        predecessor = (state.get("tasks") or {}).get(str(supersedes))
        if predecessor is None:
            errors.append(f"{task_id}: superseded task is missing from state")
        elif predecessor.get("status") != "FAILED_BOUNDED":
            errors.append(f"{task_id}: superseded task must remain FAILED_BOUNDED")
    for release, row in (state.get("releases") or {}).items():
        if row.get("status") not in RELEASE_STATUSES:
            errors.append(f"{release}: invalid release status {row.get('status')}")
        frozen = row.get("frozen_commit")
        candidate = row.get("candidate") or {}
        if candidate.get("status") in {"AUTHORIZED", "PASS"} and candidate.get("commit") != frozen:
            errors.append(f"{release}: candidate commit must equal frozen commit")
    if project.get("goal_complete"):
        if active_release is not None or active_task is not None:
            errors.append("completed project may not have active release/task")
    else:
        if active_task and active_task not in specs:
            errors.append("active task is not in task specs")
        if active_task and specs.get(active_task, {}).get("release") not in {active_release, "CONTROL"}:
            errors.append("active task release does not match active release")
        if project.get("status") == "ACTIVE":
            if active_task is not None:
                if ready_or_active != [active_task]:
                    errors.append(f"ACTIVE project must have exactly one READY/IN_PROGRESS task: {ready_or_active} vs {active_task}")
            else:
                active_row = (state.get("releases") or {}).get(active_release, {})
                if ready_or_active:
                    errors.append(f"no active task but READY/IN_PROGRESS tasks remain: {ready_or_active}")
                if active_row.get("status") not in {"FREEZE_READY", "FROZEN", "TEST_APK_READY", "CANDIDATE_GREEN"}:
                    errors.append(f"ACTIVE project without task requires a candidate-stage release, got {active_row.get('status')}")
    lease = state.get("lease")
    if lease is not None:
        if not isinstance(lease, dict) or lease.get("task_id") != active_task:
            errors.append("lease must bind active task")
    if errors:
        raise ValueError("; ".join(errors))


def read_state(repo: Path) -> dict[str, Any]:
    path = repo / STATE_PATH
    state = read_yaml(path) or {}
    # Task specs are loaded lazily here to catch tampering on every read.
    from .tasks import load_task_specs
    assert_valid_state(state, load_task_specs(repo))
    return state


def write_state(repo: Path, state: dict[str, Any], expected_revision: int) -> dict[str, Any]:
    path = repo / STATE_PATH
    current = read_yaml(path) if path.is_file() else None
    if current is not None and current.get("revision") != expected_revision:
        raise RuntimeError(f"state revision conflict: expected {expected_revision}, found {current.get('revision')}")
    updated = copy.deepcopy(state)
    updated["revision"] = expected_revision + 1
    updated["updated_at"] = utc_now()
    updated["state_hash"] = ""
    updated["state_hash"] = _state_hash(updated)
    from .tasks import load_task_specs
    assert_valid_state(updated, load_task_specs(repo))
    write_yaml(path, updated)
    return updated
