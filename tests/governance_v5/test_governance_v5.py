from __future__ import annotations

import copy
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import pytest
import yaml
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.governance.gov50.gates import (
    test_authority_check as authority_check,
    validate_candidate_evidence,
    visual_contract_command,
)
from tools.governance.gov50.hard import run_hard_protection
from tools.governance.gov50.legacy import scan_active_control_plane
from tools.governance.gov50.orchestrator import (
    _draft_manifest_from_blocker,
    _latest_worker_draft,
    _preserve_allowed_worker_draft,
    _restore_worker_draft,
    _structured_worker_draft_paths,
    _worker_blocker_with_recovery_context,
    _worker_environment,
    _worker_infrastructure_code,
    _worker_prompt,
    _worker_takeover_prompt,
    _write_worker_draft,
    bounded_recovery_candidates,
    build_active_task_payload,
    build_auto_recovery_spec,
    merged_recovery_failure_evidence,
    stale_auto_recovery_task,
    supersede_failed,
    structured_failure_evidence,
    task_gate_profile,
    worker_failure_evidence,
    worker_sandbox_args,
)
from tools.governance.gov50.secrets import scan_secrets
from tools.governance.gov50.simulation import simulate_program
from tools.governance.gov50.state import _state_hash, assert_valid_state, bootstrap_state
from tools.governance.gov50.tasks import (
    EXPECTED_FUTURE_TASKS,
    EXPECTED_SOURCE_TASKS,
    EXPECTED_TASKS,
    REPAIR_RECOVERY_TASK_ID,
    _normalize_source_task,
    build_program_plan,
    load_task_specs,
    validate_specs,
)
from tools.governance.gov50.views import next_ready_task
from tools.governance.gov50.util import git_status_lines, repository_lock, write_yaml


def _specs():
    return load_task_specs(ROOT)


def test_auto_recovery_spec_resets_accumulated_recovery_prose():
    source = copy.deepcopy(_specs()["TASK-R14-RECOVERY-016"])
    source["title"] = "自动恢复接替：" * 8 + "旧标题"
    source["requirements"] = ["旧要求"] * 20
    source["acceptance"] = ["旧验收"] * 20

    repair = build_auto_recovery_spec(source, "TASK-R14-RECOVERY-016", "TASK-R14-RECOVERY-017", "R14", 25)

    assert repair["title"] == "R14 自动有界恢复：源码冻结就绪"
    assert repair["kind"] == "recovery"
    assert repair["gate_profile"] == "freeze"
    assert task_gate_profile(repair) == "freeze"
    assert len(repair["requirements"]) == 4
    assert len(repair["acceptance"]) == 5
    assert "旧要求" not in repair["requirements"]
    assert "旧验收" not in repair["acceptance"]
    assert "python3 scripts/check_ui_visual_acceptance.py --release R14" not in repair["acceptance_commands"]
    assert "python3 scripts/check_r14_entry_contract.py" in repair["acceptance_commands"]


def test_r15_auto_recovery_is_counted_as_a_bounded_recovery_task():
    specs = _specs()
    source = copy.deepcopy(specs["TASK-R15-002"])
    repair = build_auto_recovery_spec(
        source, "TASK-R15-002", "TASK-R15-RECOVERY-001", "R15", 9
    )
    specs[repair["id"]] = repair
    plan = build_program_plan(specs)

    assert repair["kind"] == "recovery"
    assert repair["gate_profile"] == "task"
    assert task_gate_profile(repair) == "task"
    assert repair["maximum_attempts"] == 3
    assert repair["supersedes"] == "TASK-R15-002"
    assert repair["recovery_product_contract"]["objective"] == source["objective"]
    assert set(source["requirements"]).issubset(repair["requirements"])
    assert set(source["deliverables"]).issubset(repair["deliverables"])
    assert set(source["acceptance"]).issubset(repair["acceptance"])
    assert validate_specs(specs, plan) == []


def test_recovery_gate_profile_is_inherited_across_generations():
    source = build_auto_recovery_spec(
        copy.deepcopy(_specs()["TASK-R15-002"]),
        "TASK-R15-002",
        "TASK-R15-RECOVERY-001",
        "R15",
        9,
    )
    repair = build_auto_recovery_spec(
        source,
        "TASK-R15-RECOVERY-001",
        "TASK-R15-RECOVERY-002",
        "R15",
        10,
    )

    assert repair["gate_profile"] == "task"
    assert repair["recovery_product_contract"] == source["recovery_product_contract"]
    assert "task Gate" in repair["acceptance"][-2]


def test_failure_evidence_is_structured_for_the_next_recovery_worker():
    evidence = {"status": "BLOCK", "findings": [{"severity": "HIGH", "line": 59}]}

    assert structured_failure_evidence(json.dumps(evidence)) == evidence
    assert structured_failure_evidence("plain infrastructure fingerprint") == "plain infrastructure fingerprint"


def test_recovery_preserves_product_findings_when_latest_execution_times_out():
    product = {"status": "BLOCK", "findings": [{"severity": "HIGH", "line": 59}]}
    source = {"latest_failure_evidence": product}

    evidence = merged_recovery_failure_evidence(source, "worker idle timeout")

    assert evidence == {
        "current_execution_failure": "worker idle timeout",
        "product_failure_evidence": product,
    }


