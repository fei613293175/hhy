#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import hashlib
import re
import sys


ROOT = Path(__file__).resolve().parents[1]


def read_csv(relative: str) -> list[dict[str, str]]:
    with (ROOT / relative).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


catalog_tables = {row["表名"] for row in read_csv("catalogs/data_tables.csv")}
dictionary_tables = {row["表名"] for row in read_csv("database/schema_dictionary.csv")}
root_migrations = sorted((ROOT / "database/migrations").glob("V*.sql"))
runtime_dir = ROOT / "services/backend/boot/src/main/resources/db/migration"
runtime_migrations = sorted(runtime_dir.glob("V*.sql"))
migration_sql = "\n".join(path.read_text(encoding="utf-8") for path in root_migrations)
created_tables = set(re.findall(r"CREATE TABLE(?: IF NOT EXISTS)?\s+hhy\.([a-z0-9_]+)", migration_sql, re.IGNORECASE))

errors: list[str] = []
actual_migrations = [path.name.split("__", 1)[0] for path in root_migrations]
latest_migration = max((int(version.removeprefix("V")) for version in actual_migrations), default=0)
expected_migrations = [f"V{index:03d}" for index in range(1, latest_migration + 1)]
if actual_migrations != expected_migrations:
    errors.append(f"migration chain expected={expected_migrations} actual={actual_migrations}")
if "V010__p00_event_ledger_invariants.sql" not in {path.name for path in root_migrations}:
    errors.append("migration chain is missing V010 P00 event/ledger invariants")
if "V012__r01_admin_security_invariants.sql" not in {path.name for path in root_migrations}:
    errors.append("migration chain is missing V012 R01 admin security invariants")
if "V013__r01_admin_self_rbac.sql" not in {path.name for path in root_migrations}:
    errors.append("migration chain is missing V013 R01 administrator self-service RBAC")
if "V014__r01_idempotency_scope_capacity.sql" not in {path.name for path in root_migrations}:
    errors.append("migration chain is missing V014 R01 idempotency scope capacity")
if "V015__r01_totp_replay_guard.sql" not in {path.name for path in root_migrations}:
    errors.append("migration chain is missing V015 R01 TOTP replay guard")
if len(catalog_tables) != 200:
    errors.append(f"catalog table count expected=200 actual={len(catalog_tables)}")
if catalog_tables != dictionary_tables:
    errors.append(f"dictionary drift missing={sorted(catalog_tables - dictionary_tables)[:10]} extra={sorted(dictionary_tables - catalog_tables)[:10]}")
if catalog_tables != created_tables:
    errors.append(f"migration drift missing={sorted(catalog_tables - created_tables)[:10]} extra={sorted(created_tables - catalog_tables)[:10]}")
if [path.name for path in root_migrations] != [path.name for path in runtime_migrations]:
    errors.append("runtime migration file set differs from database/migrations")
else:
    for source, target in zip(root_migrations, runtime_migrations):
        if sha256(source) != sha256(target):
            errors.append(f"runtime migration hash drift: {target.name}")
verification = (ROOT / "database/verification/verify_baseline.sql").read_text(encoding="utf-8")
if "v_count NOT IN (198,199,200)" not in verification:
    errors.append("verify_baseline.sql does not preserve 198-to-200 migration compatibility")
migration_smoke = (ROOT / "scripts/run_postgres_migration_smoke.sh").read_text(encoding="utf-8")
if '"$TABLE_COUNT" == "200"' not in migration_smoke:
    errors.append("current migration smoke does not enforce exactly 200 tables")
if "assert_balanced_transaction" not in verification or "balance_snapshots" not in verification:
    errors.append("verify_baseline.sql does not enforce P00 accounting/snapshot invariants")

p00_required_files = (
    "database/migrations/V010__p00_event_ledger_invariants.sql",
    "database/rollback/U010__p00_event_ledger_invariants.sql",
    "database/tests/p00_event_ledger_invariants.sql",
    "scripts/run_p00_database_invariants.sh",
    "docs/01-architecture/adr/ADR-007-P00事件账务不变量收口.md",
    "docs/03-continuity/change-requests/CR-0006-P00事件账务不变量收口.md",
)
for relative in p00_required_files:
    if not (ROOT / relative).is_file():
        errors.append(f"missing P00 database closure asset: {relative}")

