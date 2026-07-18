#!/usr/bin/env python3
"""End-to-end smoke test for V1.2.3 stateless continuity protocol.

The test creates an isolated Git repository and exercises actual hooks/CI logic:
bootstrap, no-session rejection, unique claim, checkpoint/test enforcement,
stale checkpoint, complete CR contract, WIP handoff/takeover, close metadata,
per-commit pre-push/CI, clean export and event-chain tamper detection.
"""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
from typing import Any
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import yaml

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT = ROOT / "artifacts/validation/continuity-lifecycle-integration-v1.2.3.json"
DEFAULT_LOG = ROOT / "artifacts/validation/continuity-lifecycle-integration-v1.2.3.log"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()



def source_manifest() -> list[dict[str, str]]:
    critical = [
        ".continuity/CONTINUITY_POLICY.yaml",
        "config/REPOSITORY_TRANSPORT.yaml",
        "config/DEVELOPMENT_RUNTIME.yaml",
        "scripts/continuity_lib.py",
        "scripts/continuity.py",
        "scripts/continuity_gate.py",
        "scripts/restore_git_transport.py",
        "scripts/select_execution_profile.py",
        "scripts/verify_cloud_environment.py",
        "scripts/prepare_commit_message.py",
        "scripts/install_git_hooks.py",
        "scripts/test_continuity_protocol.py",
        ".githooks/pre-commit",
        ".githooks/prepare-commit-msg",
        ".githooks/commit-msg",
        ".githooks/pre-push",
    ]
    return [
        {"path": relative, "sha256": sha256(ROOT / relative)}
        for relative in critical
        if (ROOT / relative).is_file()
    ]

def copy_repository(source: Path, target: Path) -> None:
    ignored_names = {
        ".git", "__pycache__", "node_modules", "dist", "target", "build", ".gradle"
    }

    def ignore(directory: str, names: list[str]) -> set[str]:
        result = {name for name in names if name in ignored_names or name.endswith((".pyc", ".pyo"))}
        rel = Path(directory).resolve().relative_to(source.resolve()) if Path(directory).resolve() != source.resolve() else Path(".")
        rel_text = rel.as_posix()
        if rel_text in {".continuity/runtime", "artifacts/exports", "artifacts/handoff-exports", "artifacts/continuity-smoke"}:
            result.update(names)
        return result

    shutil.copytree(source, target, ignore=ignore, copy_function=shutil.copy2)
    runtime = target / ".continuity/runtime"
    runtime.mkdir(parents=True, exist_ok=True)
    (runtime / ".gitkeep").touch()