def test_active_task_carries_latest_failure_evidence_to_worker():
    spec = copy.deepcopy(_specs()["TASK-R15-RECOVERY-007"])
    evidence = {"status": "BLOCK", "findings": [{"severity": "HIGH", "line": 59}]}
    spec["latest_failure_evidence"] = evidence

    active = build_active_task_payload(spec["id"], spec, 1, "a" * 40)

    assert active["latest_failure_evidence"] == evidence
    assert "ACTIVE_TASK.latest_failure_evidence" in _worker_prompt(spec, 1)
    assert "ACTIVE_TASK.latest_failure_evidence" in _worker_takeover_prompt(spec, 1, {})


def test_worker_uses_current_state_failure_evidence_before_stale_task_spec():
    spec = {"latest_failure_evidence": {"status": "OLD"}}
    current = {"status": "BLOCK", "findings": [{"issue": "current reviewer finding"}]}
    row = {"last_error_fingerprint": json.dumps(current)}

    assert worker_failure_evidence(spec, row) == current


def test_worker_keeps_task_spec_failure_evidence_when_state_has_none():
    evidence = {"status": "BLOCK", "findings": [{"issue": "recovery source finding"}]}

    assert worker_failure_evidence({"latest_failure_evidence": evidence}, {}) == evidence


def test_windows_worker_prompts_require_serial_shell_commands():
    spec = _specs()["TASK-R15-RECOVERY-007"]

    assert "shell/tool 命令必须串行执行" in _worker_prompt(spec, 1)
    assert "shell/tool 命令必须串行执行" in _worker_takeover_prompt(spec, 1, {})


def test_rejected_candidate_paths_do_not_block_fresh_implementation():
    spec = _specs()["TASK-R15-RECOVERY-008"]

    assert "必须依据冻结契约和任务目标新建实现" in _worker_prompt(spec, 1)
    assert "禁止仅因文件缺失返回 ATTEMPT_FAILED" in _worker_takeover_prompt(spec, 1, {})


def test_worker_prompts_bound_read_only_discovery_before_implementation():
    spec = _specs()["TASK-R15-RECOVERY-009"]

    assert "只读定位最多使用 12 次工具调用" in _worker_prompt(spec, 1)
    assert "Fast Lane 硬规则" in _worker_takeover_prompt(spec, 1, {})


def test_worker_prompts_require_incremental_vertical_slices_and_full_draft_validation():
    spec = _specs()["TASK-R15-RECOVERY-009"]

    assert "大型任务必须拆成不超过 6 个 operationId 的可编译垂直切片" in _worker_prompt(spec, 1)
    assert "草稿不是 PASS" in _worker_takeover_prompt(spec, 1, {})
    assert "前 5 次工具调用内必须运行适用的最小编译或目标测试" in _worker_prompt(spec, 1)
    assert "当前草稿的第一个真实失败优先于历史 latest_failure_evidence" in _worker_takeover_prompt(spec, 1, {})


def test_fast_lane_rules_bound_discovery_and_preserve_gates():
    constitution = yaml.safe_load((ROOT / "governance/DEVELOPMENT_CONSTITUTION.yaml").read_text(encoding="utf-8"))
    fast_lane = constitution["fast_lane"]
    assert fast_lane["enabled"] is True
    assert fast_lane["max_read_only_discovery_tool_calls"] == 12
    assert fast_lane["first_product_file_deadline_seconds"] == 600
    assert fast_lane["max_operation_ids_per_slice"] == 6
    assert "preserve_task_gate_and_independent_reviewer" in fast_lane["rules"]
    assert "never_increase_attempt_budget" in fast_lane["rules"]
    prompt = _worker_prompt(_specs()["TASK-R15-RECOVERY-009"], 1)
    assert "Fast Lane 硬规则" in prompt
    assert "不得弱化任何门禁" in prompt


def test_worker_provider_401_is_infrastructure_not_engineering_failure():
    worker = {
        "status": "FAIL",
        "stderr_tail": "unexpected status 401 Unauthorized: INVALID_API_KEY",
    }

    assert _worker_infrastructure_code(worker) == "CODEX_PROVIDER_AUTH_UNAVAILABLE"
    assert _worker_infrastructure_code({"stderr_tail": "compile failed"}) is None
    assert _worker_infrastructure_code({"stdout_tail": "historical finding: 401 Unauthorized"}) is None
    assert _worker_infrastructure_code({
        "stdout_tail": json.dumps({"type": "error", "message": "401 Unauthorized: INVALID_API_KEY"})
    }) == "CODEX_PROVIDER_AUTH_UNAVAILABLE"
    assert _worker_infrastructure_code({
        "stderr_tail": "stream disconnected before completion: error sending request for url"
    }) == "CODEX_PROVIDER_TRANSPORT_UNAVAILABLE"


def test_infrastructure_draft_is_hash_bound_and_restored_for_same_attempt(tmp_path):
    repo = tmp_path / "repo"
    source = tmp_path / "source"
    worktree = tmp_path / "worktree"
    relative = "services/backend/src/R15.java"
    source_file = source / relative
    source_file.parent.mkdir(parents=True)
    source_file.write_text("final class R15 {}\n", encoding="utf-8")

    manifest_rel = _write_worker_draft(
        repo, source, "TASK-R15-001", 2, "a" * 40,
        ["services/backend/**"], [relative],
    )

    assert manifest_rel is not None
    manifest = _latest_worker_draft(repo, "TASK-R15-001", 2, "a" * 40)
    assert manifest is not None
    assert _restore_worker_draft(worktree, manifest) == [relative]
    assert (worktree / relative).read_text(encoding="utf-8") == "final class R15 {}\n"
    assert _latest_worker_draft(repo, "TASK-R15-001", 3, "a" * 40) is not None


