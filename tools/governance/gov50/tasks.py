from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .util import read_yaml, write_yaml

EXPECTED_SOURCE_TASKS = 264
EXPECTED_TASKS = 266
EXPECTED_FUTURE_TASKS = 144
FAILED_RECOVERY_TASK_ID = "TASK-R14-RECOVERY-001"
REPAIR_RECOVERY_TASK_ID = "TASK-R14-RECOVERY-002"
FORBIDDEN_ACTIVE_TERMS = (
    "change request", "change_request", "cr记录", "cr 记录", "session log", "session_log",
    "checkpoint", "continuity", "context pack", "current_status", "next_task",
    "attempt_exception", "attempt override", "attempt_override", "会话日志", "变更申请",
    "检查点", "持续开发记录", "问题登记", "无状态交接",
)

KIND_BY_ORDINAL = {
    1: "readiness",
    2: "database",
    3: "backend_contract",
    4: "client_ui",
    5: "quality",
    6: "staging_observability",
    7: "test_apk",
    8: "release_close",
}

FUNDS = (
    "支付", "付款", "提现", "红包", "账本", "余额", "佣金", "分销", "奖励", "会员", "订单",
    "退款", "补差价", "企业付款", "ledger", "payment", "withdraw", "wallet", "redpacket",
    "commission", "reward", "order", "billing", "settlement", "reconciliation",
)
SECURITY = (
    "登录", "注册", "认证", "实名", "权限", "安全", "验证码", "密码", "风控", "隐私", "封禁",
    "auth", "security", "permission", "credential", "captcha", "password", "privacy", "risk",
)
SHARED_UI = ("theme", "design token", "design-token", "组件库", "基础组件", "全局样式", "导航壳")
SECRET = ("密钥", "证书", "签名", "secret", "credential", "access key", "api key", "token")
PRODUCTION = ("生产激活", "正式发布", "staging", "预发布", "部署", "回滚", "告警", "observability")


def _release_number(value: str) -> int:
    if value == "P00":
        return 0
    return int(value[1:])


def _strip_legacy(items: list[Any]) -> list[str]:
    result: list[str] = []
    for item in items or []:
        text = str(item).strip()
        low = text.lower().replace("`", "")
        if any(term in low for term in FORBIDDEN_ACTIVE_TERMS):
            continue
        if text and text not in result:
            result.append(text)
    return result


def _clean_description(text: Any) -> str:
    value = str(text or "").strip()
    clauses = re.split(r"[；;。]", value)
    kept = [part.strip() for part in clauses if part.strip() and not any(t in part.lower() for t in FORBIDDEN_ACTIVE_TERMS)]
    return "；".join(kept) or "按正式产品文档、合同、代码与测试完成本任务。"


def _bind_readiness_evidence(items: list[str], release: str) -> list[str]:
    output = f"releases/{release}/evidence/project-doctor-v1.2.2.json"
    return [
        f"{item} --json-out {output}"
        if "check_v122_documentation.py" in item and "--json-out" not in item
        else item
        for item in items
    ]


def _risks(kind: str, raw: dict[str, Any]) -> list[str]:
    text = " ".join(
        [str(raw.get("title") or ""), str(raw.get("description") or "")]
        + [str(v) for v in raw.get("requirements") or []]
        + [str(v) for v in raw.get("deliverables") or []]
        + [str(v) for v in raw.get("acceptance") or []]
    ).lower()
    risks = {"secrets"}
    if kind == "database":
        risks.add("database")
    if kind == "backend_contract":
        risks.update({"contract", "security"})
    if kind == "client_ui":
        risks.add("ui")
    if kind in {"staging_observability", "release_close"}:
        risks.add("production")
    if kind == "test_apk":
        risks.update({"apk_identity", "ui"})
    if any(word in text for word in FUNDS):
        risks.update({"funds", "security", "database", "contract"})
    if any(word in text for word in SECURITY):
        risks.add("security")
    if any(word in text for word in SHARED_UI):
        risks.update({"ui", "shared_ui_foundation"})
    if any(word in text for word in SECRET):
        risks.add("secrets")
    if any(word in text for word in PRODUCTION):
        risks.add("production")
    return sorted(risks)


