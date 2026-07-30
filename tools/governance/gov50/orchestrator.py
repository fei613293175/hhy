from __future__ import annotations

import fnmatch
import json
import os
import shutil
import subprocess
import tempfile
import uuid
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

from .gates import run_gate, validate_candidate_evidence
from .state import assert_valid_state, read_state, write_state
from .tasks import load_task_specs
from .util import git, read_json, repository_lock, run, utc_now, write_json
from .views import next_ready_task, render_views

AUTH_ENV = {"HHY_GOVERNANCE_ROLE": "ORCHESTRATOR"}
OWNER_ENV = {"HHY_GOVERNANCE_ROLE": "OWNER"}
CONTROL_PREFIXES = (
    "governance/", "tools/governance/", ".codex/", ".githooks-v5/", ".github/workflows/",
    "tests/governance_v5/", "AGENTS.md", "CURRENT_STATUS.yaml", "NEXT_TASK.yaml",
)
# Candidate, Owner and release evidence is expected to be generated after the source
# freeze. It is non-product evidence and must not invalidate the frozen source commit.
EVIDENCE_PREFIXES = (
    "artifacts/releases/", "artifacts/owner/", "artifacts/production/", "artifacts/rollback/",
)


def _commit_state(repo: Path, state: dict[str, Any], specs: dict[str, dict[str, Any]], message: str, extra_paths: list[str] | None = None) -> str | None:
    render_views(repo, state, specs)
    paths = ["governance/STATE.yaml", "CURRENT_STATUS.yaml", "NEXT_TASK.yaml", "governance/views"]
    if (repo / "governance" / "evidence" / "gates").exists():
        paths.append("governance/evidence/gates")
    if (repo / "governance" / "evidence" / "reviews").exists():
        paths.append("governance/evidence/reviews")
    if (repo / "governance" / "GOAL_COMPLETE.json").is_file():
        paths.append("governance/GOAL_COMPLETE.json")
    extras = [value for value in (extra_paths or []) if value]
    git(repo, "add", *paths, env=AUTH_ENV)
    if extras:
        # Release evidence, including APKs, may be ignored by ordinary developer
        # rules. The privileged Orchestrator deliberately records the exact
        # commit-bound evidence so it survives machine and operator changes.
        git(repo, "add", "-f", "--", *extras, env=AUTH_ENV)
    staged = git(repo, "diff", "--cached", "--name-only")
    if not staged:
        return None
    git(repo, "commit", "--quiet", "-m", message, env=AUTH_ENV)
    return git(repo, "rev-parse", "HEAD")


def _product_changes(repo: Path, base: str, head: str = "HEAD") -> list[str]:
    names = git(repo, "-c", "core.quotepath=false", "diff", "--name-only", f"{base}..{head}").splitlines()
    return sorted(
        path for path in names
        if path and not path.startswith(CONTROL_PREFIXES) and not path.startswith(EVIDENCE_PREFIXES)
    )


def candidate_check(repo: Path, release: str | None = None) -> dict[str, Any]:
    state = read_state(repo)
    release = release or state["project"]["active_release"]
    row = state["releases"][release]
    frozen = row.get("frozen_commit")
    if not frozen:
        return {"schema": "hhy.candidate-check/v5.0", "status": "NOT_FROZEN", "release": release, "product_changes": []}
    changes = _product_changes(repo, frozen)
    status = "PASS" if not changes else "INVALIDATED"
    return {"schema": "hhy.candidate-check/v5.0", "status": status, "release": release, "frozen_commit": frozen, "product_changes": changes}


def activate_migration(repo: Path, commit: str, auto_commit: bool) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        assert_valid_state(state, specs)
        resolved = git(repo, "rev-parse", commit)
        state["project"]["status"] = "ACTIVE"
        state["project"]["migration_commit"] = resolved
        state["project"]["authoritative_commit"] = resolved
        state["releases"]["R14"]["status"] = "DEVELOPING"
        state["tasks"]["TASK-R14-RECOVERY-001"]["status"] = "READY"
        state["project"]["active_release"] = "R14"
        state["project"]["active_task"] = "TASK-R14-RECOVERY-001"
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        render_views(repo, state, specs)
        activation_commit = None
        if auto_commit:
            activation_commit = _commit_state(repo, state, specs, "[gov5.0] activate bounded deterministic governance")
        return {
            "schema": "hhy.migration-activation/v5.0", "status": "ACTIVE",
            "migration_commit": resolved, "activation_commit": activation_commit,
            "active_release": "R14", "active_task": "TASK-R14-RECOVERY-001",
        }


