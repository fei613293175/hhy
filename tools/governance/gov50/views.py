from __future__ import annotations

from pathlib import Path
from typing import Any

from .util import write_json, write_yaml


def _release_number(value: str) -> int:
    if value == "P00":
        return 0
    if value.startswith("R") and value[1:].isdigit():
        return int(value[1:])
    return 999


def _done(state: dict[str, Any], task_id: str) -> bool:
    return (state.get("tasks") or {}).get(task_id, {}).get("status") in {"DONE", "SUPERSEDED"}


def next_ready_task(state: dict[str, Any], specs: dict[str, dict[str, Any]]) -> str | None:
    project = state.get("project") or {}
    release = project.get("active_release")
    if not release or project.get("goal_complete"):
        return None
    candidates = [s for s in specs.values() if s.get("release") == release and s.get("mode") == "worker"]
    candidates.sort(key=lambda s: (int(s.get("ordinal") or 0), s.get("id")))
    for spec in candidates:
        task_id = spec["id"]
        status = state["tasks"][task_id]["status"]
        if status not in {"PLANNED", "READY"}:
            continue
        if all(_done(state, dep) for dep in spec.get("depends_on") or []):
            return task_id
    return None


def render_views(repo: Path, state: dict[str, Any], specs: dict[str, dict[str, Any]]) -> None:
    project = state.get("project") or {}
    active_task = project.get("active_task")
    task_spec = specs.get(active_task) if active_task else None
    current = {
        "DO_NOT_EDIT": True,
        "GENERATED_FROM": "governance/STATE.yaml",
        "SOURCE_REVISION": state.get("revision"),
        "SOURCE_HASH": state.get("state_hash"),
        "project_status": project.get("status"),
        "active_release": project.get("active_release"),
        "active_task": active_task,
        "authoritative_commit": project.get("authoritative_commit"),
        "goal_complete": project.get("goal_complete"),
    }
    next_view = {
        "DO_NOT_EDIT": True,
        "GENERATED_FROM": "governance/STATE.yaml + governance/task_specs/*.yaml",
        "SOURCE_REVISION": state.get("revision"),
        "task": task_spec,
        "instruction": "Only the Orchestrator may lease this task; a Worker may not select another task.",
    }
    write_yaml(repo / "CURRENT_STATUS.yaml", current)
    write_yaml(repo / "NEXT_TASK.yaml", next_view)
    views = repo / "governance" / "views"
    views.mkdir(parents=True, exist_ok=True)
    write_json(views / "project-status.json", current)
    releases = []
    for release in sorted(state.get("releases") or {}, key=_release_number):
        row = dict(state["releases"][release])
        row["release"] = release
        row["task_counts"] = {}
        for task_id, spec in specs.items():
            if spec.get("release") != release:
                continue
            status = state["tasks"][task_id]["status"]
            row["task_counts"][status] = row["task_counts"].get(status, 0) + 1
        releases.append(row)
    write_json(views / "release-status.json", {"source_revision": state.get("revision"), "releases": releases})