def _paths(kind: str, release: str) -> list[str]:
    common = ["tests/**", "docs/**", "catalogs/**"]
    mapping = {
        "readiness": [f"releases/{release}/**", "docs/**", "catalogs/**", "contracts/**", "database/**"],
        "database": ["database/**", "services/backend/**", "contracts/**", *common],
        "backend_contract": ["services/backend/**", "contracts/**", "packages/api-client/**", "packages/domain-types/**", *common],
        "client_ui": ["apps/android/**", "apps/admin-web/**", "apps/h5/**", "packages/**", "design/**", *common],
        "quality": ["apps/**", "services/**", "database/**", "contracts/**", "packages/**", "scripts/**", *common],
        "staging_observability": ["infra/**", "services/**", "apps/**", "scripts/**", "config/**", *common],
        "test_apk": ["apps/android/**", "scripts/**", "config/android-automation.yaml", "tests/android/**", "artifacts/**", "docs/**"],
        "release_close": ["apps/**", "services/**", "database/**", "contracts/**", "packages/**", "tests/**", "scripts/**", "infra/**", "config/**", "docs/**", "catalogs/**", "design/**", "artifacts/**"],
    }
    return sorted(set(mapping[kind]))


def _acceptance_commands(kind: str, task_id: str, release: str, risks: list[str]) -> list[str]:
    # Worker-visible commands are product/read-only checks only. The authoritative
    # Governance Gate is executed later by the Orchestrator in a clean candidate
    # worktree, so a Worker never writes governance evidence or recursively calls it.
    commands: list[str] = []
    if kind == "readiness":
        commands.append(f"python3 tools/governance/hhy_governance.py product-readiness --release {release}")
    if "database" in risks:
        commands.append("python3 scripts/check_db_schema.py")
    if task_id == "TASK-R15-002":
        commands.extend([
            "mvn -q -f services/backend/pom.xml -DskipTests install",
            "mvn -q -f services/backend/pom.xml -pl boot -Dtest=R15NotificationSupportMigrationTest test",
        ])
    if "contract" in risks:
        commands.append("python3 scripts/check_api_contract.py")
    if "ui" in risks:
        commands.append("python3 scripts/check_ui_tokens.py")
    if kind == "staging_observability":
        release_script = f"scripts/check_{release.lower()}_observability.py"
        commands.append(f"python3 {release_script}")
    return list(dict.fromkeys(commands))


def _normalize_source_task(release: str, raw: dict[str, Any], source_path: str) -> dict[str, Any]:
    task_id = str(raw["id"])
    match = re.search(r"-(\d{3})$", task_id)
    ordinal = int(match.group(1)) if match else 0
    kind = KIND_BY_ORDINAL.get(ordinal, "quality")
    risks = _risks(kind, raw)
    # Release close is deliberately a bounded Worker task. It may repair release-wide
    # integration defects before the immutable freeze, but it never writes governance
    # state or candidate evidence. Candidate authorization remains Orchestrator-only.
    mode = "worker"
    depends = [str(v) for v in raw.get("depends_on") or []]
    relnum = _release_number(release)
    if ordinal == 1 and relnum >= 15:
        previous = f"R{relnum - 1:02d}"
        depends = ["TASK-R14-RECOVERY-001"] if release == "R15" else [f"TASK-{previous}-008"]
    objective = _clean_description(raw.get("description"))
    deliverables = _strip_legacy(raw.get("deliverables") or [])
    acceptance = _strip_legacy(raw.get("acceptance") or [])
    if kind == "readiness":
        deliverables = _bind_readiness_evidence(deliverables, release)
        acceptance = _bind_readiness_evidence(acceptance, release)
    if kind == "release_close":
        objective = (
            f"在 {release} 冻结前完成全部会修改源码的集成修复，使发布级文档、接口、数据库、"
            "UI、资金、安全、秘密与生产门禁可在同一候选 Commit 上通过。"
        )
        deliverables = [
            "可冻结的产品代码与自动化测试",
            "发布级缺陷的实际修复与可复核证据",
            "不包含 Candidate、Owner 或正式发布状态写入的 Freeze-ready 候选改动",
        ]
        acceptance = [
            "所有需要修改源码的审计均在冻结前完成",
            "独立 Freeze Gate 在候选 Commit 上全部通过",
            "通过后仅由 Orchestrator 将 Release 标记为 FREEZE_READY",
        ]
    return {
        "schema": "hhy.task-spec/v5.0",
        "id": task_id,
        "release": release,
        "ordinal": ordinal,
        "title": str(raw.get("title") or task_id).replace("与无状态交接", "与机器关闭").replace("无状态交接", "机器关闭"),
        "kind": kind,
        "mode": mode,
        "source": {"path": source_path, "original_id": task_id},
        "requirements": [str(v) for v in raw.get("requirements") or []],
        "depends_on": depends,
        "objective": objective,
        "deliverables": deliverables,
        "acceptance": acceptance,
        "allowed_paths": _paths(kind, release),
        "risks": risks,
        "acceptance_commands": _acceptance_commands(kind, task_id, release, risks),
        "maximum_attempts": 3,
        "owner_verification": "ASYNC_NON_BLOCKING_FOR_NEXT_RELEASE",
        "legacy_status": str(raw.get("status") or "UNKNOWN"),
    }