def candidate_authorize(repo: Path, release: str, commit: str) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        resolved = git(repo, "rev-parse", commit)
        row = state["releases"][release]
        if row["status"] == "FROZEN":
            if row.get("frozen_commit") != resolved:
                raise RuntimeError("already frozen at a different commit")
            return {
                "schema": "hhy.candidate-authorization/v5.0",
                "status": "ALREADY_AUTHORIZED",
                "release": release,
                "frozen_commit": resolved,
                "state_commit": None,
            }
        if row["status"] != "FREEZE_READY":
            raise RuntimeError(f"release {release} is not freeze-ready: {row['status']}")
        if git(repo, "status", "--porcelain=v1", "-uall"):
            raise RuntimeError("working tree must be clean before freezing")
        row["status"] = "FROZEN"
        row["frozen_commit"] = resolved
        row["candidate"] = {"status": "AUTHORIZED", "commit": resolved, "evidence": None}
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        commit_id = _commit_state(repo, state, specs, f"[gov5.0] authorize {release} candidate at {resolved[:12]}")
        return {"schema": "hhy.candidate-authorization/v5.0", "status": "AUTHORIZED", "release": release, "frozen_commit": resolved, "state_commit": commit_id}


def candidate_report(repo: Path, release: str, evidence: Path) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        row = state["releases"][release]
        frozen = row.get("frozen_commit")
        if not frozen:
            raise RuntimeError("release is not frozen")
        check = candidate_check(repo, release)
        if check["status"] != "PASS":
            row["status"] = "INVALIDATED"
            row["candidate"] = {"status": "INVALIDATED", "commit": frozen, "evidence": None}
            rev = state["revision"]
            write_state(repo, state, expected_revision=rev)
            state = read_state(repo)
            _commit_state(repo, state, specs, f"[gov5.0] invalidate {release} candidate after source change")
            return {"schema": "hhy.candidate-result/v5.0", "status": "INVALIDATED", "detail": check}
        evidence = evidence.resolve()
        try:
            rel = evidence.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("candidate evidence must be stored inside repository") from exc
        result = validate_candidate_evidence(repo, release, evidence, frozen)
        if result["status"] != "PASS":
            return {"schema": "hhy.candidate-result/v5.0", "status": "FAIL", "release": release, "errors": result["errors"]}
        row["candidate"] = {"status": "PASS", "commit": frozen, "evidence": rel}
        row["status"] = "CANDIDATE_GREEN"
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        evidence_paths = [rel, str(result["evidence"].get("gate_report") or "")]
        apk = result["evidence"].get("apk") if isinstance(result["evidence"].get("apk"), dict) else {}
        evidence_paths.append(str(apk.get("path") or ""))
        screenshots = result["evidence"].get("screenshots") if isinstance(result["evidence"].get("screenshots"), dict) else {}
        evidence_paths.extend(str(value) for value in screenshots.get("paths") or [])
        state_commit = _commit_state(repo, state, specs, f"[gov5.0] record {release} candidate PASS", evidence_paths)
        return {"schema": "hhy.candidate-result/v5.0", "status": "PASS", "release": release, "frozen_commit": frozen, "state_commit": state_commit}


def _release_number(release: str) -> int:
    return 0 if release == "P00" else int(release[1:])


