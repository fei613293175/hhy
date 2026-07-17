#!/usr/bin/env python3
"""持续开发无状态接续强制门禁。

支持 doctor、pre-commit、commit-msg、pre-push 和 ci。任何项目内容变更都必须
与任务领取、检查点、Session Log、Current Status、Context Pack 和事件哈希链同步。
"""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
from typing import Any, Iterable
import json
import os
import re
import subprocess
import sys

import yaml

from continuity_lib import (
    ACTIVE_FILE,
    CONTINUITY_DIR,
    CR_INDEX_FILE,
    EVENT_LOG_FILE,
    PACKAGE_VERSION,
    SESSION_INDEX_FILE,
    TASK_CLAIMS_FILE,
    TASK_TRANSITIONS_FILE,
    GENERIC_SECRET_SCAN_EXCLUDE_PATTERNS,
    SECRET_SCAN_EXCLUDE_PATTERNS,
    SENSITIVE_FILENAME_PATTERNS,
    SUSPICIOUS_SECRET_REGEXES,
    ContinuityError,
    active_change_requests,
    allowed_path_errors,
    change_requirements,
    check_required_change_request,
    change_request_completeness_errors,
    canonical_json,
    classify_paths,
    context_is_fresh,
    current_session,
    expected_commit_trailers,
    filter_project_files,
    git,
    git_changed_files,
    git_info,
    is_git_repo,
    is_managed_record,
    latest_checkpoint,
    lease_is_expired,
    load_active_pointer,
    load_policy,
    load_checkpoint_by_id,
    load_session,
    load_state,
    load_yaml,
    now_utc,
    parse_iso,
    path_matches,
    project_fingerprint,
    canonical_fingerprint_bytes,
    read_current_status,
    read_next_task,
    root_from_script,
    run_command,
    secret_scan,
    session_record_path,
    sha256_file,
    sha256_text,
    validate_event_chain,
)

ROOT = root_from_script(__file__)

REQUIRED_ROOT_FILES = [
    ".continuity/CONTINUITY_POLICY.yaml",
    ".continuity/STATE.yaml",
    ".continuity/ACTIVE_SESSION.yaml",
    ".continuity/EVENT_LOG.jsonl",
    ".continuity/SESSION_INDEX.yaml",
    ".continuity/TASK_CLAIMS.yaml",
    ".continuity/CHANGE_REQUEST_INDEX.yaml",
    "catalogs/task_transition_ledger.csv",
    "catalogs/change_request_index.csv",
    "catalogs/handoff_index.csv",
    "catalogs/session_index.csv",
    ".continuity/TASK_TRANSITIONS.yaml",
    "CURRENT_STATUS.yaml",
    "NEXT_TASK.yaml",
    "AGENTS.md",
    "START_HERE.md",
    "scripts/continuity.py",
    "scripts/continuity_gate.py",
    "scripts/build_context_pack.py",
    "scripts/install_git_hooks.py",
    "docs/03-continuity/持续开发无状态接续强制门禁_V1.2.3.md",
    "docs/03-continuity/SESSION_RECORD_SCHEMA.yaml",
    "docs/03-continuity/CHECKPOINT_SCHEMA.yaml",
    "docs/03-continuity/HANDOFF_SCHEMA.yaml",
    "docs/03-continuity/EVENT_LOG_SCHEMA.yaml",
    "docs/03-continuity/CONTEXT_PACK_SCHEMA.yaml",
]

# Application-only commits cannot invalidate the frozen documentation baseline.
# Doctor/release modes still force the complete check.
DOCUMENT_GATE_INPUT_PATTERNS = (
    "PROJECT_BASELINE.yaml",
    "PROJECT_BASELINE.json",
    "V1.2.2_最终文档冻结说明.md",
    "合伙云Pro_完整项目开发文档_*.md",
    "catalogs/**",
    "contracts/**",
    "config/**",
    "database/**",
    "docs/00-baseline/**",
    "docs/01-architecture/**",
    "docs/02-config/**",
    "docs/02-contracts/**",
    "docs/02-ui/**",
    "releases/**",
    "scripts/check_v122_documentation.py",
    "scripts/check_v123_documentation.py",
)


class Report:
    def __init__(self, mode: str) -> None:
        self.mode = mode
        self.errors: list[dict[str, str]] = []
        self.warnings: list[dict[str, str]] = []
        self.metrics: dict[str, Any] = {}

    def require(self, condition: bool, code: str, message: str) -> None:
        if not condition:
            self.errors.append({"code": code, "message": message})

    def warn(self, condition: bool, code: str, message: str) -> None:
        if not condition:
            self.warnings.append({"code": code, "message": message})

    def payload(self, strict: bool) -> dict[str, Any]:
        status = "PASS" if not self.errors and (not strict or not self.warnings) else "FAIL"
        return {
            "version": "1.2.3",
            "protocol_version": "1.0",
            "mode": self.mode,
            "strict": strict,
            "status": status,
            "metrics": self.metrics,
            "errors": self.errors,
            "warnings": self.warnings,
        }