p00_migration = (ROOT / p00_required_files[0]).read_text(encoding="utf-8")
for marker in (
    "guard_outbox_event_mutation",
    "claim_inbox_message",
    "guard_ledger_account_mutation",
    "guard_accounting_entry_insert",
    "ACCOUNTING_REVERSAL_MISMATCH",
    "prevent_immutable_mutation",
    "guard_balance_snapshot",
    "ex_reconciliation_runs_active_window",
    "record_platform_status_history",
):
    if marker not in p00_migration:
        errors.append(f"V010 missing P00 invariant marker: {marker}")

r01_required_files = (
    "database/migrations/V012__r01_admin_security_invariants.sql",
    "database/rollback/U012__r01_admin_security_invariants.sql",
    "database/tests/r01_admin_security_invariants.sql",
    "database/migrations/V013__r01_admin_self_rbac.sql",
    "database/rollback/U013__r01_admin_self_rbac.sql",
    "database/tests/r01_admin_self_rbac.sql",
    "database/migrations/V014__r01_idempotency_scope_capacity.sql",
    "database/rollback/U014__r01_idempotency_scope_capacity.sql",
    "database/migrations/V015__r01_totp_replay_guard.sql",
    "database/rollback/U015__r01_totp_replay_guard.sql",
    "scripts/run_r01_database_invariants.sh",
    "scripts/bootstrap-admin.sh",
    "docs/01-architecture/adr/ADR-008-R01管理员认证安全不变量.md",
)
for relative in r01_required_files:
    if not (ROOT / relative).is_file():
        errors.append(f"missing R01 database closure asset: {relative}")

r01_migration = (ROOT / r01_required_files[0]).read_text(encoding="utf-8")
for marker in (
    "ck_admin_users_legacy_mfa_secret_empty",
    "guard_admin_session_mutation",
    "uq_admin_sessions_refresh_hash",
    "guard_admin_mfa_method_mutation",
    "consume_admin_recovery_code",
    "trg_admin_login_logs_immutable",
):
    if marker not in r01_migration:
        errors.append(f"V012 missing R01 invariant marker: {marker}")

r01_rbac_migration = (ROOT / "database/migrations/V013__r01_admin_self_rbac.sql").read_text(encoding="utf-8")
for marker in ("admin.self.read", "admin.self.security", "SUPER_ADMIN", "ON CONFLICT"):
    if marker not in r01_rbac_migration:
        errors.append(f"V013 missing R01 RBAC marker: {marker}")

r01_scope_migration = (ROOT / "database/migrations/V014__r01_idempotency_scope_capacity.sql").read_text(encoding="utf-8")
for marker in ("idempotency_records", "scope TYPE varchar(128)", "HMAC-SHA256"):
    if marker not in r01_scope_migration:
        errors.append(f"V014 missing R01 idempotency scope marker: {marker}")

r01_totp_replay_migration = (ROOT / "database/migrations/V015__r01_totp_replay_guard.sql").read_text(encoding="utf-8")
for marker in ("last_accepted_step", "RFC 6238", "nonnegative"):
    if marker not in r01_totp_replay_migration:
        errors.append(f"V015 missing R01 TOTP replay marker: {marker}")

bootstrap_admin = (ROOT / "scripts/bootstrap-admin.sh").read_text(encoding="utf-8")
for marker in (
    "HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH",
    "HHY_BOOTSTRAP_ADMIN_CONFIRM",
    "ADMIN_BOOTSTRAP_CREDENTIAL_CONFLICT",
    "ON CONFLICT (admin_id, role_id) DO NOTHING",
):
    if marker not in bootstrap_admin:
        errors.append(f"administrator bootstrap is missing safety marker: {marker}")
if "\\getenv bootstrap_hash" not in bootstrap_admin:
    errors.append("administrator bootstrap must not expose the BCrypt hash in process arguments")
if 'HHY_BOOTSTRAP_ADMIN_PASSWORD:-' not in bootstrap_admin:
    errors.append("administrator bootstrap must explicitly reject a plaintext password variable")

state_machines = (ROOT / "database/state_machines.yaml").read_text(encoding="utf-8")
for marker in ("ADMIN_SESSION_MFA_LEVEL", "ADMIN_MFA_METHOD_STATUS"):
    if marker not in state_machines:
        errors.append(f"state machine catalog missing R01 marker: {marker}")