def machine_close(repo: Path, release: str) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        row = state["releases"][release]
        if row.get("candidate", {}).get("status") != "PASS":
            raise RuntimeError("candidate must PASS before machine close")
        check = candidate_check(repo, release)
        if check["status"] != "PASS":
            raise RuntimeError(f"candidate invalidated: {check['product_changes']}")
        row["status"] = "MACHINE_CLOSED"
        row["owner_verification"]["status"] = "PENDING"
        close_task = f"TASK-{release}-008"
        if close_task in state["tasks"]:
            state["tasks"][close_task]["status"] = "DONE"
        if release == "R14":
            state["tasks"]["TASK-R14-RECOVERY-001"]["status"] = "DONE"
        number = _release_number(release)
        if release == "R32":
            state["project"]["status"] = "COMPLETE"
            state["project"]["goal_complete"] = True
            state["project"]["active_release"] = None
            state["project"]["active_task"] = None
            marker = {
                "schema": "hhy.goal-complete/v5.0", "status": "COMPLETE", "final_release": "R32",
                "frozen_commit": row["frozen_commit"], "candidate_evidence": row["candidate"]["evidence"], "completed_at": utc_now(),
            }
            write_json(repo / "governance" / "GOAL_COMPLETE.json", marker)
        else:
            next_release = f"R{number + 1:02d}"
            state["project"]["active_release"] = next_release
            next_task = f"TASK-{next_release}-001"
            state["project"]["active_task"] = next_task
            state["releases"][next_release]["status"] = "DEVELOPING"
            state["tasks"][next_task]["status"] = "READY"
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        state_commit = _commit_state(repo, state, specs, f"[gov5.0] machine-close {release}")
        return {
            "schema": "hhy.machine-close/v5.0", "status": "MACHINE_CLOSED", "release": release,
            "next_release": state["project"].get("active_release"), "next_task": state["project"].get("active_task"),
            "owner_verification": "PENDING", "state_commit": state_commit,
        }


def _validate_evidence_schema(repo: Path, schema_name: str, data: dict[str, Any]) -> None:
    path = repo / "governance" / "schemas" / schema_name
    if not path.is_file():
        raise RuntimeError(f"evidence schema missing: {schema_name}")
    schema = json.loads(path.read_text(encoding="utf-8"))
    errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda error: list(error.path))
    if errors:
        raise RuntimeError("; ".join(error.message for error in errors))


def _repo_evidence_path(repo: Path, value: str, label: str) -> Path:
    path = (repo / value).resolve()
    try:
        path.relative_to(repo.resolve())
    except ValueError as exc:
        raise RuntimeError(f"{label} must be inside repository") from exc
    if not path.is_file():
        raise RuntimeError(f"{label} file missing: {value}")
    return path


def _load_bound_release_evidence(repo: Path, value: str, schema_name: str, schema_id: str, release: str, frozen: str, label: str) -> dict[str, Any]:
    path = _repo_evidence_path(repo, value, label)
    data = read_json(path)
    _validate_evidence_schema(repo, schema_name, data)
    if data.get("schema") != schema_id or data.get("status") != "PASS":
        raise RuntimeError(f"{label} must have schema {schema_id} and status PASS")
    if data.get("release") != release or data.get("frozen_commit") != frozen:
        raise RuntimeError(f"{label} does not bind release and frozen commit")
    return data


def owner_result(repo: Path, release: str, result: str, evidence: Path) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        row = state["releases"][release]
        if row["status"] not in {"MACHINE_CLOSED", "OWNER_VERIFICATION_PENDING", "FORMALLY_ACCEPTED"}:
            raise RuntimeError("owner result requires MACHINE_CLOSED release")
        evidence = evidence.resolve()
        try:
            rel = evidence.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("owner evidence must be stored inside repository") from exc
        data = read_json(evidence)
        _validate_evidence_schema(repo, "owner-evidence.schema.json", data)
        if data.get("release") != release or data.get("frozen_commit") != row.get("frozen_commit") or data.get("result") != result:
            raise RuntimeError("owner evidence does not bind release, frozen commit and result")
        row["owner_verification"] = {"status": result, "evidence": rel}
        row["status"] = "FORMALLY_ACCEPTED" if result == "PASS" else "MACHINE_CLOSED"
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        commit_id = _commit_state(repo, state, specs, f"[gov5.0] owner {result.lower()} {release}", [rel])
        return {"schema": "hhy.owner-result/v5.0", "status": result, "release": release, "state": row["status"], "state_commit": commit_id}