def relative_paths_from_diff(base_ref: str | None, head_ref: str) -> list[str]:
    if not is_git_repo(ROOT):
        return []
    if not base_ref:
        return git_changed_files(ROOT)
    # 支持分支、SHA和GitHub origin/<base>。
    candidates = [base_ref]
    if not re.fullmatch(r"[0-9a-fA-F]{7,64}", base_ref):
        candidates.extend([f"origin/{base_ref}", f"refs/remotes/origin/{base_ref}"])
    base = None
    for candidate in candidates:
        result = run_command(["git", "rev-parse", "--verify", candidate], cwd=ROOT)
        if result.returncode == 0:
            base = result.stdout.strip()
            break
    if not base:
        raise ContinuityError(f"无法解析base-ref：{base_ref}")
    merge_base = git(ROOT, "merge-base", base, head_ref, check=True)
    output = git(ROOT, "diff", "--name-only", "--diff-filter=ACDMRTUXB", f"{merge_base}..{head_ref}")
    return sorted({line.strip() for line in output.splitlines() if line.strip()})


def session_candidates_from_paths(paths: Iterable[str]) -> list[dict[str, Any]]:
    sessions: list[dict[str, Any]] = []
    for relative in paths:
        match = re.fullmatch(r"\.continuity/sessions/(SES-[^/]+)\.yaml", relative)
        if not match:
            continue
        path = ROOT / relative
        if path.exists():
            record = load_yaml(path, {})
            if record:
                sessions.append(record)
    sessions.sort(key=lambda row: row.get("updated_at") or row.get("started_at") or "")
    return sessions


def latest_relevant_session(paths: list[str]) -> dict[str, Any] | None:
    active = current_session(ROOT)
    if active:
        return active
    candidates = session_candidates_from_paths(paths)
    return candidates[-1] if candidates else None


def mandatory_record_paths(session: dict[str, Any], checkpoint_path: str | None = None) -> set[str]:
    required = {
        EVENT_LOG_FILE,
        ACTIVE_FILE,
        "CURRENT_STATUS.yaml",
        session["session_log"],
        f".continuity/sessions/{session['session_id']}.yaml",
        "artifacts/context/CURRENT_CONTEXT_PACK.yaml",
        "artifacts/context/CURRENT_CONTEXT_PACK.md",
        "artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json",
    }
    selected_checkpoint = checkpoint_path or session.get("latest_checkpoint")
    if selected_checkpoint:
        required.add(selected_checkpoint)
    return required


def validate_session_structure(report: Report, session: dict[str, Any], policy: dict[str, Any]) -> None:
    for key in ["session_id", "status", "actor", "release", "task_id", "started_at", "scope", "git", "lease", "session_log"]:
        report.require(bool(session.get(key)), "SESSION_FIELD", f"会话缺少字段：{key}")
    actor = session.get("actor", {})
    report.require(bool(actor.get("id")), "SESSION_ACTOR", "会话必须记录Actor ID")
    report.require(session.get("status") in {"ACTIVE", "HANDED_OFF", "CLOSING", "CLOSED", "ABANDONED", "TRANSFERRED"}, "SESSION_STATUS", f"非法会话状态：{session.get('status')}")
    report.require(bool(session.get("scope", {}).get("allowed_paths")), "SESSION_SCOPE", "会话必须有允许路径")
    session_path = ROOT / CONTINUITY_DIR / "sessions" / f"{session.get('session_id')}.yaml"
    report.require(session_path.is_file(), "SESSION_RECORD", f"会话机器记录不存在：{session_path.relative_to(ROOT)}")
    report.require((ROOT / session.get("session_log", "missing")).is_file(), "SESSION_LOG", "会话Markdown日志不存在")
    if session.get("status") == "ACTIVE":
        grace = int(policy.get("lease", {}).get("ci_grace_minutes", 15))
        expires = parse_iso(session.get("lease", {}).get("expires_at"))
        report.require(bool(expires), "LEASE_MISSING", "ACTIVE会话缺少租约")
        if expires:
            report.warn(now_utc() <= expires, "LEASE_EXPIRED", f"会话租约已过期：{expires.isoformat()}（恢复前必须显式recover）")
            renewed = parse_iso(session.get("lease", {}).get("renewed_at"))
            if renewed:
                max_age = int(policy.get("checkpoint", {}).get("max_age_minutes", 60)) + grace
                report.warn((now_utc() - renewed).total_seconds() <= max_age * 60, "CHECKPOINT_AGE", "ACTIVE会话超过检查点/续租时间")


