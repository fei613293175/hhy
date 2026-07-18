from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V019__r03_provider_configuration_center.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V019__r03_provider_configuration_center.sql"
ROLLBACK = ROOT / "database/rollback/U019__r03_provider_configuration_center.sql"


class R03ProviderConfigurationMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_forward_migration_contains_security_and_concurrency_invariants(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        required = (
            "r03_secret_refs_valid",
            "uq_provider_config_versions_active_environment",
            "trg_provider_config_versions_payload_guard",
            "uq_provider_certificates_active_type",
            "trg_provider_certificates_material_guard",
            "trg_provider_certificate_access_logs_append_only",
            "service_health_status",
            "approval_requester_id <> approval_reviewer_id",
        )
        for marker in required:
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_all_frozen_r03_permissions_are_seeded_and_granted(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        permissions = {
            "provider.config.read", "provider.config.write", "provider.config.test",
            "provider.config.activate", "provider.certificate.read",
            "provider.certificate.write", "provider.certificate.rotate",
            "domain.read", "domain.write", "domain.verify",
        }
        for permission in permissions:
            self.assertGreaterEqual(sql.count(f"'{permission}'"), 3, permission)

    def test_development_rollback_removes_every_r03_extension(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for marker in (
            "DROP FUNCTION IF EXISTS hhy.r03_secret_refs_valid(jsonb)",
            "DROP COLUMN IF EXISTS service_health_status",
            "DROP COLUMN IF EXISTS secret_ref",
            "DROP COLUMN IF EXISTS secret_refs_json",
            "DELETE FROM hhy.admin_permissions",
        ):
            self.assertIn(marker, sql)


if __name__ == "__main__":
    unittest.main()