def generate_task_specs(repo: Path, out: Path) -> dict[str, dict[str, Any]]:
    out.mkdir(parents=True, exist_ok=True)
    for stale in out.glob("*.yaml"):
        stale.unlink()
    specs: dict[str, dict[str, Any]] = {}
    release_paths = sorted((repo / "releases").glob("*/TASKS.yaml"), key=lambda p: _release_number(p.parent.name))
    for path in release_paths:
        data = read_yaml(path) or {}
        release = str(data.get("release") or path.parent.name)
        for raw in data.get("tasks") or []:
            spec = _normalize_source_task(release, raw, path.relative_to(repo).as_posix())
            specs[spec["id"]] = spec
    if len(specs) != EXPECTED_SOURCE_TASKS:
        raise RuntimeError(f"expected {EXPECTED_SOURCE_TASKS} source tasks, found {len(specs)}")
    migration = {
        "schema": "hhy.task-spec/v5.0", "id": "CONTROL-GOV50-MIGRATION", "release": "CONTROL",
        "ordinal": 0, "title": "安装并激活 Governance V5.0 控制面", "kind": "control_migration",
        "mode": "orchestrator", "source": {"path": "PACKAGE", "original_id": None},
        "requirements": [], "depends_on": [],
        "objective": "以单一状态、有限重试、短会话 Worker 和独立 Gate 替换旧持续治理控制面。",
        "deliverables": ["治理安装提交", "迁移激活提交", "部署前验收报告"],
        "acceptance": ["内部治理门禁全部通过", "唯一活动任务为 TASK-R14-RECOVERY-001"],
        "allowed_paths": [], "risks": ["secrets", "security", "production"],
        "acceptance_commands": ["python3 tools/governance/hhy_governance.py doctor --ci"],
        "maximum_attempts": 3, "owner_verification": "NOT_APPLICABLE", "legacy_status": "NEW",
    }
    recovery = {
        "schema": "hhy.task-spec/v5.0", "id": "TASK-R14-RECOVERY-001", "release": "R14",
        "ordinal": 9, "title": "R14 候选失效止损、冻结重建与机器关闭恢复", "kind": "recovery",
        "mode": "worker", "source": {"path": "governance migration", "original_id": "TASK-R14-008"},
        "requirements": [], "depends_on": [],
        "objective": "对账旧候选后源码变化；在冻结前完成所有会修改源码的审计；形成新冻结 Commit、APK、模拟器、视觉及候选证据。",
        "deliverables": ["R14 差异与失效证据", "新冻结 Commit", "新 Candidate 证据", "可机器关闭的 R14"],
        "acceptance": ["旧候选仅作历史证据", "所有候选证据绑定同一冻结 Commit", "R14 达到 MACHINE_CLOSED 后只激活 R15"],
        "allowed_paths": ["apps/**", "services/**", "database/**", "contracts/**", "packages/**", "tests/**", "scripts/**", "infra/**", "config/**", "docs/**", "catalogs/**", "design/R14-UI-FROZEN/**", "artifacts/**", "releases/R14/**"],
        "risks": ["apk_identity", "contract", "database", "funds", "production", "security", "secrets", "ui"],
        "acceptance_commands": [
            "python3 scripts/check_api_contract.py",
            "python3 scripts/check_db_schema.py",
            "python3 scripts/check_ui_tokens.py",
            "python3 scripts/check_ui_visual_acceptance.py --release R14",
        ],
        "maximum_attempts": 3, "owner_verification": "ASYNC_NON_BLOCKING_FOR_NEXT_RELEASE", "legacy_status": "NEW",
    }
    specs[migration["id"]] = migration
    specs[recovery["id"]] = recovery
    for task_id, spec in sorted(specs.items()):
        write_yaml(out / f"{task_id}.yaml", spec)
    return specs