def test_later_attempt_restores_latest_ancestor_draft(tmp_path):
    repo = tmp_path / "repo"
    source = tmp_path / "source"
    relative = "services/backend/src/R15.java"
    source_file = source / relative
    source_file.parent.mkdir(parents=True)
    source_file.write_text("final class R15 {}\n", encoding="utf-8")
    repo.mkdir()
    subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.name", "test"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=repo, check=True)
    (repo / "baseline.txt").write_text("baseline\n", encoding="utf-8")
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "baseline"], cwd=repo, check=True)
    baseline = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    manifest_rel = _write_worker_draft(repo, source, "TASK-R15-001", 1, baseline, ["services/backend/**"], [relative])
    assert manifest_rel is not None
    assert _latest_worker_draft(repo, "TASK-R15-001", 2, baseline) is not None


def test_policy_violation_draft_preserves_only_allowed_product_paths(tmp_path):
    repo = tmp_path / "repo"
    worktree = tmp_path / "worktree"
    allowed = "services/backend/src/R15.java"
    violation = "database/migrations/V999__outside_scope.sql"
    for relative in (allowed, violation):
        path = worktree / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(relative, encoding="utf-8")

    manifest_rel = _preserve_allowed_worker_draft(
        repo, worktree, "TASK-R15-001", 3, "a" * 40,
        ["services/backend/**"], [allowed, violation],
    )

    assert manifest_rel is not None
    manifest = json.loads((repo / manifest_rel).read_text(encoding="utf-8"))
    assert list(manifest["files"]) == [allowed]
    assert violation not in manifest["files"]


def test_structured_worker_blocker_keeps_evidence_and_resumable_draft():
    evidence = "governance/evidence/recovery/TASK-R15-001-a2-worker-result.json"
    draft = "governance/runtime/supervisor/worker-drafts/TASK-R15-001-a2-x/manifest.json"
    blocker = {
        "code": "DATABASE_UNAVAILABLE",
        "detail": "PostgreSQL is unavailable",
        "resolution": "Restore PostgreSQL",
        "paths": ["tests/test_r15.py"],
        "worker_result_evidence": "worker self-report",
    }

    enriched = _worker_blocker_with_recovery_context(blocker, evidence, draft)

    assert enriched["detail"] == blocker["detail"]
    assert enriched["worker_result_evidence"] == evidence
    assert enriched["draft_manifest"] == draft
    assert _draft_manifest_from_blocker(enriched) == draft
    assert _draft_manifest_from_blocker({"detail": {"draft_manifest": draft}}) == draft


def test_structured_block_preserves_restored_and_worker_reported_paths():
    active = {"restored_draft": {"paths": ["services/backend/Restored.java"]}}
    result = {"changed_files": ["tests/test_current.py"]}

    assert _structured_worker_draft_paths(active, result, ["services/backend/Changed.java"]) == [
        "services/backend/Changed.java",
        "services/backend/Restored.java",
        "tests/test_current.py",
    ]


def test_visual_gate_uses_pre_freeze_entry_and_post_freeze_strict_acceptance():
    assert visual_contract_command(ROOT, "freeze", "R14", True) == (
        "visual_entry_contract",
        ["scripts/check_r14_entry_contract.py"],
    )
    assert visual_contract_command(ROOT, "candidate", "R14", True) == (
        "visual_contract",
        ["scripts/check_ui_visual_acceptance.py", "--release", "R14"],
    )


def test_worker_uses_patch_capable_windows_workspace_sandbox():
    assert worker_sandbox_args("nt") == [
        "-c", 'windows.sandbox="elevated"',
        "-c", "features.apps=false",
        "-c", "features.plugins=false",
        "-c", "features.remote_plugin=false",
        "-c", "features.memories=false",
    ]
    assert worker_sandbox_args("posix") == []


def _active_recovery_id():
    specs = _specs()
    superseded = {str(spec["supersedes"]) for spec in specs.values() if spec.get("supersedes")}
    recovery = [
        task_id for task_id, spec in specs.items()
        if spec.get("release") == "R14"
        and spec.get("kind") == "recovery"
        and task_id not in superseded
    ]
    assert len(recovery) == 1
    return recovery[0]


def test_expected_task_counts():
    specs = _specs()
    recovery_count = sum(1 for spec in specs.values() if spec.get("kind") == "recovery")
    future_recovery_count = sum(
        1 for spec in specs.values()
        if spec.get("kind") == "recovery"
        and str(spec.get("release", "")).startswith("R")
        and 15 <= int(spec["release"][1:]) <= 32
    )
    assert len(specs) == EXPECTED_TASKS + max(0, recovery_count - 1)
    assert sum(1 for s in specs.values() if s.get("source", {}).get("path", "").startswith("releases/")) == EXPECTED_SOURCE_TASKS == 264
    assert sum(1 for s in specs.values() if str(s.get("release", "")).startswith("R") and 15 <= int(s["release"][1:]) <= 32) == EXPECTED_FUTURE_TASKS + future_recovery_count


