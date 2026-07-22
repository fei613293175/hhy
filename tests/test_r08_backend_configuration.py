from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V034__r08_backend_configuration.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V034__r08_backend_configuration.sql"
ROLLBACK = ROOT / "database/rollback/U034__r08_backend_configuration.sql"


class R08BackendConfigurationTest(unittest.TestCase):
    def test_runtime_migration_is_exact_copy_and_seeds_frozen_values(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())
        sql = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(13, sql.count("'GLOBAL'"))
        self.assertIn("ON CONFLICT (key,scope) DO NOTHING", sql)
        self.assertIn("'domain.h5.host','\"h5.orbexa.cc\"'::jsonb", sql)
        self.assertIn("ALTER COLUMN title TYPE varchar(2000)", sql)
        self.assertEqual(5, sql.count("TYPE varchar(2000)"))

    def test_rollback_is_guarded_by_version_and_exact_value(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("R08_CONFIG_ROLLBACK_CHANGED_VALUE", sql)
        self.assertIn("current.version<>0 OR current.value_json<>expected.value_json", sql)
        self.assertIn("current.version=0 AND current.value_json=expected.value_json", sql)
        self.assertIn("R08_SCHEMA_ROLLBACK_LONG_VALUE", sql)
        self.assertEqual(5, sql.count("TYPE varchar(255)"))
        self.assertNotIn("DROP TABLE", sql)


if __name__ == "__main__":
    unittest.main()
