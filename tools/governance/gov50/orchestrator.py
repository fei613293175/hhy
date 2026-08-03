from __future__ import annotations

import fnmatch
import copy
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import uuid
import zipfile
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

from .gates import run_gate, validate_candidate_evidence
from .state import assert_valid_state, read_state, write_state
from .tasks import FAILED_RECOVERY_TASK_ID, REPAIR_RECOVERY_TASK_ID, load_task_specs, validate_specs
from .util import git, git_status_lines, read_json, read_yaml, repository_lock, resolve_codex_executable, run, run_with_progress_timeout, utc_now, write_json, write_yaml
from .views import next_ready_task, render_views

AUTH_ENV = {
    "HHY_GOVERNANCE_ROLE": "ORCHESTRATOR",
    "PYTHON": sys.executable,
    "HHY_PYTHON": sys.executable,
}
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
WORKER_FAILURE_DIR = Path("governance/runtime/supervisor/worker-failures")
WORKER_DRAFT_DIR = Path("governance/runtime/supervisor/worker-drafts")
TRANSIENT_PROVIDER_MARKERS = (
    "INVALID_API_KEY",
    "401 Unauthorized",
    "unexpected status 401",
)
PROVIDER_TRANSPORT_MARKERS = (
    "stream disconnected before completion",
    "error sending request for url",
    "Transport error",
    "network error",
)
TRANSIENT_PROVIDER_CODES = {
    "CODEX_PROVIDER_AUTH_UNAVAILABLE",
    "CODEX_PROVIDER_TRANSPORT_UNAVAILABLE",
}


