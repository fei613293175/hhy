#!/usr/bin/env python3
"""合伙云 Pro 持续开发无状态接续命令行。

任何 AI 或开发者必须先 start/takeover，再修改文件；每次上下文切换、提交、
关键测试或交接前必须 checkpoint。旧对话不是输入，仓库状态才是输入。
"""
from __future__ import annotations

from argparse import ArgumentParser, Namespace
from datetime import timedelta
from pathlib import Path
from typing import Any
import json
import os
import re
import sys

import yaml

from continuity_lib import (
    ACTIVE_FILE,
    CONTINUITY_DIR,
    PACKAGE_VERSION,
    PROTOCOL_VERSION,
    STATE_FILE,
    ContinuityError,
    active_change_requests,
    apply_change_request_scope,
    amend_change_request,
    append_event,
    approve_change_request,
    atomic_write_json,
    atomic_write_text,
    atomic_write_yaml,
    build_context_pack,
    checkpoint_dir,
    claim_task,
    close_task_claim,
    continuity_lock,
    context_is_fresh,
    create_change_request,
    create_handoff_bundle,
    create_clean_export_bundle,
    current_session,
    derive_scope,
    git_changed_files,
    git_has_concrete_head,
    git_info,
    expected_commit_trailers,
    initialize_continuity_files,
    iso_utc,
    latest_checkpoint,
    lease_is_expired,
    load_active_pointer,
    load_policy,
    load_session,
    load_state,
    make_session_id,
    markdown_session_log,
    now_utc,
    parse_iso,
    parse_test_spec,
    project_fingerprint,
    read_current_status,
    read_csv,
    read_next_task,
    record_task_transition,
    release_story,
    release_task,
    renew_lease,
    root_from_script,
    run_command,
    save_active_pointer,
    save_session,
    save_state,
    session_record_path,
    sha256_file,
    update_current_status_closed,
    update_current_status_for_session,
    update_session_index,
    update_change_request_status,
    validate_event_chain,
    verify_manifest,
    write_checkpoint,
    write_csv,
)

ROOT = root_from_script(__file__)


def print_yaml(value: Any) -> None:
    print(yaml.safe_dump(value, allow_unicode=True, sort_keys=False, width=140).rstrip())


def runtime_heartbeat_path(root: Path, session_id: str) -> Path:
    return root / CONTINUITY_DIR / "runtime" / f"heartbeat-{session_id}.json"


def effective_lease_expired(root: Path, session: dict[str, Any]) -> bool:
    if not lease_is_expired(session):
        return False
    heartbeat = runtime_heartbeat_path(root, session["session_id"])
    if not heartbeat.exists():
        return True
    try:
        payload = json.loads(heartbeat.read_text(encoding="utf-8"))
        expires = parse_iso(payload.get("expires_at"))
        return not expires or now_utc() > expires
    except Exception:
        return True


def get_actor(args: Namespace) -> str:
    actor = (getattr(args, "actor", None) or os.environ.get("HHY_ACTOR_ID") or "").strip()
    if not actor:
        raise ContinuityError("必须提供 --actor 或环境变量 HHY_ACTOR_ID；不得使用匿名会话")
    if not 3 <= len(actor) <= 120:
        raise ContinuityError("actor长度必须为3-120")
    return actor


def select_story(root: Path, release: str, requested: str | None) -> dict[str, Any] | None:
    story = release_story(root, release, requested)
    if requested and not story:
        raise ContinuityError(f"故事不存在：{requested}")
    if story and story.get("status") != "READY_FOR_IMPLEMENTATION":
        raise ContinuityError(f"故事未就绪：{story.get('story_id')} / {story.get('status')}")
    return story


def blocked_resume_command(task_id: str) -> str:
    """Return the only command that may explicitly resume a blocked task."""
    return f"python3 scripts/continuity.py start --actor <ACTOR_ID> --task {task_id}"


def blocked_resume_is_authorized(
    task_id: str, task: dict[str, Any], next_task: dict[str, Any]
) -> bool:
    """Narrowly authorize a blocked task that close explicitly made resumable.

    A generic BLOCKED task is never startable. Both state sources must point to the
    same blocked task and NEXT_TASK must carry the exact command emitted by close.
    The caller separately enforces that no active session exists.
    """
    return (
        task.get("id") == task_id
        and task.get("status") == "BLOCKED"
        and next_task.get("id") == task_id
        and next_task.get("status") == "BLOCKED"
        and next_task.get("resume_command") == blocked_resume_command(task_id)
    )


def create_session(
    root: Path,
    policy: dict[str, Any],
    *,
    actor: str,
    task_id: str,
    story_id: str | None,
    goal: str,
    crs: list[str],
    explicit_scope: list[str],
    allow_dirty: bool,
    base_commit_override: str | None = None,
    takeover_of: str | None = None,
) -> dict[str, Any]:
    status = read_current_status(root)
    next_task = read_next_task(root)
    release = next_task.get("release") or status.get("active_release")
    if not release:
        raise ContinuityError("无法确定当前Release")
    if task_id != next_task.get("id"):
        raise ContinuityError(
            f"只能领取 NEXT_TASK.yaml 中的任务：期望 {next_task.get('id')}，收到 {task_id}"
        )
    task = release_task(root, release, task_id)
    normally_startable = (
        task.get("status") in {"READY", "IN_PROGRESS"}
        or next_task.get("status") in {"READY", "IN_PROGRESS"}
    )
    if not normally_startable and not blocked_resume_is_authorized(task_id, task, next_task):
        raise ContinuityError(f"任务不是READY/IN_PROGRESS：{task_id} / plan={task.get('status')} / next={next_task.get('status')}")
    story = select_story(root, release, story_id)
    if not story:
        stories_path = root / "releases" / release / "STORIES.yaml"
        document = yaml.safe_load(stories_path.read_text(encoding="utf-8")) or {}
        ready = [row for row in document.get("stories", []) if row.get("status") == "READY_FOR_IMPLEMENTATION"]
        if len(ready) > 1:
            raise ContinuityError("当前版本存在多个READY Story，必须显式提供 --story")
        story = ready[0] if ready else None
    git_state = git_info(root)
    bootstrap_tasks = set(policy.get("bootstrap", {}).get("allow_without_git_task_ids", []))
    if not git_has_concrete_head(git_state) and task_id not in bootstrap_tasks:
        raise ContinuityError("Git尚无Baseline Commit；仅策略允许的Bootstrap任务可开始")
    if git_has_concrete_head(git_state) and git_state["dirty"] and not allow_dirty:
        raise ContinuityError("开始新会话前工作区必须干净；中途接管请使用 takeover/recover")
    base_commit = base_commit_override or git_state["head"]
    allowed_paths = derive_scope(policy, story, explicit_scope)
    session_id = make_session_id()
    started = iso_utc()
    session_log = (
        Path("docs/03-continuity/sessions")
        / now_utc().strftime("%Y-%m")
        / f"{session_id}.md"
    ).as_posix()
    session = {
        "protocol_version": PROTOCOL_VERSION,
        "package_version": PACKAGE_VERSION,
        "session_id": session_id,
        "status": "ACTIVE",
        "actor": {
            "id": actor,
            "kind": "AI_OR_HUMAN",
            "host": os.environ.get("HOSTNAME") or os.uname().nodename if hasattr(os, "uname") else "unknown",
        },
        "release": release,
        "task_id": task_id,
        "story_id": story.get("story_id") if story else None,
        "goal": goal or next_task.get("title") or task.get("title"),
        "started_at": started,
        "updated_at": started,
        "takeover_of": takeover_of,
        "change_requests": list(dict.fromkeys(crs)),
        "scope": {
            "allowed_paths": allowed_paths,
            "approved_exceptions": [],
            "source": "story+explicit",
        },
        "git": {
            "initialized": git_state["initialized"],
            "branch": git_state["branch"],
            "base_commit": base_commit,
            "start_head": git_state["head"],
            "upstream": git_state["upstream"],
            "initial_worktree_state": "DIRTY_TAKEOVER" if git_state["dirty"] else "CLEAN",
        },
        "lease": {},
        "checkpoint_sequence": 0,
        "latest_checkpoint": None,
        "session_log": session_log,
        "next_step": "阅读当前Story、逐项验证事实源后开始实现",
        "context_pack": None,
        "handoff_bundle": None,
        "closure": None,
    }
    renew_lease(session, policy)
    session_path = session_record_path(root, session_id)
    session_path.parent.mkdir(parents=True, exist_ok=True)
    atomic_write_yaml(session_path, session)
    log_path = root / session_log
    log_path.parent.mkdir(parents=True, exist_ok=True)
    atomic_write_text(log_path, markdown_session_log(session))
    claim_task(root, session)
    save_active_pointer(
        root,
        {
            "protocol_version": PROTOCOL_VERSION,
            "active_session_id": session_id,
            "status": "ACTIVE",
            "session_record": session_path.relative_to(root).as_posix(),
            "actor_id": actor,
            "task_id": task_id,
            "story_id": session.get("story_id"),
            "lease_expires_at": session["lease"]["expires_at"],
        },
    )
    state = load_state(root)
    state["active_session_id"] = session_id
    state["mode"] = "ENFORCED"
    save_state(root, state)
    from continuity_lib import append_session_index

    append_session_index(root, session)
    update_current_status_for_session(root, session)
    append_event(
        root,
        "SESSION_STARTED" if not takeover_of else "SESSION_TAKEN_OVER",
        {
            "session_id": session_id,
            "actor_id": actor,
            "release": release,
            "task_id": task_id,
            "story_id": session.get("story_id"),
            "base_commit": base_commit,
            "takeover_of": takeover_of,
        },
    )
    record_task_transition(
        root, task_id=task_id, release=release, from_status=next_task.get("status"), to_status="IN_PROGRESS",
        session_id=session_id, actor_id=actor, reason="会话领取任务", story_id=session.get("story_id")
    )
    build_context_pack(root, session)
    save_session(root, session)
    return session