def formal_release(repo: Path, release: str, evidence: Path) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        row = state["releases"][release]
        if row["status"] != "FORMALLY_ACCEPTED" or row["owner_verification"]["status"] != "PASS":
            raise RuntimeError("formal release requires owner PASS and FORMALLY_ACCEPTED")
        evidence = evidence.resolve()
        try:
            rel = evidence.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("formal release evidence must be inside repository") from exc
        data = read_json(evidence)
        _validate_evidence_schema(repo, "formal-release-evidence.schema.json", data)
        frozen = row.get("frozen_commit")
        if data["release"] != release or data["frozen_commit"] != frozen:
            raise RuntimeError("formal release evidence commit mismatch")
        tag_commit = git(repo, "rev-list", "-n", "1", data["tag"])
        if tag_commit != frozen:
            raise RuntimeError("release tag does not point to frozen commit")
        _load_bound_release_evidence(
            repo, str(data["production_activation_evidence"]),
            "production-activation-evidence.schema.json", "hhy.production-activation-evidence/v5.0",
            release, frozen, "production activation evidence",
        )
        _load_bound_release_evidence(
            repo, str(data["rollback_evidence"]),
            "rollback-evidence.schema.json", "hhy.rollback-evidence/v5.0",
            release, frozen, "rollback evidence",
        )
        row["status"] = "RELEASED"
        row["formal_release"] = {"status": "RELEASED", "tag": data["tag"], "evidence": rel}
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        commit_id = _commit_state(repo, state, specs, f"[gov5.0] formal release {release}", [rel, str(data["production_activation_evidence"]), str(data["rollback_evidence"])])
        return {"schema": "hhy.formal-release/v5.0", "status": "RELEASED", "release": release, "tag": data["tag"], "state_commit": commit_id}



HIGH_RISK_REVIEW = {
    "funds", "database", "security", "contract", "production",
    "apk_identity", "shared_ui_foundation",
}


def _review_prompt(task: dict[str, Any], baseline: str, candidate: str) -> str:
    return (
        "你是一次性的独立只读审查员，不是实现 Worker。禁止修改任何文件、运行写命令、重新规划项目或建议扩大范围。\n"
        "只审查 governance/runtime/ACTIVE_TASK.json 和 Git diff "
        f"{baseline}..{candidate}，确认候选是否满足当前任务、是否存在真实正确性/安全/资金/数据库/契约问题，"
        "以及是否通过删除、跳过或弱化测试来取得通过。\n"
        "仅将有具体文件、行号或可复核证据的 BLOCKER/HIGH 问题判为 BLOCK；"
        "风格、偏好、未来重构或任务范围外问题只能列为 MEDIUM/LOW，且不得阻断。\n"
        f"输出 task_id={task['id']}、commit={candidate}，严格符合 reviewer-result.schema.json。"
    )


def _run_independent_reviewer(worktree: Path, task: dict[str, Any], baseline: str, candidate: str) -> dict[str, Any]:
    if not HIGH_RISK_REVIEW.intersection(task.get("risks") or []):
        return {"status": "NOT_REQUIRED", "evidence_path": None, "result": None}
    runtime = worktree / "governance" / "runtime"
    result_path = runtime / "reviewer-result.json"
    schema_path = worktree / "governance" / "schemas" / "reviewer-result.schema.json"
    command = [
        "codex", "exec", "--ephemeral", "--sandbox", "read-only",
        "--ask-for-approval", "never", "--output-schema", str(schema_path),
        "-o", str(result_path), "-C", str(worktree),
        _review_prompt(task, baseline, candidate),
    ]
    execution = run(command, worktree, timeout=1800, env={"HHY_GOVERNANCE_ROLE": "REVIEWER"})
    if execution["status"] != "PASS" or not result_path.is_file():
        return {"status": "INFRASTRUCTURE_BLOCKED", "execution": execution, "evidence_path": None}
    try:
        data = read_json(result_path)
        schema = read_json(schema_path)
        errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda error: list(error.path))
        if errors:
            raise ValueError("; ".join(error.message for error in errors))
        if data.get("task_id") != task["id"] or data.get("commit") != candidate:
            raise ValueError("reviewer result is not bound to the current task and commit")
        blocking = [row for row in data.get("findings") or [] if row.get("severity") in {"BLOCKER", "HIGH"}]
        if data.get("status") == "PASS" and blocking:
            raise ValueError("reviewer returned PASS with blocking findings")
        if data.get("status") == "BLOCK" and not blocking:
            raise ValueError("reviewer BLOCK requires a BLOCKER or HIGH finding")
    except Exception as exc:
        return {
            "status": "INFRASTRUCTURE_BLOCKED",
            "execution": execution,
            "error": f"invalid reviewer result: {exc}",
            "evidence_path": None,
        }
    evidence = worktree / "governance" / "evidence" / "reviews" / f"{task['id']}-{candidate[:12]}.json"
    write_json(evidence, data)
    return {
        "status": data["status"],
        "result": data,
        "execution": execution,
        "evidence_path": evidence.relative_to(worktree).as_posix(),
    }


