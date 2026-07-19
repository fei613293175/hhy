import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V027__r05_identity_consent.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V027__r05_identity_consent.sql"
ROLLBACK = ROOT / "database/rollback/U027__r05_identity_consent.sql"


class R05IdentityConsentMigrationTest(unittest.TestCase):
    def test_source_and_runtime_match_and_do_not_overwrite_existing_agreement(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(source, RUNTIME.read_text(encoding="utf-8"))
        self.assertIn("IF agreement_id IS NULL THEN", source)
        self.assertIn("IDENTITY_VERIFICATION", source)
        self.assertIn("2026072001", source)
        self.assertIn("2026-07-20 00:00:00+08", source)
        self.assertIn("受托的实名认证服务机构", source)

    def test_rollback_refuses_to_delete_accepted_version(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("user_agreement_acceptances", sql)
        self.assertIn("acceptance.version_id = seed_version_id", sql)
        self.assertIn("R05_IDENTITY_CONSENT_HAS_ACCEPTANCES", sql)
        self.assertLess(sql.index("DELETE FROM hhy.agreement_versions"),
                        sql.index("DELETE FROM hhy.agreements"))


if __name__ == "__main__":
    unittest.main()
