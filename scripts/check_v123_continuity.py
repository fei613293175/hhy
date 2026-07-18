#!/usr/bin/env python3
"""V1.2.3 联合严格门禁：V1.2.2 产品/页面文档 + V1.2.3 持续无状态接续。"""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import csv
import hashlib
import json
import os
import subprocess
import sys
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1]


def read_csv(relative: str) -> list[dict[str, str]]:
    with (ROOT / relative).open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def read_yaml(relative: str) -> dict[str, Any]:
    value = yaml.safe_load((ROOT / relative).read_text(encoding="utf-8"))
    return value or {}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_gate(command: list[str], report_path: str) -> tuple[int, dict[str, Any], str]:
    environment = dict(os.environ)
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=240, env=environment)
    path = ROOT / report_path
    payload: dict[str, Any] = {}
    if path.is_file():
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            payload = {}
    return result.returncode, payload, (result.stdout + "\n" + result.stderr).strip()


def main() -> int:
    parser = ArgumentParser(description="合伙云 Pro V1.2.3 全量文档与接续联合门禁")
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--release")
    parser.add_argument("--json-out", default="artifacts/validation/project-doctor-v1.2.3.json")
    args = parser.parse_args()

    errors: list[dict[str, str]] = []
    warnings: list[dict[str, str]] = []
    metrics: dict[str, Any] = {}

    def require(condition: bool, code: str, message: str) -> None:
        if not condition:
            errors.append({"code": code, "message": message})

    def warn(condition: bool, code: str, message: str) -> None:
        if not condition:
            warnings.append({"code": code, "message": message})

    required = [
        ".continuity/CONTINUITY_POLICY.yaml",
        ".continuity/STATE.yaml",
        ".continuity/ACTIVE_SESSION.yaml",
        ".continuity/EVENT_LOG.jsonl",
        ".continuity/SESSION_INDEX.yaml",
        ".continuity/TASK_CLAIMS.yaml",
        ".continuity/TASK_TRANSITIONS.yaml",
        ".continuity/CHANGE_REQUEST_INDEX.yaml",
        "CONTINUITY_POLICY.yaml",
        "config/CONTINUITY_POLICY.yaml",
        "config/REPOSITORY_TRANSPORT.yaml",
        "config/DEVELOPMENT_RUNTIME.yaml",
        "CURRENT_STATUS.yaml",
        "NEXT_TASK.yaml",
        "AGENTS.md",
        "START_HERE.md",
        "templates/AGENTS.md",
        "templates/START_HERE.md",
        "README.md",
        "scripts/continuity.py",
        "scripts/continuity_lib.py",
        "scripts/continuity_gate.py",
        "scripts/check_v123_documentation.py",
        "scripts/check_v123_continuity.py",
        "scripts/run_continuity_self_test.py",
        "scripts/restore_git_transport.py",
        "scripts/select_execution_profile.py",
        "scripts/verify_cloud_environment.py",
        "scripts/install_git_hooks.py",
        "scripts/prepare_commit_message.py",
        "scripts/create_handoff_bundle.py",
        "scripts/bootstrap_repository.py",
        "scripts/export_clean_project_bundle.py",
        "artifacts/context/CURRENT_CONTEXT_PACK.yaml",
        "artifacts/context/CURRENT_CONTEXT_PACK.md",
        "artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json",
        "artifacts/validation/continuity-integration-v1.2.3.json",
        "artifacts/validation/continuity-lifecycle-integration-v1.2.3.json",
        "catalogs/continuity_gate_catalog.csv",
        "catalogs/continuity_event_catalog.csv",
        "catalogs/continuity_required_records.csv",
        "catalogs/session_index.csv",
        "catalogs/handoff_index.csv",
        "catalogs/change_request_index.csv",
        "catalogs/task_transition_ledger.csv",
        "docs/03-continuity/持续开发无状态接续强制门禁_V1.2.3.md",
        "docs/03-continuity/无状态接续运行手册_V1.2.3.md",
        "docs/03-continuity/异常中断与AI接管演练_V1.2.3.md",
        "docs/03-continuity/SESSION_RECORD_SCHEMA.yaml",
        "docs/03-continuity/CHECKPOINT_SCHEMA.yaml",
        "docs/03-continuity/HANDOFF_SCHEMA.yaml",
        "docs/03-continuity/EVENT_LOG_SCHEMA.yaml",
        "docs/03-continuity/CONTEXT_PACK_SCHEMA.yaml",
        "tests/test_git_transport_recovery.py",
        "tests/test_model_routing.py",
        "tests/test_cloud_environment.py",
        ".github/workflows/continuity-gate.yml",
        ".github/workflows/ci.yml",
        ".githooks/pre-commit",
        ".githooks/prepare-commit-msg",
        ".githooks/commit-msg",
        ".githooks/pre-push",
    ]
    for relative in required:
        require((ROOT / relative).is_file(), "FILE_MISSING", relative)

    # Version and canonical policy coherence.
    baseline = read_yaml("PROJECT_BASELINE.yaml")
    manifest = read_yaml("PROJECT_MANIFEST.yaml")
    current = read_yaml("CURRENT_STATUS.yaml")
    next_task = read_yaml("NEXT_TASK.yaml")
    require(str(baseline.get("package_version")) == "1.2.3", "BASELINE_VERSION", "PROJECT_BASELINE必须为1.2.3")
    require(str(manifest.get("package_version")) == "1.2.3", "MANIFEST_VERSION", "PROJECT_MANIFEST必须为1.2.3")
    require(str(current.get("baseline_version")) == "1.2.3", "STATUS_VERSION", "CURRENT_STATUS必须为1.2.3")
    require(next_task.get("id") == current.get("next_task"), "NEXT_TASK_LINK", "NEXT_TASK.id必须与CURRENT_STATUS.next_task一致")
    require(baseline.get("product_spec_baseline") == "V1.2.2", "PRODUCT_BASELINE", "产品与页面施工基线必须保持V1.2.2")
    require(baseline.get("continuity", {}).get("mode") == "ENFORCED", "CONTINUITY_MODE", "连续性模式必须为ENFORCED")

    canonical = ROOT / ".continuity/CONTINUITY_POLICY.yaml"
    pointer_policy = read_yaml("CONTINUITY_POLICY.yaml")
    operational_policy = read_yaml("config/CONTINUITY_POLICY.yaml")
    require(pointer_policy.get("status") == "POINTER_ONLY", "POLICY_POINTER", "根CONTINUITY_POLICY只能是指针，禁止形成第二套规则")
    require(pointer_policy.get("machine_source_of_truth") == ".continuity/CONTINUITY_POLICY.yaml", "POLICY_POINTER_SOURCE", "根策略必须指向权威机器策略")
    require(operational_policy.get("authoritative_machine_policy") == ".continuity/CONTINUITY_POLICY.yaml", "POLICY_OPERATIONAL_SOURCE", "运营策略必须声明权威机器策略")
    if canonical.is_file():
        metrics["canonical_policy_sha256"] = sha256(canonical)
    policy = read_yaml(".continuity/CONTINUITY_POLICY.yaml")
    require(policy.get("mode") == "ENFORCED", "POLICY_MODE", "策略mode必须ENFORCED")
    require(policy.get("conversation_context", {}).get("allowed_as_source_of_truth") is False, "CONVERSATION_DEPENDENCY", "对话不得作为事实源")
    require(policy.get("handoff", {}).get("include_git_bundle_when_available") is True, "HANDOFF_GIT", "交接必须包含可用的Git Bundle")
    require(policy.get("handoff", {}).get("manifest_sha256_required") is True, "HANDOFF_MANIFEST", "交接必须包含SHA Manifest")
    require(policy.get("export", {}).get("clean_project_bundle_supported") is True, "CLEAN_EXPORT", "必须支持干净提交状态交接包")
    required_trailers = policy.get("commit", {}).get("required_trailers", [])
    require(required_trailers == ["Task-ID", "Session-ID", "Checkpoint-ID", "Tests", "CR"], "COMMIT_TRAILERS", "提交Trailer必须统一为Task/Session/Checkpoint/Tests/CR")
    require(operational_policy.get("git", {}).get("required_commit_trailers") == required_trailers, "POLICY_TRAILER_DRIFT", "运营策略与机器策略Trailer不一致")
    require(operational_policy.get("session", {}).get("max_checkpoint_age_minutes") == policy.get("checkpoint", {}).get("max_age_minutes"), "POLICY_CHECKPOINT_DRIFT", "检查点时效策略不一致")
    require(policy.get("checkpoint", {}).get("active_pointer_refreshed_every_checkpoint") is True, "ACTIVE_POINTER_POLICY", "每个检查点必须刷新ACTIVE_SESSION指针")
    require(policy.get("change_control", {}).get("approval_requires_complete_contract") is True, "CR_CONTRACT_POLICY", "CR审批必须要求完整变更合同")
    require(operational_policy.get("change_control", {}).get("approval_requires_complete_contract") is True, "CR_OPERATIONAL_POLICY", "运营视图必须显示完整CR审批要求")
    parallel = policy.get("parallel_development", {})
    operational_parallel = operational_policy.get("parallel_development", {})
    require(parallel.get("authorization", {}).get("status") == "PROJECT_OWNER_STANDING_AUTHORIZATION", "PARALLEL_AUTHORIZATION", "必须记录项目所有者长期多代理授权")
    require(parallel.get("default_delegation_mode") == "AUTO_WHEN_SAFE_PARALLEL_WORK_EXISTS", "PARALLEL_DEFAULT_MODE", "存在安全并行工作时必须默认自动委托")
    require(parallel.get("review_triggers") == ["TASK_START", "SCOPE_CHANGE"], "PARALLEL_REVIEW_TRIGGERS", "必须在Task开始和范围变化时评估并行")
    require(parallel.get("per_task_user_confirmation_required") is False, "PARALLEL_CONFIRMATION", "长期授权后不得逐Task重复请求确认")
    require(parallel.get("non_delegation_requires_checkpoint_reason") is True, "PARALLEL_REASON", "未委托必须写入Checkpoint原因")
    require(parallel.get("capability_fallback") == "RECORD_LIMITATION_AND_DO_NOT_FABRICATE_PARALLEL_EVIDENCE", "PARALLEL_CAPABILITY_FALLBACK", "不支持代理的AI必须记录限制且禁止伪造证据")
    require(parallel.get("authoritative_active_sessions") == 1 and parallel.get("max_delegated_workers") == 3, "PARALLEL_ONE_PLUS_THREE", "并行模型必须为1主控加最多3执行代理")
    for key in ["default_delegation_mode", "review_triggers", "per_task_user_confirmation_required", "non_delegation_requires_checkpoint_reason", "capability_fallback", "user_override_allowed", "authoritative_active_sessions", "max_delegated_workers", "worker_workspace", "require_disjoint_path_leases", "integration_owner"]:
        require(operational_parallel.get(key) == parallel.get(key), "PARALLEL_OPERATIONAL_DRIFT", f"运营策略并行字段漂移：{key}")
    require(operational_parallel.get("authorization_status") == parallel.get("authorization", {}).get("status"), "PARALLEL_AUTHORIZATION_DRIFT", "运营策略长期授权状态漂移")

    runtime = read_yaml("config/DEVELOPMENT_RUNTIME.yaml")
    routing = runtime.get("model_routing", {})
    require(routing.get("complex_or_high_risk", {}).get("model") == "Sol", "MODEL_SOL_ROUTE", "复杂/高风险任务必须路由到Sol")
    require(routing.get("medium", {}).get("model") == "Terra", "MODEL_TERRA_ROUTE", "中等任务必须路由到Terra")
    light_route = routing.get("lightweight_or_mechanical_or_read_only", {})
    require(light_route.get("model") == "Luna", "MODEL_LUNA_ROUTE", "轻量/机械/只读任务必须优先路由到Luna")
    require(light_route.get("unavailable_fallback", {}).get("model") == "Terra", "MODEL_LUNA_FALLBACK", "Luna不可用时必须审计回退到Terra")
    cloud = runtime.get("cloud_environment", {})
    require(cloud.get("default_assumption") == "CODEX_ALREADY_CONNECTED_UNLESS_USER_DECLARES_DISCONNECTED", "CLOUD_DEFAULT_ASSUMPTION", "除非用户声明断连，必须默认Codex已连接项目云服务器")
    require(cloud.get("ssh_alias") == "obx-test", "CLOUD_ALIAS", "云端项目SSH alias必须为obx-test")
    require(cloud.get("startup_preflight_required") is True and cloud.get("preflight_failure") == "BLOCK_DEVELOPMENT_AND_REPORT", "CLOUD_PREFLIGHT", "云端启动预检失败必须阻断并报告")
    android_runtime = cloud.get("android", {})
    require(android_runtime.get("image") == "hhy-android-toolchain:r01-46fb273", "ANDROID_EXISTING_IMAGE", "Android必须复用固定云端镜像")
    require(android_runtime.get("gradle_cache") == "hhy-r01-android-gradle-cache", "ANDROID_EXISTING_CACHE", "Android必须复用固定Gradle缓存")
    prohibited_runtime = set(cloud.get("prohibited", []))
    require({"LOCAL_ANDROID_SDK_BOOTSTRAP", "LOCAL_ANDROID_SDK_REBUILD"} <= prohibited_runtime, "ANDROID_LOCAL_REBUILD", "必须禁止本地Android SDK bootstrap/rebuild")

    transport = read_yaml("config/REPOSITORY_TRANSPORT.yaml")
    repository = transport.get("repository", {})
    require(repository.get("canonical_url") == "https://github.com/fei613293175/hhy.git", "GIT_CANONICAL_REMOTE", "Git canonical remote必须可由仓库恢复")
    require(repository.get("remote_name") == "origin", "GIT_REMOTE_NAME", "Git remote必须为origin")
    require(transport.get("security", {}).get("prohibit_url_credentials") is True, "GIT_URL_CREDENTIALS", "tracked remote URL必须禁止凭据")
    require(transport.get("security", {}).get("prohibit_force_push") is True, "GIT_FORCE_PUSH", "必须禁止force push")
    require(transport.get("recovery", {}).get("raw_source_without_git_or_bundle") == "BLOCK", "GIT_RAW_SOURCE_HISTORY", "纯源码无Git/Bundle时必须阻断历史推送")
    require(policy.get("development_environment", {}).get("prohibit_serverless_assumption") is True, "POLICY_SERVERLESS_PROHIBITED", "权威策略必须禁止无服务器假设")
    require(policy.get("development_environment", {}).get("prohibit_local_android_sdk_bootstrap_or_rebuild") is True, "POLICY_LOCAL_ANDROID_PROHIBITED", "权威策略必须禁止本地Android SDK重建")
    require(policy.get("git_transport", {}).get("push_preflight_required") is True, "POLICY_GIT_PREFLIGHT", "权威策略必须要求Git推送预检")

    obsolete = [".continuity/LAST_CHECKPOINT.yaml", ".continuity/HANDOFF_STATE.yaml"]
    for relative in obsolete:
        require(not (ROOT / relative).exists(), "SECOND_STATE_MODEL", f"禁止遗留第二套状态文件：{relative}")

    # Full product/page documentation gate.
    doc_command = [sys.executable, str(ROOT / "scripts/check_v123_documentation.py"), "--strict", "--json-out", ".continuity/runtime/project-doctor-v1.2.3-documentation.json"]
    if args.release:
        doc_command += ["--release", args.release]
    doc_rc, doc_payload, doc_output = run_gate(doc_command, ".continuity/runtime/project-doctor-v1.2.3-documentation.json")
    require(doc_rc == 0 and doc_payload.get("status") == "PASS", "DOCUMENT_GATE", doc_output[-2000:] or "文档门禁失败")
    metrics["documentation"] = doc_payload.get("metrics", {})

    # State/event/context gate.
    continuity_command = [sys.executable, str(ROOT / "scripts/continuity_gate.py"), "--mode", "doctor", "--strict", "--json-out", ".continuity/runtime/continuity-gate-v1.2.3.json"]
    cont_rc, cont_payload, cont_output = run_gate(continuity_command, ".continuity/runtime/continuity-gate-v1.2.3.json")
    require(cont_rc == 0 and cont_payload.get("status") == "PASS", "CONTINUITY_GATE", cont_output[-2000:] or "接续门禁失败")
    metrics["continuity"] = cont_payload.get("metrics", {})

    # Catalogs and baseline evidence cannot be empty placeholders.
    gate_rows = read_csv("catalogs/continuity_gate_catalog.csv")
    event_rows = read_csv("catalogs/continuity_event_catalog.csv")
    required_rows = read_csv("catalogs/continuity_required_records.csv")
    session_rows = read_csv("catalogs/session_index.csv")
    handoff_rows = read_csv("catalogs/handoff_index.csv")
    cr_rows = read_csv("catalogs/change_request_index.csv")
    transition_rows = read_csv("catalogs/task_transition_ledger.csv")
    metrics.update({
        "continuity_gate_rules": len(gate_rows),
        "continuity_event_types": len(event_rows),
        "continuity_required_records": len(required_rows),
        "baseline_sessions": len(session_rows),
        "baseline_handoffs": len(handoff_rows),
        "change_requests": len(cr_rows),
        "task_transitions": len(transition_rows),
    })
    require(len(gate_rows) >= 38, "GATE_CATALOG", "连续性门禁至少38项")
    require(len(event_rows) >= 13, "EVENT_CATALOG", "事件类型目录不完整")
    require(any(row.get("事件类型") == "CHANGE_REQUEST_AMENDED" for row in event_rows), "CR_AMEND_EVENT", "事件目录缺少CHANGE_REQUEST_AMENDED")
    require(len(required_rows) >= 18, "RECORD_CATALOG", "强制记录目录不完整")
    require(bool(session_rows), "SESSION_BASELINE", "必须保留基线Session")
    require(bool(handoff_rows), "HANDOFF_BASELINE", "必须保留基线Handoff")
    require(any(row.get("cr_id") == "CR-0002" for row in cr_rows), "CR_V123_MISSING", "CR-0002必须进入统一CR索引")
    require(bool(transition_rows), "TRANSITION_BASELINE", "任务迁移账本不得为空")

    # The integration report proves real Git hooks, portable reconstruction and takeover.
    integration_path = ROOT / "artifacts/validation/continuity-integration-v1.2.3.json"
    integration: dict[str, Any] = {}
    if integration_path.is_file():
        try:
            integration = json.loads(integration_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            integration = {}
    require(integration.get("status") == "PASS", "INTEGRATION_SELF_TEST", "持续接续真实Git集成演练尚未通过")
    require(integration.get("conversation_context_required") is False, "INTEGRATION_NO_CONVERSATION", "集成演练必须证明不需要对话上下文")
    require(integration.get("repository_only_reconstruction") is True, "INTEGRATION_RECONSTRUCTION", "集成演练必须证明可仅用仓库/交接包重建")
    check_names = {row.get("name") for row in integration.get("checks", [])}
    required_checks = {
        "double_claim_rejected", "stale_checkpoint_rejected", "real_git_commit_with_hooks",
        "pre_push_per_commit_validation", "portable_clone_fingerprint",
        "takeover_without_conversation", "repository_only_context",
        "event_tamper_rejected", "handoff_tamper_rejected",
    }
    require(required_checks <= check_names, "INTEGRATION_CHECK_SET", "集成演练缺少强制场景：" + ",".join(sorted(required_checks - check_names)))
    for source in integration.get("source_manifest", []):
        path = ROOT / source.get("path", "")
        require(path.is_file(), "INTEGRATION_SOURCE_MISSING", source.get("path", ""))
        if path.is_file():
            require(sha256(path) == source.get("sha256"), "INTEGRATION_REPORT_STALE", source.get("path", ""))
    metrics["continuity_integration_checks"] = integration.get("check_count", 0)

    lifecycle_path = ROOT / "artifacts/validation/continuity-lifecycle-integration-v1.2.3.json"
    lifecycle: dict[str, Any] = {}
    if lifecycle_path.is_file():
        try:
            lifecycle = json.loads(lifecycle_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            lifecycle = {}
    require(lifecycle.get("status") == "PASS", "LIFECYCLE_SELF_TEST", "CR/关闭/Clean Export全生命周期演练尚未通过")
    require(lifecycle.get("conversation_context_required") is False, "LIFECYCLE_NO_CONVERSATION", "全生命周期演练必须禁止对话依赖")
    require(lifecycle.get("repository_only_handoff") is True, "LIFECYCLE_HANDOFF", "全生命周期演练必须证明仓库交接")
    lifecycle_checks = {row.get("check") for row in lifecycle.get("checks", [])}
    required_lifecycle_checks = {
        "bootstrap_atomic", "no_session_commit_blocked", "unique_active_session_and_claim",
        "checkpoint_test_required", "frozen_fact_requires_cr",
        "complete_cr_contract_and_separation", "wip_handoff_and_takeover",
        "closure_checkpoint_and_metadata_commit", "per_commit_prepush_and_ci",
        "clean_committed_export", "event_hash_chain_tamper_detected",
    }
    require(required_lifecycle_checks <= lifecycle_checks, "LIFECYCLE_CHECK_SET", "全生命周期演练缺少场景：" + ",".join(sorted(required_lifecycle_checks - lifecycle_checks)))
    for source in lifecycle.get("source_manifest", []):
        path = ROOT / source.get("path", "")
        require(path.is_file(), "LIFECYCLE_SOURCE_MISSING", source.get("path", ""))
        if path.is_file():
            require(sha256(path) == source.get("sha256"), "LIFECYCLE_REPORT_STALE", source.get("path", ""))
    metrics["continuity_lifecycle_checks"] = lifecycle.get("metrics", {}).get("checks", len(lifecycle.get("checks", [])))

    # All releases must carry the continuity protocol and no-conversation DoR.
    releases = read_csv("catalogs/release_plan.csv")
    require(len(releases) == 33, "RELEASE_COUNT", "必须包含P00与R01-R32")
    for row in releases:
        release = row.get("版本")
        if args.release and release != args.release:
            continue
        for name in ["RELEASE_MANIFEST.yaml", "DEFINITION_OF_READY.yaml", "STORIES.yaml", "TASKS.yaml", "ACCEPTANCE_MATRIX.csv"]:
            require((ROOT / "releases" / release / name).is_file(), "RELEASE_FILE", f"{release}/{name}")
        manifest_doc = read_yaml(f"releases/{release}/RELEASE_MANIFEST.yaml")
        continuity_binding = manifest_doc.get("continuity") or manifest_doc.get("continuity_protocol") or {}
        require(isinstance(continuity_binding, dict) and continuity_binding.get("policy") == ".continuity/CONTINUITY_POLICY.yaml", "RELEASE_CONTINUITY", f"{release}未绑定权威接续策略")
        dor_text = (ROOT / "releases" / release / "DEFINITION_OF_READY.yaml").read_text(encoding="utf-8")
        require("CONTINUITY" in dor_text.upper() or "无状态" in dor_text, "RELEASE_DOR_CONTINUITY", f"{release} DoR缺少接续门禁")

    # Git hooks and workflow commands must use the real unified CLI syntax.
    for relative in [".githooks/pre-commit", ".githooks/prepare-commit-msg", ".githooks/commit-msg", ".githooks/pre-push"]:
        path = ROOT / relative
        if path.is_file():
            require(os.access(path, os.X_OK), "HOOK_NOT_EXECUTABLE", f"{relative}缺少执行权限")
            hook_text = path.read_text(encoding="utf-8")
            require("continuity" in hook_text or "prepare_commit_message.py" in hook_text, "HOOK_NOT_DELEGATED", f"{relative}未委托统一门禁")
    gitmessage = (ROOT / ".gitmessage").read_text(encoding="utf-8")
    for trailer in ["Task-ID:", "Session-ID:", "Checkpoint-ID:", "Tests:", "CR:"]:
        require(trailer in gitmessage, "GITMESSAGE_TRAILER", f".gitmessage缺少{trailer}")
    prepare_text = (ROOT / "scripts/prepare_commit_message.py").read_text(encoding="utf-8")
    require("expected_commit_trailers" in prepare_text, "PREPARE_TRAILERS", "prepare-commit-msg必须从检查点生成精确Trailers")
    gate_text = (ROOT / "scripts/continuity_gate.py").read_text(encoding="utf-8")
    require("validate_commit_identity" in gate_text and "commit_records" in gate_text, "PER_COMMIT_GATE", "Git/CI必须逐Commit校验身份和记录")
    cli_text = (ROOT / "scripts/continuity.py").read_text(encoding="utf-8")
    require("export-clean" in cli_text and "command_export_clean" in cli_text, "CLEAN_EXPORT_CLI", "统一CLI缺少干净项目导出")
    require("cr-amend" in cli_text and "command_cr_amend" in cli_text, "CR_AMEND_CLI", "统一CLI缺少CR完整合同补齐命令")
    require("change_request_completeness_errors" in gate_text and "CR_INCOMPLETE" in gate_text, "CR_COMPLETENESS_GATE", "Doctor/CI必须拒绝不完整的已批准CR")
    require("closure_checkpoint" in cli_text and "CLOSING" in cli_text, "CLOSE_PROTOCOL", "任务关闭必须生成关闭检查点")

    workflow_text = "\n".join((ROOT / p).read_text(encoding="utf-8") for p in [".github/workflows/continuity-gate.yml", ".github/workflows/ci.yml"])
    forbidden_cli = ["continuity_gate.py baseline", "continuity_gate.py ci", "--base-sha", "--head-sha", "continuity.py export", "close --outcome"]
    for token in forbidden_cli:
        require(token not in workflow_text, "STALE_CLI", f"工作流仍使用过期命令：{token}")
    require("--mode ci" in workflow_text and "--base-ref" in workflow_text and "--head-ref" in workflow_text, "CI_DIFF_GATE", "CI必须使用真实Diff连续性门禁")
    require("Initial repository push" in workflow_text and "--mode ci" in workflow_text, "CI_INITIAL_HISTORY", "首次推送也必须逐Commit校验")
    require("run_continuity_self_test.py" in workflow_text, "CI_CONTINUITY_SELF_TEST", "CI必须执行真实Git无状态接续集成演练")
    require("test_continuity_protocol.py" in workflow_text, "CI_CONTINUITY_LIFECYCLE_TEST", "CI必须执行CR/关闭/Clean Export全生命周期演练")
    require("test_continuity_bootstrap_recovery.py" in workflow_text, "CI_BOOTSTRAP_RECOVERY_TEST", "CI必须执行无Git冷启动回归测试")
    require("continuity-integration-v1.2.3.json" in workflow_text, "CI_CONTINUITY_ARTIFACT", "CI必须归档接续重建报告")
    require("continuity-lifecycle-integration-v1.2.3.json" in workflow_text, "CI_CONTINUITY_LIFECYCLE_ARTIFACT", "CI必须归档接续全生命周期报告")

    # Compatibility entrypoints must delegate, not maintain a second state model.
    handoff_wrapper = (ROOT / "scripts/create_handoff_bundle.py").read_text(encoding="utf-8") if (ROOT / "scripts/create_handoff_bundle.py").is_file() else ""
    require("continuity.py" in handoff_wrapper and "handoff" in handoff_wrapper, "HANDOFF_WRAPPER", "create_handoff_bundle必须委托统一CLI")
    stale_state_tokens = ["HANDOFF_STATE.yaml", "LAST_CHECKPOINT.yaml", "artifacts/continuity/CURRENT_CONTEXT_PACK"]
    for relative in ["scripts/create_handoff_bundle.py", "scripts/start_session.py", "scripts/checkpoint_session.py", "scripts/close_session.py", "scripts/resume_project.py"]:
        if not (ROOT / relative).is_file():
            continue
        text = (ROOT / relative).read_text(encoding="utf-8")
        for token in stale_state_tokens:
            require(token not in text, "SECOND_STATE_SCRIPT", f"{relative}仍引用旧状态：{token}")

    # Context pack must be self-contained and explicitly repository-only.
    require((ROOT / "AGENTS.md").read_bytes() == (ROOT / "templates/AGENTS.md").read_bytes(), "AGENTS_TEMPLATE_DRIFT", "AGENTS根入口与导出模板必须完全一致")
    require((ROOT / "START_HERE.md").read_bytes() == (ROOT / "templates/START_HERE.md").read_bytes(), "START_TEMPLATE_DRIFT", "START_HERE根入口与导出模板必须完全一致")
    context = read_yaml("artifacts/context/CURRENT_CONTEXT_PACK.yaml")
    require(context.get("conversation_dependency") == "PROHIBITED", "CONTEXT_CONVERSATION", "Context Pack必须禁止对话依赖")
    require(context.get("source_of_truth") == "REPOSITORY_ONLY", "CONTEXT_SOURCE", "Context Pack事实源必须为仓库")
    require(bool(context.get("exact_resume_command")), "CONTEXT_COMMAND", "Context Pack必须提供精确恢复命令")
    require(context.get("parallel_development_policy") == parallel, "CONTEXT_PARALLEL_POLICY", "Context Pack必须完整携带权威并行策略")
    require(context.get("execution_routing_policy") == runtime.get("model_routing"), "CONTEXT_MODEL_ROUTING", "Context Pack必须携带模型分级策略")
    require(context.get("development_runtime") == runtime, "CONTEXT_RUNTIME", "Context Pack必须携带云端既有环境策略")
    require(context.get("repository_transport") == transport, "CONTEXT_GIT_TRANSPORT", "Context Pack必须携带Git transport descriptor")
    if context.get("active_session") and context.get("latest_checkpoint"):
        require(bool(context.get("latest_checkpoint", {}).get("parallel_execution")), "CONTEXT_PARALLEL_CHECKPOINT", "最新Checkpoint必须记录结构化并行决策")
    context_manifest = json.loads((ROOT / "artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json").read_text(encoding="utf-8"))
    require(bool(context_manifest.get("project_fingerprint", {}).get("sha256")), "CONTEXT_TREE_FINGERPRINT", "Context Manifest必须记录项目树/会话指纹")
    for key in ["yaml", "markdown"]:
        record = context_manifest.get(key, {})
        path = ROOT / record.get("path", "")
        require(path.is_file(), "CONTEXT_FILE_MISSING", record.get("path", ""))
        if path.is_file():
            require(sha256(path) == record.get("sha256"), "CONTEXT_FILE_TAMPER", record.get("path", ""))
    for source in context_manifest.get("sources", []):
        path = ROOT / source.get("path", "")
        require(path.is_file(), "CONTEXT_SOURCE_MISSING", source.get("path", ""))
        if path.is_file():
            require(sha256(path) == source.get("sha256"), "CONTEXT_SOURCE_STALE", source.get("path", ""))
    context_sources = {row.get("path") for row in context_manifest.get("sources", [])}
    require("releases/PROGRAM_EXECUTION_PLAN.yaml" in context_sources, "CONTEXT_PROGRAM_PLAN", "Context Pack来源必须包含总执行计划")
    require("config/REPOSITORY_TRANSPORT.yaml" in context_sources, "CONTEXT_TRANSPORT_SOURCE", "Context Pack来源必须包含Git transport descriptor")
    require("config/DEVELOPMENT_RUNTIME.yaml" in context_sources, "CONTEXT_RUNTIME_SOURCE", "Context Pack来源必须包含开发运行时策略")
    active_release = context.get("active_session", {}).get("release") if context.get("active_session") else None
    if active_release:
        require(f"releases/{active_release}/PARALLEL_EXECUTION_PLAN.yaml" in context_sources, "CONTEXT_RELEASE_PARALLEL_PLAN", "Context Pack来源必须包含当前Release并行计划")

    # Commands exposed to the next AI must be valid unified CLI commands.
    next_text = (ROOT / "NEXT_TASK.yaml").read_text(encoding="utf-8")
    require("continuity.py resume" in next_text, "NEXT_RESUME", "NEXT_TASK必须给出冷启动命令")
    require("continuity.py start" in next_text, "NEXT_START", "NEXT_TASK必须给出启动命令")
    require("continuity.py checkpoint" in next_text, "NEXT_CHECKPOINT", "NEXT_TASK必须给出检查点命令")
    require("continuity.py handoff" in next_text, "NEXT_HANDOFF", "NEXT_TASK必须给出交接命令")
    require("continuity.py export-clean" in next_text, "NEXT_CLEAN_EXPORT", "NEXT_TASK必须给出干净项目导出命令")
    require("continuity.py cr-amend" in next_text, "NEXT_CR_AMEND", "NEXT_TASK必须给出CR完整合同补齐命令")
    require("continuity.py export " not in next_text and "--outcome" not in next_text, "NEXT_STALE_COMMAND", "NEXT_TASK包含过期命令")

    # Package must not contain tracked transient caches or plaintext secret files.
    ignore_text = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for required_ignore in ["**/target/", "**/.gradle/", "**/node_modules/", "**/__pycache__/", "*.py[cod]", ".git-credentials", ".netrc", ".ssh/"]:
        require(required_ignore in ignore_text, "TRANSIENT_IGNORE_MISSING", f".gitignore缺少 {required_ignore}")
    tracked = subprocess.run(
        ["git", "ls-files"], cwd=ROOT, text=True, capture_output=True
    ).stdout.splitlines() if (ROOT / ".git").exists() else []
    tracked_transient = [
        path for path in tracked
        if any(part in {"node_modules", "target", ".gradle", "__pycache__"} for part in Path(path).parts)
        or Path(path).suffix in {".pyc", ".pyo"}
    ]
    require(not tracked_transient, "TRACKED_TRANSIENT_FILES", "Git不得跟踪临时产物：" + ", ".join(tracked_transient[:20]))
    status = "PASS" if not errors and (not args.strict or not warnings) else "FAIL"
    payload = {
        "version": "1.2.3",
        "product_spec_baseline": "V1.2.2",
        "continuity_protocol_version": "1.0",
        "status": status,
        "strict": args.strict,
        "release_filter": args.release,
        "metrics": metrics,
        "errors": errors,
        "warnings": warnings,
    }
    output = ROOT / args.json_out
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
