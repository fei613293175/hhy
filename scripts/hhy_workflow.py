#!/usr/bin/env python3
"""Single cross-computer entrypoint for proportionate HHY development checks."""
from __future__ import annotations

from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import Any, Iterable
import hashlib
import json
import os
import subprocess
import sys
import time

import yaml

import run_affected_tests as affected


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_POLICY = ROOT / "config" / "development-workflow.yaml"
DEFAULT_IMPACT_MAP = ROOT / "config" / "test-impact-map.yaml"
DEFAULT_STATE = ROOT / ".continuity" / "runtime" / "hhy-workflow-state.json"


class WorkflowError(RuntimeError):
    pass


def load_policy(path: Path = DEFAULT_POLICY) -> dict[str, Any]:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except (OSError, yaml.YAMLError) as exc:
        raise WorkflowError(f"cannot read workflow policy {path}: {exc}") from exc
    for key in ("modes", "simple_task", "classification", "duration_budgets_seconds"):
        if not isinstance(value.get(key), dict):
            raise WorkflowError(f"workflow policy requires {key}")
    return value


def _matches_any(path: str, patterns: Iterable[str]) -> bool:
    return any(affected.path_matches(path, pattern) for pattern in patterns)


def classify_work(
    policy: dict[str, Any], intent: str, paths: Iterable[str],
) -> dict[str, Any]:
    intent = intent.lower()
    if intent not in policy.get("intents", {}):
        raise WorkflowError(f"unknown intent: {intent}")
    normalized = sorted({affected.normalize_path(path) for path in paths if path})
    classification = policy["classification"]
    ignored = [str(value) for value in classification.get("ignored_paths", [])]
    classified = [path for path in normalized if not _matches_any(path, ignored)]
    domains: set[str] = set()
    for domain, patterns in classification.get("domains", {}).items():
        if any(_matches_any(path, [str(pattern) for pattern in patterns]) for path in classified):
            domains.add(str(domain))
    product_domain_names = {str(value) for value in classification.get("product_domains", [])}
    product_domains = sorted(domains & product_domain_names)
    escalation_patterns = [str(value) for value in classification.get("escalation_paths", [])]
    escalation_paths = sorted(path for path in classified if _matches_any(path, escalation_patterns))
    reasons: list[str] = []

    if intent == "release-close":
        mode = "RELEASE_CLOSE"
        reasons.append("explicit_release_close")
    elif intent == "test-apk":
        mode = "TEST_APK"
        reasons.append("explicit_test_apk")
    else:
        simple = policy["simple_task"]
        simple_candidate = (
            intent in {str(value) for value in simple.get("allowed_intents", [])}
            and len(classified) <= int(simple.get("max_classified_files", 0))
            and len(product_domains) <= int(simple.get("max_product_domains", 0))
            and not escalation_paths
        )
        if simple_candidate:
            mode = "SIMPLE"
            reasons.append("small_single_domain_low_risk")
        elif len(product_domains) > 1:
            mode = "CROSS_LAYER"
            reasons.append("multiple_product_domains")
        else:
            mode = "STANDARD"
            reasons.append("formal_or_escalated_single_domain_change")
        if escalation_paths:
            reasons.append("high_risk_path_present")
        if intent == "auto" and mode == "SIMPLE":
            mode = "STANDARD"
            reasons = ["auto_intent_does_not_assume_simple_bugfix"]

    mode_policy = policy["modes"][mode]
    profile = str(mode_policy["quality_profile"])
    if mode == "SIMPLE" and intent == "bugfix":
        profile = str(mode_policy.get("bugfix_quality_profile") or profile)
        reasons.append("bugfix_requires_affected_module_regression")
    return {
        "intent": intent,
        "mode": mode,
        "quality_profile": profile,
        "changed_files": normalized,
        "classified_files": classified,
        "domains": sorted(domains),
        "product_domains": product_domains,
        "escalation_paths": escalation_paths,
        "reasons": reasons,
        "rule": mode_policy.get("rule"),
        "duration_budget_seconds": int(policy["duration_budgets_seconds"].get(mode, 0)),
    }


def create_plan(args: Namespace) -> dict[str, Any]:
    policy = load_policy(args.policy)
    paths = args.changed_file or affected.changed_files(ROOT, args.base_ref, args.head_ref)
    workflow = classify_work(policy, args.intent, paths)
    impact = affected.load_impact_map(args.impact_map)
    release = args.release
    if workflow["quality_profile"] == "RELEASE" and not release:
        status = yaml.safe_load((ROOT / "CURRENT_STATUS.yaml").read_text(encoding="utf-8")) or {}
        release = status.get("active_release")
    quality_plan = affected.build_plan(
        impact, workflow["quality_profile"], workflow["classified_files"],
        root=ROOT, release=release,
    )
    return {
        "schema": "hhy.workflow-plan/v1",
        "workflow": workflow,
        "quality_plan": quality_plan,
        "next_actions": next_actions(workflow, release),
    }


def next_actions(workflow: dict[str, Any], release: str | None) -> list[str]:
    actions = [f"run affected {workflow['quality_profile']} checks"]
    if workflow["mode"] == "TEST_APK":
        target = release or "<RELEASE>"
        actions.extend([
            f"preflight exact APK route for {target} before build",
            "build once from the frozen source commit",
            "prepare and verify four-copy APK delivery",
        ])
    elif workflow["mode"] == "RELEASE_CLOSE":
        actions.extend(["verify release acceptance and documentation", "close release only after owner gates"])
    return actions