for event in ("CANCEL_ENROLLMENT", "REENROLL"):
    transition = re.search(
        rf"event: {event}\n(?P<body>.*?)(?=\n  - from:|\n- code:|\Z)",
        state_machines,
        re.DOTALL,
    )
    if transition is None or "write_admin_operation_log" not in transition.group("body"):
        errors.append(f"R01 MFA transition must write operation audit: {event}")

r12_required_files = (
    "database/migrations/V039__r12_publish_management_invariants.sql",
    "services/backend/boot/src/main/resources/db/migration/V039__r12_publish_management_invariants.sql",
    "database/rollback/U039__r12_publish_management_invariants.sql",
    "database/tests/r12_publish_management_invariants.sql",
    "database/migrations/V040__r12_review_escalation.sql",
    "services/backend/boot/src/main/resources/db/migration/V040__r12_review_escalation.sql",
    "database/rollback/U040__r12_review_escalation.sql",
    "database/tests/r12_review_escalation.sql",
    "scripts/check_content_state_machine_projection.py",
    "scripts/run_r12_database_invariants.sh",
    "database/migrations/V041__r12_review_permission_alignment.sql",
    "services/backend/boot/src/main/resources/db/migration/V041__r12_review_permission_alignment.sql",
    "database/rollback/U041__r12_review_permission_alignment.sql",
    "database/tests/r12_review_permission_alignment.sql",
)
for relative in r12_required_files:
    if not (ROOT / relative).is_file():
        errors.append(f"missing R12 database closure asset: {relative}")

