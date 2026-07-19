from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V021__r04_storage_media_invariants.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V021__r04_storage_media_invariants.sql"
ROLLBACK = ROOT / "database/rollback/U021__r04_storage_media_invariants.sql"


class R04StorageMediaMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_scope_provider_media_and_resume_invariants_are_present(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        required = (
            "ck_r04_storage_scope_code",
            "CLOUDFLARE_R2",
            "ALIYUN_OSS",
            "uq_r04_storage_scope_active",
            "uq_r04_storage_active_bucket",
            "ck_r04_storage_private_domain",
            "ck_r04_storage_binding_config",
            "ck_r04_media_sha256",
            "^[0-9A-Fa-f]{64}$",
            "ck_r04_upload_expiry",
            "ck_r04_media_token_used_at",
            "ck_r04_storage_migration_counts",
            "source_binding_id <> target_binding_id",
            "ix_r04_storage_migration_resume",
        )
        for marker in required:
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_private_scopes_and_sensitive_urls_are_not_relaxed(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for scope in (
            "private_kyc", "private_chat", "audit_evidence", "apk_release", "backup"
        ):
            self.assertIn(scope, sql)
        self.assertNotIn("read_url", sql.lower())
        self.assertNotIn("secret", sql.lower())

    def test_rollback_removes_every_r04_constraint_and_index(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for marker in (
            "DROP INDEX IF EXISTS hhy.uq_r04_storage_scope_active",
            "DROP INDEX IF EXISTS hhy.uq_r04_storage_active_bucket",
            "DROP CONSTRAINT IF EXISTS ck_r04_storage_private_domain",
            "DROP CONSTRAINT IF EXISTS ck_r04_media_sha256",
            "DROP CONSTRAINT IF EXISTS ck_r04_upload_expiry",
            "DROP CONSTRAINT IF EXISTS ck_r04_media_token_expiry",
            "DROP CONSTRAINT IF EXISTS ck_r04_storage_migration_counts",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)


if __name__ == "__main__":
    unittest.main()