def _copy_worker_evidence(worktree: Path, repo: Path, relative: str | None) -> None:
    if not relative:
        return
    source = worktree / relative
    target = repo / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def _infrastructure_block(
    repo: Path,
    state: dict[str, Any],
    specs: dict[str, dict[str, Any]],
    task_id: str,
    code: str,
    detail: Any,
) -> dict[str, Any]:
    row = state["tasks"][task_id]
    row["status"] = "INFRASTRUCTURE_BLOCKED"
    row["blocker"] = {"code": code, "detail": detail}
    row["current_attempt"] = None
    state["project"]["status"] = "INFRASTRUCTURE_BLOCKED"
    state["lease"] = None
    rev = state["revision"]
    write_state(repo, state, expected_revision=rev)
    new_state = read_state(repo)
    _commit_state(repo, new_state, specs, f"[gov5.0] block {task_id}: {code}")
    return {
        "schema": "hhy.run-once/v5.0",
        "status": "INFRASTRUCTURE_BLOCKED",
        "task_id": task_id,
        "blocker": row["blocker"],
        "attempts_used": row["attempts_used"],
        "exit_code": 21,
    }


def _scope_ok(changed: list[str], allowed: list[str]) -> tuple[bool, list[str]]:
    violations = []
    for path in changed:
        if path.startswith(CONTROL_PREFIXES) or path in {"CURRENT_STATUS.yaml", "NEXT_TASK.yaml"}:
            violations.append(path)
            continue
        if not any(fnmatch.fnmatchcase(path, pattern) for pattern in allowed):
            violations.append(path)
    return not violations, sorted(violations)


def _worker_prompt(task: dict[str, Any], attempt: int) -> str:
    strategy = {1: "直接实现或修复", 2: "先构造最小复现并定位根因，再修复", 3: "从权威基线采用一个替代实现"}[attempt]
    return (
        "只执行 governance/runtime/ACTIVE_TASK.json 指定的唯一任务。\n"
        f"这是第 {attempt}/3 次总尝试，固定策略：{strategy}。\n"
        "禁止选择下一任务、修改治理/CI/Gate/AGENTS/状态、执行任何 Git 权威命令、自判正式 PASS。\n"
        "完成实际产品代码和测试后，运行必要的针对性验证。相同命令、输出和 Diff 不得重复。\n"
        "最终仅输出符合 worker-result.schema.json 的 CANDIDATE_READY、ATTEMPT_FAILED、EXTERNAL_BLOCKED 或 INFRASTRUCTURE_BLOCKED。"
    )


def _attempt_failure(repo: Path, state: dict[str, Any], specs: dict[str, dict[str, Any]], task_id: str, reason: str, fingerprint: str | None = None) -> dict[str, Any]:
    row = state["tasks"][task_id]
    row["attempts_used"] = int(row.get("attempts_used") or 0) + 1
    row["last_error_fingerprint"] = fingerprint or reason
    row["current_attempt"] = None
    state["lease"] = None
    if row["attempts_used"] >= 3:
        row["status"] = "FAILED_BOUNDED"
        state["project"]["status"] = "FAILED_BOUNDED"
        state["project"]["active_task"] = None
        status = "FAILED_BOUNDED"
        exit_code = 20
    else:
        row["status"] = "READY"
        status = "RETRY_REQUIRED"
        exit_code = 10
    rev = state["revision"]
    write_state(repo, state, expected_revision=rev)
    new_state = read_state(repo)
    _commit_state(repo, new_state, specs, f"[gov5.0] record {task_id} attempt failure")
    return {"schema": "hhy.run-once/v5.0", "status": status, "task_id": task_id, "attempts_used": row["attempts_used"], "reason": reason, "exit_code": exit_code}