def test_every_task_has_hard_total_attempt_budget():
    for task_id, spec in _specs().items():
        assert spec["maximum_attempts"] == 3, task_id
        assert "attempt_override" not in str(spec).lower()
        assert "attempt_exception" not in str(spec).lower()


def test_program_plan_and_specs_are_valid():
    specs = _specs()
    assert validate_specs(specs, build_program_plan(specs)) == []


def test_program_is_finite_and_adjacent():
    result = simulate_program(ROOT)
    assert result["status"] == "PASS", result
    expected_first = _active_recovery_id()
    assert result["first_task"] == expected_first
    assert result["last_task"] == "TASK-R32-008"
    assert result["attempt_four_possible"] is False


def test_single_state_is_valid():
    specs = _specs()
    state = yaml.safe_load((ROOT / "governance/STATE.yaml").read_text(encoding="utf-8"))
    assert_valid_state(state, specs)
    active_release = state["project"]["active_release"]
    active_task = state["project"]["active_task"]
    if state["project"]["status"] == "FAILED_BOUNDED":
        assert active_task is None
    elif active_task:
        assert specs[active_task]["release"] in {active_release, "CONTROL"}
        project_status = state["project"]["status"]
        expected_status = project_status if project_status in {
            "INFRASTRUCTURE_BLOCKED", "EXTERNAL_BLOCKED", "POLICY_VIOLATION",
        } else "READY"
        assert state["tasks"][active_task]["status"] == expected_status
        attempts_used = state["tasks"][active_task]["attempts_used"]
        assert 0 <= attempts_used < specs[active_task]["maximum_attempts"]
    elif state["project"]["status"] == "ACTIVE":
        assert state["releases"][active_release]["status"] in {"FREEZE_READY", "CANDIDATE_GREEN"}
        assert state["project"]["status"] == "ACTIVE"
    else:
        assert state["project"]["status"] in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED", "POLICY_VIOLATION"}
        assert active_task is None


def test_attempt_four_is_rejected():
    specs = _specs()
    state = bootstrap_state(specs, "test", "0" * 40)
    state["tasks"]["TASK-R14-RECOVERY-001"]["attempts_used"] = 4
    with pytest.raises(ValueError, match="outside 0..3"):
        assert_valid_state(state, specs)


def test_state_hash_tampering_is_rejected():
    specs = _specs()
    state = bootstrap_state(specs, "test", "0" * 40)
    state["project"]["active_release"] = "R15"
    with pytest.raises(ValueError, match="state hash mismatch"):
        assert_valid_state(state, specs)


def test_recovery_is_only_active_entry_before_activation():
    state = yaml.safe_load((ROOT / "governance/STATE.yaml").read_text(encoding="utf-8"))
    if state["project"]["active_release"] != "R14":
        assert state["releases"]["R14"]["status"] == "MACHINE_CLOSED"
        assert all(
            row["status"] in {"DONE", "FAILED_BOUNDED"}
            for task_id, row in state["tasks"].items()
            if task_id.startswith("TASK-R14-RECOVERY-")
        )
        return
    if state["project"]["status"] == "FAILED_BOUNDED":
        assert state["project"]["active_task"] is None
        assert state["tasks"]["TASK-R14-RECOVERY-001"]["status"] == "FAILED_BOUNDED"
        assert state["tasks"]["TASK-R14-RECOVERY-001"]["attempts_used"] == 3
    elif state["project"]["active_task"] == _active_recovery_id():
        assert state["tasks"]["TASK-R14-RECOVERY-001"]["status"] == "FAILED_BOUNDED"
        project_status = state["project"]["status"]
        expected_status = project_status if project_status in {
            "INFRASTRUCTURE_BLOCKED", "EXTERNAL_BLOCKED", "POLICY_VIOLATION",
        } else "READY"
        assert state["tasks"][_active_recovery_id()]["status"] == expected_status
    elif state["project"]["active_task"] is None and state["releases"]["R14"]["status"] in {"FREEZE_READY", "CANDIDATE_GREEN"}:
        assert state["project"]["status"] == "ACTIVE"
    else:
        assert state["project"]["status"] in {"EXTERNAL_BLOCKED", "INFRASTRUCTURE_BLOCKED", "POLICY_VIOLATION"}
        assert state["project"]["active_task"] is None


def test_generated_views_are_read_only_views():
    state = yaml.safe_load((ROOT / "governance/STATE.yaml").read_text(encoding="utf-8"))
    current = yaml.safe_load((ROOT / "CURRENT_STATUS.yaml").read_text(encoding="utf-8"))
    nxt = yaml.safe_load((ROOT / "NEXT_TASK.yaml").read_text(encoding="utf-8"))
    assert current["DO_NOT_EDIT"] is True
    assert nxt["DO_NOT_EDIT"] is True
    assert current["SOURCE_REVISION"] == state["revision"]
    assert current["active_task"] == state["project"]["active_task"]


def test_active_control_plane_has_no_legacy_execution_routes():
    result = scan_active_control_plane(ROOT)
    assert result["status"] == "PASS", result["findings"]


def test_repository_secret_scan_passes():
    result = scan_secrets(ROOT, full=True, include_untracked=True)
    assert result["status"] == "PASS", result["findings"]


