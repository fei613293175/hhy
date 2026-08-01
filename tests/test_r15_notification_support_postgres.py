from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator
from urllib.parse import quote, unquote, urlparse, urlunparse
import os
import re
import uuid

import yaml


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V048__r15_notification_support_invariants.sql"
RUNTIME = (
    ROOT
    / "services/backend/boot/src/main/resources/db/migration"
    / "V048__r15_notification_support_invariants.sql"
)
ROLLBACK = ROOT / "database/rollback/U048__r15_notification_support_invariants_DEV_ONLY.sql"
INVARIANTS = ROOT / "database/tests/r15_notification_support_invariants.sql"
STATE_MACHINES = ROOT / "database/state_machines.yaml"
MIGRATIONS = ROOT / "database/migrations"


def _migration_files(max_version: int) -> list[Path]:
    result: list[Path] = []
    for path in sorted(MIGRATIONS.glob("V*.sql")):
        version = int(path.name.split("__", 1)[0].removeprefix("V"))
        if version <= max_version:
            result.append(path)
    assert [path.name.split("__", 1)[0] for path in result] == [
        f"V{version:03d}" for version in range(1, max_version + 1)
    ]
    return result


def _db_url_from_env() -> str:
    url = os.environ.get("DATABASE_URL") or os.environ.get("HHY_DB_MIGRATION_TEST_URL")
    assert url, "DATABASE_URL or HHY_DB_MIGRATION_TEST_URL is required for R15 PostgreSQL tests"

    user = os.environ.get("HHY_DB_MIGRATION_TEST_USER")
    password = os.environ.get("HHY_DB_MIGRATION_TEST_PASSWORD")
    if user is None and password is None:
        return url

    parsed = urlparse(url)
    assert parsed.scheme in {"postgres", "postgresql"}, parsed.scheme
    host = parsed.hostname or "localhost"
    port = f":{parsed.port}" if parsed.port else ""
    credentials = quote(user or unquote(parsed.username or ""), safe="")
    if password is not None:
        credentials += ":" + quote(password, safe="")
    elif parsed.password is not None:
        credentials += ":" + quote(unquote(parsed.password), safe="")
    netloc = f"{credentials}@{host}{port}" if credentials else f"{host}{port}"
    return urlunparse(parsed._replace(netloc=netloc))


def _url_for_database(url: str, database: str) -> str:
    parsed = urlparse(url)
    assert parsed.scheme in {"postgres", "postgresql"}, parsed.scheme
    return urlunparse(parsed._replace(path="/" + quote(database, safe="")))


def _admin_url(url: str) -> str:
    return _url_for_database(url, "postgres")


def _connect(url: str) -> Any:
    try:
        import psycopg2
    except ImportError:
        psycopg2 = None

    if psycopg2 is not None:
        return psycopg2.connect(url)

    try:
        import psycopg
    except ImportError as exc:
        raise AssertionError("psycopg2 or psycopg is required for R15 PostgreSQL tests") from exc
    return psycopg.connect(url)


def _execute_sql(url: str, sql: str) -> None:
    connection = _connect(url)
    try:
        with connection.cursor() as cursor:
            cursor.execute(sql)
        connection.commit()
    finally:
        connection.close()


def _execute_file(url: str, path: Path) -> None:
    _execute_sql(url, path.read_text(encoding="utf-8"))


def _run_migrations(url: str, max_version: int) -> None:
    for path in _migration_files(max_version):
        _execute_file(url, path)


@contextmanager
def _temporary_database(base_url: str, suffix: str) -> Iterator[str]:
    db_name = f"hhy_r15_{suffix}_{uuid.uuid4().hex[:12]}"
    assert re.fullmatch(r"[a-z0-9_]+", db_name), db_name
    admin = _connect(_admin_url(base_url))
    admin.autocommit = True
    try:
        with admin.cursor() as cursor:
            cursor.execute(f'CREATE DATABASE "{db_name}"')
        yield _url_for_database(base_url, db_name)
    finally:
        with admin.cursor() as cursor:
            cursor.execute(
                "SELECT pg_terminate_backend(pid) "
                "FROM pg_stat_activity WHERE datname = %s",
                (db_name,),
            )
            cursor.execute(f'DROP DATABASE IF EXISTS "{db_name}"')
        admin.close()