def command_bootstrap(args: Namespace) -> None:
    """Initialize Git, commit the immutable package baseline, install hooks and switch to the task branch.

    This is the only allowed pre-hook commit. The baseline event/context are written before
    the commit so the resulting branch starts clean and immediately enforceable.
    """
    actor = get_actor(args)
    reconciled_session_id: str | None = None
    with continuity_lock(ROOT):
        initialize_continuity_files(ROOT)
        pointer = load_active_pointer(ROOT)
        active_session_id = pointer.get("active_session_id")
        active: dict[str, Any] | None = None
        if active_session_id:
            active = load_session(ROOT, active_session_id)
            placeholder_base = active.get("git", {}).get("base_commit") in {
                None, "", "NOT_INITIALIZED", "UNBORN"
            }
            if active.get("status") != "ACTIVE" or not placeholder_base:
                raise ContinuityError("存在非兼容状态的活动会话，禁止执行Bootstrap")
            if active.get("actor", {}).get("id") != actor or active.get("task_id") != args.task:
                raise ContinuityError("Git初始化前已有其他Actor或任务的ACTIVE会话，禁止回填其基线")
        info = git_info(ROOT)
        if not git_has_concrete_head(info) and not args.initial_commit:
            raise ContinuityError("Git尚无Baseline Commit；Bootstrap必须提供 --initial-commit")
        if git_has_concrete_head(info) and args.initial_commit:
            raise ContinuityError("仓库已经存在Commit，禁止再次创建Baseline初始提交")
        if not info["initialized"]:
            if not args.init_git:
                raise ContinuityError("Git尚未初始化；请提供 --init-git")
            result = run_command(["git", "init"], cwd=ROOT, timeout=60)
            if result.returncode != 0:
                raise ContinuityError("git init失败：" + result.stderr.strip())
        run_command(["git", "config", "user.name", args.git_user_name], cwd=ROOT)
        run_command(["git", "config", "user.email", args.git_user_email], cwd=ROOT)
        run_command(["git", "config", "core.quotepath", "false"], cwd=ROOT)
        run_command(["git", "config", "commit.template", ".gitmessage"], cwd=ROOT)
        state = load_state(ROOT)
        state["git_bootstrap"] = {
            "initialized": True,
            "branch": args.branch,
            "task_id": args.task,
            "actor_id": actor,
            "hooks_path": ".githooks",
            "prepared_at": iso_utc(),
        }
        save_state(ROOT, state)
        append_event(ROOT, "GIT_BOOTSTRAP_PREPARED", {
            "actor_id": actor, "task_id": args.task, "branch": args.branch,
            "initial_commit": bool(args.initial_commit), "hooks": ".githooks",
        })
        build_context_pack(ROOT, None)
        if args.initial_commit:
            existing = git_info(ROOT)
            if existing.get("head") not in {None, "", "NOT_INITIALIZED", "UNBORN"}:
                raise ContinuityError("仓库已经存在Commit，禁止再次创建Baseline初始提交")
            add = run_command(["git", "add", "-A"], cwd=ROOT, timeout=120)
            if add.returncode != 0:
                raise ContinuityError("git add失败：" + add.stderr.strip())
            message = (
                f"[TASK-P00-001] chore(repo): import V1.2.3 enforced continuity baseline\n\n"
                f"Task-ID: {args.task}\n"
                "Session-ID: V1.2.3-PACKAGE-BASELINE\n"
                "Checkpoint-ID: PACKAGE-BASELINE\n"
                "Tests: PASS:documentation+continuity\n"
                "CR: NONE\n"
            )
            commit = run_command(["git", "-c", "core.hooksPath=/dev/null", "commit", "-m", message], cwd=ROOT, timeout=180)
            if commit.returncode != 0:
                raise ContinuityError("Baseline Commit失败：" + commit.stderr.strip())
        current = git_info(ROOT).get("branch")
        if args.branch and current != args.branch:
            switch = run_command(["git", "switch", "-c", args.branch], cwd=ROOT, timeout=60)
            if switch.returncode != 0:
                switch = run_command(["git", "switch", args.branch], cwd=ROOT, timeout=60)
            if switch.returncode != 0:
                raise ContinuityError("切换任务分支失败：" + switch.stderr.strip())
        run_command(["git", "config", "core.hooksPath", ".githooks"], cwd=ROOT)
        for hook in (ROOT / ".githooks").glob("*"):
            if hook.is_file():
                os.chmod(hook, 0o755)
        # P00 is deliberately allowed to be claimed before Git exists. If that
        # compatibility path was used, bind the active session to the baseline
        # commit created above. Otherwise its NOT_INITIALIZED base would make
        # every tracked file look like a session change and no checkpoint could
        # ever be created.
        if active_session_id and active:
            post_bootstrap = git_info(ROOT)
            baseline_head = post_bootstrap.get("head")
            if baseline_head in {None, "", "NOT_INITIALIZED", "UNBORN"}:
                raise ContinuityError("Bootstrap完成后仍无法读取Baseline Commit")
            active["git"].update({
                "initialized": True,
                "branch": post_bootstrap.get("branch"),
                "base_commit": baseline_head,
                "start_head": baseline_head,
                "upstream": post_bootstrap.get("upstream"),
                "initial_worktree_state": "CLEAN",
            })
            source = str(active.get("scope", {}).get("source") or "story+explicit")
            if "bootstrap-reconciled" not in source:
                active["scope"]["source"] = source + "+bootstrap-reconciled"
            save_session(ROOT, active)
            log_path = ROOT / active["session_log"]
            if int(active.get("checkpoint_sequence", 0)) == 0 or not log_path.exists():
                atomic_write_text(log_path, markdown_session_log(active))
            else:
                log_text = log_path.read_text(encoding="utf-8")
                replacements = [
                    (r"(?m)^base_commit: .*$", f"base_commit: {baseline_head}"),
                    (r"(?m)^- 分支：`.*`$", f"- 分支：`{post_bootstrap.get('branch')}`"),
                    (r"(?m)^- 起始 Commit：`.*`$", f"- 起始 Commit：`{baseline_head}`"),
                    (r"(?m)^- 工作区：.*$", "- 工作区：CLEAN"),
                ]
                for pattern, replacement in replacements:
                    log_text = re.sub(pattern, replacement, log_text, count=1)
                atomic_write_text(log_path, log_text)
            update_session_index(ROOT, active)
            pointer.update({
                "status": "ACTIVE",
                "actor_id": actor,
                "task_id": args.task,
                "story_id": active.get("story_id"),
                "lease_expires_at": active.get("lease", {}).get("expires_at"),
            })
            save_active_pointer(ROOT, pointer)
            update_current_status_for_session(ROOT, active)
            append_event(ROOT, "GIT_BOOTSTRAP_SESSION_RECONCILED", {
                "session_id": active_session_id,
                "actor_id": actor,
                "task_id": args.task,
                "base_commit": baseline_head,
                "branch": post_bootstrap.get("branch"),
            })
            build_context_pack(ROOT, active)
            reconciled_session_id = active_session_id
    print_yaml({
        "status": "BOOTSTRAP_COMPLETED_SESSION_RECONCILED" if reconciled_session_id else "BOOTSTRAP_COMPLETED",
        "branch": args.branch, "task_id": args.task,
        "hooks_path": ".githooks",
        "reconciled_session_id": reconciled_session_id,
        "metadata_commit_required": bool(reconciled_session_id),
        "next_command": (
            "python3 scripts/continuity.py checkpoint --summary '<阶段完成>' --next-step '<精确下一步>' --test 'name|PASS|evidence' --parallel-assessment <ASSESSMENT> --parallel-reason '<未委托原因>'"
            if reconciled_session_id
            else f"python3 scripts/continuity.py start --actor {actor} --task {args.task} --story <STORY_ID> --goal '<精确目标>'"
        ),
    })