def validate_current_next_consistency(report: Report, session: dict[str, Any] | None) -> None:
    current = read_current_status(ROOT)
    next_task = read_next_task(ROOT)
    report.require(current.get("baseline_version") == PACKAGE_VERSION, "STATUS_VERSION", "CURRENT_STATUS baseline_version必须为1.2.3")
    report.require(next_task.get("id") == current.get("next_task") or current.get("status") == "IN_PROGRESS", "NEXT_STATUS_LINK", "NEXT_TASK.id与CURRENT_STATUS.next_task不一致")
    if session and session.get("status") in {"ACTIVE", "HANDED_OFF", "CLOSING"}:
        report.require(current.get("active_task") == session.get("task_id"), "ACTIVE_TASK_LINK", "CURRENT_STATUS.active_task与会话任务不一致")
        report.require(current.get("active_release") == session.get("release"), "ACTIVE_RELEASE_LINK", "CURRENT_STATUS.active_release与会话Release不一致")
        report.require(next_task.get("id") == session.get("task_id"), "NEXT_TASK_ACTIVE_LINK", "活跃会话必须领取NEXT_TASK")
        cont = current.get("continuity", {})
        report.require(cont.get("active_session_id") == session.get("session_id"), "STATUS_SESSION_LINK", "CURRENT_STATUS未指向活跃会话")
    if not session:
        pointer = load_active_pointer(ROOT)
        report.require(not pointer.get("active_session_id"), "ORPHAN_ACTIVE_POINTER", "无会话时ACTIVE_SESSION指针必须为空")


def validate_indexes(report: Report, session: dict[str, Any] | None) -> None:
    session_index = load_yaml(ROOT / SESSION_INDEX_FILE, {}) or {}
    claims = load_yaml(ROOT / TASK_CLAIMS_FILE, {}) or {}
    cr_index = load_yaml(ROOT / CR_INDEX_FILE, {}) or {}
    rows = session_index.get("sessions", [])
    ids = [row.get("session_id") for row in rows]
    report.require(len(ids) == len(set(ids)), "SESSION_INDEX_DUP", "SESSION_INDEX存在重复会话")
    active_claims = [row for row in claims.get("claims", []) if row.get("status") == "ACTIVE"]
    task_keys = [(row.get("task_id"), row.get("story_id")) for row in active_claims]
    report.require(len(task_keys) == len(set(task_keys)), "TASK_CLAIM_DUP", "同一任务/故事存在多个活跃领取")
    if session and session.get("status") == "ACTIVE":
        report.require(any(row.get("session_id") == session.get("session_id") for row in active_claims), "ACTIVE_CLAIM_MISSING", "ACTIVE会话缺少任务领取记录")
    transitions = load_yaml(ROOT / TASK_TRANSITIONS_FILE, {}) or {}
    transition_rows = transitions.get("transitions", [])
    transition_ids = [row.get("transition_id") for row in transition_rows]
    report.require(bool(transition_rows), "TASK_TRANSITION_EMPTY", "任务迁移账本不得为空")
    report.require(len(transition_ids) == len(set(transition_ids)), "TASK_TRANSITION_DUP", "任务迁移账本存在重复ID")
    cr_ids = [row.get("cr_id") for row in cr_index.get("change_requests", [])]
    report.require(len(cr_ids) == len(set(cr_ids)), "CR_INDEX_DUP", "CR索引存在重复ID")
    for row in cr_index.get("change_requests", []):
        if row.get("status") in {"APPROVED", "IMPLEMENTING", "IMPLEMENTED", "CLOSED"}:
            report.require(row.get("requester_actor_id") != row.get("approver_actor_id"), "CR_SELF_APPROVAL", f"{row.get('cr_id')}申请人与审批人相同")
            missing = change_request_completeness_errors(row)
            report.require(not missing, "CR_INCOMPLETE", f"{row.get('cr_id')}已批准但变更合同不完整：{'、'.join(missing)}")


def changed_commit_messages(base_ref: str | None, head_ref: str) -> str:
    if not is_git_repo(ROOT) or not base_ref:
        return ""
    candidates = [base_ref, f"origin/{base_ref}"]
    base = None
    for candidate in candidates:
        result = run_command(["git", "rev-parse", "--verify", candidate], cwd=ROOT)
        if result.returncode == 0:
            base = result.stdout.strip()
            break
    if not base:
        return ""
    merge_base = git(ROOT, "merge-base", base, head_ref)
    return git(ROOT, "log", "--format=%B%x00", f"{merge_base}..{head_ref}")