def test_secret_scanner_detects_untracked_high_confidence_token(tmp_path):
    repo = tmp_path / "repo"
    repo.mkdir()
    subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
    (repo / "config").mkdir()
    (repo / "config/secret-scan-allowlist.yaml").write_text("schema: test\nallow: []\n", encoding="utf-8")
    (repo / "leak.txt").write_text("token=ghp_" + "A" * 36 + "\n", encoding="utf-8")
    result = scan_secrets(repo, full=True, include_untracked=True)
    assert result["status"] == "FAIL"
    assert any(row["rule"] == "GITHUB_TOKEN" for row in result["findings"])


def test_worker_result_schema_is_strict_enough():
    schema = json.loads((ROOT / "governance/schemas/worker-result.schema.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    good = {
        "schema": "hhy.worker-result/v5.0",
        "status": "CANDIDATE_READY",
        "task_id": "X",
        "summary": "done",
        "changed_files": [],
        "commands_run": [],
        "error_fingerprint": None,
        "blocker": None,
    }
    assert list(validator.iter_errors(good)) == []
    bad = dict(good, status="PASS")
    assert list(validator.iter_errors(bad))


def test_worker_prompt_exposes_controller_python_runtime_on_windows():
    prompt = _worker_prompt({"id": "TASK-R15-001"}, 1)
    assert "HHY_PYTHON" in prompt
    assert "PATH" in prompt


def test_worker_and_takeover_inherit_controller_python_runtime():
    source = (ROOT / "tools/governance/gov50/orchestrator.py").read_text(encoding="utf-8")
    assert 'env=_worker_environment("WORKER")' in source
    assert 'env=_worker_environment("WORKER_TAKEOVER")' in source


def test_worker_environment_exposes_existing_portable_toolchain(tmp_path):
    java = tmp_path / "jdk" / "jdk-21" / "bin" / "java.exe"
    adb = tmp_path / "android" / "platform-tools" / "adb.exe"
    java.parent.mkdir(parents=True)
    adb.parent.mkdir(parents=True)
    java.touch()
    adb.touch()

    env = _worker_environment("WORKER", {"PATH": "existing"}, tmp_path)

    assert env["HHY_GOVERNANCE_ROLE"] == "WORKER"
    assert env["HHY_PYTHON"]
    assert env["JAVA_HOME"] == str(tmp_path / "jdk" / "jdk-21")
    assert env["ANDROID_HOME"] == str(tmp_path / "android")
    assert env["HHY_MAVEN_REPOSITORY"] == str(Path.home() / ".m2" / "repository")
    assert "maven.repo.local=" in env["MAVEN_ARGS"]
    assert str(java.parent) in env["PATH"]
    assert str(adb.parent) in env["PATH"]


def test_worker_prompt_requires_offline_maven_with_injected_java_home():
    prompt = _worker_prompt({"id": "TASK-R15-001"}, 1)
    assert "JAVA_HOME" in prompt
    assert "mvnw.cmd -o" in prompt
    assert "maven.repo.local" in prompt
    assert "实际存在性与版本" in prompt


def test_candidate_evidence_schema_requires_complete_apk_identity():
    schema = json.loads((ROOT / "governance/schemas/candidate-evidence.schema.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    data = {
        "schema": "hhy.candidate-evidence/v5.0", "status": "PASS", "release": "R14",
        "commit": "a" * 40, "gate_report": "x.json",
        "apk": {"path": "a.apk", "sha256": "b" * 64, "source_commit": "a" * 40, "version_code": 1, "version_name": "1.0"},
        "screenshots": {"source_commit": "a" * 40, "paths": ["a.png"]},
    }
    errors = list(validator.iter_errors(data))
    assert any("signing_sha256" in e.message for e in errors)


def test_owner_schema_binds_frozen_commit():
    schema = json.loads((ROOT / "governance/schemas/owner-evidence.schema.json").read_text(encoding="utf-8"))
    assert "frozen_commit" in schema["required"]
    assert "result" in schema["required"]


def test_formal_release_schema_requires_production_and_rollback():
    schema = json.loads((ROOT / "governance/schemas/formal-release-evidence.schema.json").read_text(encoding="utf-8"))
    assert {"production_activation_evidence", "rollback_evidence", "tag"} <= set(schema["required"])


def test_hooks_define_compaction_guard_and_stop_validator():
    hooks = json.loads((ROOT / ".codex/hooks.json").read_text(encoding="utf-8"))
    assert "SessionStart" in hooks["hooks"]
    assert "PreToolUse" in hooks["hooks"]
    assert "PostToolUse" in hooks["hooks"]
    assert "Stop" in hooks["hooks"]
    text = (ROOT / ".codex/hooks/session_start.py").read_text(encoding="utf-8")
    assert "SECOND_COMPACTION_FORBIDDEN" in text
    assert "continue':False" in text or '"continue":false' in text.lower()


def test_worker_guard_denies_git_authority_commands():
    text = (ROOT / ".codex/hooks/pre_tool_guard.py").read_text(encoding="utf-8")
    for command in ["commit", "push", "merge", "rebase", "reset", "tag"]:
        assert command in text
    assert "permissionDecision':'deny'" in text or 'permissionDecision\":\"deny' in text


def test_worker_commit_failure_persists_diagnostics_without_bypassing_hooks():
    source = (ROOT / "tools/governance/gov50/orchestrator.py").read_text(encoding="utf-8")
    assert "hhy.worker-commit-failure/v5.0" in source
    assert 'worktree / ".githooks-v5" / "pre-commit"' in source
    assert '"--no-verify"' not in source


def test_watchdog_stops_second_identical_action():
    text = (ROOT / ".codex/hooks/post_tool_watchdog.py").read_text(encoding="utf-8")
    assert "REPEATED_COMMAND_OUTPUT_DIFF" in text
    assert "WORKER_TOOL_CALL_BUDGET_EXHAUSTED" in text


def test_stable_github_required_check_names_exist():
    governance = yaml.safe_load((ROOT / ".github/workflows/governance-v5.yml").read_text(encoding="utf-8"))
    core = yaml.safe_load((ROOT / ".github/workflows/core-v5.yml").read_text(encoding="utf-8"))
    assert governance["jobs"]["governance"]["name"] == "governance"
    assert core["jobs"]["core"]["name"] == "core"


def test_worker_protected_paths_cover_governance_and_ci():
    policy = yaml.safe_load((ROOT / "governance/policies/PROTECTED_PATHS.yaml").read_text(encoding="utf-8"))
    denied = set(policy["worker_denied"])
    assert "governance/**" in denied
    assert ".github/workflows/**" in denied
    assert "tests/governance_v5/**" in denied


def test_release_close_is_bounded_worker_not_self_closing():
    specs = _specs()
    for release_num in range(15, 33):
        task = specs[f"TASK-R{release_num:02d}-008"]
        assert task["mode"] == "worker"
        assert task["maximum_attempts"] == 3
        assert "Orchestrator" in " ".join(task["acceptance"])


def test_machine_close_preserves_failed_recovery_history():
    source = (ROOT / "tools/governance/gov50/orchestrator.py").read_text(encoding="utf-8")
    assert 'state["tasks"]["TASK-R14-RECOVERY-001"]["status"] = "DONE"' not in source


def test_readiness_documentation_evidence_stays_inside_release_scope():
    raw = {
        "id": "TASK-R16-001",
        "title": "readiness",
        "status": "READY",
        "deliverables": ["python scripts/check_v122_documentation.py --release R16"],
        "acceptance": ["python scripts/check_v122_documentation.py --release R16"],
    }
    spec = _normalize_source_task("R16", raw, "releases/R16/TASKS.yaml")
    expected = "--json-out releases/R16/evidence/project-doctor-v1.2.2.json"
    assert expected in spec["deliverables"][0]
    assert expected in spec["acceptance"][0]
    assert "releases/R16/**" in spec["allowed_paths"]


def test_r15_database_task_requires_real_postgres_execution():
    spec = _specs()["TASK-R15-002"]
    commands = spec["acceptance_commands"]
    postgres_test = "python3 -m pytest -q tests/test_r15_notification_support_postgres.py"
    assert postgres_test in commands
    assert all("mvn" not in command for command in commands)
    acceptance = " ".join(spec["acceptance"])
    assert "HHY_DB_SMOKE_CONFIRM=YES" in acceptance
    assert "缺少确认、连接或测试文件时必须失败，不得跳过" in acceptance
    assert "database/tests/r15_notification_support_invariants.sql" in acceptance
    assert "空库与升级库迁移" in acceptance
    assert "不得以读取 SQL 文本或匹配标记替代数据库运行" in acceptance


def test_r15_depends_on_r14_recovery():
    expected = "TASK-R14-RECOVERY-002" if REPAIR_RECOVERY_TASK_ID in _specs() else "TASK-R14-RECOVERY-001"
    assert _specs()["TASK-R15-001"]["depends_on"] == [_active_recovery_id()]


def test_release_dependencies_are_adjacent():
    specs = _specs()
    for release_num in range(16, 33):
        task = specs[f"TASK-R{release_num:02d}-001"]
        assert task["depends_on"] == [f"TASK-R{release_num - 1:02d}-008"]


def test_funds_tasks_exist_and_have_hard_test_contracts():
    specs = _specs()
    funds = [task_id for task_id, spec in specs.items() if "funds" in set(spec.get("risks") or [])]
    assert len(funds) >= 1
    # The contract checker may report a source-product gap, but it must execute and
    # explicitly enumerate requirements; it may never silently skip the funds risk.
    result = run_hard_protection(ROOT, funds[0], "task")
    assert result["schema"] == "hhy.hard-protection/v5.0"
    assert "funds" in result["risks"]


def test_test_authority_check_is_present_and_commit_bound():
    result = authority_check(ROOT, "HEAD")
    assert result["commit"] == subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    assert result["status"] in {"PASS", "FAIL"}


def test_governance_constitution_has_single_authority_and_no_long_goal():
    constitution = yaml.safe_load((ROOT / "governance/DEVELOPMENT_CONSTITUTION.yaml").read_text(encoding="utf-8"))
    assert constitution["single_authority"]["mutable_state"] == "governance/STATE.yaml"
    assert constitution["execution"]["one_worker_run_one_task"] is True
    assert constitution["limits"]["total_attempts_per_task"] == 3
    assert constitution["completion"]["final_release"] == "R32"


def test_goal_complete_marker_is_defined():
    constitution = yaml.safe_load((ROOT / "governance/DEVELOPMENT_CONSTITUTION.yaml").read_text(encoding="utf-8"))
    assert constitution["completion"]["marker"] == "governance/GOAL_COMPLETE.json"


def test_no_worker_may_self_declare_pass():
    schema = json.loads((ROOT / "governance/schemas/worker-result.schema.json").read_text(encoding="utf-8"))
    assert "PASS" not in schema["properties"]["status"]["enum"]


def test_candidate_validation_rejects_missing_files(tmp_path):
    evidence = tmp_path / "candidate.json"
    evidence.write_text(json.dumps({
        "schema": "hhy.candidate-evidence/v5.0", "status": "PASS", "release": "R14", "commit": "a" * 40,
        "gate_report": "missing.json",
        "apk": {"path": "missing.apk", "sha256": "b" * 64, "source_commit": "a" * 40, "version_code": 1, "version_name": "1", "signing_sha256": "c" * 64},
        "screenshots": {"source_commit": "a" * 40, "paths": ["missing.png"]},
    }), encoding="utf-8")
    result = validate_candidate_evidence(ROOT, "R14", evidence, "a" * 40)
    assert result["status"] == "FAIL"
    assert any("missing" in error.lower() or "does not exist" in error.lower() for error in result["errors"])


def test_stale_repository_lock_is_reclaimed(tmp_path):
    repo = tmp_path / "repo"
    (repo / ".git").mkdir(parents=True)
    lock = repo / ".git/hhy-governance-v50.lock"
    # PID 99999999 is expected to be absent; a dead worker must not deadlock future runs.
    lock.write_text("99999999:stale", encoding="utf-8")
    with repository_lock(repo, timeout=1):
        assert lock.is_file()
        assert not lock.read_text(encoding="utf-8").startswith("99999999:")
    assert not lock.exists()


def test_git_status_lines_preserve_porcelain_leading_status_space(tmp_path):
    repo = tmp_path / "repo"
    repo.mkdir()
    subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.name", "test"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=repo, check=True)
    target = repo / "apps" / "feature.txt"
    target.parent.mkdir()
    target.write_text("before\n", encoding="utf-8")
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "baseline"], cwd=repo, check=True)
    target.write_text("after\n", encoding="utf-8")
    lines = git_status_lines(repo, "status", "--porcelain=v1", "-uall")
    assert lines == [" M apps/feature.txt"]
    assert lines[0][3:] == "apps/feature.txt"


