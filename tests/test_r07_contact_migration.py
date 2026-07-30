import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V032__r07_contact_contract_alignment.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V032__r07_contact_contract_alignment.sql"
ROLLBACK = ROOT / "database/rollback/U032__r07_contact_contract_alignment.sql"
INVARIANTS = ROOT / "database/tests/r07_contact_contract_alignment.sql"


class R07ContactMigrationTest(unittest.TestCase):
    def test_runtime_copy_matches_authoritative_v032(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_v032_uses_bounded_text_and_preserves_legacy_values(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        self.assertIn("ALTER COLUMN value_cipher TYPE varchar(2048)", sql)
        self.assertIn("USING value_cipher::text", sql)
        self.assertIn("ck_r07_contact_cipher_envelope", sql)
        self.assertIn("NOT VALID", sql)

    def test_u032_blocks_lossy_envelope_rollback_before_casting(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        guard = sql.index("R07_CONTACT_CIPHER_ROLLBACK_REQUIRES_DATA_EXPORT")
        cast = sql.index("ALTER COLUMN value_cipher TYPE bigint")
        self.assertLess(guard, cast)
        self.assertIn("value_cipher !~ '^[0-9]+$'", sql)
        self.assertIn("USING value_cipher::bigint", sql)

    def test_runtime_invariant_checks_type_constraint_and_new_rows(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        self.assertIn("character varying(2048)", sql)
        self.assertIn("ck_r07_contact_cipher_envelope", sql)
        self.assertIn("hhy-contact-v1.test-nonce.test-ciphertext", sql)
        self.assertIn("WHEN check_violation THEN NULL", sql)


if __name__ == "__main__":
    unittest.main()
