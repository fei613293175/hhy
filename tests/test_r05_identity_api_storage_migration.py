from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V024__r05_identity_api_storage.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V024__r05_identity_api_storage.sql"
ROLLBACK = ROOT / "database/rollback/U024__r05_identity_api_storage.sql"


class R05IdentityApiStorageMigrationTest(unittest.TestCase):
    def test_runtime_copy_is_exact(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_consent_failure_and_cipher_capacity_are_explicit(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        self.assertIn("ALTER COLUMN name_cipher TYPE text", sql)
        self.assertIn("ALTER COLUMN id_no_cipher TYPE text", sql)
        self.assertIn("ADD COLUMN consent_version varchar(2000)", sql)
        self.assertIn("ADD COLUMN failure_code varchar(128)", sql)
        self.assertIn("ck_r05_identity_session_consent", sql)
        self.assertIn("ck_r05_identity_session_failure", sql)

    def test_rollback_refuses_cipher_truncation(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("R05_IDENTITY_CIPHER_TOO_LONG_FOR_U024_ROLLBACK", sql)
        self.assertIn("DROP COLUMN IF EXISTS consent_version", sql)
        self.assertNotIn("left(name_cipher", sql.lower())


if __name__ == "__main__":
    unittest.main()