def git_blob_at(commit_sha: str, relative: str) -> tuple[str, bytes | str | None]:
    tree = run_command(["git", "ls-tree", commit_sha, "--", relative], cwd=ROOT, timeout=60)
    if tree.returncode != 0 or not tree.stdout.strip():
        return "DELETED", None
    first = tree.stdout.splitlines()[0]
    match = re.match(r"^(\d+)\s+(\w+)\s+([0-9a-f]+)\t", first)
    if not match:
        raise ContinuityError(f"无法解析Git树条目：{commit_sha}:{relative}")
    mode, object_type, object_id = match.groups()
    if object_type != "blob":
        return "DIRECTORY", None
    content = subprocess.run(
        ["git", "cat-file", "blob", object_id], cwd=ROOT,
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        timeout=60, check=False,
    )
    if content.returncode != 0:
        raise ContinuityError(f"无法读取Git对象：{commit_sha}:{relative}")
    if mode == "120000":
        return "SYMLINK", content.stdout.decode("utf-8", errors="strict")
    return "FILE", content.stdout


def project_fingerprint_at_commit(session: dict[str, Any], commit_sha: str) -> dict[str, Any]:
    base = session.get("git", {}).get("base_commit")
    if not base or base in {"NOT_INITIALIZED", "UNBORN"}:
        changed = [
            line.strip() for line in git(ROOT, "ls-tree", "-r", "--name-only", commit_sha).splitlines()
            if line.strip()
        ]
    else:
        result = run_command(
            ["git", "diff", "--name-only", "--diff-filter=ACDMRTUXB", f"{base}..{commit_sha}"],
            cwd=ROOT, timeout=120,
        )
        if result.returncode != 0:
            raise ContinuityError(f"无法计算历史项目指纹：{base}..{commit_sha}")
        changed = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    files = filter_project_files(changed)
    tokens: list[dict[str, Any]] = []
    for relative in files:
        state, content = git_blob_at(commit_sha, relative)
        token: dict[str, Any] = {"path": relative, "state": state}
        if state == "FILE":
            assert isinstance(content, bytes)
            content = canonical_fingerprint_bytes(content)
            token.update({"size": len(content), "sha256": __import__("hashlib").sha256(content).hexdigest()})
        elif state == "SYMLINK":
            token["target"] = str(content)
        tokens.append(token)
    payload = {"base_commit": base or "NOT_INITIALIZED", "files": tokens}
    return {
        "sha256": sha256_text(canonical_json(payload)),
        "files": files,
        "file_count": len(files),
        "payload": payload,
    }


def secret_scan_at_commit(commit_sha: str, paths: Iterable[str], max_bytes: int = 2_000_000) -> list[dict[str, str]]:
    findings: list[dict[str, str]] = []
    for relative in sorted(set(paths)):
        if any(path_matches(relative, pattern) for pattern in SECRET_SCAN_EXCLUDE_PATTERNS):
            continue
        state, content = git_blob_at(commit_sha, relative)
        if state != "FILE" or not isinstance(content, bytes) or len(content) > max_bytes:
            continue
        if any(path_matches(relative, pattern) for pattern in SENSITIVE_FILENAME_PATTERNS):
            if not relative.endswith((".example", ".example.env")) and not relative.endswith(".env.example"):
                findings.append({"path": relative, "rule": "sensitive_filename"})
        try:
            text = content.decode("utf-8")
        except UnicodeDecodeError:
            continue
        for code, pattern in SUSPICIOUS_SECRET_REGEXES.items():
            if code == "generic_secret_assignment" and any(
                path_matches(relative, excluded) for excluded in GENERIC_SECRET_SCAN_EXCLUDE_PATTERNS
            ):
                continue
            if pattern.search(text):
                findings.append({"path": relative, "rule": code})
    unique = {(row["path"], row["rule"]): row for row in findings}
    return [unique[key] for key in sorted(unique)]


