#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Callable

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.governance.gov50.gates import product_readiness, run_gate
from tools.governance.gov50.legacy import scan_active_control_plane
from tools.governance.gov50.orchestrator import (
    activate_migration, candidate_authorize, candidate_check, candidate_report,
    formal_release, machine_close, owner_result, run_loop, run_once, supersede_failed, unblock,
)
from tools.governance.gov50.secrets import scan_secrets
from tools.governance.gov50.simulation import simulate_program
from tools.governance.gov50.state import assert_valid_state, read_state
from tools.governance.gov50.tasks import EXPECTED_FUTURE_TASKS, EXPECTED_TASKS, REPAIR_RECOVERY_TASK_ID, load_task_specs, validate_specs
from tools.governance.gov50.util import git, read_yaml
from tools.governance.gov50.views import render_views


def _role(repo: Path) -> str:
    explicit = os.environ.get("HHY_GOVERNANCE_ROLE")
    if explicit:
        return explicit
    if (repo / "governance/runtime/ACTIVE_TASK.json").is_file():
        return "WORKER"
    return "ORCHESTRATOR"


def _require(repo: Path, allowed: set[str]) -> None:
    role = _role(repo)
    if role not in allowed:
        raise PermissionError(f"operation requires {sorted(allowed)}, current role={role}")


