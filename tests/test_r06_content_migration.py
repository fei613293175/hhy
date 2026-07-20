from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V029__r06_content_home_invariants.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V029__r06_content_home_invariants.sql"
ROLLBACK = ROOT / "database/rollback/U029__r06_content_home_invariants.sql"


class R06ContentMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_content_home_invariants_are_present(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "ck_r06_content_type",
            "uq_r06_team_leader_owner_active",
            "assert_r06_content_detail_type",
            "guard_r06_content_type_change",
            "ck_r06_content_contact",
            "ck_r06_content_version_snapshot",
            "trg_r06_content_version_immutable",
            "trg_r06_content_status_immutable",
            "fk_r06_content_review_admin",
            "ck_r06_home_module_config",
            "ck_r06_banner_schedule",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_rollback_removes_every_r06_object(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for marker in (
            "DROP TRIGGER IF EXISTS trg_r06_content_review_immutable",
            "DROP TRIGGER IF EXISTS trg_r06_content_type_change",
            "DROP FUNCTION IF EXISTS hhy.guard_r06_content_type_change()",
            "DROP INDEX IF EXISTS hhy.uq_r06_team_leader_owner_active",
            "DROP CONSTRAINT IF EXISTS ck_r06_content_type",
            "DROP CONSTRAINT IF EXISTS ck_r06_home_module_config",
            "DROP CONSTRAINT IF EXISTS ck_r06_banner_schedule",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)


if __name__ == "__main__":
    unittest.main()