def run_once(repo: Path, dry_run: bool = False) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        assert_valid_state(state, specs)
        if state["project"].get("goal_complete"):
            return {"schema": "hhy.run-once/v5.0", "status": "PROGRAM_COMPLETE", "exit_code": 0}
        if state["project"].get("status") != "ACTIVE":
            return {"schema": "hhy.run-once/v5.0", "status": "MIGRATION_NOT_ACTIVE", "exit_code": 30}
        task_id = state["project"].get("active_task")
        if not task_id:
            release = state["project"].get("active_release")
            if release and state["releases"][release]["status"] in {"FREEZE_READY", "FROZEN"}:
                evidence = repo / "artifacts" / "releases" / release / "CANDIDATE_EVIDENCE.json"
                if state["releases"][release]["status"] == "FREEZE_READY":
                    auth = candidate_authorize(repo, release, "HEAD")
                else:
                    auth = {"status": "ALREADY_FROZEN"}
                if not evidence.is_file():
                    return {"schema": "hhy.run-once/v5.0", "status": "CANDIDATE_EVIDENCE_REQUIRED", "release": release, "authorization": auth, "expected_evidence": evidence.relative_to(repo).as_posix(), "exit_code": 21}
                result = candidate_report(repo, release, evidence)
                if result["status"] != "PASS":
                    return {**result, "exit_code": 21}
                return {**machine_close(repo, release), "exit_code": 0}
            return {"schema": "hhy.run-once/v5.0", "status": "NO_READY_TASK", "exit_code": 21}
        spec = specs[task_id]
        row = state["tasks"][task_id]
        if row["status"] in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED", "FAILED_BOUNDED", "POLICY_VIOLATION"}:
            return {"schema": "hhy.run-once/v5.0", "status": row["status"], "task_id": task_id, "blocker": row.get("blocker"), "exit_code": 21 if "BLOCKED" in row["status"] else 20}
        if int(row.get("attempts_used") or 0) >= 3:
            row["status"] = "FAILED_BOUNDED"
            row["current_attempt"] = None
            state["project"]["status"] = "FAILED_BOUNDED"
            state["project"]["active_task"] = None
            state["lease"] = None
            rev = state["revision"]
            write_state(repo, state, expected_revision=rev)
            new_state = read_state(repo)
            _commit_state(repo, new_state, specs, f"[gov5.0] enforce bounded failure {task_id}")
            return {
                "schema": "hhy.run-once/v5.0", "status": "FAILED_BOUNDED",
                "task_id": task_id, "attempts_used": 3,
                "reason": "attempt budget already exhausted", "exit_code": 20,
            }
        if spec.get("mode") != "worker":
            raise RuntimeError(f"active task {task_id} is not a Worker task")
        attempt = int(row.get("attempts_used") or 0) + 1
        if dry_run:
            return {"schema": "hhy.run-once-plan/v5.0", "status": "DRY_RUN", "task_id": task_id, "attempt": attempt, "strategy": {1: "direct", 2: "minimal_reproduction", 3: "alternative"}[attempt], "allowed_paths": spec["allowed_paths"]}
        if shutil.which("codex") is None:
            row["status"] = "INFRASTRUCTURE_BLOCKED"
            row["blocker"] = {"code": "CODEX_CLI_MISSING", "resolution": "Install/authenticate current Codex CLI, then unblock with evidence."}
            state["project"]["status"] = "INFRASTRUCTURE_BLOCKED"
            rev = state["revision"]
            write_state(repo, state, expected_revision=rev)
            state = read_state(repo)
            _commit_state(repo, state, specs, f"[gov5.0] block {task_id}: Codex CLI missing")
            return {"schema": "hhy.run-once/v5.0", "status": "INFRASTRUCTURE_BLOCKED", "task_id": task_id, "blocker": row["blocker"], "exit_code": 21}
        if git(repo, "status", "--porcelain=v1", "-uall"):
            return {"schema": "hhy.run-once/v5.0", "status": "DIRTY_AUTHORITY_WORKTREE", "exit_code": 40}
        baseline = git(repo, "rev-parse", "HEAD")
        work_root = repo / ".git" / "hhy-governance-worktrees"
        work_root.mkdir(parents=True, exist_ok=True)
        worktree = work_root / f"{task_id.lower()}-a{attempt}-{uuid.uuid4().hex[:8]}"
        branch = f"gov-worker/{task_id.lower()}-a{attempt}-{uuid.uuid4().hex[:6]}"
        git(repo, "worktree", "add", "-b", branch, str(worktree), baseline, env=AUTH_ENV)
        try:
            active = {
                "schema": "hhy.active-task/v5.0", "task_id": task_id, "release": spec["release"],
                "attempt": attempt, "maximum_attempts": 3, "baseline_commit": baseline,
                "objective": spec["objective"], "deliverables": spec["deliverables"], "acceptance": spec["acceptance"],
                "allowed_paths": spec["allowed_paths"],
                "protected_paths": ["AGENTS.md", "governance/**", "tools/governance/**", ".codex/**", ".github/workflows/**", ".githooks-v5/**", "tests/governance_v5/**", "CURRENT_STATUS.yaml", "NEXT_TASK.yaml", ".continuity/**", "releases/*/TASKS.yaml", "design/effect-previews/**"],
                "acceptance_commands": spec["acceptance_commands"], "risks": spec["risks"],
                "limits": {"max_tool_calls": 100, "max_compactions": 1},
                "ledger_path": "governance/runtime/command-ledger.jsonl",
            }
            runtime = worktree / "governance" / "runtime"
            runtime.mkdir(parents=True, exist_ok=True)
            write_json(runtime / "ACTIVE_TASK.json", active)
            result_path = runtime / "worker-result.json"
            schema_path = worktree / "governance" / "schemas" / "worker-result.schema.json"
            command = [
                "codex", "exec", "--ephemeral", "--sandbox", "workspace-write", "--ask-for-approval", "never",
                "--json", "--output-schema", str(schema_path), "-o", str(result_path), "-C", str(worktree),
                _worker_prompt(spec, attempt),
            ]
            worker = run(command, worktree, timeout=3600, env={"HHY_GOVERNANCE_ROLE": "WORKER"})
            if worker["status"] != "PASS" or not result_path.is_file():
                return _attempt_failure(repo, state, specs, task_id, "worker execution failed or produced no result")
            result = read_json(result_path)
            status = result.get("status")
            changed = [line[3:].split(" -> ")[-1] for line in git(worktree, "-c", "core.quotepath=false", "status", "--porcelain=v1", "-uall").splitlines() if len(line) > 3 and not line[3:].startswith("governance/runtime/")]
            ok, violations = _scope_ok(changed, spec["allowed_paths"])
            if not ok:
                row["status"] = "POLICY_VIOLATION"
                row["blocker"] = {"code": "WORKER_SCOPE_VIOLATION", "paths": violations}
                state["project"]["status"] = "POLICY_VIOLATION"
                state["project"]["active_task"] = None
                state["lease"] = None
                rev = state["revision"]
                write_state(repo, state, expected_revision=rev)
                state = read_state(repo)
                _commit_state(repo, state, specs, f"[gov5.0] policy violation {task_id}")
                return {"schema": "hhy.run-once/v5.0", "status": "POLICY_VIOLATION", "paths": violations, "exit_code": 22}
            if status in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED"}:
                row["status"] = status
                row["blocker"] = result.get("blocker") or {"code": "UNSPECIFIED_BLOCKER"}
                row["current_attempt"] = None
                state["project"]["status"] = status
                state["lease"] = None
                rev = state["revision"]
                write_state(repo, state, expected_revision=rev)
                state = read_state(repo)
                _commit_state(repo, state, specs, f"[gov5.0] block {task_id}")
                return {"schema": "hhy.run-once/v5.0", "status": status, "task_id": task_id, "blocker": row["blocker"], "attempts_used": row["attempts_used"], "exit_code": 21}
            if status != "CANDIDATE_READY" or not changed:
                return _attempt_failure(repo, state, specs, task_id, str(result.get("summary") or "worker did not produce a candidate"), str(result.get("error_fingerprint") or ""))
            git(worktree, "add", "-A", env=AUTH_ENV)
            git(worktree, "commit", "-m", f"[{task_id}] bounded attempt {attempt} candidate", env=AUTH_ENV)
            candidate = git(worktree, "rev-parse", "HEAD")
            gate_profile = "freeze" if spec.get("kind") in {"release_close", "recovery"} else "task"
            gate = run_gate(worktree, profile=gate_profile, task_id=task_id, commit=candidate, release=spec["release"])
            gate_rel = str(gate["evidence_path"])
            _copy_worker_evidence(worktree, repo, gate_rel)
            if gate["status"] != "PASS":
                return _attempt_failure(
                    repo, state, specs, task_id,
                    f"independent {gate_profile} gate failed",
                    json.dumps(gate.get("checks"), ensure_ascii=False),
                )
            review = _run_independent_reviewer(worktree, spec, baseline, candidate)
            _copy_worker_evidence(worktree, repo, review.get("evidence_path"))
            if review["status"] == "INFRASTRUCTURE_BLOCKED":
                return _infrastructure_block(
                    repo, state, specs, task_id, "INDEPENDENT_REVIEWER_FAILED",
                    {key: value for key, value in review.items() if key != "result"},
                )
            if review["status"] == "BLOCK":
                return _attempt_failure(
                    repo, state, specs, task_id,
                    "independent read-only reviewer found a blocking issue",
                    json.dumps(review.get("result"), ensure_ascii=False),
                )
            git(repo, "merge", "--ff-only", candidate, env=AUTH_ENV)
            row["attempts_used"] = attempt
            row["status"] = "DONE"
            row["last_candidate_commit"] = candidate
            row["last_gate_evidence"] = gate_rel
            row["current_attempt"] = None
            row["blocker"] = None
            state["lease"] = None
            state["project"]["authoritative_commit"] = candidate
            if spec.get("kind") in {"release_close", "recovery"}:
                state["releases"][spec["release"]]["status"] = "FREEZE_READY"
                state["project"]["active_task"] = None
            else:
                nxt = next_ready_task(state, specs)
                if nxt and nxt != task_id:
                    state["tasks"][nxt]["status"] = "READY"
                    state["project"]["active_task"] = nxt
                else:
                    state["project"]["active_task"] = None
            rev = state["revision"]
            write_state(repo, state, expected_revision=rev)
            state = read_state(repo)
            state_commit = _commit_state(repo, state, specs, f"[gov5.0] accept {task_id}")
            return {"schema": "hhy.run-once/v5.0", "status": "TASK_DONE", "task_id": task_id, "attempt": attempt, "candidate_commit": candidate, "state_commit": state_commit, "next_task": state["project"].get("active_task"), "exit_code": 0}
        finally:
            git(repo, "worktree", "remove", "--force", str(worktree), check=False, env=AUTH_ENV)
            git(repo, "branch", "-D", branch, check=False, env=AUTH_ENV)