if all((ROOT / relative).is_file() for relative in r12_required_files):
    r12_migration = (ROOT / r12_required_files[0]).read_text(encoding="utf-8")
    for marker in (
        "R12_DIRTY_UPGRADE_CONTENT_DETAIL_CARDINALITY",
        "R12_DIRTY_UPGRADE_ILLEGAL_STATUS_HISTORY_EDGE",
        "R12_DIRTY_UPGRADE_INVALID_CONTENT_CONTACT",
        "R12_DIRTY_UPGRADE_INVALID_CONTENT_MEDIA",
        "R12_DIRTY_UPGRADE_DUPLICATE_CONTACT_ORDER",
        "R12_DIRTY_UPGRADE_DUPLICATE_CONTACT_CHANNEL",
        "R12_DIRTY_UPGRADE_DUPLICATE_CONTENT_MEDIA",
        "R12_DIRTY_UPGRADE_INVALID_CONTENT_VERSION_SEQUENCE",
        "R12_DIRTY_UPGRADE_REVIEW_VERSION_UNBINDABLE",
        "-- BEGIN CONTENT_STATUS_SQL_PROJECTION",
        "-- END CONTENT_STATUS_SQL_PROJECTION",
        "guard_r12_content_post",
        "record_r12_content_write",
        "guard_r12_content_status_log",
        "guard_r12_content_version_insert",
        "guard_r12_content_review_insert",
        "enrich_r12_content_outbox",
        "prevent_r12_content_physical_delete",
        "assert_r12_content_commit",
        "transition_version",
        "snapshot_version_id",
        "command_id",
        "removed_at",
        "uq_r12_content_media_identity",
        "uq_r12_content_contact_channel",
        "uq_r12_content_contact_order",
    ):
        if marker not in r12_migration:
            errors.append(f"V039 missing R12 publish invariant marker: {marker}")

    r12_rollback = (ROOT / r12_required_files[2]).read_text(encoding="utf-8")
    if "DROP COLUMN" in r12_rollback.upper():
        errors.append("U039 must preserve R12 compatibility columns and business data")
    for marker in (
        "trg_r12_content_commit",
        "uq_r12_content_media_identity",
        "uq_r12_content_contact_channel",
        "uq_r12_content_contact_order",
        "ck_r12_content_review_action",
        "ck_r12_content_status_transition_version",
    ):
        if marker not in r12_rollback:
            errors.append(f"U039 missing R12 rollback marker: {marker}")

    r12_runner = (ROOT / r12_required_files[9]).read_text(encoding="utf-8")
    for marker in (
        "R12_DIRTY_UPGRADE_ATOMIC_MATRIX",
        "R12_PUBLISH_MANAGEMENT_INVARIANTS",
        "R12_U039_ROLLBACK_V039_REPLAY",
        "R12_DATABASE_OBJECTS",
        "R12_OPTIMISTIC_CONCURRENCY",
        "detail_cardinality",
        "unbindable_review_version",
    ):
        if marker not in r12_runner:
            errors.append(f"R12 database runner missing closure marker: {marker}")

    r12_escalation_migration = (ROOT / r12_required_files[4]).read_text(encoding="utf-8")
    for marker in (
        "uq_r12_content_review_escalation_snapshot",
        "R12_CONTENT_REVIEW_ESCALATE_REQUIRES_REVIEWING",
        "R12_CONTENT_REVIEW_ESCALATE_SNAPSHOT_NOT_LATEST",
        "R12_CONTENT_SECOND_REVIEWER_REQUIRED",
        "R12_CONTENT_REVIEW_ESCALATION_AUDIT_REQUIRED",
        "R12_CONTENT_REVIEW_ESCALATION_OUTBOX_REQUIRED",
        "content.review.escalated.v1",
        "commandId",
    ):
        if marker not in r12_escalation_migration:
            errors.append(f"V040 missing R12 escalation invariant marker: {marker}")

    r12_escalation_rollback = (ROOT / r12_required_files[6]).read_text(encoding="utf-8")
    for marker in (
        "R12_U040_ESCALATION_FACTS_PRESENT",
        "DROP TRIGGER IF EXISTS trg_r12_review_escalation_commit",
        "DROP INDEX IF EXISTS hhy.uq_r12_content_review_escalation_snapshot",
        "decision IN ('CLAIM','ASSIGN','APPROVE','REJECT')",
    ):
        if marker not in r12_escalation_rollback:
            errors.append(f"U040 missing R12 escalation rollback marker: {marker}")

    r12_escalation_test = (ROOT / r12_required_files[7]).read_text(encoding="utf-8")
    for marker in (
        "R12_ESCALATION_WITHOUT_PARENT_WRITE_ACCEPTED",
        "R12_ESCALATION_WITHOUT_AUDIT_ACCEPTED",
        "R12_ESCALATION_WITH_MISMATCHED_AUDIT_COMMAND_ACCEPTED",
        "R12_ESCALATION_WITH_MISMATCHED_AUDIT_VERSION_ACCEPTED",
        "R12_ESCALATION_WITH_FAILED_AUDIT_ACCEPTED",
        "R12_ESCALATION_WITHOUT_APPLICATION_OUTBOX_ACCEPTED",
        "R12_ESCALATION_WITH_MISMATCHED_OUTBOX_VERSION_ACCEPTED",
        "R12_DUPLICATE_SNAPSHOT_ESCALATION_ACCEPTED",
        "R12_FINAL_DECISION_WITHOUT_SECOND_ASSIGNMENT_ACCEPTED",
        "R12_WRONG_SECOND_REVIEWER_ACCEPTED",
        "R12_REVIEW_ESCALATION_INVARIANTS PASS",
    ):
        if marker not in r12_escalation_test:
            errors.append(f"R12 escalation test missing closure marker: {marker}")

    r12_permission_migration = (ROOT / r12_required_files[10]).read_text(encoding="utf-8")
    for marker in (
        "review.read",
        "review.decide",
        "review.assign",
        "report.read",
        "appeal.read",
        "R12_REVIEW_MANAGER_PERMISSION_MAPPING_INCOMPLETE",
    ):
        if marker not in r12_permission_migration:
            errors.append(f"V041 missing R12 review permission marker: {marker}")

    r12_permission_rollback = (ROOT / r12_required_files[12]).read_text(encoding="utf-8")
    if "R12_U041_INDEPENDENT_GRANULAR_GRANTS_PRESENT" not in r12_permission_rollback:
        errors.append("U041 must preserve independently assigned granular review grants")

    r12_permission_test = (ROOT / r12_required_files[13]).read_text(encoding="utf-8")
    if "R12_REVIEW_PERMISSION_ALIGNMENT PASS" not in r12_permission_test:
        errors.append("R12 permission alignment test is missing its PASS marker")

    for marker in (
        'migration_number >= 39',
        "V039__r12_publish_management_invariants.sql",
        "run_r12_database_invariants.sh",
    ):
        if marker not in migration_smoke:
            errors.append(f"migration smoke missing R12 chain marker: {marker}")

if errors:
    print("DB_SCHEMA_FAIL")
    print("\n".join(errors))
    sys.exit(1)
print(f"DB_SCHEMA_OK tables={len(catalog_tables)} migrations={len(root_migrations)} runtime_hashes=PASS")