def validate_change_set(
    report: Report,
    policy: dict[str, Any],
    paths: list[str],
    session: dict[str, Any] | None,
    *,
    mode: str,
    commit_message: str = "",
    require_closed: bool = False,
    checkpoint_override: dict[str, Any] | None = None,
    checkpoint_path_override: str | None = None,
    commit_sha: str | None = None,
) -> None:
    project_paths = filter_project_files(paths)
    classification = classify_paths(project_paths, policy)
    report.metrics["changed_files"] = len(paths)
    report.metrics["project_changed_files"] = len(project_paths)
    report.metrics["change_classification"] = {key: len(value) for key, value in classification.items()}
    if not project_paths:
        return
    baseline_session = policy.get("commit", {}).get("baseline_commit", {}).get("session_id")
    if session is None and commit_message and parse_trailers(commit_message).get("session-id") == baseline_session:
        # The immutable repository import is validated exhaustively by validate_commit_identity.
        return
    report.require(session is not None, "SESSION_REQUIRED", "存在项目变更但没有可追溯会话")
    if not session:
        return
    validate_session_structure(report, session, policy)
    if require_closed:
        report.require(session.get("status") in {"CLOSED", "TRANSFERRED"}, "SESSION_NOT_CLOSED", "合并到受保护分支前会话必须CLOSED或TRANSFERRED")
    outside = allowed_path_errors(session, project_paths)
    report.require(not outside, "OUT_OF_SCOPE", "变更超出会话范围：" + ", ".join(outside[:30]))
    findings = secret_scan_at_commit(commit_sha, project_paths) if commit_sha else secret_scan(ROOT, project_paths)
    report.require(not findings, "SECRET_DETECTED", "疑似秘密：" + json.dumps(findings, ensure_ascii=False))
    required_records = mandatory_record_paths(session, checkpoint_path_override)
    changed_set = set(paths)
    missing = sorted(required_records - changed_set)
    report.require(not missing, "CONTINUITY_RECORDS_MISSING", "项目变更缺少同步记录：" + ", ".join(missing))
    checkpoint = checkpoint_override or latest_checkpoint(ROOT, session)
    report.require(checkpoint is not None, "CHECKPOINT_REQUIRED", "项目变更没有检查点")
    if checkpoint:
        report.require(bool(checkpoint.get("summary")), "CHECKPOINT_SUMMARY", "检查点缺少summary")
        report.require(bool(checkpoint.get("next_step")), "CHECKPOINT_NEXT", "检查点缺少next_step")
        report.require(checkpoint.get("session_id") == session.get("session_id"), "CHECKPOINT_SESSION", "检查点不属于当前会话")
        if commit_sha:
            historical_fp = project_fingerprint_at_commit(session, commit_sha)
            report.require(
                historical_fp["sha256"] == checkpoint.get("project_fingerprint", {}).get("sha256"),
                "CHECKPOINT_STALE", f"{commit_sha}内容与其检查点不一致",
            )
        elif mode in {"pre-commit", "doctor"}:
            current_fp = project_fingerprint(ROOT, session)
            report.require(current_fp["sha256"] == checkpoint.get("project_fingerprint", {}).get("sha256"), "CHECKPOINT_STALE", "项目内容在检查点后发生变化")
    required = change_requirements(classification, commit_message=commit_message)
    bootstrap_import = session.get("git", {}).get("base_commit") in {"NOT_INITIALIZED", "UNBORN"} and session.get("task_id") in set(policy.get("bootstrap", {}).get("allow_without_git_task_ids", []))
    if "APPROVED_CHANGE_REQUEST" in required and not bootstrap_import:
        errors = check_required_change_request(ROOT, session)
        report.require(not errors, "CR_REQUIRED", "；".join(errors))
        cr_paths = {row.get("document") for row in active_change_requests(ROOT, session)} | {row.get("machine_record") for row in active_change_requests(ROOT, session)}
        report.require(bool(changed_set & {path for path in cr_paths if path}), "CR_NOT_IN_CHANGESET", "冻结事实变化时CR记录必须随变更提交")
    if "CHANGELOG" in required:
        report.require("CHANGELOG.md" in changed_set, "CHANGELOG_REQUIRED", "用户可见变化必须更新CHANGELOG.md")
    if "PROBLEM_REGISTRY" in required:
        report.require("docs/03-continuity/PROBLEM_REGISTRY.yaml" in changed_set, "PROBLEM_REGISTRY_REQUIRED", "Bug修复必须更新Problem Registry")
    if "REGRESSION_TEST" in required:
        report.require(any(path.startswith(("tests/", "apps/", "services/", "packages/")) and ("test" in path.lower() or "/src/test/" in path) for path in project_paths), "REGRESSION_TEST_REQUIRED", "Bug修复必须包含回归测试")
    if classification.get("database"):
        report.require(any(path.startswith("database/tests/") or "migration" in path.lower() for path in paths), "DB_TEST_RECORD", "数据库变更必须包含迁移/不变量测试或证据")


def parse_trailer_entries(message: str) -> tuple[dict[str, str], list[str]]:
    trailers: dict[str, str] = {}
    duplicates: list[str] = []
    for line in message.splitlines():
        match = re.match(r"^([A-Za-z][A-Za-z0-9-]+):\s*(.*?)\s*$", line)
        if not match:
            continue
        key = match.group(1).lower()
        if key in trailers:
            duplicates.append(key)
        trailers[key] = match.group(2)
    return trailers, duplicates


def parse_trailers(message: str) -> dict[str, str]:
    return parse_trailer_entries(message)[0]


def commit_subject(message: str) -> str:
    for line in message.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            return stripped
    return ""


