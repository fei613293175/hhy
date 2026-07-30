#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config" / "owner-test-environment.yaml"


def fail(message: str) -> None:
    raise SystemExit(f"OWNER_TEST_ENVIRONMENT_INVALID: {message}")


def main() -> int:
    document = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    if document.get("schema_version") != 1 or document.get("status") != "ACTIVE":
        fail("schema or status drift")
    if document.get("public_api_url") != "https://api.orbexa.cc":
        fail("public API URL drift")
    active = document.get("active") or {}
    if active.get("database_container") != "hhy-owner-test-postgres":
        fail("persistent database container drift")
    if active.get("database_volume") != "hhy-owner-test-postgres-data":
        fail("persistent database volume drift")
    if active.get("docker_network") != "hhy-owner-test":
        fail("persistent network drift")
    if not re.fullmatch(r"127\.0\.0\.1:[1-9][0-9]{3,4}", str(active.get("loopback_upstream", ""))):
        fail("owner-test upstream must remain loopback-only")
    database_name = str(active.get("database_name", "")).lower()
    if any(token in database_name for token in ("smoke", "candidate", "staging")):
        fail("public owner-test database cannot be a candidate database")
    persistence = document.get("persistence") or {}
    required_true = {
        "preserve_across_releases",
        "application_upgrade_only",
        "pre_upgrade_snapshot_required",
        "forbid_database_recreate",
        "forbid_volume_replace",
        "forbid_down_with_volumes",
        "forbid_user_data_truncate",
    }
    if any(persistence.get(key) is not True for key in required_true):
        fail("persistence hard boundary is incomplete")
    if persistence.get("migration_mode") != "FORWARD_ONLY":
        fail("database migration mode must remain forward-only")
    separation = document.get("candidate_separation") or {}
    if separation.get("restore_owner_test_route_after_candidate") != "ALWAYS":
        fail("candidate completion must always restore owner-test route")
    if separation.get("restore_on_candidate_pass") is not True \
            or separation.get("restore_on_candidate_failure") is not True:
        fail("candidate PASS and FAIL restoration are both mandatory")
    if separation.get("maximum_route_lease_minutes") != 45:
        fail("candidate public route lease must remain bounded to 45 minutes")
    validation = document.get("public_validation") or {}
    if validation.get("ci_automation_enabled") is not False:
        fail("persistent public backend must disable CI automation")
    for relative in (
        separation.get("restore_script"),
        separation.get("promotion_script"),
    ):
        if not relative or not (ROOT / relative).is_file():
            fail(f"required owner-test operation script missing: {relative}")

    baseline = (ROOT / "docs" / "00-baseline" / "正式商业系统全局硬性开发边界.md").read_text(encoding="utf-8")
    runbook = (ROOT / "docs" / "07-operations" / "DEPLOYMENT_RUNBOOK.md").read_text(encoding="utf-8")
    for marker in (
        "`api.orbexa.cc` 常态必须指向独立的 `owner-test` 持久数据库",
        "候选结束无论 PASS、FAIL",
    ):
        if marker not in baseline:
            fail(f"global boundary missing marker: {marker}")
    for marker in (
        "hhy-owner-test-postgres-data",
        "scripts/restore_android_candidate_route.sh",
        "scripts/promote_owner_test_backend.sh",
    ):
        if marker not in runbook:
            fail(f"deployment runbook missing marker: {marker}")
    print("OWNER_TEST_ENVIRONMENT_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