def test_failed_recovery_authorization_schema_has_strict_owner_binding():
    schema = json.loads((ROOT / "governance/schemas/failed-recovery-authorization.schema.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    good = {
        "schema": "hhy.failed-recovery-authorization/v5.0",
        "status": "APPROVED",
        "authorized_by": "PROJECT_OWNER",
        "authorized_at": "2026-07-31T00:00:00Z",
        "from_task": "TASK-R14-RECOVERY-001",
        "to_task": "TASK-R14-RECOVERY-002",
        "supersedes": "TASK-R14-RECOVERY-001",
        "baseline_commit": "a" * 40,
        "decision": "Retain post-89ccaf45 fixes and rebuild from current main.",
        "scope": {"retain_prior_fixes": True, "reject_old_candidate": True, "release": "R14", "maximum_attempts": 3},
    }
    assert list(validator.iter_errors(good)) == []
    bad = dict(good, authorized_by="MODEL")
    assert list(validator.iter_errors(bad))


def test_failed_recovery_rejects_any_non_formal_transition():
    with pytest.raises(RuntimeError, match="only TASK-R14-RECOVERY-001"):
        supersede_failed(ROOT, "TASK-R14-RECOVERY-001", "TASK-R14-RECOVERY-003", ROOT / "missing.json")


def test_failed_recovery_transition_preserves_predecessor_and_binds_new_task(tmp_path):
    repo = tmp_path / "repo"
    shutil.copytree(ROOT / "governance", repo / "governance")
    shutil.copytree(ROOT / "tools" / "governance", repo / "tools" / "governance")
    for name in ("CURRENT_STATUS.yaml", "NEXT_TASK.yaml"):
        shutil.copy2(ROOT / name, repo / name)
    # Build a clean pre-transition fixture even when the suite itself runs on
    # the branch that already contains TASK-R14-RECOVERY-002.
    for path in (repo / "governance/task_specs").glob("TASK-*-RECOVERY-*.yaml"):
        if path.name != "TASK-R14-RECOVERY-001.yaml":
            path.unlink(missing_ok=True)
    plan = yaml.safe_load((repo / "governance/PROGRAM_PLAN.yaml").read_text(encoding="utf-8"))
    plan["task_count"] = 266
    for release, tasks in plan["release_tasks"].items():
        plan["release_tasks"][release] = [
            task for task in tasks
            if "-RECOVERY-" not in task or task == "TASK-R14-RECOVERY-001"
        ]
    plan["recovery_tasks"] = ["TASK-R14-RECOVERY-001"]
    write_yaml(repo / "governance/PROGRAM_PLAN.yaml", plan)
    r15 = yaml.safe_load((repo / "governance/task_specs/TASK-R15-001.yaml").read_text(encoding="utf-8"))
    r15["depends_on"] = ["TASK-R14-RECOVERY-001"]
    write_yaml(repo / "governance/task_specs/TASK-R15-001.yaml", r15)
    r15_next = yaml.safe_load((repo / "governance/task_specs/TASK-R15-003.yaml").read_text(encoding="utf-8"))
    r15_next["depends_on"] = ["TASK-R15-002"]
    write_yaml(repo / "governance/task_specs/TASK-R15-003.yaml", r15_next)
    for task_id, dependencies in {
        "TASK-R15-004": ["TASK-R15-003"],
        "TASK-R15-005": ["TASK-R15-003", "TASK-R15-004"],
    }.items():
        task_path = repo / "governance/task_specs" / f"{task_id}.yaml"
        task_spec = yaml.safe_load(task_path.read_text(encoding="utf-8"))
        task_spec["depends_on"] = dependencies
        write_yaml(task_path, task_spec)
    state = yaml.safe_load((repo / "governance/STATE.yaml").read_text(encoding="utf-8"))
    state["project"]["status"] = "FAILED_BOUNDED"
    state["project"]["active_task"] = None
    for task_id in list(state["tasks"]):
        if "-RECOVERY-" in task_id and task_id != "TASK-R14-RECOVERY-001":
            state["tasks"].pop(task_id, None)
    state["tasks"]["TASK-R14-RECOVERY-001"].update({"status": "FAILED_BOUNDED", "attempts_used": 3, "current_attempt": None})
    state["lease"] = None
    state["state_hash"] = _state_hash(state)
    write_yaml(repo / "governance/STATE.yaml", state)
    subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.name", "test"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=repo, check=True)
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "baseline"], cwd=repo, check=True)
    baseline = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    authorization = repo / "authorization.json"
    authorization.write_text(json.dumps({
        "schema": "hhy.failed-recovery-authorization/v5.0",
        "status": "APPROVED",
        "authorized_by": "PROJECT_OWNER",
        "authorized_at": "2026-07-31T00:00:00Z",
        "from_task": "TASK-R14-RECOVERY-001",
        "to_task": "TASK-R14-RECOVERY-002",
        "supersedes": "TASK-R14-RECOVERY-001",
        "baseline_commit": baseline,
        "decision": "Retain post-89ccaf45 fixes and rebuild from current main.",
        "scope": {"retain_prior_fixes": True, "reject_old_candidate": True, "release": "R14", "maximum_attempts": 3},
    }), encoding="utf-8")
    result = supersede_failed(repo, "TASK-R14-RECOVERY-001", "TASK-R14-RECOVERY-002", authorization)
    assert result["status"] == "READY"
    state = yaml.safe_load((repo / "governance/STATE.yaml").read_text(encoding="utf-8"))
    assert state["project"]["active_task"] == "TASK-R14-RECOVERY-002"
    assert state["project"]["status"] == "ACTIVE"
    assert state["tasks"]["TASK-R14-RECOVERY-001"]["status"] == "FAILED_BOUNDED"
    assert state["tasks"]["TASK-R14-RECOVERY-001"]["attempts_used"] == 3
    assert state["tasks"]["TASK-R14-RECOVERY-002"]["status"] == "READY"
    assert state["tasks"]["TASK-R14-RECOVERY-002"]["attempts_used"] == 0
    assert yaml.safe_load((repo / "governance/task_specs/TASK-R15-001.yaml").read_text(encoding="utf-8"))["depends_on"] == ["TASK-R14-RECOVERY-002"]
    simulated = simulate_program(repo)
    assert simulated["status"] == "PASS", simulated
    assert simulated["first_task"] == "TASK-R14-RECOVERY-002"