def checkpoint_path_for_id(session_id: str, checkpoint_id: str) -> str | None:
    directory = ROOT / ".continuity" / "checkpoints" / session_id
    if not directory.exists():
        return None
    for path in sorted(directory.glob("*.yaml")):
        record = load_yaml(path, {}) or {}
        if record.get("checkpoint_id") == checkpoint_id:
            return path.relative_to(ROOT).as_posix()
    return None


def commit_records(base_ref: str | None, head_ref: str) -> list[dict[str, Any]]:
    if not is_git_repo(ROOT):
        return []
    if base_ref:
        candidates = [base_ref]
        if not re.fullmatch(r"[0-9a-fA-F]{7,64}", base_ref):
            candidates.extend([f"origin/{base_ref}", f"refs/remotes/origin/{base_ref}"])
        base = None
        for candidate in candidates:
            result = run_command(["git", "rev-parse", "--verify", candidate], cwd=ROOT)
            if result.returncode == 0:
                base = result.stdout.strip()
                break
        if not base:
            raise ContinuityError(f"无法解析提交范围base-ref：{base_ref}")
        merge_base = git(ROOT, "merge-base", base, head_ref, check=True)
        rev_range = f"{merge_base}..{head_ref}"
    else:
        rev_range = head_ref
    commits = [line.strip() for line in git(ROOT, "rev-list", "--reverse", "--no-merges", rev_range).splitlines() if line.strip()]
    rows: list[dict[str, Any]] = []
    for sha in commits:
        message = git(ROOT, "show", "-s", "--format=%B", sha)
        paths = [line.strip() for line in git(ROOT, "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", sha).splitlines() if line.strip()]
        rows.append({"sha": sha, "message": message, "paths": sorted(set(paths))})
    return rows


def validate_commit_identity(
    report: Report,
    policy: dict[str, Any],
    *,
    message: str,
    changed: list[str],
    session: dict[str, Any] | None,
    commit_sha: str = "STAGED",
) -> tuple[dict[str, Any] | None, str | None]:
    subject = commit_subject(message)
    trailers, duplicates = parse_trailer_entries(message)
    report.require(not duplicates, "TRAILER_DUPLICATE", f"{commit_sha}存在重复Trailer：{sorted(set(duplicates))}")
    commit_policy = policy.get("commit", {})
    pattern = commit_policy.get("subject_pattern")
    report.require(bool(subject), "COMMIT_SUBJECT", f"{commit_sha}缺少提交主题")
    if pattern and subject:
        report.require(bool(re.fullmatch(pattern, subject)), "COMMIT_SUBJECT_PATTERN", f"{commit_sha}主题不符合规范：{subject}")
    if commit_policy.get("reject_placeholder_trailers", True):
        report.require(not any(token in message for token in ["TASK-...", "STORY-...", "SES-...", "CP-...", "<type>", "<summary>"]), "COMMIT_PLACEHOLDER", f"{commit_sha}仍含模板占位符")

    baseline = commit_policy.get("baseline_commit", {})
    if trailers.get("session-id") == baseline.get("session_id"):
        expected = {
            "task-id": str(baseline.get("task_id")),
            "session-id": str(baseline.get("session_id")),
            "checkpoint-id": str(baseline.get("checkpoint_id")),
            "tests": str(baseline.get("tests")),
            "cr": str(baseline.get("cr")),
        }
        for key, value in expected.items():
            report.require(trailers.get(key) == value, "BASELINE_TRAILER", f"{commit_sha} {key}应为{value}")
        return None, None

    for key in [str(value).lower() for value in commit_policy.get("required_trailers", [])]:
        report.require(bool(trailers.get(key)), "COMMIT_TRAILER", f"{commit_sha}缺少 {key}")
    report.require(session is not None, "COMMIT_SESSION", f"{commit_sha}找不到Session记录")
    if not session:
        return None, None
    checkpoint_id = trailers.get("checkpoint-id", "")
    checkpoint = load_checkpoint_by_id(ROOT, session["session_id"], checkpoint_id) if checkpoint_id else None
    checkpoint_path = checkpoint_path_for_id(session["session_id"], checkpoint_id) if checkpoint_id else None
    report.require(checkpoint is not None, "COMMIT_CHECKPOINT", f"{commit_sha}引用的检查点不存在：{checkpoint_id}")
    if not checkpoint:
        return None, checkpoint_path
    expected = {key.lower(): value for key, value in expected_commit_trailers(session, checkpoint).items()}
    for key, value in expected.items():
        report.require(trailers.get(key) == value, "COMMIT_TRAILER_MISMATCH", f"{commit_sha} {key}应为{value}，实际为{trailers.get(key)}")
    if not session.get("story_id"):
        report.require(not trailers.get("story-id"), "UNEXPECTED_STORY_TRAILER", f"{commit_sha}会话无Story但提交含Story-ID")
    anchor = session.get("story_id") or session.get("task_id")
    report.require(subject.startswith(f"[{anchor}] "), "COMMIT_SUBJECT_ANCHOR", f"{commit_sha}主题必须以[{anchor}]开头")

    changed_set = set(changed)
    required_records = mandatory_record_paths(session, checkpoint_path)
    missing = sorted(required_records - changed_set)
    report.require(not missing, "COMMIT_RECORDS_MISSING", f"{commit_sha}缺少同步记录：{missing}")
    return checkpoint, checkpoint_path


