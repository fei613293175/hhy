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
if len(catalog_tables) != 198:
    errors.append(f"catalog table count expected=198 actual={len(catalog_tables)}")
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
if "v_count <> 198" not in verification:
    errors.append("verify_baseline.sql does not enforce 198 tables")
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

if errors:
    print("DB_SCHEMA_FAIL")
    print("\n".join(errors))
    sys.exit(1)
print(f"DB_SCHEMA_OK tables={len(catalog_tables)} migrations={len(root_migrations)} runtime_hashes=PASS")
