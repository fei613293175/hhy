from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V042__r13_activity_invariants.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V042__r13_activity_invariants.sql"
ROLLBACK = ROOT / "database/rollback/U042__r13_activity_invariants.sql"
INVARIANTS = ROOT / "database/tests/r13_activity_invariants.sql"


class R13ActivityMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_activity_guards_reuse_existing_idempotency_and_audit_sources(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "R13_FAVORITE_UNIQUENESS_PREREQUISITE_MISSING",
            "R13_IDEMPOTENCY_PREREQUISITE_MISSING",
            "R13_CONTACT_AUDIT_IMMUTABILITY_PREREQUISITE_MISSING",
            "R13_ADMIN_AUDIT_PREREQUISITE_MISSING",
            "ck_r13_content_view_event",
            "ck_r13_contact_access_contract",
            "fk_r13_content_reports_reporter",
            "ck_r13_content_report_contract",
            "trg_r13_favorite_no_update",
            "trg_r13_content_view_immutable",
            "ix_r13_favorite_user_recent",
            "ix_r13_history_user_recent",
            "ix_r13_report_status_recent",
            "ORGANIC_TRAFFIC",
            "INCENTIVIZED_RED_PACKET_TRAFFIC",
            "INCENTIVIZED_TASK_TRAFFIC",
            "WECHAT_MOMENTS",
            "REJECTED_UNAVAILABLE",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertNotIn("CREATE TABLE", sql.upper())
        self.assertNotIn("DELETE FROM HHY.", sql.upper())

    def test_invariant_suite_covers_positive_and_negative_paths(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R13_DUPLICATE_FAVORITE_WAS_ACCEPTED",
            "R13_FAVORITE_UPDATE_WAS_ACCEPTED",
            "R13_UNKNOWN_TRAFFIC_TYPE_WAS_ACCEPTED",
            "R13_NEGATIVE_DURATION_WAS_ACCEPTED",
            "R13_UNKNOWN_SHARE_CHANNEL_WAS_ACCEPTED",
            "R13_VIEW_EVENT_UPDATE_WAS_ACCEPTED",
            "R13_VIEW_EVENT_DELETE_WAS_ACCEPTED",
            "R13_UNKNOWN_CONTACT_CHANNEL_WAS_ACCEPTED",
            "R13_UNKNOWN_CONTACT_ACTION_WAS_ACCEPTED",
            "R13_CONTACT_AUDIT_DELETE_WAS_ACCEPTED",
            "R13_ORPHAN_REPORTER_WAS_ACCEPTED",
            "R13_BLANK_REPORT_TYPE_WAS_ACCEPTED",
            "R13_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_rollback_removes_only_r13_schema_objects(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for marker in (
            "DROP TRIGGER IF EXISTS trg_r13_content_view_immutable",
            "DROP TRIGGER IF EXISTS trg_r13_favorite_no_update",
            "DROP FUNCTION IF EXISTS hhy.prevent_r13_favorite_update",
            "DROP INDEX IF EXISTS hhy.ix_r13_report_status_recent",
            "DROP INDEX IF EXISTS hhy.ix_r13_history_user_recent",
            "DROP INDEX IF EXISTS hhy.ix_r13_favorite_user_recent",
            "DROP CONSTRAINT IF EXISTS ck_r13_content_report_contract",
            "DROP CONSTRAINT IF EXISTS fk_r13_content_reports_reporter",
            "DROP CONSTRAINT IF EXISTS ck_r13_contact_access_contract",
            "DROP CONSTRAINT IF EXISTS ck_r13_content_view_event",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertNotIn("DELETE FROM", sql.upper())
        self.assertNotIn("DROP TABLE", sql.upper())


if __name__ == "__main__":
    unittest.main()
