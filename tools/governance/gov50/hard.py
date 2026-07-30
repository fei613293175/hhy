from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path
from typing import Any

from .tasks import FUNDS, SECURITY, load_task_specs

FINANCIAL_DOMAINS = {"支付", "账务", "提现", "红包", "奖励", "推广", "订单", "会员", "道具", "任务"}
CLOSURE_PROFILES = {"freeze", "candidate", "release"}


def _catalog(repo: Path) -> dict[str, list[dict[str, str]]]:
    path = repo / "catalogs" / "test_cases.csv"
    if not path.is_file():
        return {}
    with path.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    result: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        req = (row.get("需求ID") or "").strip()
        if req:
            result[req].append(row)
    return dict(result)


def _category(row: dict[str, str]) -> str:
    value = " ".join([row.get("测试ID") or "", row.get("类型") or "", row.get("测试标题") or ""]).upper()
    for category in ("HAPPY", "REJECT", "IDEMPOTENT", "CONCURRENCY", "RECOVERY", "SECURITY"):
        if category in value:
            return category
    if "安全" in value or "隐私" in value:
        return "SECURITY"
    return "OTHER"


def _task_requirements(spec: dict[str, Any], specs: dict[str, dict[str, Any]]) -> list[str]:
    reqs = [str(v) for v in spec.get("requirements") or []]
    if reqs:
        return reqs
    if spec.get("kind") in {"recovery", "release_close"}:
        release = spec.get("release")
        result: list[str] = []
        for row in specs.values():
            if row.get("release") == release:
                result.extend(str(v) for v in row.get("requirements") or [])
        return list(dict.fromkeys(result))
    return []


def _matches_words(text: str, words: tuple[str, ...]) -> bool:
    low = text.lower()
    return any(word.lower() in low for word in words)


def _automation_errors(repo: Path, rows: list[dict[str, str]], required: set[str]) -> list[str]:
    errors: list[str] = []
    for row in rows:
        if _category(row) not in required:
            continue
        raw = (row.get("自动化路径") or "").strip()
        if not raw or raw.startswith(("http://", "https://")) or " / " in raw:
            errors.append(f"{row.get('测试ID')}: missing repository automation path")
            continue
        path = repo / raw
        if not path.exists():
            errors.append(f"{row.get('测试ID')}: automation path missing: {raw}")
        elif path.is_file() and path.stat().st_size == 0:
            errors.append(f"{row.get('测试ID')}: automation adapter empty: {raw}")
    return errors


def run_hard_protection(repo: Path, task_id: str, profile: str) -> dict[str, Any]:
    specs = load_task_specs(repo)
    spec = specs[task_id]
    catalog = _catalog(repo)
    risks = set(spec.get("risks") or [])
    requirements = _task_requirements(spec, specs)
    errors: list[str] = []
    details: dict[str, Any] = {}

    if "funds" in risks:
        relevant: list[str] = []
        for req in requirements:
            rows = catalog.get(req) or []
            text = " ".join([req] + [row.get("领域") or "" for row in rows] + [row.get("测试标题") or "" for row in rows])
            if any((row.get("领域") or "") in FINANCIAL_DOMAINS for row in rows) or _matches_words(text, FUNDS):
                relevant.append(req)
        if not relevant and spec.get("release"):
            relevant = [req for req, rows in catalog.items() if any((row.get("计划版本") or "") == spec["release"] and (row.get("领域") or "") in FINANCIAL_DOMAINS for row in rows)]
        relevant = sorted(set(relevant))
        details["funds_requirements"] = relevant
        if not relevant:
            errors.append("funds risk is active but no source-backed financial requirement/test contract was resolved")
        for req in relevant:
            rows = catalog.get(req) or []
            categories = {_category(row) for row in rows}
            for required in {"HAPPY", "REJECT", "IDEMPOTENT"}:
                if required not in categories:
                    errors.append(f"{req}: missing {required} contract")
            if ({"CONCURRENCY", "RECOVERY"} & categories) and not {"CONCURRENCY", "RECOVERY"}.issubset(categories):
                errors.append(f"{req}: CONCURRENCY and RECOVERY must be paired")
            if any((row.get("状态") or "").upper() in {"DISABLED", "SKIPPED", "DELETED"} for row in rows):
                errors.append(f"{req}: financial test disabled/skipped/deleted")
            if profile in CLOSURE_PROFILES:
                required = {"HAPPY", "REJECT", "IDEMPOTENT"} | ({"CONCURRENCY", "RECOVERY"} & categories)
                errors.extend(_automation_errors(repo, rows, required))

    if "security" in risks:
        relevant = []
        for req in requirements:
            rows = catalog.get(req) or []
            text = " ".join([req] + [row.get("领域") or "" for row in rows] + [row.get("测试标题") or "" for row in rows])
            if rows and (_matches_words(text, SECURITY) or any(_category(row) == "SECURITY" for row in rows)):
                relevant.append(req)
        if not relevant:
            relevant = [req for req in requirements if catalog.get(req)]
        relevant = sorted(set(relevant))
        details["security_requirements"] = relevant
        # Security is also attached to broad backend tasks. No resolved requirements means
        # the task-level hard contract is not applicable; release-level checks still scan all.
        for req in relevant:
            rows = catalog[req]
            categories = {_category(row) for row in rows}
            if "REJECT" not in categories:
                errors.append(f"{req}: missing rejection/authorization contract")
            if any((row.get("状态") or "").upper() in {"DISABLED", "SKIPPED", "DELETED"} for row in rows):
                errors.append(f"{req}: security test disabled/skipped/deleted")
            if profile in CLOSURE_PROFILES:
                required = {"REJECT"}
                if "SECURITY" in categories:
                    required.add("SECURITY")
                errors.extend(_automation_errors(repo, rows, required))

    return {
        "schema": "hhy.hard-protection/v5.0",
        "status": "PASS" if not errors else "FAIL",
        "task_id": task_id,
        "release": spec.get("release"),
        "profile": profile,
        "risks": sorted(risks),
        "details": details,
        "errors": sorted(set(errors)),
    }


def run_release_hard_protection(repo: Path, release: str, profile: str) -> dict[str, Any]:
    specs = load_task_specs(repo)
    task_ids = [task_id for task_id, spec in specs.items() if spec.get("release") == release and ({"funds", "security"} & set(spec.get("risks") or []))]
    results = [run_hard_protection(repo, task_id, profile) for task_id in task_ids]
    errors = [f"{row['task_id']}: {error}" for row in results for error in row.get("errors") or []]
    return {"schema": "hhy.release-hard-protection/v5.0", "status": "PASS" if not errors else "FAIL", "release": release, "profile": profile, "task_count": len(task_ids), "errors": errors, "results": results}