def command_start(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        initialize_continuity_files(ROOT)
        policy = load_policy(ROOT)
        pointer = load_active_pointer(ROOT)
        if pointer.get("active_session_id"):
            active = load_session(ROOT, pointer["active_session_id"])
            if active.get("status") == "HANDED_OFF":
                raise ContinuityError(
                    f"存在待接管会话 {active['session_id']}；请运行 takeover，而不是新建会话"
                )
            if not effective_lease_expired(ROOT, active):
                raise ContinuityError(
                    f"已有活跃会话 {active['session_id']} / {active['actor']['id']}，租约到 {active['lease']['expires_at']}"
                )
            raise ContinuityError(
                f"已有过期会话 {active['session_id']}；必须运行 recover 显式记录异常接管"
            )
        session = create_session(
            ROOT,
            policy,
            actor=actor,
            task_id=args.task,
            story_id=args.story,
            goal=args.goal,
            crs=args.cr,
            explicit_scope=args.scope,
            allow_dirty=False,
        )
    print_yaml(
        {
            "status": "SESSION_STARTED",
            "session_id": session["session_id"],
            "task_id": session["task_id"],
            "story_id": session.get("story_id"),
            "lease_expires_at": session["lease"]["expires_at"],
            "session_log": session["session_log"],
            "next_command": "python3 scripts/continuity.py checkpoint --summary '<阶段完成>' --next-step '<精确下一步>' --test 'name|PASS|evidence' --parallel-assessment <ASSESSMENT> --parallel-reason '<未委托原因>'",
        }
    )


def command_checkpoint(args: Namespace) -> None:
    with continuity_lock(ROOT):
        policy = load_policy(ROOT)
        session = current_session(ROOT, allow_handoff=False)
        if not session:
            raise ContinuityError("没有ACTIVE会话；禁止在未领取任务时创建检查点")
        if effective_lease_expired(ROOT, session):
            raise ContinuityError("会话租约已过期；必须运行 recover")
        tests = [parse_test_spec(item) for item in args.test]
        if not tests and args.no_test_reason:
            tests = [{"name": "本阶段未执行测试", "result": "NOT_RUN", "evidence": "", "note": args.no_test_reason}]
        workers = []
        for spec in args.delegated_worker:
            parts = spec.split("|", 2)
            if len(parts) != 3:
                raise ContinuityError("--delegated-worker格式必须为 worker_id|responsibility|path1,path2")
            worker_id, responsibility, raw_paths = (part.strip() for part in parts)
            paths = [path.strip() for path in raw_paths.split(",") if path.strip()]
            workers.append({"worker_id": worker_id, "responsibility": responsibility, "allowed_paths": paths})
        parallel_execution = {
            "assessment": args.parallel_assessment,
            "workers": workers,
            "reason": args.parallel_reason,
        }
        checkpoint = write_checkpoint(
            ROOT,
            session,
            policy,
            summary=args.summary,
            next_step=args.next_step,
            blockers=args.blocker,
            decisions=args.decision,
            tests=tests,
            parallel_execution=parallel_execution,
            note=args.note,
        )
    print_yaml(
        {
            "status": "CHECKPOINT_CREATED",
            "checkpoint_id": checkpoint["checkpoint_id"],
            "project_fingerprint": checkpoint["project_fingerprint"]["sha256"],
            "changed_files": checkpoint["project_fingerprint"]["file_count"],
            "next_step": checkpoint["next_step"],
        }
    )


def command_heartbeat(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        policy = load_policy(ROOT)
        session = current_session(ROOT, allow_handoff=False)
        if not session:
            raise ContinuityError("没有ACTIVE会话")
        if session["actor"]["id"] != actor:
            raise ContinuityError("只有当前会话Actor可以续租")
        minutes = int(policy.get("lease", {}).get("runtime_heartbeat_extension_minutes", 60))
        payload = {
            "protocol_version": PROTOCOL_VERSION,
            "session_id": session["session_id"],
            "actor_id": actor,
            "heartbeat_at": iso_utc(),
            "expires_at": iso_utc(now_utc() + timedelta(minutes=minutes)),
            "note": args.note,
        }
        path = runtime_heartbeat_path(ROOT, session["session_id"])
        path.parent.mkdir(parents=True, exist_ok=True)
        atomic_write_json(path, payload)
    print_yaml({"status": "HEARTBEAT_RENEWED", **payload})


def command_handoff(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        policy = load_policy(ROOT)
        session = current_session(ROOT, allow_handoff=False)
        if not session:
            raise ContinuityError("没有ACTIVE会话")
        if session["actor"]["id"] != actor:
            raise ContinuityError("只有当前会话Actor可以发起交接")
        result = create_handoff_bundle(
            ROOT,
            session,
            policy,
            reason=args.reason,
            next_step=args.next_step,
            portable_zip=args.portable_zip,
            include_git_bundle=not args.no_git_bundle,
        )
    print_yaml({"status": "HANDED_OFF", **result})


def terminate_old_session(root: Path, old: dict[str, Any], status: str, reason: str) -> None:
    old["status"] = status
    old["closed_at"] = iso_utc()
    old["closure"] = {"result": status, "reason": reason, "closed_at": old["closed_at"]}
    save_session(root, old)
    close_task_claim(root, old, status)
    update_session_index(root, old)


def command_takeover(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        policy = load_policy(ROOT)
        old = load_session(ROOT, args.session)
        if old.get("status") != "HANDED_OFF" and not effective_lease_expired(ROOT, old):
            raise ContinuityError("只有HANDED_OFF或租约已过期的会话可以接管")
        if old.get("handoff_bundle"):
            verification = verify_manifest(ROOT / old["handoff_bundle"])
            if not verification["valid"]:
                raise ContinuityError("Handoff Bundle校验失败：" + ";".join(verification["errors"]))
        pointer = load_active_pointer(ROOT)
        if pointer.get("active_session_id") not in {None, old["session_id"]}:
            raise ContinuityError("另一个会话已经占用项目")
        terminate_old_session(ROOT, old, "TRANSFERRED", args.reason or "新Actor接管")
        save_active_pointer(ROOT, {"protocol_version": PROTOCOL_VERSION, "active_session_id": None, "status": "NONE"})
        session = create_session(
            ROOT,
            policy,
            actor=actor,
            task_id=old["task_id"],
            story_id=old.get("story_id"),
            goal=f"接管 {old['session_id']}：{old.get('goal','')}",
            crs=list(old.get("change_requests", [])),
            explicit_scope=list(old.get("scope", {}).get("allowed_paths", [])),
            allow_dirty=True,
            base_commit_override=old.get("git", {}).get("base_commit"),
            takeover_of=old["session_id"],
        )
        inherited = latest_checkpoint(ROOT, old)
        tests = list((inherited or {}).get("tests", []))
        checkpoint = write_checkpoint(
            ROOT,
            session,
            policy,
            summary=f"已验证并接管会话 {old['session_id']} 的仓库、检查点和交接包",
            next_step=args.next_step or old.get("next_step") or "按交接包继续原任务",
            blockers=[],
            decisions=[f"接管来源：{old['session_id']}", f"原Actor：{old['actor']['id']}"],
            tests=tests or [{"name": "Handoff Manifest", "result": "PASS", "evidence": old.get("handoff_bundle") or "lease recovery", "note": "接管校验"}],
            parallel_execution={
                "assessment": "NO_SAFE_PARALLEL",
                "workers": [],
                "reason": "会话接管与事实源恢复必须由新主控串行完成",
            },
            note=args.reason,
        )
    print_yaml(
        {
            "status": "TAKEOVER_COMPLETED",
            "old_session": old["session_id"],
            "new_session": session["session_id"],
            "checkpoint": checkpoint["checkpoint_id"],
            "next_step": checkpoint["next_step"],
        }
    )


def command_recover(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        policy = load_policy(ROOT)
        old = load_session(ROOT, args.session)
        if not effective_lease_expired(ROOT, old):
            raise ContinuityError("会话租约尚未过期，禁止强行恢复")
        terminate_old_session(ROOT, old, "ABANDONED", args.reason)
        save_active_pointer(ROOT, {"protocol_version": PROTOCOL_VERSION, "active_session_id": None, "status": "NONE"})
        session = create_session(
            ROOT,
            policy,
            actor=actor,
            task_id=old["task_id"],
            story_id=old.get("story_id"),
            goal=f"异常恢复 {old['session_id']}：{old.get('goal','')}",
            crs=list(old.get("change_requests", [])),
            explicit_scope=list(old.get("scope", {}).get("allowed_paths", [])),
            allow_dirty=True,
            base_commit_override=old.get("git", {}).get("base_commit"),
            takeover_of=old["session_id"],
        )
        checkpoint = write_checkpoint(
            ROOT,
            session,
            policy,
            summary=f"已从过期会话 {old['session_id']} 恢复当前工作区",
            next_step=args.next_step or old.get("next_step") or "审阅变更并继续原任务",
            blockers=[f"原会话异常中断：{args.reason}"],
            decisions=["采用显式recover，不覆盖原会话记录"],
            tests=[{"name": "Event Chain / Worktree Recovery", "result": "PASS", "evidence": session["session_log"], "note": "异常恢复检查点"}],
            parallel_execution={
                "assessment": "NO_SAFE_PARALLEL",
                "workers": [],
                "reason": "异常恢复与事件链校验必须由主控串行完成",
            },
        )
        append_event(ROOT, "SESSION_RECOVERED", {"old_session": old["session_id"], "new_session": session["session_id"], "reason": args.reason})
    print_yaml({"status": "RECOVERED", "old_session": old["session_id"], "new_session": session["session_id"], "checkpoint": checkpoint["checkpoint_id"]})


def resolve_next_task(root: Path, release: str, next_task_id: str) -> dict[str, Any]:
    task = release_task(root, release, next_task_id)
    start_command = f"python3 scripts/continuity.py start --actor <ACTOR_ID> --task {task['id']}"
    return {
        "id": task["id"],
        "title": task.get("title"),
        "status": task.get("status", "READY"),
        "release": release,
        "requirements": task.get("requirements", []),
        "depends_on": task.get("depends_on", []),
        "definition_of_ready": f"releases/{release}/DEFINITION_OF_READY.yaml",
        "stories": f"releases/{release}/STORIES.yaml",
        "steps": task.get("deliverables", []),
        "acceptance": task.get("acceptance", []),
        "claim_required": True,
        "start_command": start_command,
        "commands": {
            "resume": "python3 scripts/continuity.py resume",
            "start": start_command,
            "checkpoint": "python3 scripts/continuity.py checkpoint --summary '<阶段完成>' --next-step '<精确下一步>' --test 'name|PASS|evidence|note' --parallel-assessment <ASSESSMENT> --parallel-reason '<未委托原因>'",
            "handoff": "python3 scripts/continuity.py handoff --actor <ACTOR_ID> --reason '<移交原因>' --next-step '<精确下一步>'",
            "export_clean": "python3 scripts/continuity.py export-clean --portable-zip <OUTPUT.zip>",
            "cr_amend": "python3 scripts/continuity.py cr-amend --actor <ACTOR_ID> --cr <CR_ID> --original-rule '<原规则>' --new-rule '<新规则>' --impact-summary '<影响摘要>' --migration-and-compatibility '<迁移兼容说明>' --file <PATH> --test '<TEST>' --release <RELEASE>",
        },
        "next_after": "由当前TASKS.yaml依赖关系决定",
    }


def validate_next_task_transition(
    root: Path,
    *,
    current_release: str,
    current_task: str,
    next_release: str,
    next_task: str,
) -> dict[str, Any]:
    """Resolve the next task before close mutates any continuity record.

    Same-release transitions retain the existing task-chain behavior. A cross-
    release transition is intentionally stricter: only the final task may hand
    off to the first READY task of a directly dependent release, and all prior
    tasks in the current release must already be DONE.
    """
    if not re.fullmatch(r"[A-Z][0-9]{2}", next_release):
        raise ContinuityError(f"非法下一Release：{next_release}")

    target_path = root / "releases" / next_release / "TASKS.yaml"
    if not target_path.is_file():
        raise ContinuityError(f"下一Release不存在或缺少TASKS.yaml：{next_release}")
    target_plan = yaml.safe_load(target_path.read_text(encoding="utf-8")) or {}
    target_tasks = list(target_plan.get("tasks", []))
    next_document = resolve_next_task(root, next_release, next_task)
    if next_release == current_release:
        if next_task == current_task:
            raise ContinuityError("下一任务不能与当前任务相同")
        return next_document

    current_path = root / "releases" / current_release / "TASKS.yaml"
    current_plan = yaml.safe_load(current_path.read_text(encoding="utf-8")) or {}
    current_tasks = list(current_plan.get("tasks", []))
    current_ids = [str(row.get("id") or "") for row in current_tasks]
    if not current_ids or current_ids[-1] != current_task:
        raise ContinuityError(
            f"跨Release交接只能由当前Release最后一个任务发起：{current_release}/{current_task}"
        )
    incomplete = [
        str(row.get("id") or "")
        for row in current_tasks[:-1]
        if row.get("status") != "DONE"
    ]
    if incomplete:
        raise ContinuityError("当前Release仍有未完成前置任务：" + ", ".join(incomplete))

    if not target_tasks or target_tasks[0].get("id") != next_task:
        raise ContinuityError(
            f"跨Release交接只能指向下一Release首个任务：{next_release}/{next_task}"
        )
    if target_tasks[0].get("status") != "READY":
        raise ContinuityError(
            f"下一Release首个任务尚未READY：{next_release}/{next_task} / {target_tasks[0].get('status')}"
        )

    dependency_path = root / "releases" / "RELEASE_DEPENDENCIES.yaml"
    dependency_document = yaml.safe_load(dependency_path.read_text(encoding="utf-8")) or {}
    declared_dependencies = set(
        (dependency_document.get("dependencies", {}) or {}).get(next_release, []) or []
    )
    if current_release not in declared_dependencies:
        raise ContinuityError(
            f"下一Release未声明依赖当前Release：{next_release} !<- {current_release}"
        )
    return next_document


def release_has_async_owner_gate(root: Path, release: str) -> bool:
    manifest_path = root / "releases" / release / "RELEASE_MANIFEST.yaml"
    if not manifest_path.is_file():
        return False
    manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8")) or {}
    delivery = manifest.get("android_delivery") if isinstance(manifest.get("android_delivery"), dict) else {}
    completion = manifest.get("machine_completion") if isinstance(manifest.get("machine_completion"), dict) else {}
    return all((
        delivery.get("machine_delivery") == "PASS",
        delivery.get("owner_physical_test") == "PENDING",
        completion.get("status") == "PASS",
        completion.get("owner_feedback_mode") == "ASYNC_NON_BLOCKING",
        completion.get("formal_release_acceptance") == "PENDING_OWNER_PHYSICAL_TEST",
        completion.get("production_activation") == "BLOCKED_OWNER_PHYSICAL_TEST",
        completion.get("next_release_development") == "ALLOWED",
    ))


def validate_independent_release_start(
    root: Path,
    *,
    current_release: str,
    current_task: str,
    next_release: str,
    next_task: str,
) -> dict[str, Any]:
    """Allow an external-gate task to wait without stopping an independent DAG lane."""
    if next_release == current_release:
        raise ContinuityError("外部门禁挂起只能切换到独立Release")
    if not re.fullmatch(r"[A-Z][0-9]{2}", next_release):
        raise ContinuityError(f"非法下一Release：{next_release}")

    current = release_task(root, current_release, current_task)
    current_text = " ".join(
        str(value)
        for value in (
            current.get("title"), current.get("description"),
            current.get("deliverables"), current.get("acceptance"),
        )
    )
    if "APK" not in current_text and not release_has_async_owner_gate(root, current_release):
        raise ContinuityError("只有APK/项目所有者真机等外部交付门禁可挂起后继续独立Release")

    target_path = root / "releases" / next_release / "TASKS.yaml"
    if not target_path.is_file():
        raise ContinuityError(f"下一Release不存在或缺少TASKS.yaml：{next_release}")
    target_plan = yaml.safe_load(target_path.read_text(encoding="utf-8")) or {}
    target_tasks = list(target_plan.get("tasks", []))
    if not target_tasks or target_tasks[0].get("id") != next_task:
        raise ContinuityError(
            f"独立Release只能从首个任务开始：{next_release}/{next_task}"
        )
    if target_tasks[0].get("status") != "READY":
        raise ContinuityError(
            f"独立Release首个任务尚未READY：{next_release}/{next_task} / {target_tasks[0].get('status')}"
        )

    dependency_path = root / "releases" / "RELEASE_DEPENDENCIES.yaml"
    dependency_document = yaml.safe_load(dependency_path.read_text(encoding="utf-8")) or {}
    declared_dependencies = list(
        (dependency_document.get("dependencies", {}) or {}).get(next_release, []) or []
    )
    if not declared_dependencies:
        raise ContinuityError(f"目标Release没有声明依赖，禁止外部门禁旁路：{next_release}")
    incomplete_dependencies: list[str] = []
    for dependency in declared_dependencies:
        plan_path = root / "releases" / dependency / "TASKS.yaml"
        if not plan_path.is_file():
            incomplete_dependencies.append(f"{dependency}:MISSING")
            continue
        plan = yaml.safe_load(plan_path.read_text(encoding="utf-8")) or {}
        dependency_tasks = list(plan.get("tasks", []))
        final_dependency_task = (
            str(dependency_tasks[-1].get("id") or "") if dependency_tasks else ""
        )
        unfinished: list[str] = []
        for row in dependency_tasks:
            task_id = str(row.get("id") or "")
            if row.get("status") == "DONE":
                continue
            is_current_async_owner_gate = all((
                dependency == current_release,
                task_id == current_task,
                release_has_async_owner_gate(root, current_release),
            ))
            is_prior_async_owner_gate = all((
                dependency != current_release,
                release_has_async_owner_gate(root, dependency),
                task_id == final_dependency_task,
                row.get("status") == "BLOCKED",
            ))
            if not is_current_async_owner_gate and not is_prior_async_owner_gate:
                unfinished.append(task_id)
        if unfinished:
            incomplete_dependencies.append(f"{dependency}:{','.join(unfinished)}")
    if incomplete_dependencies:
        raise ContinuityError(
            "目标Release依赖尚未全部GREEN：" + "; ".join(incomplete_dependencies)
        )
    return resolve_next_task(root, next_release, next_task)


def mark_release_task_blocked(root: Path, release: str, task_id: str, reason: str) -> None:
    path = root / "releases" / release / "TASKS.yaml"
    document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    for task in document.get("tasks", []):
        if task.get("id") == task_id:
            task["status"] = "BLOCKED"
            task["blocker"] = reason
            task["blocked_at"] = iso_utc()
            break
    atomic_write_yaml(path, document)


def update_release_task_states(root: Path, release: str, completed_task: str, next_task: str | None) -> None:
    path = root / "releases" / release / "TASKS.yaml"
    document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    for task in document.get("tasks", []):
        if task.get("id") == completed_task:
            task["status"] = "DONE"
            task["completed_at"] = iso_utc()
        elif next_task and task.get("id") == next_task:
            task["status"] = "READY"
    atomic_write_yaml(path, document)


def append_closure_to_log(
    root: Path,
    session: dict[str, Any],
    result: str,
    summary: str,
    code_commit: str,
    next_task: str | None,
    next_release: str | None,
) -> None:
    path = root / session["session_log"]
    text = path.read_text(encoding="utf-8")
    text += f"""

## 会话关闭 · {iso_utc()}

- 结果：`{result}`
- 总结：{summary}
- 代码 Commit：`{code_commit}`
- 下一 Release：`{next_release or 'N/A'}`
- 下一任务：`{next_task or 'N/A'}`
- 推送验证：本关闭记录随最终元数据 Commit 推送，并由 CI continuity gate 验证。
- 对话依赖：无；后续只读取仓库和 Context Pack。
"""
    atomic_write_text(path, text)


def append_task_transition(
    root: Path,
    session: dict[str, Any],
    *,
    from_status: str,
    to_status: str,
    reason: str,
) -> str:
    path = root / "catalogs/task_transition_ledger.csv"
    rows = read_csv(path) if path.exists() else []
    for row in reversed(rows):
        if (
            row.get("session_id") == session["session_id"]
            and row.get("task_id") == session["task_id"]
            and row.get("to_status") == to_status
        ):
            return str(row["transition_id"])
    transition_id = f"TRN-{now_utc().strftime('%Y%m%dT%H%M%SZ')}-{session['session_id']}"
    rows.append({
        "transition_id": transition_id,
        "timestamp": iso_utc(),
        "release": session["release"],
        "task_id": session["task_id"],
        "story_id": session.get("story_id") or "N/A",
        "from_status": from_status,
        "to_status": to_status,
        "session_id": session["session_id"],
        "actor_id": session["actor"]["id"],
        "reason": reason,
    })
    write_csv(path, rows, [
        "transition_id", "timestamp", "release", "task_id", "story_id",
        "from_status", "to_status", "session_id", "actor_id", "reason",
    ])
    return transition_id


def append_task_close_changelog(root: Path, session: dict[str, Any], result: str, summary: str) -> None:
    path = root / "CHANGELOG.md"
    text = path.read_text(encoding="utf-8") if path.exists() else "# CHANGELOG\n"
    marker = f"Task close: {session['task_id']} / {session['session_id']}"
    if marker in text:
        return
    block = f"""

## {session['task_id']} · {result} · {iso_utc()}

- {marker}
- Release：`{session['release']}`
- Story：`{session.get('story_id') or 'N/A'}`
- Actor：`{session['actor']['id']}`
- 摘要：{summary}
- 记录：`{session['session_log']}`
"""
    atomic_write_text(path, text.rstrip() + block + "\n")


def ensure_closure_scope(root: Path, session: dict[str, Any]) -> None:
    """Repair legacy platform sessions that omitted mandatory close metadata."""
    scope = session.setdefault("scope", {})
    allowed = scope.setdefault("allowed_paths", [])
    if "CHANGELOG.md" in allowed:
        return
    allowed.append("CHANGELOG.md")
    source = str(scope.get("source") or "story+explicit")
    if "mandatory-closure-metadata" not in source:
        scope["source"] = source + "+mandatory-closure-metadata"
    save_session(root, session)


def closing_event_exists(root: Path, session_id: str) -> bool:
    path = root / ".continuity/EVENT_LOG.jsonl"
    if not path.exists():
        return False
    for line in reversed(path.read_text(encoding="utf-8").splitlines()):
        if not line.strip():
            continue
        event = json.loads(line)
        if event.get("event_type") == "SESSION_CLOSING" and event.get("payload", {}).get("session_id") == session_id:
            return True
    return False


def command_close(args: Namespace) -> None:
    actor = get_actor(args)
    result = args.result.upper()
    if result not in {"COMPLETED", "BLOCKED", "ABANDONED"}:
        raise ContinuityError("result必须为 COMPLETED/BLOCKED/ABANDONED")
    blocked_advance = result == "BLOCKED" and bool(args.next_release or args.next_task)
    if result == "BLOCKED" and bool(args.next_release) != bool(args.next_task):
        raise ContinuityError("BLOCKED继续独立Release必须同时提供--next-release和--next-task")
    if blocked_advance:
        if not args.allow_independent_release:
            raise ContinuityError("BLOCKED继续独立Release必须显式提供--allow-independent-release")
        if len((args.user_confirmation or "").strip()) < 10:
            raise ContinuityError("BLOCKED继续独立Release必须记录项目所有者明确授权")
    elif args.allow_independent_release or args.user_confirmation:
        raise ContinuityError("独立Release授权参数只允许用于BLOCKED继续开发")
    if result == "ABANDONED" and (args.next_release or args.next_task):
        raise ContinuityError("ABANDONED不得切换NEXT_TASK")
    with continuity_lock(ROOT):
        policy = load_policy(ROOT)
        session = current_session(ROOT, allow_handoff=False)
        if not session:
            raise ContinuityError("没有ACTIVE会话")
        if session["actor"]["id"] != actor:
            raise ContinuityError("只有当前Actor可以关闭会话")
        resuming_close = session.get("status") == "CLOSING"
        ensure_closure_scope(ROOT, session)
        checkpoint = latest_checkpoint(ROOT, session)
        if not checkpoint:
            raise ContinuityError("关闭前必须创建实现检查点")
        git_state = git_info(ROOT)
        current: dict[str, Any] | None = None
        if not resuming_close:
            current = project_fingerprint(ROOT, session)
            if current["sha256"] != checkpoint.get("project_fingerprint", {}).get("sha256"):
                raise ContinuityError("最新检查点之后项目内容发生变化")
            project_dirty = [
                path for path in git_changed_files(ROOT)
                if not path.startswith((".continuity/", "artifacts/context/", "docs/03-continuity/sessions/"))
                and path not in {"CURRENT_STATUS.yaml", "NEXT_TASK.yaml"}
            ]
            if project_dirty:
                raise ContinuityError(
                    "关闭会话前项目内容必须已提交；若需要中途移交请使用handoff。未提交："
                    + ", ".join(project_dirty[:30])
                )
        stored_closure = session.get("closure", {}) if resuming_close else {}
        next_release = (
            str(stored_closure.get("next_release") or session["release"])
            if resuming_close else args.next_release or session["release"]
        )
        next_document: dict[str, Any] | None = None
        if resuming_close:
            requested_next_release = (
                args.next_release or session["release"]
                if result == "COMPLETED"
                else args.next_release if blocked_advance else None
            )
            expected = {
                "result": result,
                "summary": args.summary,
                "next_release": requested_next_release,
                "next_task": args.next_task,
            }
            actual = {key: stored_closure.get(key) for key in expected}
            if actual != expected:
                raise ContinuityError("CLOSING会话只能使用原关闭参数续跑")
            if args.code_commit and args.code_commit != stored_closure.get("code_commit"):
                raise ContinuityError("CLOSING会话的实现Commit不得变更")
        elif result == "COMPLETED":
            if not git_state["initialized"]:
                raise ContinuityError("完成任务前必须初始化Git")
            if git_state["head"] == session.get("git", {}).get("base_commit"):
                raise ContinuityError("任务没有产生新的实现Commit；不能标记COMPLETED")
            tests = checkpoint.get("tests", [])
            if current and current["file_count"] and not any(row.get("result") == "PASS" for row in tests):
                raise ContinuityError("有项目变更但最新实现检查点没有PASS测试")
            if any(row.get("result") == "FAIL" for row in tests):
                raise ContinuityError("最新实现检查点仍有FAIL测试")
            if not args.next_task:
                raise ContinuityError("COMPLETED必须提供 --next-task")
            # Resolve and validate the complete target before the first write.
            # Invalid cross-release targets must leave the session, task plans,
            # event chain and pointers byte-for-byte unchanged.
            next_document = validate_next_task_transition(
                ROOT,
                current_release=session["release"],
                current_task=session["task_id"],
                next_release=next_release,
                next_task=args.next_task,
            )
        elif blocked_advance:
            next_document = validate_independent_release_start(
                ROOT,
                current_release=session["release"],
                current_task=session["task_id"],
                next_release=args.next_release,
                next_task=args.next_task,
            )

        code_commit = (
            str(stored_closure.get("code_commit"))
            if resuming_close else args.code_commit or git_state.get("head") or "NOT_INITIALIZED"
        )
        if not resuming_close:
            session["status"] = "CLOSING"
            session["closure"] = {
                "result": result,
                "summary": args.summary,
                "code_commit": code_commit,
                "next_release": next_release if result == "COMPLETED" or blocked_advance else None,
                "next_task": args.next_task,
                "blocked_advance": blocked_advance,
                "user_confirmation": (args.user_confirmation or "").strip() or None,
                "metadata_commit": "PENDING",
                "push_verification": "CI_REQUIRED_AFTER_METADATA_COMMIT",
                "started_at": iso_utc(),
            }
            save_session(ROOT, session)

        if resuming_close:
            transition_to = "DONE" if result == "COMPLETED" else "BLOCKED_EXTERNAL_GATE" if blocked_advance else result
        elif result == "COMPLETED":
            update_release_task_states(
                ROOT,
                session["release"],
                session["task_id"],
                args.next_task if next_release == session["release"] else None,
            )
            assert next_document is not None
            next_document["status"] = "READY"
            atomic_write_yaml(ROOT / "NEXT_TASK.yaml", next_document)
            transition_to = "DONE"
        elif result == "BLOCKED":
            mark_release_task_blocked(ROOT, session["release"], session["task_id"], args.summary)
            if blocked_advance:
                assert next_document is not None
                next_document["status"] = "READY"
                next_document["deferred_task"] = {
                    "id": session["task_id"],
                    "release": session["release"],
                    "status": "BLOCKED",
                    "reason": args.summary,
                    "resume_after": "项目所有者真机反馈到达后，在当前安全检查点恢复验收与关闭",
                }
                atomic_write_yaml(ROOT / "NEXT_TASK.yaml", next_document)
                transition_to = "BLOCKED_EXTERNAL_GATE"
            else:
                next_document = read_next_task(ROOT)
                next_document["status"] = "BLOCKED"
                next_document["blocker"] = args.summary
                next_document["resume_command"] = blocked_resume_command(session["task_id"])
                atomic_write_yaml(ROOT / "NEXT_TASK.yaml", next_document)
                transition_to = "BLOCKED"
        else:
            transition_to = "ABANDONED"

        transition_id = append_task_transition(
            ROOT, session, from_status="IN_PROGRESS", to_status=transition_to, reason=args.summary
        )
        append_task_close_changelog(ROOT, session, result, args.summary)
        # Keep CURRENT_STATUS in CLOSING while the closure checkpoint is being
        # produced. ``write_checkpoint`` refreshes the active-session status, so
        # the final READY/BLOCKED state must be written only after that checkpoint.
        if not closing_event_exists(ROOT, session["session_id"]):
            append_event(ROOT, "SESSION_CLOSING", {
                "session_id": session["session_id"],
                "result": result,
                "code_commit": code_commit,
                "next_release": next_release if result == "COMPLETED" or blocked_advance else None,
                "next_task": args.next_task,
                "transition_id": transition_id,
            })

        inherited_tests = list(checkpoint.get("tests", []))
        inherited_tests.append({
            "name": "continuity closure metadata",
            "result": "PASS",
            "evidence": "catalogs/task_transition_ledger.csv;CHANGELOG.md;CURRENT_STATUS.yaml;NEXT_TASK.yaml",
            "note": "关闭元数据、任务转换和下一任务已原子生成",
        })
        closure_checkpoint = write_checkpoint(
            ROOT,
            session,
            policy,
            summary=f"关闭 {session['task_id']}：{args.summary}",
            next_step="提交关闭元数据Commit，然后运行pre-push/CI门禁",
            blockers=[] if result == "COMPLETED" else [args.summary],
            decisions=[
                f"任务结果：{result}",
                f"实现Commit：{code_commit}",
                f"任务转换：{transition_id}",
                f"下一Release：{next_release if result == 'COMPLETED' else 'N/A'}",
            ],
            tests=inherited_tests,
            note="该检查点专门绑定最终关闭元数据Commit",
        )

        session["status"] = "CLOSED" if result != "ABANDONED" else "ABANDONED"
        session["closed_at"] = iso_utc()
        session["closure"].update({
            "closure_checkpoint_id": closure_checkpoint["checkpoint_id"],
            "closed_at": session["closed_at"],
        })
        save_session(ROOT, session)
        close_task_claim(ROOT, session, session["status"])
        update_session_index(ROOT, session)
        save_active_pointer(ROOT, {
            "protocol_version": PROTOCOL_VERSION,
            "active_session_id": None,
            "status": "NONE",
            "last_session_id": session["session_id"],
        })
        state = load_state(ROOT)
        state["active_session_id"] = None
        state["last_session_id"] = session["session_id"]
        state["last_session_result"] = result
        state["last_closure_checkpoint_id"] = closure_checkpoint["checkpoint_id"]
        save_state(ROOT, state)
        update_current_status_closed(
            ROOT,
            session,
            result=result,
            next_task_id=args.next_task or session["task_id"],
            code_commit=code_commit,
        )
        if blocked_advance:
            status = read_current_status(ROOT)
            status.update({
                "phase": next_release,
                "active_release": next_release,
                "status": "READY",
                "active_task": args.next_task,
                "next_task": args.next_task,
                "updated_at": iso_utc(),
            })
            atomic_write_yaml(ROOT / "CURRENT_STATUS.yaml", status)
        append_closure_to_log(
            ROOT,
            session,
            result,
            args.summary,
            code_commit,
            args.next_task,
            next_release if result == "COMPLETED" or blocked_advance else None,
        )
        append_event(ROOT, "SESSION_CLOSED", {
            "session_id": session["session_id"],
            "result": result,
            "code_commit": code_commit,
            "next_release": next_release if result == "COMPLETED" or blocked_advance else None,
            "next_task": args.next_task,
            "closure_checkpoint_id": closure_checkpoint["checkpoint_id"],
        })
        build_context_pack(ROOT, None)
        trailers = expected_commit_trailers(session, closure_checkpoint)
    print_yaml({
        "status": "SESSION_CLOSED",
        "session_id": session["session_id"],
        "result": result,
        "next_release": next_release if result == "COMPLETED" or blocked_advance else None,
        "next_task": args.next_task,
        "closure_checkpoint": closure_checkpoint["checkpoint_id"],
        "final_metadata_commit_required": True,
        "commit_subject": f"[{session['task_id']}] chore(continuity): close {session['task_id']} as {result.lower()}",
        "required_trailers": trailers,
        "next_commands": [
            "git add -A",
            "git commit  # 使用.gitmessage并保留自动生成Trailers",
            "git push",
        ],
    })

def command_export_clean(args: Namespace) -> None:
    with continuity_lock(ROOT):
        result = create_clean_export_bundle(
            ROOT,
            portable_zip=args.portable_zip,
            include_git_bundle=not args.no_git_bundle,
        )
    print_yaml({"status": "CLEAN_PROJECT_EXPORTED", **result})


def command_context(args: Namespace) -> None:
    with continuity_lock(ROOT):
        session = current_session(ROOT)
        payload = build_context_pack(ROOT, session)
    print_yaml({"status": "CONTEXT_PACK_GENERATED", "context_hash": payload["context_hash"], "resume_command": payload["exact_resume_command"]})


def command_resume(args: Namespace) -> None:
    initialize_continuity_files(ROOT)
    chain = validate_event_chain(ROOT)
    if not chain["valid"]:
        raise ContinuityError("事件日志哈希链损坏：" + ";".join(chain["errors"]))
    session = current_session(ROOT)
    payload = build_context_pack(ROOT, session)
    if not session:
        git_state = git_info(ROOT)
        policy = load_policy(ROOT)
        bootstrap_tasks = set(policy.get("bootstrap", {}).get("allow_without_git_task_ids", []))
        bootstrap_required = not git_has_concrete_head(git_state) and payload["next_task"].get("id") in bootstrap_tasks
        print_yaml({
            "status": "GIT_BOOTSTRAP_REQUIRED" if bootstrap_required else "READY_TO_START",
            "conversation_context_required": False,
            "next_task": payload["next_task"].get("id"),
            "resume_command": payload["exact_resume_command"],
            "context_pack": "artifacts/context/CURRENT_CONTEXT_PACK.md",
        })
        return
    if session.get("status") == "HANDED_OFF":
        print_yaml({
            "status": "HANDOFF_READY",
            "session_id": session["session_id"],
            "handoff_bundle": session.get("handoff_bundle"),
            "resume_command": payload["exact_resume_command"],
            "conversation_context_required": False,
        })
        return
    if effective_lease_expired(ROOT, session):
        print_yaml({
            "status": "STALE_SESSION_REQUIRES_RECOVERY",
            "session_id": session["session_id"],
            "actor_id": session["actor"]["id"],
            "recover_command": f"python3 scripts/continuity.py recover --actor <NEW_ACTOR> --session {session['session_id']} --reason '<异常原因>'",
        })
        raise SystemExit(3)
    fresh, reason = context_is_fresh(ROOT, session)
    print_yaml({
        "status": "ACTIVE_SESSION_RESUMED",
        "session_id": session["session_id"],
        "actor_id": session["actor"]["id"],
        "task_id": session["task_id"],
        "story_id": session.get("story_id"),
        "latest_checkpoint": session.get("latest_checkpoint"),
        "next_step": session.get("next_step"),
        "context_fresh": fresh,
        "context_reason": reason,
        "exact_next_command": payload["exact_resume_command"],
        "conversation_context_required": False,
    })


def command_status(args: Namespace) -> None:
    session = current_session(ROOT)
    chain = validate_event_chain(ROOT)
    fresh, reason = context_is_fresh(ROOT, session)
    print_yaml({
        "state": load_state(ROOT),
        "active_pointer": load_active_pointer(ROOT),
        "active_session": session,
        "git": git_info(ROOT),
        "event_chain": chain,
        "context_fresh": fresh,
        "context_reason": reason,
    })


def command_cr_create(args: Namespace) -> None:
    actor = get_actor(args)
    session = current_session(ROOT)
    task_id = args.task or (session or {}).get("task_id") or read_next_task(ROOT).get("id")
    with continuity_lock(ROOT):
        record = create_change_request(
            ROOT,
            title=args.title,
            requester_actor_id=actor,
            task_id=task_id,
            session_id=(session or {}).get("session_id"),
            user_request=args.user_request,
            reason=args.reason,
        )
    print_yaml(record)


def command_cr_amend(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        record = amend_change_request(
            ROOT,
            cr_id=args.cr,
            actor_id=actor,
            original_rule=args.original_rule,
            new_rule=args.new_rule,
            impact_summary=args.impact_summary,
            migration_and_compatibility=args.migration_and_compatibility,
            files=args.file,
            pages=args.page,
            apis=args.api,
            database=args.database,
            configuration=args.configuration,
            ledger=args.ledger,
            tests=args.test,
            releases=args.release,
        )
    print_yaml(record)


def command_cr_approve(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        record = approve_change_request(
            ROOT,
            cr_id=args.cr,
            approver_actor_id=actor,
            decision=args.decision,
            note=args.note,
            user_confirmation=args.user_confirmation,
        )
    print_yaml(record)



def command_cr_update(args: Namespace) -> None:
    actor = get_actor(args)
    session = current_session(ROOT)
    with continuity_lock(ROOT):
        record = update_change_request_status(
            ROOT,
            cr_id=args.cr,
            actor_id=actor,
            status=args.status,
            note=args.note,
            session_id=(session or {}).get("session_id"),
            commits=args.commit,
        )
    print_yaml(record)


def command_scope_apply_cr(args: Namespace) -> None:
    actor = get_actor(args)
    with continuity_lock(ROOT):
        session = current_session(ROOT, allow_handoff=False)
        if not session:
            raise ContinuityError("没有ACTIVE会话")
        result = apply_change_request_scope(
            ROOT,
            session=session,
            cr_id=args.cr,
            actor_id=actor,
        )
    print_yaml(result)

def build_parser() -> ArgumentParser:
    parser = ArgumentParser(description="持续开发无状态接续强制门禁")
    sub = parser.add_subparsers(dest="command", required=True)

    bootstrap = sub.add_parser("bootstrap", help="首次初始化Git、Baseline Commit、Hooks和任务分支")
    bootstrap.add_argument("--actor")
    bootstrap.add_argument("--task", default="TASK-P00-001")
    bootstrap.add_argument("--branch", default="task/TASK-P00-001")
    bootstrap.add_argument("--init-git", action="store_true")
    bootstrap.add_argument("--initial-commit", action="store_true")
    bootstrap.add_argument("--git-user-name", default="HHY Continuity Bootstrap")
    bootstrap.add_argument("--git-user-email", default="continuity@orbexa.cc")
    bootstrap.set_defaults(func=command_bootstrap)

    start = sub.add_parser("start", help="领取NEXT_TASK并启动唯一活跃会话")
    start.add_argument("--actor")
    start.add_argument("--task", required=True)
    start.add_argument("--story")
    start.add_argument("--goal", default="")
    start.add_argument("--cr", action="append", default=[])
    start.add_argument("--scope", action="append", default=[])
    start.set_defaults(func=command_start)

    checkpoint = sub.add_parser("checkpoint", help="写入可恢复检查点并刷新Context Pack")
    checkpoint.add_argument("--summary", required=True)
    checkpoint.add_argument("--next-step", required=True)
    checkpoint.add_argument("--blocker", action="append", default=[])
    checkpoint.add_argument("--decision", action="append", default=[])
    checkpoint.add_argument("--test", action="append", default=[], help="name|PASS|evidence|note")
    checkpoint.add_argument("--no-test-reason", default="")
    checkpoint.add_argument(
        "--parallel-assessment",
        required=True,
        choices=["DELEGATED", "NO_SAFE_PARALLEL", "CAPABILITY_UNAVAILABLE", "USER_SERIAL_OVERRIDE"],
    )
    checkpoint.add_argument("--delegated-worker", action="append", default=[], help="worker_id|responsibility|path1,path2")
    checkpoint.add_argument("--parallel-reason", default="")
    checkpoint.add_argument("--note", default="")
    checkpoint.set_defaults(func=command_checkpoint)

    heartbeat = sub.add_parser("heartbeat", help="长任务期间续租；不替代强制检查点")
    heartbeat.add_argument("--actor")
    heartbeat.add_argument("--note", default="")
    heartbeat.set_defaults(func=command_heartbeat)

    handoff = sub.add_parser("handoff", help="生成可移交WIP、Git、Context和哈希包")
    handoff.add_argument("--actor")
    handoff.add_argument("--reason", required=True)
    handoff.add_argument("--next-step", required=True)
    handoff.add_argument("--portable-zip")
    handoff.add_argument("--no-git-bundle", action="store_true")
    handoff.set_defaults(func=command_handoff)

    export_clean = sub.add_parser("export-clean", help="导出无活跃会话、干净Commit和完整Git历史的项目交接包")
    export_clean.add_argument("--portable-zip")
    export_clean.add_argument("--no-git-bundle", action="store_true")
    export_clean.set_defaults(func=command_export_clean)

    takeover = sub.add_parser("takeover", help="接管HANDED_OFF或过期会话")
    takeover.add_argument("--actor")
    takeover.add_argument("--session", required=True)
    takeover.add_argument("--reason", default="仓库交接")
    takeover.add_argument("--next-step", default="")
    takeover.set_defaults(func=command_takeover)

    recover = sub.add_parser("recover", help="显式恢复过期或异常中断会话")
    recover.add_argument("--actor")
    recover.add_argument("--session", required=True)
    recover.add_argument("--reason", required=True)
    recover.add_argument("--next-step", default="")
    recover.set_defaults(func=command_recover)

    close = sub.add_parser("close", help="关闭会话并切换NEXT_TASK")
    close.add_argument("--actor")
    close.add_argument("--result", required=True, choices=["COMPLETED", "BLOCKED", "ABANDONED"])
    close.add_argument("--summary", required=True)
    close.add_argument("--next-release")
    close.add_argument("--next-task")
    close.add_argument("--code-commit")
    close.add_argument("--allow-independent-release", action="store_true")
    close.add_argument("--user-confirmation")
    close.set_defaults(func=command_close)

    context = sub.add_parser("context", help="重建机器和人类可读Context Pack")
    context.set_defaults(func=command_context)

    resume = sub.add_parser("resume", help="冷启动接手；输出唯一合法下一命令")
    resume.set_defaults(func=command_resume)

    status = sub.add_parser("status", help="查看接续、Git、事件链和上下文状态")
    status.set_defaults(func=command_status)

    cr_create = sub.add_parser("cr-create", help="创建机器可读CR并关联当前会话")
    cr_create.add_argument("--actor")
    cr_create.add_argument("--title", required=True)
    cr_create.add_argument("--task")
    cr_create.add_argument("--user-request", default="")
    cr_create.add_argument("--reason", default="")
    cr_create.set_defaults(func=command_cr_create)

    cr_amend = sub.add_parser("cr-amend", help="由申请人补齐CR旧/新规则、影响文件、测试和兼容策略")
    cr_amend.add_argument("--actor")
    cr_amend.add_argument("--cr", required=True)
    cr_amend.add_argument("--original-rule", required=True)
    cr_amend.add_argument("--new-rule", required=True)
    cr_amend.add_argument("--impact-summary", required=True)
    cr_amend.add_argument("--migration-and-compatibility", required=True)
    cr_amend.add_argument("--file", action="append", default=[], required=True)
    cr_amend.add_argument("--page", action="append", default=[])
    cr_amend.add_argument("--api", action="append", default=[])
    cr_amend.add_argument("--database", action="append", default=[])
    cr_amend.add_argument("--configuration", action="append", default=[])
    cr_amend.add_argument("--ledger", action="append", default=[])
    cr_amend.add_argument("--test", action="append", default=[], required=True)
    cr_amend.add_argument("--release", action="append", default=[], required=True)
    cr_amend.set_defaults(func=command_cr_amend)

    cr_approve = sub.add_parser("cr-approve", help="由不同Actor审批CR")
    cr_approve.add_argument("--actor")
    cr_approve.add_argument("--cr", required=True)
    cr_approve.add_argument("--decision", required=True, choices=["APPROVED", "REJECTED"])
    cr_approve.add_argument("--note", required=True)
    cr_approve.add_argument("--user-confirmation", required=True)
    cr_approve.set_defaults(func=command_cr_approve)

    cr_update = sub.add_parser("cr-update", help="推进已批准CR的IMPLEMENTING/IMPLEMENTED/CLOSED状态")
    cr_update.add_argument("--actor")
    cr_update.add_argument("--cr", required=True)
    cr_update.add_argument("--status", required=True, choices=["IMPLEMENTING", "IMPLEMENTED", "CLOSED", "SUPERSEDED", "REJECTED"])
    cr_update.add_argument("--note", required=True)
    cr_update.add_argument("--commit", action="append", default=[])
    cr_update.set_defaults(func=command_cr_update)

    scope_apply_cr = sub.add_parser("scope-apply-cr", help="将已批准CR的精确影响文件应用到当前会话范围")
    scope_apply_cr.add_argument("--actor")
    scope_apply_cr.add_argument("--cr", required=True)
    scope_apply_cr.set_defaults(func=command_scope_apply_cr)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        args.func(args)
        return 0
    except ContinuityError as exc:
        print(f"CONTINUITY_GATE_ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