def load_task_specs(repo: Path) -> dict[str, dict[str, Any]]:
    specs: dict[str, dict[str, Any]] = {}
    root = repo / "governance" / "task_specs"
    for path in sorted(root.glob("*.yaml")):
        spec = read_yaml(path) or {}
        task_id = str(spec.get("id") or "")
        if not task_id:
            raise RuntimeError(f"task spec has no id: {path}")
        if task_id in specs:
            raise RuntimeError(f"duplicate task id: {task_id}")
        specs[task_id] = spec
    # The latest bounded R14 recovery task is the dependency for R15. The
    # Orchestrator persists this relationship in the task-spec file as well,
    # so repeated automatic recovery generations remain deterministic.
    recovery_ids = [
        task_id for task_id, spec in specs.items()
        if spec.get("release") == "R14" and spec.get("kind") == "recovery"
    ]
    recovery_ids.sort(key=lambda task_id: (int(str(task_id).rsplit("-", 1)[-1]) if str(task_id).rsplit("-", 1)[-1].isdigit() else -1, task_id))
    if recovery_ids and "TASK-R15-001" in specs:
        specs["TASK-R15-001"]["depends_on"] = [recovery_ids[-1]]
    return specs


def build_program_plan(specs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    releases = ["P00"] + [f"R{i:02d}" for i in range(1, 33)]
    return {
        "schema": "hhy.program-plan/v5.0",
        "program": "HHY_PRO_R01_R32",
        "release_order": releases,
        "final_release": "R32",
        "task_count": len(specs),
        "future_task_count_R15_R32": sum(1 for s in specs.values() if s.get("release", "").startswith("R") and 15 <= int(s["release"][1:]) <= 32),
        "maximum_attempts_per_task": 3,
        "adjacent_release_only": True,
        "release_tasks": {r: [s["id"] for s in sorted(specs.values(), key=lambda x: (x.get("ordinal", 0), x["id"])) if s.get("release") == r] for r in releases},
        "control_tasks": ["CONTROL-GOV50-MIGRATION"],
        "recovery_tasks": [
            task_id for task_id, spec in sorted(specs.items(), key=lambda item: (item[1].get("release", ""), item[1].get("ordinal", 0), item[0]))
            if spec.get("kind") == "recovery"
        ],
    }


def validate_specs(specs: dict[str, dict[str, Any]], plan: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    recovery_count = sum(1 for spec in specs.values() if spec.get("kind") == "recovery")
    expected_tasks = EXPECTED_TASKS + max(0, recovery_count - 1)
    if len(specs) != expected_tasks:
        errors.append(f"task_count expected {expected_tasks}, got {len(specs)}")
    future = sum(1 for s in specs.values() if s.get("release", "").startswith("R") and 15 <= int(s["release"][1:]) <= 32)
    if future != EXPECTED_FUTURE_TASKS:
        errors.append(f"R15-R32 task count expected {EXPECTED_FUTURE_TASKS}, got {future}")
    if plan.get("task_count") != len(specs):
        errors.append("program plan task_count mismatch")
    for task_id, spec in specs.items():
        if spec.get("maximum_attempts") != 3:
            errors.append(f"{task_id}: maximum_attempts must equal 3")
        text = str(spec).lower()
        for term in FORBIDDEN_ACTIVE_TERMS:
            if term in text:
                errors.append(f"{task_id}: legacy active term remains: {term}")
        for dep in spec.get("depends_on") or []:
            if dep not in specs:
                errors.append(f"{task_id}: missing dependency {dep}")
        supersedes = spec.get("supersedes")
        if supersedes is not None and supersedes not in specs:
            errors.append(f"{task_id}: missing superseded task {supersedes}")
        if spec.get("mode") == "worker" and not spec.get("allowed_paths"):
            errors.append(f"{task_id}: worker task has no allowed paths")
    # Acyclic dependency check.
    visiting: set[str] = set()
    visited: set[str] = set()
    def visit(node: str) -> None:
        if node in visited:
            return
        if node in visiting:
            errors.append(f"dependency cycle at {node}")
            return
        visiting.add(node)
        for dep in specs[node].get("depends_on") or []:
            visit(dep)
        visiting.remove(node)
        visited.add(node)
    for task_id in specs:
        visit(task_id)
    return sorted(set(errors))