def test_bounded_recovery_candidates_exclude_failures_with_successors():
    specs = {
        "TASK-R15-002": {"release": "R15", "ordinal": 2},
        "TASK-R15-RECOVERY-001": {
            "release": "R15", "ordinal": 9, "kind": "recovery", "supersedes": "TASK-R15-002"
        },
        "TASK-R15-RECOVERY-002": {
            "release": "R15", "ordinal": 10, "kind": "recovery", "supersedes": "TASK-R15-RECOVERY-001"
        },
        "TASK-R15-003": {"release": "R15", "ordinal": 3},
    }
    state = {"tasks": {
        "TASK-R15-002": {"status": "FAILED_BOUNDED", "attempts_used": 3},
        "TASK-R15-RECOVERY-001": {"status": "FAILED_BOUNDED", "attempts_used": 3},
        "TASK-R15-RECOVERY-002": {"status": "DONE", "attempts_used": 3},
        "TASK-R15-003": {"status": "FAILED_BOUNDED", "attempts_used": 3},
    }}

    candidates = bounded_recovery_candidates(state, specs, "R15")

    assert [task_id for _, task_id, _ in candidates] == ["TASK-R15-003"]


def test_stale_auto_recovery_detects_duplicate_of_done_successor():
    specs = {
        "TASK-R15-RECOVERY-001": {"release": "R15", "kind": "recovery"},
        "TASK-R15-RECOVERY-002": {
            "release": "R15", "kind": "recovery", "supersedes": "TASK-R15-RECOVERY-001"
        },
        "TASK-R15-RECOVERY-003": {
            "release": "R15",
            "kind": "recovery",
            "supersedes": "TASK-R15-RECOVERY-001",
            "source": {"path": "Supervisor automatic bounded recovery"},
        },
    }
    state = {
        "project": {"status": "ACTIVE", "active_task": "TASK-R15-RECOVERY-003"},
        "tasks": {
            "TASK-R15-RECOVERY-001": {"status": "FAILED_BOUNDED", "attempts_used": 3},
            "TASK-R15-RECOVERY-002": {"status": "DONE", "attempts_used": 3},
            "TASK-R15-RECOVERY-003": {"status": "READY", "attempts_used": 0},
        },
    }

    assert stale_auto_recovery_task(state, specs) == "TASK-R15-RECOVERY-003"