def _seed_v047_legacy_rows(url: str) -> None:
    _execute_sql(
        url,
        """
        SET search_path TO hhy, public;

        INSERT INTO hhy.users(phone, status, invite_code)
        VALUES ('13900000001', 'ACTIVE', 'R15LEGACY00000001');

        INSERT INTO hhy.notifications(user_id, type, title, body, biz_type, biz_id)
        VALUES (1, 'SYSTEM', 'legacy notification', 'body', 'legacy', 15);

        INSERT INTO hhy.notification_deliveries(notification_id, channel, status, provider_msg_id)
        VALUES (1, 'PUSH', 'SENT', 'legacy-provider-message');

        INSERT INTO hhy.announcements(title, content, status, published_at)
        VALUES ('legacy announcement', 'content', 'PUBLISHED', clock_timestamp());

        INSERT INTO hhy.announcement_reads(announcement_id, user_id, read_at)
        VALUES (1, 1, clock_timestamp());

        INSERT INTO hhy.support_tickets(ticket_no, user_id, type, status)
        VALUES
          ('R15-LEGACY-OPEN', 1, 'QUESTION', 'OPEN'),
          ('R15-LEGACY-PENDING', 1, 'QUESTION', 'PENDING'),
          ('R15-LEGACY-RESOLVED', 1, 'QUESTION', 'RESOLVED'),
          ('R15-LEGACY-CLOSED', 1, 'QUESTION', 'CLOSED');

        INSERT INTO hhy.ticket_status_logs(ticket_id, from_status, to_status, operator)
        VALUES (3, 'PENDING', 'RESOLVED', 'legacy-agent'),
               (4, 'RESOLVED', 'CLOSED', 'legacy-agent');
        """,
    )


def _assert_legacy_rows_preserved(url: str) -> None:
    connection = _connect(url)
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                  (SELECT count(*) FROM hhy.notifications
                   WHERE legacy_without_idempotency AND idempotency_key IS NULL AND request_hash IS NULL),
                  (SELECT count(*) FROM hhy.notification_deliveries
                   WHERE legacy_without_idempotency AND idempotency_key IS NULL AND request_hash IS NULL),
                  (SELECT count(*) FROM hhy.announcement_reads
                   WHERE legacy_without_idempotency AND idempotency_key IS NULL AND request_hash IS NULL)
                """
            )
            assert cursor.fetchone() == (1, 1, 1)
    finally:
        connection.close()


def test_state_machine_keeps_mfa_and_support_as_top_level_siblings() -> None:
    catalog = yaml.safe_load(STATE_MACHINES.read_text(encoding="utf-8"))
    machines = catalog["machines"]
    by_code = {machine["code"]: machine for machine in machines}

    assert {"ADMIN_MFA_METHOD_STATUS", "SUPPORT_TICKET_STATUS"} <= set(by_code)
    mfa_events = {transition["event"] for transition in by_code["ADMIN_MFA_METHOD_STATUS"]["transitions"]}
    assert mfa_events == {"CONFIRM", "CANCEL_ENROLLMENT", "DISABLE", "REENROLL"}

    support = by_code["SUPPORT_TICKET_STATUS"]
    assert support["initial"] == "OPEN"
    assert support["terminal"] == ["CLOSED"]
    support_edges = {
        (transition["from"], transition["to"], transition["event"])
        for transition in support["transitions"]
    }
    assert support_edges == {
        ("OPEN", "PENDING", "ASSIGN"),
        ("PENDING", "RESOLVED", "RESOLVE"),
        ("RESOLVED", "PENDING", "REOPEN"),
        ("RESOLVED", "CLOSED", "CLOSE"),
    }


def test_v048_static_contract_matches_r15_recovery_requirements() -> None:
    sql = SOURCE.read_text(encoding="utf-8")
    runtime = RUNTIME.read_text(encoding="utf-8")
    invariant_sql = INVARIANTS.read_text(encoding="utf-8")
    rollback = ROLLBACK.read_text(encoding="utf-8")
    test_source = Path(__file__).read_text(encoding="utf-8")

    assert sql == runtime
    assert "pytest" + ".skip" not in test_source
    assert "HHY_R15" + "_POSTGRES_DSN" not in test_source
    assert "idempotency_key IS NOT NULL" in sql
    assert "request_hash IS NOT NULL" in sql
    assert "R15_NOTIFICATION_LEGACY_MARKER_FORBIDDEN" in sql
    assert "R15_SUPPORT_TICKET_INVALID_TRANSITION" in sql
    assert "(OLD.status = 'RESOLVED' AND NEW.status IN ('PENDING', 'CLOSED'))" in sql
    assert "OLD.status = 'OPEN' AND NEW.status = 'CLOSED'" not in sql
    assert "OLD.status = 'PENDING' AND NEW.status = 'CLOSED'" not in sql
    assert "trg_r15_support_tickets_status_history" in sql
    assert "R15_SUPPORT_TICKET_PENDING_TO_CLOSED_WAS_ACCEPTED" in invariant_sql
    assert "R15_NOTIFICATION_SUPPORT_INVARIANTS PASS" in invariant_sql
    assert "R15_U048_BUSINESS_FACTS_PRESENT" in rollback
    assert not re.search(r"(?im)^\s*(DELETE|TRUNCATE)\s+(FROM\s+)?hhy\.", rollback)


def test_postgresql_empty_and_v047_upgrade_paths_execute_r15_matrix() -> None:
    base_url = _db_url_from_env()

    with _temporary_database(base_url, "empty") as empty_url:
        _run_migrations(empty_url, 48)
        _execute_file(empty_url, INVARIANTS)

    with _temporary_database(base_url, "upgrade") as upgrade_url:
        _run_migrations(upgrade_url, 47)
        _seed_v047_legacy_rows(upgrade_url)
        _execute_file(upgrade_url, SOURCE)
        _assert_legacy_rows_preserved(upgrade_url)
        _execute_file(upgrade_url, INVARIANTS)
