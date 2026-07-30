import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V028__r05_private_identity_evidence.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V028__r05_private_identity_evidence.sql"
ROLLBACK = ROOT / "database/rollback/U028__r05_private_identity_evidence.sql"


class R05PrivateIdentityEvidenceMigrationTest(unittest.TestCase):
    def test_source_and_runtime_are_exact_and_reject_existing_duplicates(self):
        source = SOURCE.read_bytes()
        self.assertEqual(source, RUNTIME.read_bytes())
        text = source.decode("utf-8")
        self.assertIn("duplicate storage object keys must be resolved before V028", text)
        self.assertIn("CREATE UNIQUE INDEX uq_r05_media_binding_object_key", text)
        self.assertIn("storage_binding_id, object_key", text)

    def test_rollback_only_removes_the_v028_index(self):
        text = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("DROP INDEX IF EXISTS hhy.uq_r05_media_binding_object_key", text)
        self.assertNotIn("DELETE FROM", text.upper())
        self.assertNotIn("DROP TABLE", text.upper())


if __name__ == "__main__":
    unittest.main()
