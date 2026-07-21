from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V031__r07_search_invariants.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V031__r07_search_invariants.sql"
ROLLBACK = ROOT / "database/rollback/U031__r07_search_invariants.sql"


class R07SearchMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_search_hot_term_contact_and_idempotency_guards_are_present(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "R07_IDEMPOTENCY_PREREQUISITE_MISSING",
            "R07_DUPLICATE_HOT_SEARCH_TERMS_REQUIRE_REVIEW",
            "ck_r07_search_history_keyword",
            "ix_r07_search_history_user_recent",
            "ck_r07_hot_keyword_weight",
            "ck_r07_hot_schedule",
            "uq_r07_hot_keyword",
            "ix_r07_hot_active_rank",
            "ck_r07_contact_access_complete",
            "ix_r07_contact_access_recent",
            "trg_r07_contact_access_immutable",
            "hhy.prevent_immutable_mutation()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertNotIn("DELETE FROM hhy.hot_search_terms", sql)

    def test_rollback_removes_every_r07_object_and_restores_weight_default(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for marker in (
            "DROP TRIGGER IF EXISTS trg_r07_contact_access_immutable",
            "DROP INDEX IF EXISTS hhy.ix_r07_contact_access_recent",
            "DROP INDEX IF EXISTS hhy.ix_r07_hot_active_rank",
            "DROP INDEX IF EXISTS hhy.uq_r07_hot_keyword",
            "DROP INDEX IF EXISTS hhy.ix_r07_search_history_user_recent",
            "DROP CONSTRAINT IF EXISTS ck_r07_contact_access_complete",
            "DROP CONSTRAINT IF EXISTS ck_r07_hot_schedule",
            "DROP CONSTRAINT IF EXISTS ck_r07_search_history_keyword",
            "ALTER COLUMN weight DROP DEFAULT",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)


if __name__ == "__main__":
    unittest.main()