def document_gate_required(paths: Iterable[str]) -> bool:
    return any(
        path_matches(relative, pattern)
        for relative in paths
        for pattern in DOCUMENT_GATE_INPUT_PATTERNS
    )


def run_document_gate(report: Report, paths: Iterable[str] = (), *, force: bool = False) -> None:
    normalized_paths = sorted({path.replace("\\", "/") for path in paths if path})
    required = force or document_gate_required(normalized_paths)
    report.metrics["document_gate"] = {
        "status": "EXECUTED" if required else "SKIPPED_UNAFFECTED",
        "forced": force,
        "affected_inputs": [
            path for path in normalized_paths
            if any(path_matches(path, pattern) for pattern in DOCUMENT_GATE_INPUT_PATTERNS)
        ],
    }
    if not required:
        return
    script = ROOT / "scripts/check_v123_documentation.py"
    result = run_command([
        sys.executable,
        str(script),
        "--strict",
        "--json-out",
        ".continuity/runtime/project-doctor-v1.2.3-documentation.json",
    ], cwd=ROOT, timeout=180)
    report.require(result.returncode == 0, "DOCUMENT_GATE", "V1.2.2页面/运营文档门禁失败")


def prepush_base_ref(git_state: dict[str, Any], session: dict[str, Any] | None) -> str | None:
    """Use the task's recorded base for a branch that has no upstream yet."""
    return git_state.get("upstream") or (session or {}).get("git", {}).get("base_commit")