def run_loop(repo: Path, max_runs: int) -> dict[str, Any]:
    if max_runs < 1 or max_runs > 100:
        raise ValueError("max-runs must be between 1 and 100")
    results = []
    for _ in range(max_runs):
        result = run_once(repo)
        results.append(result)
        if result.get("status") not in {"TASK_DONE"}:
            break
    status = results[-1]["status"] if results else "NO_RUNS"
    return {"schema": "hhy.run-loop/v5.0", "status": status, "runs": len(results), "results": results, "bounded_by": max_runs}


def unblock(repo: Path, task_id: str, evidence: Path) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        row = state["tasks"][task_id]
        if row["status"] not in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED"}:
            raise RuntimeError("task is not blocked")
        evidence = evidence.resolve()
        try:
            rel = evidence.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("unblock evidence must be inside repository") from exc
        data = read_json(evidence)
        if not data.get("resolved") or data.get("task_id") != task_id:
            raise RuntimeError("unblock evidence must bind task and resolved=true")
        row["status"] = "READY"
        row["blocker"] = None
        state["project"]["active_task"] = task_id
        if state["project"]["status"] in {"INFRASTRUCTURE_BLOCKED", "EXTERNAL_BLOCKED"}:
            state["project"]["status"] = "ACTIVE"
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        commit_id = _commit_state(repo, state, specs, f"[gov5.0] unblock {task_id}")
        return {"schema": "hhy.unblock/v5.0", "status": "READY", "task_id": task_id, "evidence": rel, "state_commit": commit_id}