def _worker_idle_timeout(repo: Path) -> int:
    constitution = read_yaml(repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml") or {}
    return int((constitution.get("limits") or {}).get("worker_idle_timeout_seconds", 1800))


def _worker_startup_timeout(repo: Path) -> int:
    constitution = read_yaml(repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml") or {}
    return int((constitution.get("limits") or {}).get("worker_startup_timeout_seconds", 600))


def _worker_progress_poll(repo: Path) -> float:
    constitution = read_yaml(repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml") or {}
    return float((constitution.get("limits") or {}).get("worker_progress_poll_seconds", 15))


def _worker_timeout(repo: Path) -> int:
    constitution = read_yaml(repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml") or {}
    return int((constitution.get("limits") or {}).get("worker_timeout_seconds", 3600))


def _fast_lane(repo: Path) -> dict[str, Any]:
    constitution = read_yaml(repo / "governance" / "DEVELOPMENT_CONSTITUTION.yaml") or {}
    return constitution.get("fast_lane") or {}


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


def validate_reviewer_result(
    data: dict[str, Any],
    schema: dict[str, Any],
    task_id: str,
    candidate: str,
) -> None:
    errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda error: list(error.path))
    if errors:
        raise ValueError("; ".join(error.message for error in errors))
    if data.get("task_id") != task_id or data.get("commit") != candidate:
        raise ValueError("reviewer result is not bound to the current task and commit")
    blocking = [
        row for row in data.get("findings") or []
        if row.get("severity") in {"BLOCKER", "HIGH"}
    ]
    if data.get("status") == "PASS" and blocking:
        raise ValueError("reviewer returned PASS with blocking findings")
    if data.get("status") == "BLOCK" and not blocking:
        raise ValueError("reviewer BLOCK requires a BLOCKER or HIGH finding")


def worker_sandbox_args(platform_name: str | None = None) -> list[str]:
    """Select the Windows backend that supports workspace-write apply_patch."""
    platform_name = platform_name or os.name
    if platform_name == "nt":
        return [
            "-c", 'windows.sandbox="elevated"',
            "-c", "features.apps=false",
            "-c", "features.plugins=false",
            "-c", "features.remote_plugin=false",
            "-c", "features.memories=false",
        ]
    return []


def task_gate_profile(spec: dict[str, Any]) -> str:
    explicit = str(spec.get("gate_profile") or "")
    if explicit:
        if explicit not in {"task", "freeze"}:
            raise RuntimeError(f"unsupported task gate profile: {explicit}")
        return explicit
    return "freeze" if spec.get("kind") in {"release_close", "recovery"} else "task"


def structured_failure_evidence(raw: Any) -> Any:
    if not isinstance(raw, str):
        return raw
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def merged_recovery_failure_evidence(source: dict[str, Any], current_raw: Any) -> Any:
    current = structured_failure_evidence(current_raw)
    product = copy.deepcopy(source.get("latest_failure_evidence"))
    if current and product:
        return {
            "current_execution_failure": current,
            "product_failure_evidence": product,
        }
    return current or product


def worker_failure_evidence(spec: dict[str, Any], row: dict[str, Any]) -> Any:
    current = structured_failure_evidence(row.get("last_error_fingerprint"))
    return copy.deepcopy(current or spec.get("latest_failure_evidence"))


def _run_independent_reviewer(worktree: Path, task: dict[str, Any], baseline: str, candidate: str) -> dict[str, Any]:
    if not HIGH_RISK_REVIEW.intersection(task.get("risks") or []):
        return {"status": "NOT_REQUIRED", "evidence_path": None, "result": None}
    runtime = worktree / "governance" / "runtime"
    result_path = runtime / "reviewer-result.json"
    schema_path = worktree / "governance" / "schemas" / "reviewer-result.schema.json"
    codex = resolve_codex_executable()
    if not codex:
        return {"status": "INFRASTRUCTURE_BLOCKED", "error": "CODEX_CLI_MISSING_OR_INACCESSIBLE", "evidence_path": None}
    command = [
        codex, "--ask-for-approval", "never", "exec", "--ephemeral", "--sandbox", "read-only",
        "--output-schema", str(schema_path),
        "-o", str(result_path), "-C", str(worktree),
        _review_prompt(task, baseline, candidate),
    ]
    execution = run(command, worktree, timeout=1800, env={"HHY_GOVERNANCE_ROLE": "REVIEWER"})
    if execution["status"] != "PASS" or not result_path.is_file():
        return {"status": "INFRASTRUCTURE_BLOCKED", "execution": execution, "evidence_path": None}
    try:
        data = read_json(result_path)
        schema = read_json(schema_path)
        validate_reviewer_result(data, schema, task["id"], candidate)
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


def _worker_infrastructure_code(worker: dict[str, Any]) -> str | None:
    stderr = str(worker.get("stderr_tail") or "")
    category = str(worker.get("error_category") or "")
    combined = f"{category}\n{stderr}"
    if any(marker in combined for marker in TRANSIENT_PROVIDER_MARKERS):
        return "CODEX_PROVIDER_AUTH_UNAVAILABLE"
    if any(marker in combined for marker in PROVIDER_TRANSPORT_MARKERS):
        return "CODEX_PROVIDER_TRANSPORT_UNAVAILABLE"
    for line in str(worker.get("stdout_tail") or "").splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("type") not in {"error", "turn.failed"}:
            continue
        if any(marker in line for marker in TRANSIENT_PROVIDER_MARKERS):
            return "CODEX_PROVIDER_AUTH_UNAVAILABLE"
    return None


def _probe_codex_provider() -> dict[str, Any]:
    codex = resolve_codex_executable()
    if not codex:
        return {"status": "FAIL", "error": "Codex CLI unavailable"}
    with tempfile.TemporaryDirectory(prefix="hhy-codex-provider-probe-") as directory:
        execution = run(
            [
                codex, "--ask-for-approval", "never", "exec", "--ephemeral",
                "--sandbox", "read-only", *worker_sandbox_args(), "--json",
                "-C", directory,
                '仅输出 JSON 文本 {"status":"OK"}，不要调用任何工具。',
            ],
            Path(directory),
            timeout=90,
            env={"HHY_GOVERNANCE_ROLE": "INFRASTRUCTURE_PROBE"},
        )
    healthy = execution.get("status") == "PASS" and '"turn.completed"' in str(execution.get("stdout_tail") or "")
    return {
        "status": "PASS" if healthy else "FAIL",
        "exit_code": execution.get("exit_code"),
        "stderr_tail": str(execution.get("stderr_tail") or "")[-2000:],
    }


def _auto_resume_transient_provider(
    repo: Path,
    state: dict[str, Any],
    specs: dict[str, dict[str, Any]],
    task_id: str,
) -> dict[str, Any] | None:
    row = state["tasks"][task_id]
    blocker = row.get("blocker") or {}
    if row.get("status") != "INFRASTRUCTURE_BLOCKED" or blocker.get("code") not in TRANSIENT_PROVIDER_CODES:
        return None
    probe = _probe_codex_provider()
    if probe["status"] != "PASS":
        return None
    draft_relative = _draft_manifest_from_blocker(blocker)
    evidence = repo / "governance" / "evidence" / "recovery" / f"{task_id}-provider-recovered-{uuid.uuid4().hex[:10]}.json"
    write_json(evidence, {
        "schema": "hhy.infrastructure-recovery/v5.0",
        "task_id": task_id,
        "resolved": True,
        "code": "CODEX_PROVIDER_AUTH_UNAVAILABLE",
        "probe": probe,
        "at": utc_now(),
    })
    row["status"] = "READY"
    row["blocker"] = None
    row["current_attempt"] = None
    state["project"]["status"] = "ACTIVE"
    state["project"]["active_task"] = task_id
    state["lease"] = None
    rev = state["revision"]
    write_state(repo, state, expected_revision=rev)
    new_state = read_state(repo)
    state_commit = _commit_state(
        repo, new_state, specs, f"[gov5.0] auto-resume provider for {task_id}",
        extra_paths=[evidence.relative_to(repo).as_posix()],
    )
    if state_commit and draft_relative:
        draft_data = read_json(repo / draft_relative)
        draft_data["baseline_commit"] = state_commit
        write_json(repo / draft_relative, draft_data)
    return read_state(repo)


def _worker_changed_paths(worktree: Path) -> list[str]:
    return sorted({
        line[3:].split(" -> ")[-1]
        for line in git_status_lines(
            worktree, "-c", "core.quotepath=false", "status", "--porcelain=v1", "-uall"
        )
        if len(line) > 3 and not line[3:].startswith("governance/runtime/")
    })


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with _filesystem_path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _filesystem_path(path: Path) -> Path:
    if os.name == "nt":
        absolute = str(path.absolute())
        if not absolute.startswith("\\\\?\\"):
            return Path("\\\\?\\" + absolute)
    return path


def _write_worker_draft(
    repo: Path,
    source: Path,
    task_id: str,
    attempt: int,
    baseline: str,
    allowed_paths: list[str],
    changed_paths: list[str],
) -> str | None:
    ok, violations = _scope_ok(changed_paths, allowed_paths)
    if not ok:
        return None
    existing = [path for path in changed_paths if (source / path).is_file()]
    deleted = [path for path in changed_paths if not (source / path).exists()]
    if not existing and not deleted:
        return None
    draft = repo / WORKER_DRAFT_DIR / f"{task_id}-a{attempt}-{uuid.uuid4().hex[:10]}"
    draft.mkdir(parents=True, exist_ok=False)
    archive_path = draft / "files.zip"
    hashes: dict[str, str] = {}
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for relative in existing:
            source_file = source / relative
            hashes[relative] = _file_sha256(source_file)
            archive.write(_filesystem_path(source_file), arcname=relative)
    manifest = {
        "schema": "hhy.worker-draft/v5.0",
        "task_id": task_id,
        "attempt": attempt,
        "baseline_commit": baseline,
        "created_at": utc_now(),
        "files": hashes,
        "deleted_paths": deleted,
        "quality_status": "UNVERIFIED_REQUIRES_WORKER_GATE_AND_REVIEWER",
    }
    manifest_path = draft / "manifest.json"
    write_json(manifest_path, manifest)
    return manifest_path.relative_to(repo).as_posix()


def _preserve_worker_draft(
    repo: Path,
    worktree: Path,
    task_id: str,
    attempt: int,
    baseline: str,
    allowed_paths: list[str],
) -> str | None:
    return _write_worker_draft(
        repo, worktree, task_id, attempt, baseline, allowed_paths,
        _worker_changed_paths(worktree),
    )


def _preserve_allowed_worker_draft(
    repo: Path,
    worktree: Path,
    task_id: str,
    attempt: int,
    baseline: str,
    allowed_paths: list[str],
    changed_paths: list[str],
) -> str | None:
    allowed_changes = [
        path for path in changed_paths if _scope_ok([path], allowed_paths)[0]
    ]
    return _write_worker_draft(
        repo, worktree, task_id, attempt, baseline, allowed_paths, allowed_changes
    )


def _draft_manifest_from_blocker(blocker: Any) -> str | None:
    if not isinstance(blocker, dict):
        return None
    direct = blocker.get("draft_manifest")
    if isinstance(direct, str) and direct:
        return direct
    detail = blocker.get("detail")
    if isinstance(detail, dict):
        nested = detail.get("draft_manifest")
        if isinstance(nested, str) and nested:
            return nested
    return None


def _worker_blocker_with_recovery_context(
    blocker: Any,
    worker_result_evidence: str,
    draft_manifest: str | None,
) -> dict[str, Any]:
    enriched = dict(blocker) if isinstance(blocker, dict) else {
        "code": "UNSPECIFIED_BLOCKER",
        "detail": None,
        "resolution": None,
        "paths": None,
    }
    enriched["worker_result_evidence"] = worker_result_evidence
    if draft_manifest:
        enriched["draft_manifest"] = draft_manifest
    return enriched


def _structured_worker_draft_paths(
    active: dict[str, Any], result: dict[str, Any], changed_paths: list[str]
) -> list[str]:
    restored = active.get("restored_draft") or {}
    return sorted({
        str(path)
        for path in [
            *changed_paths,
            *(restored.get("paths") or []),
            *(result.get("changed_files") or []),
        ]
        if path
    })


def _latest_worker_draft(repo: Path, task_id: str, attempt: int, baseline: str) -> Path | None:
    root = repo / WORKER_DRAFT_DIR
    candidates = sorted(
        root.glob(f"{task_id}-a{attempt}-*/manifest.json"),
        key=lambda path: path.stat().st_mtime_ns,
        reverse=True,
    ) if root.is_dir() else []
    for manifest_path in candidates:
        manifest = read_json(manifest_path)
        if manifest.get("baseline_commit") == baseline:
            return manifest_path
    return None


def _restore_worker_draft(worktree: Path, manifest_path: Path) -> list[str]:
    manifest = read_json(manifest_path)
    archive_path = manifest_path.parent / "files.zip"
    restored: list[str] = []
    with zipfile.ZipFile(archive_path, "r") as archive:
        archive_names = set(archive.namelist())
        for relative, expected_hash in (manifest.get("files") or {}).items():
            if relative not in archive_names:
                raise RuntimeError(f"worker draft file missing: {relative}")
            data = archive.read(relative)
            if hashlib.sha256(data).hexdigest() != expected_hash:
                raise RuntimeError(f"worker draft hash mismatch: {relative}")
            target = _filesystem_path(worktree / relative)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            restored.append(relative)
    for relative in manifest.get("deleted_paths") or []:
        target = _filesystem_path(worktree / relative)
        if target.is_file():
            target.unlink()
        restored.append(relative)
    return sorted(restored)


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
    state_commit = _commit_state(repo, new_state, specs, f"[gov5.0] block {task_id}: {code}")
    draft_relative = detail.get("draft_manifest") if isinstance(detail, dict) else None
    if state_commit and draft_relative:
        draft_data = read_json(repo / draft_relative)
        draft_data["baseline_commit"] = state_commit
        write_json(repo / draft_relative, draft_data)
    return {
        "schema": "hhy.run-once/v5.0",
        "status": "INFRASTRUCTURE_BLOCKED",
        "task_id": task_id,
        "blocker": row["blocker"],
        "attempts_used": row["attempts_used"],
        "state_commit": state_commit,
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
    fast_lane = _fast_lane(Path.cwd())
    discovery_limit = int(fast_lane.get("max_read_only_discovery_tool_calls", 12))
    deadline = int(fast_lane.get("first_product_file_deadline_seconds", 600))
    return (
        "只执行 governance/runtime/ACTIVE_TASK.json 指定的唯一任务。\n"
        f"这是第 {attempt}/3 次总尝试，固定策略：{strategy}。\n"
        "禁止选择下一任务、修改治理/CI/Gate/AGENTS/状态、执行任何 Git 权威命令、自判正式 PASS。\n"
        "若 ACTIVE_TASK.latest_failure_evidence 非空，先按其中的具体文件、行号和问题修复，禁止重新扫描全部历史证据。\n"
        "失败证据可能来自已拒绝且未合并的 Candidate；引用路径在当前权威基线不存在时，必须依据冻结契约和任务目标新建实现，禁止仅因文件缺失返回 ATTEMPT_FAILED 或 blocker。\n"
        f"Fast Lane 硬规则：只读定位最多使用 {discovery_limit} 次工具调用；启动后 {deadline} 秒内必须开始一个允许路径内的真实产品文件，禁止先做全仓库扫描或把分析延伸到整个版本。\n"
        "大型任务必须拆成不超过 6 个 operationId 的可编译垂直切片；每个切片完成后立即运行最小验证并继续，不得把全部实现滞留到最终回复。\n"
        "若 ACTIVE_TASK.restored_draft 存在，前 5 次工具调用内必须运行适用的最小编译或目标测试；当前草稿的第一个真实失败优先于历史 latest_failure_evidence，修复后立即复验。\n"
        "必须审查并继续恢复草稿中的有效改动；草稿不是 PASS，仍须完成编译、测试和结果契约。\n"
        "必须保留 Task Gate、独立 Reviewer、全量测试、APK、模拟器和发布门禁；Fast Lane 只减少分析和返工，不得弱化任何门禁。\n"
        "在 Windows 上所有 shell/tool 命令必须串行执行；上一条结束前禁止并发启动下一条。\n"
        "所有文件路径必须完整保留 ACTIVE_TASK.allowed_paths 的前缀（例如 scripts/，禁止截断为 cripts/ 或其他变体）。\n"
        "完成实际产品代码和测试后，运行必要的针对性验证。相同命令、输出和 Diff 不得重复。\n"
        "最终仅输出符合 worker-result.schema.json 的 CANDIDATE_READY、ATTEMPT_FAILED、EXTERNAL_BLOCKED 或 INFRASTRUCTURE_BLOCKED；必须包含 schema、status、task_id、summary、changed_files、commands_run、error_fingerprint、blocker 字段，无值时使用空数组、空字符串或 null；blocker 非 null 时必须包含 code、detail、resolution、paths、worker_result_evidence 五个字段，无值使用 null。"
    )


def _worker_takeover_prompt(task: dict[str, Any], attempt: int, failure: dict[str, Any]) -> str:
    fast_lane = _fast_lane(Path.cwd())
    discovery_limit = int(fast_lane.get("max_read_only_discovery_tool_calls", 12))
    deadline = int(fast_lane.get("first_product_file_deadline_seconds", 600))
    return (
        "你是接管当前任务的备用 Worker。主 Worker 没有提交可读取的结果，"
        "请直接接管并完成 ACTIVE_TASK.json 中的同一个任务，不要等待主 Worker。\n"
        f"任务 {task['id']}，第 {attempt}/3 次 Attempt。\n"
        "先检查当前工作区已有改动和 ACTIVE_TASK.json，保留有效改动，修复主 Worker 未完成的部分。"
        "禁止修改治理、状态、CI 或 AGENTS 文件，禁止选择下一任务，禁止执行 Git 权威命令。\n"
        "若 ACTIVE_TASK.latest_failure_evidence 非空，先按其中的具体文件、行号和问题修复，禁止重新扫描全部历史证据。\n"
        "失败证据可能来自已拒绝且未合并的 Candidate；引用路径在当前权威基线不存在时，必须依据冻结契约和任务目标新建实现，禁止仅因文件缺失返回 ATTEMPT_FAILED 或 blocker。\n"
        f"Fast Lane 硬规则：只读定位最多使用 {discovery_limit} 次工具调用；接管后 {deadline} 秒内必须开始真实产品文件，禁止重新扫描整个仓库。\n"
        "大型任务必须拆成不超过 6 个 operationId 的可编译垂直切片，每个切片立即最小验证；不得把全部实现滞留到最终回复。\n"
        "若 ACTIVE_TASK.restored_draft 存在，前 5 次工具调用内必须运行适用的最小编译或目标测试；当前草稿的第一个真实失败优先于历史 latest_failure_evidence，修复后立即复验。\n"
        "必须审查并继续恢复草稿中的有效改动；草稿不是 PASS，仍须完成编译、测试和结果契约。\n"
        "必须保留 Task Gate、独立 Reviewer、全量测试、APK、模拟器和发布门禁；Fast Lane 不改变 Attempt 预算。\n"
        "在 Windows 上所有 shell/tool 命令必须串行执行；上一条结束前禁止并发启动下一条。\n"
        "完成产品代码和针对性测试后，必须写出符合 worker-result.schema.json 的结果；blocker 非 null 时必须包含 code、detail、resolution、paths、worker_result_evidence 五个字段，无值使用 null。"
        f"主 Worker 接管摘要：{failure.get('stderr_tail') or failure.get('stdout_tail') or '未返回可用输出'}"
    )


def _persist_worker_failure(repo: Path, task_id: str, attempt: int, worker: dict[str, Any], result_path: Path) -> str:
    target = repo / WORKER_FAILURE_DIR / f"{task_id}-a{attempt}-{uuid.uuid4().hex[:10]}.json"
    payload: dict[str, Any] = {
        "schema": "hhy.worker-failure-evidence/v5.0",
        "at": utc_now(),
        "task_id": task_id,
        "attempt": attempt,
        "result_file_present": result_path.is_file(),
        "execution": worker,
    }
    if result_path.is_file():
        try:
            payload["worker_result"] = read_json(result_path)
        except Exception as exc:
            payload["worker_result_read_error"] = str(exc)
    write_json(target, payload)
    return target.relative_to(repo).as_posix()


def build_active_task_payload(
    task_id: str, spec: dict[str, Any], attempt: int, baseline: str
) -> dict[str, Any]:
    constitution = read_yaml(Path.cwd() / "governance" / "DEVELOPMENT_CONSTITUTION.yaml") or {}
    limits = constitution.get("limits") or {}
    return {
        "schema": "hhy.active-task/v5.0",
        "task_id": task_id,
        "release": spec["release"],
        "attempt": attempt,
        "maximum_attempts": 3,
        "baseline_commit": baseline,
        "objective": spec["objective"],
        "deliverables": spec["deliverables"],
        "acceptance": spec["acceptance"],
        "latest_failure_evidence": spec.get("latest_failure_evidence"),
        "allowed_paths": spec["allowed_paths"],
        "protected_paths": [
            "AGENTS.md", "governance/**", "tools/governance/**", ".codex/**",
            ".github/workflows/**", ".githooks-v5/**", "tests/governance_v5/**",
            "CURRENT_STATUS.yaml", "NEXT_TASK.yaml", ".continuity/**",
            "releases/*/TASKS.yaml", "design/effect-previews/**",
        ],
        "acceptance_commands": spec["acceptance_commands"],
        "risks": spec["risks"],
        "limits": {
            "max_tool_calls": int(limits.get("maximum_worker_tool_calls", 100)),
            "max_compactions": int(limits.get("maximum_compactions_per_attempt", 1)),
        },
        "ledger_path": "governance/runtime/command-ledger.jsonl",
    }


def _attempt_failure(
    repo: Path,
    state: dict[str, Any],
    specs: dict[str, dict[str, Any]],
    task_id: str,
    reason: str,
    fingerprint: str | None = None,
    diagnostic_path: str | None = None,
) -> dict[str, Any]:
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
    result = {
        "schema": "hhy.run-once/v5.0",
        "status": status,
        "task_id": task_id,
        "attempts_used": row["attempts_used"],
        "reason": reason,
        "exit_code": exit_code,
    }
    if diagnostic_path:
        result["diagnostic_path"] = diagnostic_path
    return result


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
        resumed_state = _auto_resume_transient_provider(repo, state, specs, task_id)
        if resumed_state is not None:
            state = resumed_state
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
        codex = resolve_codex_executable()
        if not codex:
            row["status"] = "INFRASTRUCTURE_BLOCKED"
            row["blocker"] = {"code": "CODEX_CLI_MISSING_OR_INACCESSIBLE", "resolution": "Install/authenticate a user-accessible Codex CLI, then unblock with evidence."}
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
            active = build_active_task_payload(task_id, spec, attempt, baseline)
            active["latest_failure_evidence"] = worker_failure_evidence(spec, row)
            draft_manifest = _latest_worker_draft(repo, task_id, attempt, baseline)
            if draft_manifest:
                active["restored_draft"] = {
                    "manifest": draft_manifest.relative_to(repo).as_posix(),
                    "paths": _restore_worker_draft(worktree, draft_manifest),
                    "quality_status": "UNVERIFIED_REQUIRES_WORKER_GATE_AND_REVIEWER",
                }
            runtime = worktree / "governance" / "runtime"
            runtime.mkdir(parents=True, exist_ok=True)
            write_json(runtime / "ACTIVE_TASK.json", active)
            result_path = runtime / "worker-result.json"
            schema_path = worktree / "governance" / "schemas" / "worker-result.schema.json"
            command = [
                codex, "--ask-for-approval", "never", "exec", "--ephemeral", "--sandbox", "workspace-write",
                *worker_sandbox_args(), "--json", "--output-schema", str(schema_path), "-o", str(result_path), "-C", str(worktree),
                _worker_prompt(spec, attempt),
            ]
            worker = run_with_progress_timeout(
                command,
                worktree,
                timeout=_worker_timeout(repo),
                startup_timeout=_worker_startup_timeout(repo),
                idle_timeout=_worker_idle_timeout(repo),
                progress_paths=spec["allowed_paths"],
                poll_interval=_worker_progress_poll(repo),
                initial_product_progress=False,
                env={"HHY_GOVERNANCE_ROLE": "WORKER"},
            )
            if worker["status"] != "PASS" or not result_path.is_file():
                first_failure = worker
                first_diagnostic = _persist_worker_failure(repo, task_id, attempt, worker, result_path)
                infrastructure_code = _worker_infrastructure_code(worker)
                if infrastructure_code:
                    draft = _preserve_worker_draft(
                        repo, worktree, task_id, attempt, baseline, spec["allowed_paths"]
                    )
                    return _infrastructure_block(
                        repo, state, specs, task_id, infrastructure_code,
                        {"diagnostic_path": first_diagnostic, "draft_manifest": draft},
                    )
                result_path.unlink(missing_ok=True)
                takeover_command = [
                    codex, "--ask-for-approval", "never", "exec", "--ephemeral", "--sandbox", "workspace-write",
                    *worker_sandbox_args(), "--json", "--output-schema", str(schema_path), "-o", str(result_path), "-C", str(worktree),
                    _worker_takeover_prompt(spec, attempt, first_failure),
                ]
                worker = run_with_progress_timeout(
                    takeover_command,
                    worktree,
                    timeout=3600,
                    startup_timeout=_worker_startup_timeout(repo),
                    idle_timeout=_worker_idle_timeout(repo),
                    progress_paths=spec["allowed_paths"],
                    poll_interval=_worker_progress_poll(repo),
                    initial_product_progress=False,
                    env={"HHY_GOVERNANCE_ROLE": "WORKER_TAKEOVER"},
                )
                if worker["status"] != "PASS" or not result_path.is_file():
                    diagnostic = _persist_worker_failure(repo, task_id, attempt, worker, result_path)
                    infrastructure_code = _worker_infrastructure_code(worker)
                    if infrastructure_code:
                        draft = _preserve_worker_draft(
                            repo, worktree, task_id, attempt, baseline, spec["allowed_paths"]
                        )
                        return _infrastructure_block(
                            repo, state, specs, task_id, infrastructure_code,
                            {
                                "diagnostic_path": diagnostic,
                                "primary_diagnostic_path": first_diagnostic,
                                "draft_manifest": draft,
                            },
                        )
                    reason = (
                        "主 Worker 未返回结果，备用 Worker 接管也未返回结果"
                        f"；主证据={first_diagnostic}；备用证据={diagnostic}"
                    )
                    return _attempt_failure(
                        repo, state, specs, task_id, reason,
                        diagnostic_path=diagnostic,
                    )
            try:
                result = read_json(result_path)
            except Exception as exc:
                diagnostic = _persist_worker_failure(
                    repo,
                    task_id,
                    attempt,
                    {**worker, "result_read_error": str(exc)},
                    result_path,
                )
                return _attempt_failure(
                    repo, state, specs, task_id,
                    f"Worker 已退出但结果文件无法读取：{exc}",
                    diagnostic_path=diagnostic,
                )
            status = result.get("status")
            changed = _worker_changed_paths(worktree)
            ok, violations = _scope_ok(changed, spec["allowed_paths"])
            if not ok:
                draft = _preserve_allowed_worker_draft(
                    repo, worktree, task_id, attempt, baseline,
                    spec["allowed_paths"], changed,
                )
                row["status"] = "POLICY_VIOLATION"
                row["blocker"] = {
                    "code": "WORKER_SCOPE_VIOLATION",
                    "paths": violations,
                    "draft_manifest": draft,
                }
                state["project"]["status"] = "POLICY_VIOLATION"
                state["project"]["active_task"] = None
                state["lease"] = None
                rev = state["revision"]
                write_state(repo, state, expected_revision=rev)
                state = read_state(repo)
                _commit_state(repo, state, specs, f"[gov5.0] policy violation {task_id}")
                return {
                    "schema": "hhy.run-once/v5.0",
                    "status": "POLICY_VIOLATION",
                    "paths": violations,
                    "draft_manifest": draft,
                    "exit_code": 22,
                }
            if status in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED"}:
                result_evidence = worktree / "governance" / "evidence" / "recovery" / f"{task_id}-a{attempt}-worker-result.json"
                write_json(result_evidence, result)
                result_rel = result_evidence.relative_to(worktree).as_posix()
                _copy_worker_evidence(worktree, repo, result_rel)
                draft = _write_worker_draft(
                    repo, worktree, task_id, attempt, baseline, spec["allowed_paths"],
                    _structured_worker_draft_paths(active, result, changed),
                )
                row["status"] = status
                row["blocker"] = _worker_blocker_with_recovery_context(
                    result.get("blocker"), result_rel, draft,
                )
                row["current_attempt"] = None
                state["project"]["status"] = status
                state["lease"] = None
                rev = state["revision"]
                write_state(repo, state, expected_revision=rev)
                state = read_state(repo)
                state_commit = _commit_state(
                    repo, state, specs, f"[gov5.0] block {task_id}",
                    extra_paths=[result_rel],
                )
                if state_commit and draft:
                    draft_data = read_json(repo / draft)
                    draft_data["baseline_commit"] = state_commit
                    write_json(repo / draft, draft_data)
                return {
                    "schema": "hhy.run-once/v5.0",
                    "status": status,
                    "task_id": task_id,
                    "blocker": state["tasks"][task_id]["blocker"],
                    "attempts_used": state["tasks"][task_id]["attempts_used"],
                    "worker_result_evidence": result_rel,
                    "draft_manifest": draft,
                    "state_commit": state_commit,
                    "exit_code": 21,
                }
            if status != "CANDIDATE_READY" or not changed:
                return _attempt_failure(repo, state, specs, task_id, str(result.get("summary") or "worker did not produce a candidate"), str(result.get("error_fingerprint") or ""))
            git(worktree, "add", "-A", env=AUTH_ENV)
            try:
                git(worktree, "commit", "-m", f"[{task_id}] bounded attempt {attempt} candidate", env=AUTH_ENV)
            except Exception as exc:
                hook = run([str(worktree / ".githooks-v5" / "pre-commit")], worktree, timeout=300, env=AUTH_ENV)
                diagnostic = {
                    "schema": "hhy.worker-commit-failure/v5.0",
                    "task_id": task_id,
                    "attempt": attempt,
                    "baseline_commit": baseline,
                    "changed_paths": changed,
                    "status_porcelain": git_status_lines(worktree, "-c", "core.quotepath=false", "status", "--porcelain=v1", "-uall", check=False),
                    "staged_paths": git(worktree, "diff", "--cached", "--name-status", check=False).splitlines(),
                    "staged_diff_check": git(worktree, "diff", "--cached", "--check", check=False),
                    "hook": hook,
                    "commit_error": str(exc),
                }
                diagnostic_path = WORKER_FAILURE_DIR / f"{task_id}-a{attempt}-commit-failure.json"
                write_json(repo / diagnostic_path, diagnostic)
                raise RuntimeError(f"worker candidate commit failed; diagnostic={diagnostic_path.as_posix()}") from exc
            candidate = git(worktree, "rev-parse", "HEAD")
            gate_profile = task_gate_profile(spec)
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
            if gate_profile == "freeze":
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


def reclassify_infrastructure_attempt(
    repo: Path,
    task_id: str,
    evidence: Path,
    draft_source: Path | None = None,
    draft_paths: list[str] | None = None,
) -> dict[str, Any]:
    """Correct an engineering attempt consumed by a proven Worker infrastructure outage."""
    with repository_lock(repo):
        if git(repo, "status", "--porcelain=v1", "-uall"):
            raise RuntimeError("authority worktree must be clean before reclassification")
        specs = load_task_specs(repo)
        state = read_state(repo)
        if state["project"].get("active_task") != task_id:
            raise RuntimeError("task must be the current active task")
        row = state["tasks"][task_id]
        if row.get("status") != "READY" or int(row.get("attempts_used") or 0) < 1:
            raise RuntimeError("task must be READY with at least one consumed attempt")
        evidence = evidence.resolve()
        try:
            evidence_rel = evidence.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("failure evidence must be inside repository") from exc
        if evidence_rel not in str(row.get("last_error_fingerprint") or ""):
            raise RuntimeError("evidence is not bound to the latest attempt failure")
        failure = read_json(evidence)
        infrastructure_code = _worker_infrastructure_code(failure.get("execution") or {})
        if not infrastructure_code:
            raise RuntimeError("evidence does not prove a recognized infrastructure failure")
        attempt = int(failure.get("attempt") or 0)
        if attempt != int(row.get("attempts_used") or 0):
            raise RuntimeError("evidence attempt does not match current attempts_used")

        draft_manifest = None
        if draft_source:
            source = draft_source.resolve()
            work_root = (repo / ".git" / "hhy-governance-worktrees").resolve()
            try:
                source.relative_to(work_root)
            except ValueError as exc:
                raise RuntimeError("draft source must be an isolated governance worktree") from exc
            active_path = source / "governance" / "runtime" / "ACTIVE_TASK.json"
            active = read_json(active_path)
            if active.get("task_id") != task_id or int(active.get("attempt") or 0) != attempt:
                raise RuntimeError("draft source does not match task and attempt")
            expanded: list[str] = []
            for relative in draft_paths or []:
                candidate = source / relative
                if candidate.is_dir():
                    expanded.extend(
                        path.relative_to(source).as_posix()
                        for path in candidate.rglob("*") if path.is_file()
                    )
                elif candidate.is_file():
                    expanded.append(relative.replace("\\", "/"))
                else:
                    raise RuntimeError(f"draft path does not exist: {relative}")
            changed = sorted({
                relative for relative in expanded
                if not (repo / relative).is_file()
                or _file_sha256(source / relative) != _file_sha256(repo / relative)
            })
            draft_manifest = _write_worker_draft(
                repo, source, task_id, attempt, str(active["baseline_commit"]),
                specs[task_id]["allowed_paths"], changed,
            )
            if changed and not draft_manifest:
                raise RuntimeError("failed to preserve the infrastructure-interrupted draft")

        row["attempts_used"] = attempt - 1
        row["last_error_fingerprint"] = (
            f"RECLASSIFIED_INFRASTRUCTURE:{infrastructure_code};evidence={evidence_rel}"
        )
        row["current_attempt"] = None
        row["status"] = "READY"
        row["blocker"] = None
        state["project"]["status"] = "ACTIVE"
        state["project"]["active_task"] = task_id
        state["lease"] = None
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        new_state = read_state(repo)
        commit_id = _commit_state(
            repo, new_state, specs,
            f"[gov5.0] reclassify {task_id} attempt {attempt} as infrastructure",
        )
        if commit_id and draft_manifest:
            draft_data = read_json(repo / draft_manifest)
            draft_data["baseline_commit"] = commit_id
            write_json(repo / draft_manifest, draft_data)
        return {
            "schema": "hhy.reclassify-infrastructure-attempt/v5.0",
            "status": "READY",
            "task_id": task_id,
            "attempts_used": attempt - 1,
            "infrastructure_code": infrastructure_code,
            "evidence": evidence_rel,
            "draft_manifest": draft_manifest,
            "state_commit": commit_id,
        }


def unblock(repo: Path, task_id: str, evidence: Path) -> dict[str, Any]:
    with repository_lock(repo):
        specs = load_task_specs(repo)
        state = read_state(repo)
        row = state["tasks"][task_id]
        policy_cleanup_pending = row["status"] == "READY" and state["project"].get("status") == "POLICY_VIOLATION"
        if row["status"] not in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED", "POLICY_VIOLATION"} and not policy_cleanup_pending:
            raise RuntimeError("task is not blocked")
        evidence = evidence.resolve()
        try:
            rel = evidence.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("unblock evidence must be inside repository") from exc
        data = read_json(evidence)
        if not data.get("resolved") or data.get("task_id") != task_id:
            raise RuntimeError("unblock evidence must bind task and resolved=true")
        if (row["status"] == "POLICY_VIOLATION" or policy_cleanup_pending) and (
            data.get("violation_disposition") != "REJECTED_UNMERGED"
            or data.get("authority_worktree_clean") is not True
        ):
            raise RuntimeError("policy violation evidence must prove the violating candidate was rejected and authority worktree is clean")
        draft_manifest = _draft_manifest_from_blocker(row.get("blocker"))
        row["status"] = "READY"
        row["blocker"] = None
        state["project"]["active_task"] = task_id
        if state["project"]["status"] in {"INFRASTRUCTURE_BLOCKED", "EXTERNAL_BLOCKED", "POLICY_VIOLATION"}:
            state["project"]["status"] = "ACTIVE"
        rev = state["revision"]
        write_state(repo, state, expected_revision=rev)
        state = read_state(repo)
        commit_id = _commit_state(repo, state, specs, f"[gov5.0] unblock {task_id}")
        if commit_id and draft_manifest and (repo / draft_manifest).is_file():
            draft_data = read_json(repo / draft_manifest)
            draft_data["baseline_commit"] = commit_id
            write_json(repo / draft_manifest, draft_data)
        return {
            "schema": "hhy.unblock/v5.0", "status": "READY", "task_id": task_id,
            "evidence": rel, "draft_manifest": draft_manifest, "state_commit": commit_id,
        }


def supersede_failed(repo: Path, from_task: str, to_task: str, authorization: Path) -> dict[str, Any]:
    """Atomically authorize a bounded repair task after a FAILED_BOUNDED task.

    This is the only state transition that can replace a failed task. It never
    changes the predecessor's attempt count or status and never creates a
    worker attempt itself.
    """
    with repository_lock(repo):
        if from_task != FAILED_RECOVERY_TASK_ID or to_task != REPAIR_RECOVERY_TASK_ID:
            raise RuntimeError("only TASK-R14-RECOVERY-001 -> TASK-R14-RECOVERY-002 is supported")
        state = read_state(repo)
        if state["project"].get("status") != "FAILED_BOUNDED":
            raise RuntimeError("project must be FAILED_BOUNDED")
        predecessor = state["tasks"].get(from_task)
        if not predecessor or predecessor.get("status") != "FAILED_BOUNDED" or predecessor.get("attempts_used") != 3:
            raise RuntimeError("predecessor must remain FAILED_BOUNDED with exactly 3 attempts")
        if to_task in state["tasks"]:
            raise RuntimeError("repair task already exists in authoritative state")
        authorization = authorization.resolve()
        try:
            auth_rel = authorization.relative_to(repo.resolve()).as_posix()
        except ValueError as exc:
            raise RuntimeError("authorization evidence must be inside repository") from exc
        auth = read_json(authorization)
        schema_path = repo / "governance" / "schemas" / "failed-recovery-authorization.schema.json"
        schema = read_json(schema_path)
        errors = sorted(Draft202012Validator(schema).iter_errors(auth), key=lambda error: list(error.path))
        if errors:
            raise RuntimeError("invalid recovery authorization: " + "; ".join(error.message for error in errors))
        baseline = git(repo, "rev-parse", "HEAD")
        if auth.get("baseline_commit") != baseline:
            raise RuntimeError("authorization baseline_commit must equal current HEAD")
        if git(repo, "status", "--porcelain=v1", "-uall"):
            dirty = [
                line[3:]
                for line in git_status_lines(repo, "status", "--porcelain=v1", "-uall")
                if len(line) >= 4
            ]
            if dirty != [auth_rel]:
                raise RuntimeError("only the authorization evidence may be uncommitted")

        template_path = repo / "governance" / "recovery_templates" / f"{to_task}.yaml"
        template = read_yaml(template_path) or {}
        task_schema = read_json(repo / "governance" / "schemas" / "task-spec.schema.json")
        task_errors = sorted(Draft202012Validator(task_schema).iter_errors(template), key=lambda error: list(error.path))
        if task_errors:
            raise RuntimeError("invalid repair task template: " + "; ".join(error.message for error in task_errors))
        if template.get("id") != to_task or template.get("supersedes") != from_task or template.get("maximum_attempts") != 3:
            raise RuntimeError("repair template is not bound to the failed predecessor")

        target_path = repo / "governance" / "task_specs" / f"{to_task}.yaml"
        r15_path = repo / "governance" / "task_specs" / "TASK-R15-001.yaml"
        plan_path = repo / "governance" / "PROGRAM_PLAN.yaml"
        transition_paths = [
            target_path, r15_path, plan_path,
            repo / "governance" / "STATE.yaml",
            repo / "CURRENT_STATUS.yaml", repo / "NEXT_TASK.yaml",
            repo / "governance" / "views" / "project-status.json",
            repo / "governance" / "views" / "release-status.json",
        ]
        original_files = {path: path.read_bytes() if path.is_file() else None for path in transition_paths}
        try:
            write_yaml(target_path, template)
            r15 = read_yaml(r15_path) or {}
            r15["depends_on"] = [to_task]
            write_yaml(r15_path, r15)
            plan = read_yaml(plan_path) or {}
            plan["task_count"] = int(plan.get("task_count") or 0) + 1
            plan.setdefault("release_tasks", {}).setdefault("R14", []).append(to_task)
            plan["recovery_tasks"] = list(plan.get("recovery_tasks") or []) + [to_task]
            write_yaml(plan_path, plan)
            specs = load_task_specs(repo)
            errors = validate_specs(specs, plan)
            if errors:
                raise RuntimeError("repair task specs invalid: " + "; ".join(errors))
            state["project"]["status"] = "ACTIVE"
            state["project"]["active_release"] = "R14"
            state["project"]["active_task"] = to_task
            state["project"]["authoritative_commit"] = baseline
            state["lease"] = None
            state["tasks"][to_task] = {
                "status": "READY",
                "attempts_used": 0,
                "current_attempt": None,
                "last_error_fingerprint": None,
                "last_candidate_commit": None,
                "last_gate_evidence": None,
                "blocker": None,
            }
            rev = state["revision"]
            write_state(repo, state, expected_revision=rev)
            state = read_state(repo)
            commit_id = _commit_state(
                repo,
                state,
                specs,
                f"[gov5.0] supersede {from_task} with {to_task}",
                [auth_rel, str(target_path.relative_to(repo)), str(r15_path.relative_to(repo)), str(plan_path.relative_to(repo))],
            )
            return {
                "schema": "hhy.failed-recovery-supersede/v5.0",
                "status": "READY",
                "supersedes": from_task,
                "task_id": to_task,
                "baseline_commit": baseline,
                "authorization": auth_rel,
                "state_commit": commit_id,
                "attempts_used": 0,
                "maximum_attempts": 3,
            }
        except Exception:
            for path, raw in original_files.items():
                if raw is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_bytes(raw)
            git(repo, "reset", "--quiet", "--", auth_rel, *[str(path.relative_to(repo)) for path in transition_paths], check=False, env=AUTH_ENV)
            raise


def build_auto_recovery_spec(
    source: dict[str, Any], from_task: str, to_task: str, release: str, ordinal: int
) -> dict[str, Any]:
    """Build one bounded recovery spec without inheriting prior recovery prose."""
    repair = copy.deepcopy(source)
    release_entry_contract = f"python3 scripts/check_{release.lower()}_entry_contract.py"
    acceptance_commands = [
        command for command in repair.get("acceptance_commands") or []
        if "check_ui_visual_acceptance.py" not in command
    ]
    if release == "R14" and release_entry_contract not in acceptance_commands:
        acceptance_commands.append(release_entry_contract)
    recovery_gate_profile = str(source.get("gate_profile") or "")
    if not recovery_gate_profile:
        recovery_gate_profile = "freeze" if source.get("kind") in {"release_close", "recovery"} else "task"
    gate_label = "freeze Gate" if recovery_gate_profile == "freeze" else "task Gate"
    product_contract = copy.deepcopy(source.get("recovery_product_contract") or {})
    if not product_contract and source.get("kind") != "recovery":
        product_contract = {
            "requirements": list(source.get("requirements") or []),
            "objective": str(source.get("objective") or ""),
            "deliverables": list(source.get("deliverables") or []),
            "acceptance": list(source.get("acceptance") or []),
        }
    control_requirements = [
        "仅使用当前权威 HEAD 作为源码基线",
        "保留前序失败任务及其三次尝试记录",
        "不得继承前序 Candidate、APK、截图、视觉批准或 PASS",
        "Worker 阶段只生成可接受的源码 Commit，不得伪造 Gate 或审查证据",
    ]
    product_objective = str(product_contract.get("objective") or "")
    repair.update({
        "id": to_task,
        "ordinal": ordinal,
        "title": f"{release} 自动有界恢复：源码冻结就绪",
        "kind": "recovery",
        "gate_profile": recovery_gate_profile,
        "recovery_product_contract": product_contract,
        "source": {"path": "Supervisor automatic bounded recovery", "original_id": from_task},
        "supersedes": from_task,
        "depends_on": [],
        "requirements": list(dict.fromkeys(list(product_contract.get("requirements") or []) + control_requirements)),
        "objective": f"修复最新失败证据确认的源码或规则根因并完成原任务目标：{product_objective}；生成通过 {gate_label} 和独立审查的源码 Commit。",
        "deliverables": list(dict.fromkeys(list(product_contract.get("deliverables") or []) + [
            "最新失败根因对应的实际修复",
            f"通过 {gate_label} 与独立审查的源码 Commit",
        ])),
        "acceptance": list(dict.fromkeys(list(product_contract.get("acceptance") or []) + [
            f"{from_task} 永久保持 FAILED_BOUNDED",
            "不继承或冒用旧 Candidate、APK、截图、视觉批准或 PASS",
            "Worker 候选不得伪造 Gate、审查或自动化证据",
            f"源码 Commit 通过 {gate_label} 和独立只读审查",
            f"{release} 达到 MACHINE_CLOSED 后才允许激活下一版本",
        ])),
        "acceptance_commands": acceptance_commands,
        "legacy_status": "AUTOMATIC_RECOVERY_PLANNED",
    })
    return repair


def bounded_recovery_candidates(
    state: dict[str, Any], specs: dict[str, dict[str, Any]], release: str
) -> list[tuple[int, str, dict[str, Any]]]:
    """Return failed leaf tasks that do not already have a recovery successor."""
    covered = {
        str(spec["supersedes"])
        for spec in specs.values()
        if spec.get("release") == release and spec.get("supersedes")
    }
    return [
        (int(spec.get("ordinal") or 0), task_id, spec)
        for task_id, spec in specs.items()
        if spec.get("release") == release
        and task_id not in covered
        and (state.get("tasks") or {}).get(task_id, {}).get("status") == "FAILED_BOUNDED"
        and (state.get("tasks") or {}).get(task_id, {}).get("attempts_used") == 3
    ]


def stale_auto_recovery_task(
    state: dict[str, Any], specs: dict[str, dict[str, Any]]
) -> str | None:
    """Detect an unstarted duplicate recovery for an already repaired predecessor."""
    project = state.get("project") or {}
    active_task = str(project.get("active_task") or "")
    active_spec = specs.get(active_task) or {}
    active_row = (state.get("tasks") or {}).get(active_task) or {}
    predecessor = str(active_spec.get("supersedes") or "")
    if (
        project.get("status") != "ACTIVE"
        or active_spec.get("kind") != "recovery"
        or (active_spec.get("source") or {}).get("path") != "Supervisor automatic bounded recovery"
        or active_row.get("status") != "READY"
        or active_row.get("attempts_used") != 0
        or not predecessor
    ):
        return None
    successful_successors = [
        task_id
        for task_id, spec in specs.items()
        if task_id != active_task
        and spec.get("supersedes") == predecessor
        and (state.get("tasks") or {}).get(task_id, {}).get("status") == "DONE"
    ]
    return active_task if successful_successors else None


def auto_supersede_failed(repo: Path, policy_path: Path | None = None) -> dict[str, Any]:
    """Create the next bounded repair task under a standing owner policy.

    This never edits a failed task or increases its attempt budget. It only
    creates a new repair task, rebinds downstream dependencies, and returns
    the project to ACTIVE so the normal Supervisor/Gate flow can continue.
    """
    policy_path = (policy_path or (repo / "governance" / "evidence" / "recovery" / "PROJECT_OWNER_STANDING_AUTHORIZATION.json")).resolve()
    with repository_lock(repo):
        policy = read_json(policy_path)
        if not isinstance(policy, dict):
            raise RuntimeError("standing recovery policy is missing or invalid")
        required = {
            "schema": "hhy.project-owner-standing-recovery/v5.0",
            "status": "ENABLED",
            "authorized_by": "PROJECT_OWNER",
            "mode": "AUTONOMOUS_BOUNDED_RECOVERY",
        }
        if any(policy.get(key) != value for key, value in required.items()):
            raise RuntimeError("standing recovery policy is not enabled by PROJECT_OWNER")
        if policy.get("preserve_attempt_bounds") is not True or policy.get("require_new_task_per_failed_task") is not True:
            raise RuntimeError("standing recovery policy must preserve bounded attempts")

        state = read_state(repo)
        specs = load_task_specs(repo)
        project = state.get("project") or {}
        stale_recovery = stale_auto_recovery_task(state, specs)
        if project.get("status") != "FAILED_BOUNDED" and stale_recovery is None:
            return {"schema": "hhy.auto-recovery/v5.0", "status": "NO_ACTION", "reason": "project is not FAILED_BOUNDED"}
        release = str(project.get("active_release") or "")
        allowed_releases = policy.get("allowed_releases") or []
        if allowed_releases and release not in allowed_releases:
            raise RuntimeError(f"standing recovery policy does not cover release {release}")

        failed = bounded_recovery_candidates(state, specs, release)
        if not failed:
            return {"schema": "hhy.auto-recovery/v5.0", "status": "NO_ACTION", "reason": "no bounded failed task is ready for recovery"}
        _, from_task, source = sorted(failed)[-1]

        max_generations = int(policy.get("max_recovery_generations") or 20)
        generation_count = sum(1 for spec in specs.values() if spec.get("release") == release and spec.get("kind") == "recovery")
        if generation_count >= max_generations:
            return {
                "schema": "hhy.auto-recovery/v5.0",
                "status": "RECOVERY_LIMIT_REACHED",
                "release": release,
                "failed_task": from_task,
                "generations": generation_count,
                "limit": max_generations,
            }

        pattern = re.compile(rf"^TASK-{re.escape(release)}-RECOVERY-(\d+)$")
        numbers = [
            int(match.group(1))
            for task_id in specs
            if (match := pattern.match(task_id))
        ]
        to_task = f"TASK-{release}-RECOVERY-{(max(numbers or [0]) + 1):03d}"
        baseline = git(repo, "rev-parse", "HEAD")
        plan_path = repo / "governance" / "PROGRAM_PLAN.yaml"
        target_path = repo / "governance" / "task_specs" / f"{to_task}.yaml"
        dependent_paths: list[Path] = []
        original_files: dict[Path, bytes | None] = {}
        changed_specs = copy.deepcopy(specs)

        repair = build_auto_recovery_spec(
            source,
            from_task,
            to_task,
            release,
            max(int(spec.get("ordinal") or 0) for spec in specs.values() if spec.get("release") == release) + 1,
        )
        repair["latest_failure_evidence"] = merged_recovery_failure_evidence(
            source,
            (state.get("tasks") or {}).get(from_task, {}).get("last_error_fingerprint"),
        )
        changed_specs[to_task] = repair

        for task_id, spec in changed_specs.items():
            if task_id == to_task:
                continue
            dependencies = list(spec.get("depends_on") or [])
            if from_task not in dependencies:
                continue
            spec["depends_on"] = [to_task if dep == from_task else dep for dep in dependencies]
            dependent_paths.append(repo / "governance" / "task_specs" / f"{task_id}.yaml")

        plan = read_yaml(plan_path) or {}
        plan["task_count"] = len(changed_specs)
        plan.setdefault("release_tasks", {}).setdefault(release, []).append(to_task)
        plan["recovery_tasks"] = [
            task_id for task_id, spec in sorted(changed_specs.items(), key=lambda item: (item[1].get("release", ""), item[1].get("ordinal", 0), item[0]))
            if spec.get("kind") == "recovery"
        ]
        changed_plan = plan

        transition_paths = [
            target_path, plan_path, *dependent_paths,
            repo / "governance" / "STATE.yaml", repo / "CURRENT_STATUS.yaml", repo / "NEXT_TASK.yaml",
            repo / "governance" / "views" / "project-status.json", repo / "governance" / "views" / "release-status.json",
        ]
        original_files = {path: path.read_bytes() if path.is_file() else None for path in transition_paths}
        try:
            if stale_recovery:
                stale_row = state["tasks"][stale_recovery]
                stale_row["status"] = "SUPERSEDED"
                stale_row["current_attempt"] = None
                stale_row["blocker"] = None
            write_yaml(target_path, repair)
            for path in dependent_paths:
                task_id = path.stem
                write_yaml(path, changed_specs[task_id])
            write_yaml(plan_path, changed_plan)
            loaded = load_task_specs(repo)
            errors = validate_specs(loaded, changed_plan)
            if errors:
                raise RuntimeError("automatic recovery task specs invalid: " + "; ".join(errors))
            state["project"]["status"] = "ACTIVE"
            state["project"]["active_release"] = release
            state["project"]["active_task"] = to_task
            state["project"]["authoritative_commit"] = baseline
            state["lease"] = None
            state["tasks"][to_task] = {
                "status": "READY",
                "attempts_used": 0,
                "current_attempt": None,
                "last_error_fingerprint": None,
                "last_candidate_commit": None,
                "last_gate_evidence": None,
                "blocker": None,
            }
            rev = state["revision"]
            write_state(repo, state, expected_revision=rev)
            state = read_state(repo)
            commit_id = _commit_state(
                repo,
                state,
                loaded,
                f"[gov5.0] automatic recovery {from_task} -> {to_task}",
                [str(path.relative_to(repo)) for path in [target_path, plan_path, *dependent_paths]],
            )
            return {
                "schema": "hhy.auto-recovery/v5.0",
                "status": "READY",
                "release": release,
                "supersedes": from_task,
                "task_id": to_task,
                "baseline_commit": baseline,
                "state_commit": commit_id,
                "attempts_used": 0,
                "maximum_attempts": 3,
                "retired_stale_recovery": stale_recovery,
            }
        except Exception:
            for path, raw in original_files.items():
                if raw is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_bytes(raw)
            git(repo, "reset", "--quiet", "--", *[str(path.relative_to(repo)) for path in transition_paths], check=False, env=AUTH_ENV)
            raise
