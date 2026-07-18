#!/usr/bin/env python3
"""Generate the deterministic R02-R32 rolling execution plan."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
RELEASES = [f"R{index:02d}" for index in range(2, 33)]
COMPLETED_BASELINE = {"P00", "R01"}

CRITICAL_RELEASES = {
    "R17",
    "R20",
    "R21",
    "R22",
    "R23",
    "R24",
    "R25",
    "R26",
    "R29",
    "R30",
    "R31",
    "R32",
}
HIGH_RELEASES = {
    "R02",
    "R03",
    "R04",
    "R05",
    "R14",
    "R15",
    "R16",
    "R18",
    "R19",
    "R28",
}

RISK_DOMAINS = {
    "R02": ["IDENTITY", "SESSION_SECURITY", "SMS", "APK_SIGNING"],
    "R03": ["SECRETS", "CERTIFICATES", "DNS", "PROVIDER_ACTIVATION"],
    "R04": ["OBJECT_STORAGE", "PRIVATE_MEDIA", "UPLOAD_CONCURRENCY"],
    "R05": ["REAL_NAME", "PRIVACY", "CALLBACK_VERIFICATION"],
    "R14": ["REALTIME_MESSAGING", "ORDERING", "OFFLINE_RECOVERY"],
    "R15": ["CONTENT_GOVERNANCE", "CUSTOMER_SERVICE", "PRIVACY"],
    "R16": ["ORDER_STATE_MACHINE", "PRICE_CONSISTENCY"],
    "R17": ["PAYMENT", "CALLBACK_IDEMPOTENCY", "RECONCILIATION"],
    "R18": ["MEMBERSHIP_ENTITLEMENT", "UPGRADE_DIFFERENCE"],
    "R19": ["PAID_EXPOSURE", "ORDER_LINKAGE"],
    "R20": ["RED_PACKET_BUDGET", "PRE_REVIEW"],
    "R21": ["PAYMENT", "PRICE_INCREASE", "PAUSE_STATE_MACHINE"],
    "R22": ["TIMED_ELIGIBILITY", "ANTI_REPLAY", "CONCURRENCY"],
    "R23": ["LEDGER", "ANTI_FRAUD", "RECONCILIATION"],
    "R24": ["REWARD_ACCOUNT", "WITHDRAWAL", "PAYOUT", "DOUBLE_ENTRY"],
    "R25": ["COMMISSION", "ATTRIBUTION", "REVERSAL"],
    "R26": ["TIER_REWARD", "ACTIVE_USER_CALCULATION"],
    "R28": ["PUBLIC_H5", "DOWNLOAD", "CONTENT_SECURITY"],
    "R29": ["BUILD_PIPELINE", "PRODUCTION_SIGNING", "ARTIFACT_SUPPLY_CHAIN"],
    "R30": ["RBAC", "OPERATIONS", "CONFIGURATION_GOVERNANCE"],
    "R31": ["SECURITY", "PERFORMANCE", "BACKUP", "DISASTER_RECOVERY"],
    "R32": ["PRODUCTION_RELEASE", "COMPLIANCE", "PROVIDER_CUTOVER"],
}

EXTERNAL_GATES = {
    "R02": ["SMS_SANDBOX", "STABLE_STAGING_SIGNING", "API_AND_DOWNLOAD_TLS"],
    "R03": [
        "STABLE_STAGING_SIGNING",
        "SMS_SECRETREF",
        "OBJECT_STORAGE_SANDBOX",
        "REAL_NAME_SANDBOX",
        "PAYMENT_SANDBOX",
        "PAYOUT_SANDBOX",
        "DNS_AND_TLS_CONTROL",
    ],
    "R04": ["OBJECT_STORAGE_BUCKET", "CORS", "LIFECYCLE_POLICY"],
    "R05": ["REAL_NAME_AND_LIVENESS_SANDBOX", "CALLBACK_ALLOWLIST"],
    "R16": ["PAYMENT_MERCHANT_SANDBOX", "PAYMENT_CERTIFICATE", "RECONCILIATION_FEED"],
    "R22": ["PAYOUT_SANDBOX", "PAYOUT_CERTIFICATE"],
    "R28": ["PUBLIC_H5_TLS", "ASSET_CDN", "APPLICATION_MARKET_PREPARATION"],
    "R29": ["PROTECTED_PRODUCTION_SIGNING", "PROTECTED_BUILD_RUNNER"],
    "R31": ["LOAD_TEST_ENVIRONMENT", "BACKUP_TARGET", "DISASTER_RECOVERY_ENVIRONMENT"],
    "R32": [
        "PRODUCTION_PROVIDER_CREDENTIALS",
        "PENETRATION_TEST",
        "LEGAL_PRIVACY_TAX_APPROVAL",
        "APPLICATION_MARKET_ACCESS",
        "PRODUCTION_RELEASE_APPROVAL",
    ],
}


def load_yaml(path: Path) -> dict[str, Any]:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"expected mapping: {path}")
    return data


def story_list(path: Path) -> list[dict[str, Any]]:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if isinstance(data, list):
        return data
    if isinstance(data, dict) and isinstance(data.get("stories"), list):
        return data["stories"]
    raise ValueError(f"expected story list: {path}")


def topological_waves(dependencies: dict[str, list[str]]) -> list[list[str]]:
    completed = set(COMPLETED_BASELINE)
    pending = set(RELEASES)
    waves: list[list[str]] = []
    while pending:
        ready = sorted(
            release
            for release in pending
            if set(dependencies.get(release, [])) <= completed
        )
        if not ready:
            raise ValueError(f"release dependency cycle or missing dependency: {sorted(pending)}")
        waves.append(ready)
        completed.update(ready)
        pending.difference_update(ready)
    return waves


def longest_path_to(
    release: str, dependencies: dict[str, list[str]], memo: dict[str, list[str]]
) -> list[str]:
    if release in memo:
        return memo[release]
    parents = dependencies.get(release, [])
    if not parents:
        path = [release]
    else:
        candidates = [longest_path_to(parent, dependencies, memo) + [release] for parent in parents]
        path = max(candidates, key=lambda item: (len(item), item))
    memo[release] = path
    return path


def release_metrics(root: Path, release: str) -> tuple[dict[str, Any], dict[str, int], list[str]]:
    base = root / "releases" / release
    manifest = load_yaml(base / "RELEASE_MANIFEST.yaml")
    stories = story_list(base / "STORIES.yaml")
    tasks = load_yaml(base / "TASKS.yaml").get("tasks", [])
    ui = manifest.get("ui", {})
    contracts = manifest.get("contracts", {})
    story_operation_references = [
        str(operation)
        for story in stories
        for operation in story.get("operation_ids", [])
    ]
    metrics = {
        "requirements": len(manifest.get("requirements", [])),
        "stories": len(stories),
        "tasks": len(tasks),
        "android_surfaces": len(ui.get("android", [])),
        "h5_surfaces": len(ui.get("h5", [])),
        "admin_surfaces": len(ui.get("admin", [])),
        "client_api_references": len(contracts.get("client_api", [])),
        "admin_api_references": len(contracts.get("admin_api", [])),
        "websocket_references": len(contracts.get("websocket", [])),
        "manifest_endpoint_references": sum(
            len(contracts.get(kind, []))
            for kind in ("client_api", "admin_api", "websocket")
        ),
        "story_operation_references": len(story_operation_references),
        "story_unique_operation_ids": len(set(story_operation_references)),
        "database_table_references": len(manifest.get("database_tables", [])),
        "test_references": len(manifest.get("tests", [])),
    }
    platforms = sorted({str(story.get("platform", "UNKNOWN")) for story in stories})
    return manifest, metrics, platforms


def risk_tier(release: str) -> str:
    if release in CRITICAL_RELEASES:
        return "P0_CRITICAL"
    if release in HIGH_RELEASES:
        return "P1_HIGH"
    return "P2_STANDARD"


def client_partition(platforms: list[str]) -> str:
    visible = [value for value in ("ANDROID", "ADMIN", "H5") if value in platforms]
    return "CLIENT_" + "_".join(visible) if visible else "CLIENT_NOT_APPLICABLE"


def build_plan(root: Path) -> dict[str, Any]:
    dependency_doc = load_yaml(root / "releases" / "RELEASE_DEPENDENCIES.yaml")
    dependencies = dependency_doc["dependencies"]
    parallel_policy = load_yaml(root / ".continuity" / "CONTINUITY_POLICY.yaml")["parallel_development"]
    waves = topological_waves(dependencies)
    critical_path = longest_path_to("R32", dependencies, {})

    release_entries: list[dict[str, Any]] = []
    totals = {
        "releases": 0,
        "apk_releases": 0,
        "requirements": 0,
        "stories": 0,
        "tasks": 0,
        "android_surfaces": 0,
        "h5_surfaces": 0,
        "admin_surfaces": 0,
        "client_api_references": 0,
        "admin_api_references": 0,
        "websocket_references": 0,
        "manifest_endpoint_references": 0,
        "story_operation_references": 0,
        "story_unique_operation_ids": 0,
        "database_table_references": 0,
        "test_references": 0,
    }
    for release in RELEASES:
        manifest, metrics, platforms = release_metrics(root, release)
        apk_required = bool(manifest.get("android_test_apk_required"))
        entry = {
            "release": release,
            "title": manifest.get("title"),
            "milestone": manifest.get("milestone"),
            "depends_on": dependencies.get(release, []),
            "planning_depth": (
                "EXECUTION_READY"
                if release == "R02"
                else "STORY_READY"
                if release in {"R03", "R04"}
                else "PORTFOLIO_READY"
            ),
            "android_test_apk_required": apk_required,
            "risk_tier": risk_tier(release),
            "risk_domains": RISK_DOMAINS.get(release, ["STANDARD_DELIVERY"]),
            "external_gates": EXTERNAL_GATES.get(release, []),
            "agent_partitions": [
                "BACKEND_DATA",
                client_partition(platforms),
                "QUALITY_DELIVERY",
            ],
            "metrics": metrics,
            "facts": {
                "manifest": f"releases/{release}/RELEASE_MANIFEST.yaml",
                "stories": f"releases/{release}/STORIES.yaml",
                "tasks": f"releases/{release}/TASKS.yaml",
                "acceptance": f"releases/{release}/ACCEPTANCE_MATRIX.csv",
            },
        }
        if release in {"R02", "R03", "R04"}:
            entry["parallel_execution_plan"] = f"releases/{release}/PARALLEL_EXECUTION_PLAN.yaml"
        release_entries.append(entry)
        totals["releases"] += 1
        totals["apk_releases"] += int(apk_required)
        for key, value in metrics.items():
            totals[key] += value

    return {
        "schema": "hhy.program-execution-plan/v1",
        "version": "1.0",
        "change_request": "CR-0021",
        "scope": {"from": "R02", "through": "R32", "completed_baseline": ["P00", "R01"]},
        "source_of_truth": {
            "dependencies": "releases/RELEASE_DEPENDENCIES.yaml",
            "release_pattern": "releases/<release>/{RELEASE_MANIFEST,STORIES,TASKS,DEFINITION_OF_READY,ACCEPTANCE_MATRIX}",
            "documentation_baseline": "V1.2.2",
            "continuity_baseline": "V1.2.3",
        },
        "portfolio_totals": totals,
        "operating_model": {
            "actual_concurrency_slots": 1 + int(parallel_policy["max_delegated_workers"]),
            "coordinator_slots": 1,
            "delegated_worker_slots": parallel_policy["max_delegated_workers"],
            "authoritative_session_count": parallel_policy["authoritative_active_sessions"],
            "default_delegation_mode": parallel_policy["default_delegation_mode"],
            "review_triggers": parallel_policy["review_triggers"],
            "per_task_user_confirmation_required": parallel_policy["per_task_user_confirmation_required"],
            "non_delegation_requires_checkpoint_reason": parallel_policy["non_delegation_requires_checkpoint_reason"],
            "capability_fallback": parallel_policy["capability_fallback"],
            "user_override_allowed": parallel_policy["user_override_allowed"],
            "simultaneous_business_release_limit": 1,
            "cross_release_parallelism": "PLANNING_AND_EXTERNAL_PREPARATION_ONLY",
            "worker_rules": [
                "每个执行代理使用隔离scratch worktree和互不重叠的路径租约",
                "执行代理不修改连续性和Release事实，不提交、不推送、不发布",
                "主控逐项审查补丁、运行门禁并作为唯一提交者",
                "资金、状态机、并发、安全和破坏性迁移由高推理主控复核",
            ],
            "default_partitions": [
                {"id": "BACKEND_DATA", "paths": ["services/backend/**", "database/**"]},
                {
                    "id": "CLIENT",
                    "paths": ["apps/android/**", "apps/admin-web/**", "apps/h5/**"],
                    "rule": "每次任务只领取实际涉及的客户端目录",
                },
                {"id": "QUALITY_DELIVERY", "paths": ["tests/**", "artifacts/validation/**"]},
            ],
        },
        "rolling_window": {
            "current_release": "R02",
            "execution_ready": ["R02"],
            "story_ready": ["R03", "R04"],
            "portfolio_ready": [f"R{index:02d}" for index in range(5, 33)],
            "refinement_trigger": "每个版本封板时把当前+1提升为EXECUTION_READY，并把当前+3提升为STORY_READY",
            "rules": [
                "当前版本细化到代理、目录、接口、页面、数据和测试",
                "后两个版本细化到纵向Story、依赖、外部资源和验收",
                "其余版本保持里程碑、DAG、规模、风险和最晚外部准备版本",
                "滚动细化不得改变冻结业务事实；需要改变时必须新建CR",
            ],
        },
        "delivery_flow": [
            "DOR_AND_SESSION",
            "THREE_PARTITION_VERTICAL_IMPLEMENTATION",
            "FAST_AND_MODULE_TESTS",
            "COORDINATOR_INTEGRATION",
            "INTEGRATION_PROFILE",
            "STAGING_AND_FAULT_INJECTION",
            "RELEASE_PROFILE",
            "APK_DESKTOP_AND_PUBLIC_DELIVERY_IF_REQUIRED",
            "OWNER_PHYSICAL_DEVICE_ACCEPTANCE_IF_REQUIRED",
            "RELEASE_CLOSE_AND_STATELESS_HANDOFF",
        ],
        "release_waves": [
            {
                "wave": f"WAVE-{index:02d}",
                "eligible_releases": releases,
                "meaning": "依赖满足后的候选集合，不表示同时编码",
            }
            for index, releases in enumerate(waves, start=1)
        ],
        "critical_path": {
            "representative_longest_path": critical_path,
            "join_barriers": [
                {"releases": ["R08", "R09", "R10", "R11"], "unlocks": "R12"},
                {"releases": ["R20", "R21", "R22", "R23", "R24"], "unlocks": "R30"},
                {"releases": ["R28", "R29", "R30", "R31", "R32"], "unlocks": "PRODUCTION"},
            ],
            "scheduler_rule": "优先完成阻塞最多后继版本的Release；同一候选波次内仍只保持一个业务实现版本",
        },
        "external_readiness": {
            "verified_now": [
                {
                    "resource": "api.orbexa.cc",
                    "status": "DNS_TLS_REACHABLE_SERVICE_HEALTH_PENDING",
                    "evidence": "2026-07-18 DNS A/AAAA + HTTPS 200",
                },
                {
                    "resource": "download.orbexa.cc",
                    "status": "DNS_TLS_REACHABLE_R01_ARTIFACT_VERIFIED",
                    "evidence": "2026-07-18 DNS A/AAAA + HTTPS reachable; R01 APK evidence in repository",
                },
            ],
            "hard_rules": [
                "Secret只允许SecretRef，禁止仓库、日志和命令行明文",
                "P0/P1外部依赖到最晚版本未验证时，相关Story必须BLOCKED",
                "测试环境证据不得冒充生产激活或应用市场准入证据",
                "真机验收只能由项目所有者明确确认",
            ],
        },
        "release_plan": release_entries,
    }


def render_plan(plan: dict[str, Any]) -> str:
    return yaml.safe_dump(
        plan,
        allow_unicode=True,
        sort_keys=False,
        width=120,
        default_flow_style=False,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "releases" / "PROGRAM_EXECUTION_PLAN.yaml")
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    rendered = render_plan(build_plan(ROOT))
    output = args.output if args.output.is_absolute() else ROOT / args.output
    if args.check:
        if not output.exists():
            print(f"PROGRAM_PLAN_ERROR: missing {output.relative_to(ROOT)}")
            return 1
        actual = output.read_text(encoding="utf-8")
        if actual != rendered:
            print(f"PROGRAM_PLAN_ERROR: generated plan drift: {output.relative_to(ROOT)}")
            return 1
        print("PROGRAM_PLAN_PASS: generated plan is deterministic and current")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(rendered, encoding="utf-8", newline="\n")
    print(output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
