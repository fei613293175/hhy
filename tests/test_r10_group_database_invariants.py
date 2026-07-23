import os
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V036__r10_group_promotion_invariants.sql"
RUNTIME = (
    ROOT
    / "services/backend/boot/src/main/resources/db/migration/"
    / "V036__r10_group_promotion_invariants.sql"
)
ROLLBACK = ROOT / "database/rollback/U036__r10_group_promotion_invariants.sql"
INVARIANTS = ROOT / "database/tests/r10_group_promotion_invariants.sql"
RUNNER = ROOT / "scripts/run_r10_database_invariants.sh"
CONTAINER = ROOT / "scripts/run_r10_disposable_postgres_container.sh"


class R10GroupDatabaseSourceContractTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertTrue(SOURCE.is_file(), SOURCE)
        self.assertTrue(RUNTIME.is_file(), RUNTIME)
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_v036_declares_fail_closed_prerequisites_and_restrict_qr_fk(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "R10_GROUP_UNIQUENESS_PREREQUISITE_MISSING",
            "R10_IDEMPOTENCY_PREREQUISITE_MISSING",
            "R10_CONTENT_CONTACT_PREREQUISITE_MISSING",
            "R10_GROUP_QR_MEDIA_ORPHAN_REQUIRES_REVIEW",
            "R10_LEGACY_GROUP_DETAIL_MISSING_REQUIRES_REVIEW",
            "R10_LEGACY_JOIN_PASSWORD_NON_GROUP_REQUIRES_REVIEW",
            "R10_LEGACY_JOIN_PASSWORD_DUPLICATE_REQUIRES_REVIEW",
            "R10_LEGACY_ONLINE_GROUP_ENTRY_CHANNEL_MISSING_REQUIRES_REVIEW",
            "R10_LEGACY_ONLINE_GROUP_OWNER_CONTACT_MISSING_REQUIRES_REVIEW",
        ):
            with self.subTest(marker=marker):
                self.assertRegex(sql, rf"RAISE\s+EXCEPTION\s+'{marker}'")
        self.assertRegex(
            sql,
            r"(?is)FOREIGN\s+KEY\s*\(\s*qr_media_id\s*\)\s*"
            r"REFERENCES\s+hhy\.media_objects\s*\(\s*id\s*\)\s*"
            r"ON\s+DELETE\s+RESTRICT",
        )
        self.assertRegex(sql, r"(?is)ADD\s+CONSTRAINT\s+ck_r10_\w+.*?NOT\s+VALID")
        deferred_triggers = re.findall(
            r"(?is)CREATE\s+CONSTRAINT\s+TRIGGER\s+trg_r10_\w+.*?;", sql
        )
        for table in ("content_posts", "group_details", "content_contacts"):
            with self.subTest(deferred_final_state_table=table):
                self.assertTrue(
                    any(
                        re.search(rf"(?is)ON\s+hhy\.{table}\b", trigger)
                        and re.search(
                            r"(?is)DEFERRABLE\s+INITIALLY\s+DEFERRED", trigger
                        )
                        for trigger in deferred_triggers
                    ),
                    f"missing deferred R10 final-state trigger on hhy.{table}",
                )
        self.assertNotRegex(sql, r"(?im)^\s*(DELETE|UPDATE|TRUNCATE)\s+(FROM\s+)?hhy\.")

    def test_u036_drops_every_v036_object_and_never_mutates_business_rows(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        rollback = ROLLBACK.read_text(encoding="utf-8")
        created = {
            "constraint": set(
                re.findall(r"(?i)ADD\s+CONSTRAINT\s+(\w*r10\w*)", source)
            ),
            "index": set(
                re.findall(
                    r"(?i)CREATE\s+(?:UNIQUE\s+)?INDEX\s+(\w*r10\w*)",
                    source,
                )
            ),
            "trigger": set(
                re.findall(
                    r"(?i)CREATE\s+(?:CONSTRAINT\s+)?TRIGGER\s+(\w*r10\w*)",
                    source,
                )
            ),
            "function": set(
                re.findall(
                    r"(?i)CREATE\s+OR\s+REPLACE\s+FUNCTION\s+hhy\.(\w*r10\w*)",
                    source,
                )
            ),
        }
        for object_type, names in created.items():
            with self.subTest(object_type=object_type):
                self.assertTrue(names, f"V036 must create at least one R10 {object_type}")
        for name in created["constraint"]:
            self.assertRegex(
                rollback,
                rf"(?i)DROP\s+CONSTRAINT\s+IF\s+EXISTS\s+{re.escape(name)}\b",
            )
        for name in created["index"]:
            self.assertRegex(
                rollback,
                rf"(?i)DROP\s+INDEX\s+IF\s+EXISTS\s+hhy\.{re.escape(name)}\b",
            )
        for name in created["trigger"]:
            self.assertRegex(
                rollback,
                rf"(?i)DROP\s+TRIGGER\s+IF\s+EXISTS\s+{re.escape(name)}\b",
            )
        for name in created["function"]:
            self.assertRegex(
                rollback,
                rf"(?i)DROP\s+FUNCTION\s+IF\s+EXISTS\s+hhy\.{re.escape(name)}\b",
            )
        self.assertNotRegex(
            rollback,
            r"(?im)^\s*(DELETE|UPDATE|INSERT|TRUNCATE|DROP\s+TABLE)\b",
        )

    def test_executable_sql_contains_each_required_negative_and_positive_path(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        cases = {
            "R10_MISSING_GROUP_DETAIL_WAS_ACCEPTED": "check_violation",
            "R10_BLANK_GROUP_PLATFORM_WAS_ACCEPTED": "check_violation",
            "R10_BLANK_GROUP_SIZE_RANGE_WAS_ACCEPTED": "check_violation",
            "R10_BLANK_GROUP_JOIN_REQUIREMENT_WAS_ACCEPTED": "check_violation",
            "R10_BLANK_GROUP_LINK_WAS_ACCEPTED": "check_violation",
            "R10_BLANK_GROUP_NO_WAS_ACCEPTED": "check_violation",
            "R10_UNKNOWN_QR_MEDIA_WAS_ACCEPTED": "foreign_key_violation",
            "R10_UNKNOWN_QR_MEDIA_UPDATE_WAS_ACCEPTED": "foreign_key_violation",
            "R10_DUPLICATE_GROUP_DETAIL_WAS_ACCEPTED": "unique_violation",
            "R10_GROUP_ENTRY_CHANNEL_REMOVAL_WAS_ACCEPTED": "check_violation",
            "R10_REFERENCED_QR_MEDIA_DELETE_WAS_ACCEPTED": "foreign_key_violation",
            "R10_ACTIVE_GROUP_DETAIL_DELETE_WAS_ACCEPTED": "check_violation",
            "R10_LAST_ONLINE_GROUP_CONTACT_DELETE_WAS_ACCEPTED": "check_violation",
            "R10_LAST_ONLINE_GROUP_CONTACT_UPDATE_AWAY_WAS_ACCEPTED": "check_violation",
            "R10_JOIN_REQUIREMENT_COUNTED_AS_ENTRY_CHANNEL": "check_violation",
            "R10_JOIN_PASSWORD_WITHOUT_OWNER_CONTACT_WAS_ACCEPTED": "check_violation",
            "R10_DUPLICATE_JOIN_PASSWORD_WAS_ACCEPTED": "unique_violation",
            "R10_JOIN_PASSWORD_NON_GROUP_WAS_ACCEPTED": "check_violation",
            "R10_GROUP_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED": "unique_violation",
        }
        for marker, exception_name in cases.items():
            with self.subTest(marker=marker):
                marker_position = sql.find(f"RAISE EXCEPTION '{marker}'")
                self.assertGreaterEqual(marker_position, 0)
                handler_position = sql.find(
                    f"EXCEPTION WHEN {exception_name} THEN NULL;", marker_position
                )
                self.assertGreater(handler_position, marker_position)
        self.assertRegex(
            sql,
            r"(?is)UPDATE\s+hhy\.content_posts\s+SET\s+status\s*=\s*'DELETED'.*?"
            r"DELETE\s+FROM\s+hhy\.group_details",
        )
        self.assertTrue(sql.lstrip().startswith("SET search_path TO hhy, public;\nBEGIN;"))
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))
        self.assertIn("SET CONSTRAINTS ALL DEFERRED", sql)
        self.assertIn("R10 parent-first group", sql)
        self.assertIn("R10 child-first group", sql)
        self.assertIn("'JOIN_PASSWORD'", sql)

    def test_shell_runners_execute_real_sql_and_compare_runtime_state(self) -> None:
        shell = RUNNER.read_text(encoding="utf-8")
        self.assertIn("set -euo pipefail", shell)
        self.assertIn("HHY_DB_SMOKE_CONFIRM", shell)
        invariant_call = (
            '"${PSQL[@]}" -f '
            '"${ROOT}/database/tests/r10_group_promotion_invariants.sql"'
        )
        self.assertIn(invariant_call, shell)
        self.assertLess(
            shell.index(invariant_call),
            shell.index('echo "R10_GROUP_PROMOTION_INVARIANTS PASS"'),
        )
        self.assertIn("U036__r10_group_promotion_invariants.sql", shell)
        self.assertIn("V036__r10_group_promotion_invariants.sql", shell)
        self.assertIn('"${after_data}" == "${before_data}"', shell)
        self.assertIn('"${reapplied_objects}" == "${before_objects}"', shell)
        self.assertNotIn("psql", "\n".join(
            line for line in shell.splitlines() if "|| true" in line
        ))

        container = CONTAINER.read_text(encoding="utf-8")
        self.assertIn("postgres:17.10-alpine", container)
        self.assertIn("hhy-r10-", container)
        self.assertIn("apply_before_v036", container)
        self.assertIn("apply_through_v036", container)
        self.assertIn("R10_EMPTY_DATABASE_TO_V036 PASS", container)
        self.assertIn("R10_UPGRADE_DATABASE_TO_V036 PASS", container)
        self.assertIn("R10_U036_ROLLBACK PASS", shell)
        self.assertIn("R10_V036_REPLAY PASS", shell)
        self.assertIn("R10_PARENT_LOCK_WRITE_SKEW PASS", shell)
        self.assertIn("pg_sleep(2)", shell)
        self.assertIn("concurrent_a_pid", shell)
        self.assertIn("concurrent_b_pid", shell)
        self.assertIn("confdeltype='r'", container)
        self.assertIn("R10_GROUP_UNIQUENESS_PREREQUISITE_MISSING", container)
        self.assertIn("R10_IDEMPOTENCY_PREREQUISITE_MISSING", container)
        self.assertIn("R10_GROUP_QR_MEDIA_ORPHAN_REQUIRES_REVIEW", container)
        self.assertIn("R10_LEGACY_GROUP_DETAIL_MISSING_REQUIRES_REVIEW", container)
        self.assertIn(
            "R10_LEGACY_JOIN_PASSWORD_NON_GROUP_REQUIRES_REVIEW", container
        )
        self.assertIn(
            "R10_LEGACY_JOIN_PASSWORD_DUPLICATE_REQUIRES_REVIEW", container
        )
        self.assertIn(
            "R10_LEGACY_ONLINE_GROUP_ENTRY_CHANNEL_MISSING_REQUIRES_REVIEW",
            container,
        )
        self.assertIn(
            "R10_LEGACY_ONLINE_GROUP_OWNER_CONTACT_MISSING_REQUIRES_REVIEW",
            container,
        )
        self.assertIn(
            'bash "${ROOT}/scripts/run_r10_database_invariants.sh"', container
        )
        self.assertNotIn("docker system prune", container)


