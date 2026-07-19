from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V023__r05_identity_invariants.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V023__r05_identity_invariants.sql"
ROLLBACK = ROOT / "database/rollback/U023__r05_identity_invariants.sql"


class R05IdentityMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_identity_uniqueness_history_and_audit_guards_are_present(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        required = (
            "uq_r05_identity_session_idempotency",
            "uq_r05_identity_active_session",
            "fk_r05_identity_retry_session",
            "ck_r05_identity_session_completion",
            "uq_r05_identity_provider_idempotency",
            "uq_r05_identity_provider_order",
            "ck_r05_identity_provider_history",
            "storage_scope = 'private_kyc'",
            "uq_r05_identity_media_object",
            "ck_r05_identity_review_history",
            "trg_r05_identity_review_immutable",
            "ck_r05_sensitive_access_reason",
            "ck_r05_sensitive_access_request",
            "fk_r05_sensitive_media",
        )
        for marker in required:
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_sensitive_payloads_are_not_added_as_plaintext(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8").lower()
        for forbidden in (
            "id_no_plain", "name_plain", "id_card_url", "face_url",
            "provider_secret", "access_token", "read_url",
        ):
            self.assertNotIn(forbidden, sql)

    def test_rollback_removes_every_r05_constraint_index_trigger_and_column(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        required = (
            "DROP TRIGGER IF EXISTS trg_r05_identity_review_immutable",
            "DROP INDEX IF EXISTS hhy.uq_r05_identity_active_session",
            "DROP INDEX IF EXISTS hhy.uq_r05_identity_provider_order",
            "DROP INDEX IF EXISTS hhy.uq_r05_identity_media_object",
            "DROP CONSTRAINT IF EXISTS ck_r05_identity_session_completion",
            "DROP CONSTRAINT IF EXISTS ck_r05_identity_provider_history",
            "DROP CONSTRAINT IF EXISTS ck_r05_sensitive_access_request",
            "DROP COLUMN IF EXISTS idempotency_key",
            "DROP COLUMN IF EXISTS storage_scope",
            "DROP COLUMN IF EXISTS verified_at",
        )
        for marker in required:
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)


if __name__ == "__main__":
    unittest.main()
