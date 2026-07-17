#!/usr/bin/env python3
"""Strict validation for the R02-R32 rolling execution plan."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import yaml

from generate_program_execution_plan import RELEASES, build_plan, render_plan


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(path: Path) -> dict[str, Any]:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"expected mapping: {path}")
    return data


def ancestors(release: str, dependencies: dict[str, list[str]]) -> set[str]:
    result: set[str] = set()
    stack = list(dependencies.get(release, []))
    while stack:
        item = stack.pop()
        if item in result:
            continue
        result.add(item)
        stack.extend(dependencies.get(item, []))
    return result


def task_dag_errors(release: str, tasks: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    task_ids = {task.get("id") for task in tasks}
    if None in task_ids or len(task_ids) != len(tasks):
        errors.append(f"{release}: duplicate or missing task id")
        return errors
    for task in tasks:
        unknown = set(task.get("depends_on", [])) - task_ids
        if unknown:
            errors.append(f"{release}: {task['id']} has unknown dependencies {sorted(unknown)}")
    visiting: set[str] = set()
    visited: set[str] = set()
    by_id = {task["id"]: task for task in tasks}

    def visit(task_id: str) -> None:
        if task_id in visited:
            return
        if task_id in visiting:
            errors.append(f"{release}: task dependency cycle at {task_id}")
            return
        visiting.add(task_id)
        for parent in by_id[task_id].get("depends_on", []):
            if parent in by_id:
                visit(parent)
        visiting.remove(task_id)
        visited.add(task_id)

    for task_id in sorted(by_id):
        visit(task_id)
    return errors


def story_ids(path: Path) -> set[str]:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    stories = data if isinstance(data, list) else data.get("stories", [])
    return {str(story["story_id"]) for story in stories}


def validate_plan(root: Path, plan: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if plan.get("schema") != "hhy.program-execution-plan/v1":
        errors.append("invalid plan schema")

    model = plan.get("operating_model", {})
    if model.get("actual_concurrency_slots") != 4:
        errors.append("actual concurrency must remain 4 until runtime evidence changes")
    if model.get("coordinator_slots") != 1 or model.get("delegated_worker_slots") != 3:
        errors.append("operating model must be one coordinator plus three delegated workers")
    if model.get("authoritative_session_count") != 1:
        errors.append("only one authoritative session is allowed")
    if model.get("simultaneous_business_release_limit") != 1:
        errors.append("only one business release may be implemented at a time")

    release_entries = plan.get("release_plan", [])
    actual_release_ids = [entry.get("release") for entry in release_entries]
    if actual_release_ids != RELEASES:
        errors.append("release plan must contain R02-R32 exactly once and in order")

    dependency_doc = load_yaml(root / "releases" / "RELEASE_DEPENDENCIES.yaml")
    dependencies = dependency_doc.get("dependencies", {})
    required_final_ancestors = {"P00", "R01", *RELEASES[:-1]}
    missing_final_ancestors = required_final_ancestors - ancestors("R32", dependencies)
    if missing_final_ancestors:
        errors.append(
            "R32 does not transitively require all planned releases: "
            + ",".join(sorted(missing_final_ancestors))
        )

    expected = build_plan(root)
    if plan.get("portfolio_totals") != expected.get("portfolio_totals"):
        errors.append("portfolio totals drifted from release manifests")
    for actual, wanted in zip(release_entries, expected.get("release_plan", [])):
        release = wanted["release"]
        if actual.get("depends_on") != dependencies.get(release, []):
            errors.append(f"{release}: dependencies drifted from RELEASE_DEPENDENCIES")
        if actual.get("metrics") != wanted.get("metrics"):
            errors.append(f"{release}: metrics drifted from release facts")
        for key, relative in actual.get("facts", {}).items():
            if key and not (root / relative).exists():
                errors.append(f"{release}: missing fact source {relative}")

    rolling = plan.get("rolling_window", {})
    if rolling.get("current_release") != "R02":
        errors.append("rolling window current release must be R02")
    if rolling.get("execution_ready") != ["R02"]:
        errors.append("only R02 may be EXECUTION_READY")
    if rolling.get("story_ready") != ["R03", "R04"]:
        errors.append("R03 and R04 must be STORY_READY")

    near_term = {"R02", "R03", "R04"}
    for release in sorted(near_term):
        parallel_path = root / "releases" / release / "PARALLEL_EXECUTION_PLAN.yaml"
        if not parallel_path.exists():
            errors.append(f"{release}: missing parallel execution plan")
            continue
        parallel = load_yaml(parallel_path)
        if parallel.get("authoritative_session_count") != 1:
            errors.append(f"{release}: parallel plan must keep one authoritative session")
        if int(parallel.get("max_parallel_workers", 0)) > 3:
            errors.append(f"{release}: parallel plan exceeds three delegated workers")
        expected_stories = story_ids(root / "releases" / release / "STORIES.yaml")
        covered = {
            str(story)
            for lane in parallel.get("lanes", [])
            for story in lane.get("stories", [])
        }
        if covered != expected_stories:
            errors.append(
                f"{release}: lane story coverage mismatch; missing={sorted(expected_stories-covered)} "
                f"unknown={sorted(covered-expected_stories)}"
            )

        task_doc = load_yaml(root / "releases" / release / "TASKS.yaml")
        tasks = task_doc.get("tasks", [])
        errors.extend(task_dag_errors(release, tasks))
        task_story_coverage = {
            str(story)
            for task in tasks
            for story in task.get("stories", [])
        }
        if task_story_coverage != expected_stories:
            errors.append(
                f"{release}: task story coverage mismatch; missing={sorted(expected_stories-task_story_coverage)} "
                f"unknown={sorted(task_story_coverage-expected_stories)}"
            )

    apk_by_release = {
        entry["release"]: entry.get("android_test_apk_required") for entry in release_entries
    }
    missing_apk_releases = [release for release in RELEASES if apk_by_release.get(release) is not True]
    if missing_apk_releases:
        errors.append(
            "every release must keep desktop/public/owner-tested APK delivery: "
            + ",".join(missing_apk_releases)
        )
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--plan",
        type=Path,
        default=ROOT / "releases" / "PROGRAM_EXECUTION_PLAN.yaml",
    )
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    plan_path = args.plan if args.plan.is_absolute() else ROOT / args.plan
    if not plan_path.exists():
        errors = [f"missing plan: {plan_path}"]
    else:
        plan = load_yaml(plan_path)
        errors = validate_plan(ROOT, plan)
        if plan_path.read_text(encoding="utf-8") != render_plan(build_plan(ROOT)):
            errors.append("PROGRAM_EXECUTION_PLAN.yaml is not the current deterministic output")
    result = {
        "status": "PASS" if not errors else "FAIL",
        "release_count": len(RELEASES),
        "errors": errors,
    }
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"PROGRAM_PLAN_{result['status']}: releases={result['release_count']} errors={len(errors)}")
        for error in errors:
            print(f"- {error}")
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
