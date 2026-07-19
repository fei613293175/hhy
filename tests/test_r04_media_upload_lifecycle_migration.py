import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V022__r04_media_upload_lifecycle.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V022__r04_media_upload_lifecycle.sql"
ROLLBACK = ROOT / "database/rollback/U022__r04_media_upload_lifecycle.sql"


class R04MediaUploadLifecycleMigrationTest(unittest.TestCase):
    def test_runtime_mirror_has_the_same_sql(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        runtime = RUNTIME.read_text(encoding="utf-8")
        runtime_sql = runtime.split("\n\n", 1)[1]
        source_sql = source.split("\n\n", 1)[1]
        self.assertEqual(source_sql, runtime_sql)

    def test_lifecycle_has_required_identity_and_guards(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for token in (
            "purpose varchar(2000)",
            "storage_scope varchar(64)",
            "storage_binding_id bigint",
            "sha256 varchar(64)",
            "fk_r04_upload_storage_binding",
            "ck_r04_upload_intent",
            "ck_r04_upload_completion",
            "uq_r04_upload_media_id",
            "fk_r04_media_storage_binding",
            "ck_r04_media_lifecycle",
            "ck_r04_media_deleted_at",
        ):
            self.assertIn(token, sql)

    def test_rollback_removes_every_added_column_constraint_and_index(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for token in (
            "DROP INDEX IF EXISTS hhy.ix_r04_media_binding_status",
            "DROP INDEX IF EXISTS hhy.uq_r04_upload_media_id",
            "DROP CONSTRAINT IF EXISTS ck_r04_media_lifecycle",
            "DROP CONSTRAINT IF EXISTS ck_r04_upload_completion",
            "DROP CONSTRAINT IF EXISTS fk_r04_upload_storage_binding",
            "DROP COLUMN IF EXISTS storage_binding_id",
            "DROP COLUMN IF EXISTS purpose",
        ):
            self.assertIn(token, sql)


if __name__ == "__main__":
    unittest.main()