def _print(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


def doctor(repo: Path, ci: bool) -> dict[str, Any]:
    checks: list[dict[str, Any]] = []
    def add(name: str, status: str, detail: Any = None) -> None:
        row = {"id": name, "status": status}
        if detail not in (None, [], {}): row["detail"] = detail
        checks.append(row)
    try:
        specs = load_task_specs(repo)
        plan = read_yaml(repo / "governance/PROGRAM_PLAN.yaml") or {}
        errors = validate_specs(specs, plan)
        add("task_specs", "PASS" if not errors else "FAIL", errors)
        future = sum(1 for s in specs.values() if str(s.get("release", "")).startswith("R") and 15 <= int(str(s["release"])[1:]) <= 32)
        expected_total = EXPECTED_TASKS + int(REPAIR_RECOVERY_TASK_ID in specs)
        add("task_counts", "PASS" if (len(specs), future) == (expected_total, EXPECTED_FUTURE_TASKS) else "FAIL", {"total": len(specs), "expected_total": expected_total, "R15_R32": future})
    except Exception as exc:
        specs = {}
        add("task_specs", "FAIL", str(exc))
    try:
        state = read_state(repo)
        assert_valid_state(state, specs)
        add("single_state", "PASS", {"revision": state.get("revision"), "active_release": state.get("project", {}).get("active_release"), "active_task": state.get("project", {}).get("active_task")})
    except Exception as exc:
        state = {}
        add("single_state", "FAIL", str(exc))
    legacy = scan_active_control_plane(repo)
    add("active_legacy_routes", legacy["status"], legacy.get("findings"))
    simulation = simulate_program(repo) if specs else {"status": "FAIL", "errors": ["specs unavailable"]}
    add("bounded_program_simulation", simulation["status"], simulation.get("errors"))
    secrets = scan_secrets(repo, full=ci, include_untracked=True)
    add("secret_scan", secrets["status"], {"files_considered": secrets["files_considered"], "files_scanned": secrets["files_scanned"], "findings": secrets["findings"]})
    try:
        current = yaml.safe_load((repo / "CURRENT_STATUS.yaml").read_text(encoding="utf-8")) or {}
        nxt = yaml.safe_load((repo / "NEXT_TASK.yaml").read_text(encoding="utf-8")) or {}
        ok = bool(state) and current.get("DO_NOT_EDIT") is True and nxt.get("DO_NOT_EDIT") is True and current.get("SOURCE_REVISION") == state.get("revision") and current.get("active_task") == state.get("project", {}).get("active_task")
        add("generated_views", "PASS" if ok else "FAIL")
    except Exception as exc:
        add("generated_views", "FAIL", str(exc))
    expected = [
        "AGENTS.md", "governance/DEVELOPMENT_CONSTITUTION.yaml", "governance/STATE.yaml",
        "governance/PROGRAM_PLAN.yaml", ".codex/hooks.json", ".githooks-v5/pre-commit",
        ".githooks-v5/pre-push", ".github/workflows/governance-v5.yml",
        ".github/workflows/core-v5.yml", ".github/workflows/release-candidate-v5.yml",
    ]
    missing = [p for p in expected if not (repo / p).is_file()]
    add("control_plane_files", "PASS" if not missing else "FAIL", missing)
    hook_path = git(repo, "config", "--local", "--get", "core.hooksPath", check=False)
    add("git_hooks_path", "PASS" if hook_path == ".githooks-v5" else "FAIL", hook_path)
    attempt_text = ""
    for path in [repo / "config/android-automation.yaml", repo / "governance/DEVELOPMENT_CONSTITUTION.yaml"]:
        if path.is_file(): attempt_text += path.read_text(encoding="utf-8", errors="replace").lower()
    forbidden = [term for term in ["attempt_override", "attempt_exception", "approved_attempt_exceptions", "effective_attempt_limit", "max_ai_attempts_override"] if term in attempt_text]
    add("no_attempt_overrides", "PASS" if not forbidden else "FAIL", forbidden)
    diff = git(repo, "diff", "--check", check=False)
    add("git_diff_check", "PASS" if not diff else "FAIL", diff)
    status = "PASS" if all(row["status"] == "PASS" for row in checks) else "FAIL"
    return {"schema": "hhy.doctor/v5.0", "status": status, "ci": ci, "checks": checks}


def main() -> int:
    p = argparse.ArgumentParser(description="HHY Governance V5.0 deterministic controller")
    sub = p.add_subparsers(dest="command", required=True)
    a = sub.add_parser("active-task"); a.add_argument("--format", choices=["json", "context"], default="json")
    d = sub.add_parser("doctor"); d.add_argument("--ci", action="store_true")
    sub.add_parser("simulate")
    r = sub.add_parser("product-readiness"); r.add_argument("--release", required=True)
    g = sub.add_parser("gate"); g.add_argument("--profile", required=True); g.add_argument("--task"); g.add_argument("--release"); g.add_argument("--commit", default="HEAD")
    am = sub.add_parser("activate-migration"); am.add_argument("--commit", required=True); am.add_argument("--auto-commit", action="store_true")
    ro = sub.add_parser("run-once"); ro.add_argument("--dry-run", action="store_true")
    rl = sub.add_parser("run-loop"); rl.add_argument("--max-runs", type=int, default=20)
    cc = sub.add_parser("candidate-check"); cc.add_argument("--release")
    ca = sub.add_parser("candidate-authorize"); ca.add_argument("--release", required=True); ca.add_argument("--commit", required=True)
    cr = sub.add_parser("candidate-report"); cr.add_argument("--release", required=True); cr.add_argument("--evidence", required=True)
    mc = sub.add_parser("machine-close"); mc.add_argument("--release", required=True)
    ow = sub.add_parser("owner-result"); ow.add_argument("--release", required=True); ow.add_argument("--result", choices=["PASS", "FAIL"], required=True); ow.add_argument("--evidence", required=True)
    fr = sub.add_parser("formal-release"); fr.add_argument("--release", required=True); fr.add_argument("--evidence", required=True)
    ub = sub.add_parser("unblock"); ub.add_argument("--task", required=True); ub.add_argument("--evidence", required=True)
    sf = sub.add_parser("supersede-failed"); sf.add_argument("--from-task", required=True); sf.add_argument("--to-task", required=True); sf.add_argument("--authorization", required=True)
    args = p.parse_args()
    repo = ROOT
    try:
        if args.command == "active-task":
            state = read_state(repo); specs = load_task_specs(repo); task_id = state["project"].get("active_task"); spec = specs.get(task_id) if task_id else None
            if args.format == "context":
                if not spec: print("PROGRAM_COMPLETE_OR_NO_ACTIVE_TASK")
                else:
                    print(f"TASK={task_id}\nRELEASE={spec['release']}\nSTATUS={state['tasks'][task_id]['status']}\nATTEMPTS_USED={state['tasks'][task_id]['attempts_used']}/3\nOBJECTIVE={spec['objective']}\nALLOWED_PATHS=" + ",".join(spec.get("allowed_paths") or []))
                return 0
            _print({"state": state.get("project"), "task": spec}); return 0
        if args.command == "doctor": result = doctor(repo, args.ci)
        elif args.command == "simulate": result = simulate_program(repo)
        elif args.command == "product-readiness": result = product_readiness(repo, args.release)
        elif args.command == "gate": _require(repo, {"ORCHESTRATOR", "CI", "MIGRATION_OPERATOR"}); result = run_gate(repo, profile=args.profile, task_id=args.task, commit=args.commit, release=args.release)
        elif args.command == "activate-migration": _require(repo, {"ORCHESTRATOR", "MIGRATION_OPERATOR"}); result = activate_migration(repo, args.commit, args.auto_commit)
        elif args.command == "run-once": _require(repo, {"ORCHESTRATOR"}); result = run_once(repo, dry_run=args.dry_run)
        elif args.command == "run-loop": _require(repo, {"ORCHESTRATOR"}); result = run_loop(repo, args.max_runs)
        elif args.command == "candidate-check": result = candidate_check(repo, args.release)
        elif args.command == "candidate-authorize": _require(repo, {"ORCHESTRATOR", "CI"}); result = candidate_authorize(repo, args.release, args.commit)
        elif args.command == "candidate-report": _require(repo, {"ORCHESTRATOR", "CI"}); result = candidate_report(repo, args.release, Path(args.evidence))
        elif args.command == "machine-close": _require(repo, {"ORCHESTRATOR", "CI"}); result = machine_close(repo, args.release)
        elif args.command == "owner-result": _require(repo, {"OWNER"}); result = owner_result(repo, args.release, args.result, Path(args.evidence))
        elif args.command == "formal-release": _require(repo, {"OWNER", "RELEASE_OPERATOR"}); result = formal_release(repo, args.release, Path(args.evidence))
        elif args.command == "unblock": _require(repo, {"ORCHESTRATOR", "MIGRATION_OPERATOR"}); result = unblock(repo, args.task, Path(args.evidence))
        elif args.command == "supersede-failed": _require(repo, {"ORCHESTRATOR", "MIGRATION_OPERATOR"}); result = supersede_failed(repo, args.from_task, args.to_task, Path(args.authorization))
        else: raise RuntimeError("unsupported command")
        _print(result)
        return 0 if result.get("status") in {"PASS", "ACTIVE", "AUTHORIZED", "ALREADY_AUTHORIZED", "TASK_DONE", "MACHINE_CLOSED", "READY", "FORMALLY_ACCEPTED", "RELEASED", "PROGRAM_COMPLETE", "DRY_RUN", "NO_READY_TASK"} else int(result.get("exit_code", 1))
    except Exception as exc:
        _print({"schema": "hhy.governance-error/v5.0", "status": "FAIL", "command": args.command, "error": str(exc)})
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
