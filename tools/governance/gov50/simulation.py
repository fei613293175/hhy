from __future__ import annotations

from pathlib import Path
from typing import Any

from .tasks import load_task_specs, validate_specs
from .util import read_yaml


def simulate_program(repo: Path) -> dict[str, Any]:
    specs = load_task_specs(repo)
    plan = read_yaml(repo / "governance" / "PROGRAM_PLAN.yaml") or {}
    errors = validate_specs(specs, plan)
    done = {
        task_id for task_id, spec in specs.items()
        if task_id == "CONTROL-GOV50-MIGRATION"
        or spec.get("release") == "P00"
        or (str(spec.get("release", "")).startswith("R") and int(str(spec["release"])[1:]) <= 13)
        or (spec.get("release") == "R14" and task_id != "TASK-R14-RECOVERY-001")
    }
    sequence: list[str] = []
    for release_num in range(14, 33):
        release = f"R{release_num:02d}"
        pending = [s for s in specs.values() if s.get("release") == release and s.get("mode") == "worker" and s["id"] not in done and s["id"] != "TASK-R14-008"]
        pending.sort(key=lambda s: (int(s.get("ordinal") or 0), s["id"]))
        while pending:
            ready = [s for s in pending if all(dep in done for dep in s.get("depends_on") or [])]
            if not ready:
                errors.append(f"{release}: no dependency-safe task among {[s['id'] for s in pending]}")
                break
            task = ready[0]
            if task.get("maximum_attempts") != 3:
                errors.append(f"{task['id']}: attempt budget is not exactly 3")
            done.add(task["id"])
            sequence.append(task["id"])
            pending.remove(task)
        if release == "R14":
            if "TASK-R14-RECOVERY-001" not in done:
                errors.append("R14 recovery task did not complete")
        else:
            close_id = f"TASK-{release}-008"
            if close_id not in done:
                errors.append(f"{release}: release close task did not complete")
    worker_tasks = {task_id for task_id, spec in specs.items() if spec.get("mode") == "worker" and task_id != "TASK-R14-008"}
    missing = sorted(worker_tasks - done)
    if missing:
        errors.append(f"unreachable worker tasks: {missing[:20]}")
    if sequence and sequence[0] != "TASK-R14-RECOVERY-001":
        errors.append(f"first active task must be R14 recovery, got {sequence[0]}")
    if not sequence or sequence[-1] != "TASK-R32-008":
        errors.append(f"last task must be TASK-R32-008, got {sequence[-1] if sequence else None}")
    return {
        "schema": "hhy.program-simulation/v5.0",
        "status": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "executed_task_count": len(sequence),
        "first_task": sequence[0] if sequence else None,
        "last_task": sequence[-1] if sequence else None,
        "program_complete": not errors,
        "attempt_four_possible": False,
    }