def main() -> int:
    parser = ArgumentParser(description="持续开发无状态接续强制门禁")
    parser.add_argument("--mode", choices=["doctor", "release", "pre-commit", "commit-msg", "pre-push", "ci"], default="doctor")
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--base-ref")
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--commit-message-file")
    parser.add_argument("--require-closed", action="store_true")
    parser.add_argument("--json-out", default="artifacts/validation/continuity-gate-v1.2.3.json")
    args = parser.parse_args()
    report = Report(args.mode)
    try:
        policy = load_policy(ROOT)
        for relative in REQUIRED_ROOT_FILES:
            report.require((ROOT / relative).is_file(), "FILE_MISSING", relative)
        state = load_state(ROOT)
        report.require(state.get("protocol_version") == "1.0", "PROTOCOL_VERSION", "STATE协议版本必须为1.0")
        report.require(state.get("mode") == "ENFORCED", "MODE_NOT_ENFORCED", "接续模式必须为ENFORCED")
        chain = validate_event_chain(ROOT)
        report.require(chain["valid"], "EVENT_CHAIN", "；".join(chain["errors"]))
        report.metrics["event_count"] = chain["events"]
        report.metrics["event_head_hash"] = chain["head_hash"]
        pointer = load_active_pointer(ROOT)
        session = current_session(ROOT)
        if pointer.get("active_session_id"):
            report.require(session is not None, "ACTIVE_POINTER_INVALID", "ACTIVE_SESSION指向无效会话")
        if session:
            validate_session_structure(report, session, policy)
        validate_current_next_consistency(report, session)
        validate_indexes(report, session)
        message = ""
        if args.commit_message_file:
            message = Path(args.commit_message_file).read_text(encoding="utf-8")
        document_paths: list[str] = []

        if args.mode == "pre-commit":
            if not is_git_repo(ROOT):
                report.errors.append({"code": "GIT_REQUIRED", "message": "pre-commit必须在Git仓库中运行"})
            staged = git_changed_files(ROOT, staged=True)
            full = git_changed_files(ROOT)
            staged_project = set(filter_project_files(staged))
            full_project = set(filter_project_files(full))
            report.require(staged_project == full_project, "PARTIAL_COMMIT", "禁止部分提交项目内容；先创建检查点并一次性暂存全部项目变更")
            relevant = latest_relevant_session(staged)
            validate_change_set(report, policy, staged, relevant, mode=args.mode)
            document_paths = staged

        elif args.mode == "commit-msg":
            if not args.commit_message_file:
                report.errors.append({"code": "MESSAGE_FILE", "message": "commit-msg模式需要--commit-message-file"})
            changed = git_changed_files(ROOT, staged=True)
            relevant = latest_relevant_session(changed)
            checkpoint, checkpoint_path = validate_commit_identity(
                report, policy, message=message, changed=changed, session=relevant, commit_sha="STAGED"
            )
            validate_change_set(
                report, policy, changed, relevant, mode=args.mode, commit_message=message,
                checkpoint_override=checkpoint, checkpoint_path_override=checkpoint_path,
            )
            document_paths = changed

        elif args.mode == "pre-push":
            if not is_git_repo(ROOT):
                report.errors.append({"code": "GIT_REQUIRED", "message": "pre-push必须在Git仓库中运行"})
            info = git_info(ROOT)
            report.require(not info.get("dirty"), "DIRTY_PUSH", "推送前工作区必须完全干净，避免远程状态落后于本地记录")
            if session:
                checkpoint = latest_checkpoint(ROOT, session)
                report.require(checkpoint is not None, "CHECKPOINT_REQUIRED", "推送前需要检查点")
                if checkpoint:
                    report.require(project_fingerprint(ROOT, session)["sha256"] == checkpoint.get("project_fingerprint", {}).get("sha256"), "CHECKPOINT_STALE", "推送内容与检查点不一致")
            fresh, reason = context_is_fresh(ROOT, session)
            report.require(fresh, "CONTEXT_STALE", reason)
            upstream = prepush_base_ref(info, session)
            records = commit_records(upstream, "HEAD") if is_git_repo(ROOT) else []
            report.metrics["validation_base_ref"] = upstream
            report.metrics["unpushed_commits"] = len(records)
            document_paths = sorted({path for record in records for path in record["paths"]})
            for record in records:
                trailers = parse_trailers(record["message"])
                sid = trailers.get("session-id")
                commit_session = None
                if sid and sid != policy.get("commit", {}).get("baseline_commit", {}).get("session_id"):
                    try:
                        commit_session = load_session(ROOT, sid)
                    except ContinuityError:
                        commit_session = None
                checkpoint, checkpoint_path = validate_commit_identity(
                    report, policy, message=record["message"], changed=record["paths"],
                    session=commit_session, commit_sha=record["sha"],
                )
                validate_change_set(
                    report, policy, record["paths"], commit_session, mode=args.mode,
                    commit_message=record["message"], checkpoint_override=checkpoint,
                    checkpoint_path_override=checkpoint_path, commit_sha=record["sha"],
                )

        elif args.mode == "ci":
            records = commit_records(args.base_ref, args.head_ref)
            report.metrics["validated_commits"] = len(records)
            document_paths = sorted({path for record in records for path in record["paths"]})
            require_closed = args.require_closed or os.environ.get("GITHUB_BASE_REF") in {"main", "master"}
            for record in records:
                trailers = parse_trailers(record["message"])
                sid = trailers.get("session-id")
                commit_session = None
                if sid and sid != policy.get("commit", {}).get("baseline_commit", {}).get("session_id"):
                    try:
                        commit_session = load_session(ROOT, sid)
                    except ContinuityError:
                        commit_session = None
                checkpoint, checkpoint_path = validate_commit_identity(
                    report, policy, message=record["message"], changed=record["paths"],
                    session=commit_session, commit_sha=record["sha"],
                )
                validate_change_set(
                    report, policy, record["paths"], commit_session, mode=args.mode,
                    commit_message=record["message"], require_closed=require_closed,
                    checkpoint_override=checkpoint, checkpoint_path_override=checkpoint_path,
                    commit_sha=record["sha"],
                )
            relevant = current_session(ROOT)
            fresh, reason = context_is_fresh(ROOT, relevant if relevant and relevant.get("status") in {"ACTIVE", "HANDED_OFF", "CLOSING"} else None)
            report.require(fresh, "CONTEXT_STALE", reason)

        else:  # doctor/release
            fresh, reason = context_is_fresh(ROOT, session)
            report.require(fresh, "CONTEXT_STALE", reason)
            if session and session.get("status") == "ACTIVE" and session.get("latest_checkpoint"):
                cp = latest_checkpoint(ROOT, session)
                report.require(project_fingerprint(ROOT, session)["sha256"] == cp.get("project_fingerprint", {}).get("sha256"), "CHECKPOINT_STALE", "最新检查点与工作区不一致")

        run_document_gate(
            report,
            document_paths,
            force=args.mode in {"doctor", "release"},
        )

    except (ContinuityError, OSError, ValueError, yaml.YAMLError) as exc:
        report.errors.append({"code": "GATE_EXCEPTION", "message": str(exc)})

    payload = report.payload(args.strict)
    output = ROOT / args.json_out
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if payload["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