def reset_continuity_fixture(root: Path) -> None:
    """Remove the caller's active runtime identity from an isolated lifecycle copy."""
    (root / ".continuity/EVENT_LOG.jsonl").write_text("", encoding="utf-8")
    records = {
        ".continuity/ACTIVE_SESSION.yaml": {
            "protocol_version": "1.0", "active_session_id": None, "status": "NONE"
        },
        ".continuity/SESSION_INDEX.yaml": {"version": "1.0", "sessions": []},
        ".continuity/TASK_CLAIMS.yaml": {"version": "1.0", "claims": []},
        ".continuity/TASK_TRANSITIONS.yaml": {"version": "1.0", "transitions": []},
        ".continuity/STATE.yaml": {
            "protocol_version": "1.0",
            "package_version": "1.2.3",
            "mode": "ENFORCED",
            "active_session_id": None,
            "event_log": {"sequence": 0, "head_hash": "0" * 64},
        },
    }
    for relative, payload in records.items():
        (root / relative).write_text(
            yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8"
        )
    status_path = root / "CURRENT_STATUS.yaml"
    status = yaml.safe_load(status_path.read_text(encoding="utf-8")) or {}
    status.update({
        "phase": "P00",
        "active_release": "P00",
        "active_task": None,
        "status": "READY",
        "in_progress_tasks": [],
        "next_task": "TASK-P00-001",
        "completed_tasks": [
            item for item in status.get("completed_tasks", [])
            if not str(item).startswith("TASK-P00-")
        ],
    })
    status_path.write_text(
        yaml.safe_dump(status, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )

    tasks_path = root / "releases/P00/TASKS.yaml"
    task_document = yaml.safe_load(tasks_path.read_text(encoding="utf-8")) or {}
    tasks = task_document.get("tasks") or []
    for task in tasks:
        task["status"] = "READY" if task.get("id") == "TASK-P00-001" else "BLOCKED"
        task.pop("completed_at", None)
    tasks_path.write_text(
        yaml.safe_dump(task_document, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )

    first_task = next(task for task in tasks if task.get("id") == "TASK-P00-001")
    next_path = root / "NEXT_TASK.yaml"
    next_task = yaml.safe_load(next_path.read_text(encoding="utf-8")) or {}
    next_task.update({
        "id": first_task["id"],
        "title": first_task["title"],
        "status": "READY",
        "release": "P00",
        "requirements": first_task.get("requirements", []),
        "depends_on": first_task.get("depends_on", []),
        "steps": first_task.get("deliverables", []),
        "acceptance": first_task.get("acceptance", []),
        "start_command": "python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-P00-001",
    })
    next_path.write_text(
        yaml.safe_dump(next_task, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )


class Smoke:
    def __init__(self, repo: Path, output_dir: Path) -> None:
        self.repo = repo
        self.output_dir = output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.env = dict(os.environ)
        self.env.update({
            "PYTHONDONTWRITEBYTECODE": "1",
            "HHY_PYTHON": sys.executable,
            "GIT_TERMINAL_PROMPT": "0",
            "GIT_EDITOR": "true",
            "GIT_SEQUENCE_EDITOR": "true",
            "GIT_PAGER": "cat",
            "PAGER": "cat",
            "LC_ALL": "C.UTF-8",
            "LANG": "C.UTF-8",
        })
        self.results: list[dict[str, Any]] = []
        self.commands: list[dict[str, Any]] = []

    def run(self, label: str, args: list[str], *, expected: set[int] = {0}, timeout: int = 420) -> subprocess.CompletedProcess[str]:
        print(f"[LIFECYCLE-TEST] START {label}", flush=True)
        started = time.monotonic()
        resolved_args = [sys.executable, *args[1:]] if args and args[0] == "python3" else args
        proc = subprocess.run(
            resolved_args,
            cwd=self.repo,
            env=self.env,
            text=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
        )
        duration = round(time.monotonic() - started, 3)
        (self.output_dir / f"{label}.stdout.txt").write_text(proc.stdout, encoding="utf-8")
        (self.output_dir / f"{label}.stderr.txt").write_text(proc.stderr, encoding="utf-8")
        self.commands.append({"label": label, "args": resolved_args, "returncode": proc.returncode, "duration_seconds": duration})
        if proc.returncode not in expected:
            raise RuntimeError(
                f"{label} failed: expected {sorted(expected)}, got {proc.returncode}\nSTDOUT:\n{proc.stdout[-4000:]}\nSTDERR:\n{proc.stderr[-4000:]}"
            )
        print(f"[LIFECYCLE-TEST] PASS  {label} ({duration}s, rc={proc.returncode})", flush=True)
        return proc

    def pass_check(self, name: str, detail: str = "PASS") -> None:
        self.results.append({"check": name, "status": "PASS", "detail": detail})

    def load_yaml(self, relative: str) -> dict[str, Any]:
        return yaml.safe_load((self.repo / relative).read_text(encoding="utf-8")) or {}

    def commit(self, label: str, subject: str) -> tuple[str, str]:
        self.run(label + "_add", ["git", "add", "-A"])
        self.run(label + "_commit", ["git", "commit", "-m", subject], timeout=600)
        message = self.run(label + "_message", ["git", "log", "-1", "--pretty=%B"]).stdout
        for trailer in ["Task-ID:", "Session-ID:", "Checkpoint-ID:", "Tests:", "CR:"]:
            if trailer not in message:
                raise RuntimeError(f"{label}: missing trailer {trailer}\n{message}")
        head = self.run(label + "_head", ["git", "rev-parse", "HEAD"]).stdout.strip()
        return head, message


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    parser.add_argument("--log", default=str(DEFAULT_LOG))
    parser.add_argument("--workdir")
    parser.add_argument("--keep-temp", action="store_true")
    args = parser.parse_args()

    report_path = Path(args.report).expanduser().resolve()
    log_path = Path(args.log).expanduser().resolve()
    base_dir = Path(args.workdir).expanduser().resolve() if args.workdir else Path(tempfile.mkdtemp(prefix="hhy-v123-continuity-"))
    created_temp = not args.workdir
    repo = base_dir / "repository"
    outputs = base_dir / "outputs"
    if repo.exists():
        shutil.rmtree(repo)
    copy_repository(ROOT, repo)
    reset_continuity_fixture(repo)
    remote = base_dir / "transport-remote.git"
    subprocess.run(["git", "init", "--bare", str(remote)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    transport_path = repo / "config/REPOSITORY_TRANSPORT.yaml"
    transport = yaml.safe_load(transport_path.read_text(encoding="utf-8")) or {}
    transport.setdefault("repository", {})["canonical_url"] = remote.as_uri()
    transport_path.write_text(
        yaml.safe_dump(transport, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )
    smoke = Smoke(repo, outputs)

    try:
        # Atomic baseline and hooks.
        smoke.run("01_bootstrap", [
            "python3", "scripts/continuity.py", "bootstrap",
            "--actor", "smoke-bootstrap", "--init-git", "--initial-commit",
            "--task", "TASK-P00-001", "--branch", "task/TASK-P00-001",
            "--git-user-name", "Continuity Smoke", "--git-user-email", "smoke@example.invalid",
        ])
        base_commit = smoke.run("01_base_head", ["git", "rev-parse", "HEAD"]).stdout.strip()
        if smoke.run("01_clean", ["git", "status", "--porcelain"]).stdout:
            raise RuntimeError("bootstrap did not leave a clean worktree")
        smoke.run("01_transport_remote", ["git", "remote", "add", "origin", remote.as_uri()])
        smoke.run("01_transport_upstream", [
            "git", "-c", "core.hooksPath=.git/no-hooks", "push", "--set-upstream",
            "origin", "task/TASK-P00-001",
        ])
        smoke.pass_check("bootstrap_atomic", base_commit)

        # No session means project commits are blocked.
        no_session = repo / "scripts/smoke/no_session_probe.txt"
        no_session.parent.mkdir(parents=True, exist_ok=True)
        no_session.write_text("no-session\n", encoding="utf-8")
        smoke.run("02_no_session_add", ["git", "add", "-A"])
        negative = smoke.run(
            "02_no_session_rejected",
            ["python3", "scripts/continuity_gate.py", "--mode", "pre-commit", "--strict", "--json-out", str(outputs / "no-session.json")],
            expected={1},
        )
        if "SESSION_REQUIRED" not in negative.stdout:
            raise RuntimeError("no-session rejection did not report SESSION_REQUIRED")
        smoke.run("02_reset", ["git", "reset", "--hard", "HEAD"])
        smoke.pass_check("no_session_commit_blocked")

        # Start and reject a second claim.
        smoke.run("03_start", [
            "python3", "scripts/continuity.py", "start", "--actor", "smoke-a",
            "--task", "TASK-P00-001", "--story", "STORY-P00-002",
            "--goal", "验证持续开发无状态接续协议",
        ])
        session_a = smoke.load_yaml(".continuity/ACTIVE_SESSION.yaml")["active_session_id"]
        duplicate = smoke.run("03_double_claim_rejected", [
            "python3", "scripts/continuity.py", "start", "--actor", "smoke-c",
            "--task", "TASK-P00-001", "--story", "STORY-P00-002", "--goal", "非法并发领取",
        ], expected={2})
        if "已有活跃会话" not in duplicate.stderr:
            raise RuntimeError("double claim was not rejected")
        smoke.pass_check("unique_active_session_and_claim", session_a)

        # Test record is mandatory.
        probe = repo / "scripts/smoke/protocol_probe.txt"
        probe.parent.mkdir(parents=True, exist_ok=True)
        probe.write_text("continuity-probe-v1\n", encoding="utf-8")
        serial_parallel = [
            "--parallel-assessment", "NO_SAFE_PARALLEL", "--parallel-reason",
            "生命周期演练按单一状态链串行验证",
        ]
        no_test = smoke.run("04_checkpoint_without_test", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "首次变更", "--next-step", "记录测试",
            *serial_parallel,
        ], expected={2})
        if "必须记录测试" not in no_test.stderr:
            raise RuntimeError("checkpoint without test was not rejected")
        smoke.pass_check("checkpoint_test_required")

        smoke.run("05_checkpoint", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "完成首次协议变更", "--next-step", "提交实现变更",
            "--test", "continuity-smoke|PASS|scripts/check_v123_continuity.py|联合门禁作为证据",
            *serial_parallel,
        ])
        pointer = smoke.load_yaml(".continuity/ACTIVE_SESSION.yaml")
        if not pointer.get("checkpoint_id") or not pointer.get("project_fingerprint"):
            raise RuntimeError("active pointer was not refreshed by checkpoint")
        commit1, msg1 = smoke.commit("06_first", "chore(continuity): add protocol smoke probe")
        smoke.pass_check("checkpoint_pointer_and_commit_trailers", commit1)

        # Stale checkpoint is rejected.
        with probe.open("a", encoding="utf-8") as f:
            f.write("continuity-probe-v2\n")
        smoke.run("07_stale_add", ["git", "add", "-A"])
        stale = smoke.run("07_stale_rejected", [
            "python3", "scripts/continuity_gate.py", "--mode", "pre-commit", "--strict",
            "--json-out", str(outputs / "stale-checkpoint.json"),
        ], expected={1})
        if "CHECKPOINT_STALE" not in stale.stdout:
            raise RuntimeError("stale checkpoint was not rejected")
        smoke.pass_check("stale_checkpoint_blocked")
        smoke.run("08_checkpoint", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "更新协议探针", "--next-step", "验证完整CR合同",
            "--test", f"stale-gate|PASS|{outputs / 'stale-checkpoint.json'}|旧检查点已阻断",
            *serial_parallel,
        ])
        commit2, _ = smoke.commit("09_second", "test(continuity): verify stale checkpoint rejection")
        smoke.pass_check("historical_checkpoint_commit", commit2)

        # Frozen fact without CR.
        source = repo / "docs/00-baseline/SOURCE_OF_TRUTH.md"
        original = source.read_text(encoding="utf-8")
        source.write_text(original + "\n<!-- no-cr-negative -->\n", encoding="utf-8")
        no_cr = smoke.run("10_no_cr_rejected", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "无CR冻结事实变更", "--next-step", "应阻断",
            "--test", "cr-negative|PASS|docs/00-baseline/SOURCE_OF_TRUTH.md|负向门禁",
            *serial_parallel,
        ], expected={2})
        if "CR" not in no_cr.stderr:
            raise RuntimeError("frozen fact without CR was not rejected")
        source.write_text(original, encoding="utf-8")
        smoke.pass_check("frozen_fact_requires_cr")

        # Complete CR contract and separation of duties.
        smoke.run("11_cr_create", [
            "python3", "scripts/continuity.py", "cr-create", "--actor", "smoke-requester",
            "--title", "Continuity smoke frozen fact update", "--task", "TASK-P00-001",
            "--user-request", "验证冻结事实完整变更合同", "--reason", "集成演练",
        ])
        cr = smoke.load_yaml(".continuity/CHANGE_REQUEST_INDEX.yaml")["change_requests"][-1]["cr_id"]
        incomplete = smoke.run("11_incomplete_cr_rejected", [
            "python3", "scripts/continuity.py", "cr-approve", "--actor", "smoke-approver",
            "--cr", cr, "--decision", "APPROVED", "--note", "信息尚不完整",
            "--user-confirmation", "integration user confirmation",
        ], expected={2})
        if "未达到可审批状态" not in incomplete.stderr:
            raise RuntimeError("incomplete CR was not rejected")
        smoke.run("12_cr_amend", [
            "python3", "scripts/continuity.py", "cr-amend", "--actor", "smoke-requester", "--cr", cr,
            "--original-rule", "SOURCE_OF_TRUTH文档没有集成演练标记",
            "--new-rule", "加入仅用于临时仓库演练的受控标记",
            "--impact-summary", "仅影响临时测试仓库的一份开发前事实文档，不改变业务功能",
            "--migration-and-compatibility", "文档兼容，无数据迁移；测试结束后临时仓库销毁",
            "--file", "docs/00-baseline/SOURCE_OF_TRUTH.md",
            "--test", "continuity CR gate integration smoke",
            "--release", "P00",
        ])
        self_approval = smoke.run("12_self_approval_rejected", [
            "python3", "scripts/continuity.py", "cr-approve", "--actor", "smoke-requester",
            "--cr", cr, "--decision", "APPROVED", "--note", "禁止自审",
            "--user-confirmation", "integration user confirmation",
        ], expected={2})
        if "不得审批" not in self_approval.stderr:
            raise RuntimeError("CR requester self-approval was not rejected")
        smoke.run("13_cr_approve", [
            "python3", "scripts/continuity.py", "cr-approve", "--actor", "smoke-approver",
            "--cr", cr, "--decision", "APPROVED", "--note", "完整合同已复核",
            "--user-confirmation", "integration user confirmation recorded in repository",
        ])
        source.write_text(original + "\n<!-- continuity-smoke-cr-approved -->\n", encoding="utf-8")
        smoke.run("14_cr_checkpoint", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "批准CR后的冻结事实变更", "--next-step", "提交并演练WIP交接",
            "--test", "cr-positive|PASS|.continuity/CHANGE_REQUEST_INDEX.yaml|完整合同和不同Actor审批通过",
            *serial_parallel,
        ])
        commit3, msg3 = smoke.commit("15_cr", "docs(continuity): verify approved change request path")
        if f"CR: {cr}" not in msg3:
            raise RuntimeError("commit CR trailer does not match checkpoint")
        smoke.pass_check("complete_cr_contract_and_separation", commit3)

        # Dirty WIP handoff and takeover without prior dialog.
        with probe.open("a", encoding="utf-8") as f:
            f.write("handoff-wip-v1\n")
        smoke.run("16_handoff_checkpoint", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "形成可移交WIP", "--next-step", "新AI验证Manifest后提交WIP",
            "--test", "handoff-prep|PASS|scripts/continuity.py|交接前检查点",
            *serial_parallel,
        ])
        handoff_zip = outputs / "wip-handoff.zip"
        smoke.run("17_handoff", [
            "python3", "scripts/continuity.py", "handoff", "--actor", "smoke-a",
            "--reason", "切换AI进行无对话接管", "--next-step", "验证Manifest后提交WIP",
            "--portable-zip", str(handoff_zip),
        ], timeout=600)
        if not handoff_zip.is_file() or not Path(str(handoff_zip) + ".sha256").is_file():
            raise RuntimeError("portable WIP handoff was not created")
        smoke.run("18_takeover", [
            "python3", "scripts/continuity.py", "takeover", "--actor", "smoke-b",
            "--session", session_a, "--reason", "无旧对话接管", "--next-step", "重新检查点并提交WIP",
        ], timeout=600)
        session_b = smoke.load_yaml(".continuity/ACTIVE_SESSION.yaml")["active_session_id"]
        if session_b == session_a:
            raise RuntimeError("takeover did not create a new session")
        smoke.run("19_takeover_checkpoint", [
            "python3", "scripts/continuity.py", "checkpoint",
            "--summary", "接管并验证WIP", "--next-step", "提交接管后的实现",
            "--test", "takeover-verify|PASS|artifacts/context/CURRENT_CONTEXT_PACK.md|无需旧对话恢复",
            *serial_parallel,
        ])
        commit4, _ = smoke.commit("20_takeover", "chore(continuity): continue handed-off work without dialog")
        smoke.pass_check("wip_handoff_and_takeover", commit4)

        # Close protocol and metadata commit.
        smoke.run("21_close", [
            "python3", "scripts/continuity.py", "close", "--actor", "smoke-b",
            "--result", "COMPLETED", "--summary", "持续接续协议集成演练完成",
            "--next-task", "TASK-P00-002",
        ], timeout=600)
        if smoke.load_yaml(".continuity/ACTIVE_SESSION.yaml").get("active_session_id"):
            raise RuntimeError("close did not clear active pointer")
        commit5, msg5 = smoke.commit("22_close_metadata", "chore(continuity): close completed smoke task")
        if "continuity closure metadata" not in msg5 and "Tests: PASS:" not in msg5:
            raise RuntimeError("closure metadata commit is not bound to closure checkpoint")
        smoke.pass_check("closure_checkpoint_and_metadata_commit", commit5)

        # Every commit is checked again before push and in CI.
        smoke.run("23_prepush", [
            "python3", "scripts/continuity_gate.py", "--mode", "pre-push", "--strict",
            "--json-out", str(outputs / "prepush-report.json"),
        ], timeout=900)
        smoke.run("24_ci", [
            "python3", "scripts/continuity_gate.py", "--mode", "ci", "--strict",
            "--head-ref", "HEAD", "--json-out", str(outputs / "ci-report.json"),
        ], timeout=900)
        smoke.pass_check("per_commit_prepush_and_ci")

        # Clean committed export.
        clean_zip = outputs / "clean-project-export.zip"
        smoke.run("25_export_clean", [
            "python3", "scripts/continuity.py", "export-clean", "--portable-zip", str(clean_zip),
        ], timeout=900)
        if not clean_zip.is_file() or not Path(str(clean_zip) + ".sha256").is_file():
            raise RuntimeError("clean export was not created")
        if smoke.run("25_clean_after_export", ["git", "status", "--porcelain"]).stdout:
            raise RuntimeError("clean export dirtied the repository")
        smoke.pass_check("clean_committed_export", sha256(clean_zip))

        # Tamper detection in an isolated copy.
        tamper = base_dir / "tampered"
        shutil.copytree(repo, tamper, copy_function=shutil.copy2)
        with (tamper / ".continuity/EVENT_LOG.jsonl").open("a", encoding="utf-8") as f:
            f.write('{"tampered":true}\n')
        proc = subprocess.run(
            [sys.executable, "scripts/continuity_gate.py", "--mode", "doctor", "--strict", "--json-out", str(outputs / "tamper-report.json")],
            cwd=tamper, env=smoke.env, text=True, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=420,
        )
        (outputs / "26_tamper.stdout.txt").write_text(proc.stdout, encoding="utf-8")
        (outputs / "26_tamper.stderr.txt").write_text(proc.stderr, encoding="utf-8")
        if proc.returncode == 0 or ("EVENT" not in proc.stdout.upper() and "HASH" not in proc.stdout.upper()):
            raise RuntimeError("event log tamper was not detected")
        smoke.pass_check("event_hash_chain_tamper_detected")

        final_commit = smoke.run("27_final_head", ["git", "rev-parse", "HEAD"]).stdout.strip()
        payload = {
            "version": "1.2.3",
            "status": "PASS",
            "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "conversation_context_required": False,
            "repository_only_handoff": True,
            "source_manifest": source_manifest(),
            "base_commit": base_commit,
            "final_commit": final_commit,
            "session_a": session_a,
            "session_b": session_b,
            "change_request": cr,
            "checks": smoke.results,
            "commands": smoke.commands,
            "metrics": {
                "checks": len(smoke.results),
                "commits": int(smoke.run("27_commit_count", ["git", "rev-list", "--count", "HEAD"]).stdout.strip()),
                "wip_handoff_sha256": sha256(handoff_zip),
                "clean_export_sha256": sha256(clean_zip),
            },
        }
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        log_path.parent.mkdir(parents=True, exist_ok=True)
        log_path.write_text("\n".join(
            f"{row['label']} rc={row['returncode']} duration={row['duration_seconds']}s :: {' '.join(row['args'])}"
            for row in smoke.commands
        ) + "\n", encoding="utf-8")
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        failure = {
            "version": "1.2.3", "status": "FAIL", "error": str(exc),
            "source_manifest": source_manifest(),
            "checks": smoke.results, "commands": smoke.commands, "workdir": str(base_dir),
        }
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(failure, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(failure, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1
    finally:
        if created_temp and not args.keep_temp and base_dir.exists():
            shutil.rmtree(base_dir, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