@unittest.skipUnless(
    os.environ.get("HHY_R10_POSTGRES_TEST") == "1",
    "set HHY_R10_POSTGRES_TEST=1 to execute PostgreSQL 17 container scenarios",
)
class R10GroupDatabasePostgres17Test(unittest.TestCase):
    def test_empty_upgrade_rollback_replay_and_runtime_invariants(self) -> None:
        completed = subprocess.run(
            ["bash", str(CONTAINER)],
            cwd=ROOT,
            env=os.environ.copy(),
            text=True,
            capture_output=True,
            timeout=900,
            check=False,
        )
        if completed.returncode != 0:
            self.fail(
                "R10 PostgreSQL 17 scenarios failed\n"
                f"stdout:\n{completed.stdout}\n"
                f"stderr:\n{completed.stderr}"
            )
        for marker in (
            "R10_EMPTY_DATABASE_TO_V036 PASS",
            "R10_UPGRADE_DATABASE_TO_V036 PASS",
            "R10_U036_ROLLBACK PASS",
            "R10_V036_REPLAY PASS",
            "R10_PARENT_LOCK_WRITE_SKEW PASS",
            "R10_DISPOSABLE_POSTGRES_CONTAINER PASS",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, completed.stdout)


if __name__ == "__main__":
    unittest.main()
