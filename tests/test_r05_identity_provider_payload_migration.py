import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V025__r05_identity_provider_payload.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V025__r05_identity_provider_payload.sql"
ROLLBACK = ROOT / "database/rollback/U025__r05_identity_provider_payload.sql"


class R05IdentityProviderPayloadMigrationTest(unittest.TestCase):
    def test_source_and_runtime_are_identical_and_expand_encrypted_payload(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(source, RUNTIME.read_text(encoding="utf-8"))
        self.assertIn("ALTER COLUMN response_cipher TYPE text", source)

    def test_rollback_refuses_lossy_ciphertext_truncation(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("length(response_cipher) > 255", sql)
        self.assertIn("R05_IDENTITY_PROVIDER_PAYLOAD_TOO_LONG_FOR_U025_ROLLBACK", sql)
        self.assertIn("ALTER COLUMN response_cipher TYPE varchar(255)", sql)


if __name__ == "__main__":
    unittest.main()