def workflow_fingerprint(plan: dict[str, Any], *, root: Path = ROOT) -> str:
    file_states: list[dict[str, str]] = []
    for value in plan["workflow"]["classified_files"]:
        path = root / value
        digest = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "DELETED_OR_MISSING"
        file_states.append({"path": value, "sha256": digest})
    payload = {
        "mode": plan["workflow"]["mode"],
        "profile": plan["workflow"]["quality_profile"],
        "files": file_states,
        "checks": plan["quality_plan"]["checks"],
    }
    return hashlib.sha256(
        json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()


def _atomic_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def execute_resumable(
    plan: dict[str, Any], state_path: Path = DEFAULT_STATE, *, fresh: bool = False,
) -> dict[str, Any]:
    fingerprint = workflow_fingerprint(plan)
    previous: dict[str, Any] = {}
    if not fresh and state_path.is_file():
        try:
            loaded = json.loads(state_path.read_text(encoding="utf-8"))
            if loaded.get("fingerprint") == fingerprint:
                previous = {
                    str(row.get("id")): row
                    for row in loaded.get("results", [])
                    if isinstance(row, dict) and row.get("status") == "PASS"
                }
        except (OSError, json.JSONDecodeError):
            previous = {}
    results: list[dict[str, Any]] = []
    resumed_checks: list[str] = []
    started = time.monotonic()
    for check in plan["quality_plan"]["checks"]:
        check_id = str(check["id"])
        if check_id in previous:
            results.append({**previous[check_id], "resumed": True})
            resumed_checks.append(check_id)
            continue
        single_plan = {**plan["quality_plan"], "checks": [check]}
        result = affected.execute_plan(single_plan)["results"][0]
        result["resumed"] = False
        results.append(result)
        _atomic_json(state_path, {
            "schema": "hhy.workflow-state/v1",
            "fingerprint": fingerprint,
            "mode": plan["workflow"]["mode"],
            "profile": plan["workflow"]["quality_profile"],
            "results": results,
            "updated_at_epoch": int(time.time()),
        })
        if result["status"] != "PASS":
            break
    expected_ids = [str(row["id"]) for row in plan["quality_plan"]["checks"]]
    passed_ids = {str(row["id"]) for row in results if row.get("status") == "PASS"}
    status = "PASS" if set(expected_ids) == passed_ids else "FAIL"
    duration = round(time.monotonic() - started, 3)
    budget = int(plan["workflow"].get("duration_budget_seconds") or 0)
    return {
        **plan["quality_plan"],
        "status": status,
        "executed": True,
        "results": results,
        "resumed_checks": resumed_checks,
        "state_path": str(state_path),
        "fingerprint": fingerprint,
        "duration_seconds": duration,
        "budget_seconds": budget,
        "budget_warning": bool(budget and duration > budget),
    }


def resume_repository() -> dict[str, Any]:
    started = time.monotonic()
    completed = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "continuity.py"), "resume"],
        cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120,
    )
    if completed.returncode != 0:
        raise WorkflowError(completed.stderr.strip() or completed.stdout.strip() or "continuity resume failed")
    current = yaml.safe_load((ROOT / "CURRENT_STATUS.yaml").read_text(encoding="utf-8")) or {}
    next_task = yaml.safe_load((ROOT / "NEXT_TASK.yaml").read_text(encoding="utf-8")) or {}
    return {
        "schema": "hhy.workflow-resume/v1",
        "status": "PASS",
        "duration_seconds": round(time.monotonic() - started, 3),
        "continuity_output": completed.stdout.strip(),
        "active_release": current.get("active_release"),
        "active_task": current.get("active_task"),
        "task_status": next_task.get("status"),
        "blocker": next_task.get("blocker"),
        "python": sys.executable,
        "git": affected.resolve_git_executable(),
        "pnpm": affected.resolve_pnpm_executable(),
        "next_command": "python scripts/hhy_workflow.py plan --intent <bugfix|feature|test-apk|release-close>",
    }


def add_plan_arguments(parser: ArgumentParser) -> None:
    parser.add_argument("--intent", default="auto", choices=["auto", "maintenance", "bugfix", "feature", "test-apk", "release-close"])
    parser.add_argument("--changed-file", action="append", default=[])
    parser.add_argument("--base-ref")
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--release")
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--impact-map", type=Path, default=DEFAULT_IMPACT_MAP)


def main(argv: list[str] | None = None) -> int:
    parser = ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("resume", help="resume repository facts and resolve portable tools")
    plan_parser = subparsers.add_parser("plan", help="classify work and print proportionate checks")
    add_plan_arguments(plan_parser)
    run_parser = subparsers.add_parser("run", help="classify and execute proportionate checks")
    add_plan_arguments(run_parser)
    run_parser.add_argument("--state-file", type=Path, default=DEFAULT_STATE)
    run_parser.add_argument("--fresh", action="store_true", help="ignore prior PASS checks for the same inputs")
    args = parser.parse_args(argv)
    try:
        if args.command == "resume":
            result = resume_repository()
        else:
            result = create_plan(args)
            if args.command == "run":
                execution = execute_resumable(result, args.state_file, fresh=args.fresh)
                result["execution"] = execution
                result["duration_seconds"] = execution["duration_seconds"]
                result["status"] = execution["status"]
            else:
                result["status"] = "DRY_RUN"
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result.get("status") in {"PASS", "DRY_RUN"} else 1
    except (WorkflowError, affected.ImpactMapError, OSError, subprocess.TimeoutExpired) as exc:
        print(f"HHY_WORKFLOW_FAILED: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
