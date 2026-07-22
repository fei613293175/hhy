from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V033__r08_project_invariants.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V033__r08_project_invariants.sql"
ROLLBACK = ROOT / "database/rollback/U033__r08_project_invariants.sql"


class R08ProjectMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_project_guards_and_indexes_are_present(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "R08_IDEMPOTENCY_PREREQUISITE_MISSING",
            "R08_PROJECT_UNIQUENESS_PREREQUISITE_MISSING",
            "ck_r08_project_detail_values",
            "ck_r08_content_stats_numeric",
            "ck_r08_content_version_number",
            "ix_r08_project_public_list",
            "ix_r08_project_region",
            "trg_r08_project_detail_delete",
            "R08_PROJECT_DETAIL_DELETE_REQUIRES_SOFT_DELETE",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertNotIn("DELETE FROM hhy.", sql)
        self.assertNotIn("UPDATE hhy.", sql)

    def test_rollback_removes_every_r08_object_without_business_deletes(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for marker in (
            "DROP TRIGGER IF EXISTS trg_r08_project_detail_delete",
            "DROP FUNCTION IF EXISTS hhy.guard_r08_project_detail_delete",
            "DROP INDEX IF EXISTS hhy.ix_r08_project_region",
            "DROP INDEX IF EXISTS hhy.ix_r08_project_public_list",
            "DROP CONSTRAINT IF EXISTS ck_r08_content_version_number",
            "DROP CONSTRAINT IF EXISTS ck_r08_content_stats_numeric",
            "DROP CONSTRAINT IF EXISTS ck_r08_project_detail_values",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertNotIn("DELETE FROM", sql)
        self.assertNotIn("DROP TABLE", sql)


if __name__ == "__main__":
    unittest.main()
