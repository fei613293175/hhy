from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V030__r06_content_admin_permissions.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V030__r06_content_admin_permissions.sql"
ROLLBACK = ROOT / "database/rollback/U030__r06_content_admin_permissions.sql"


class R06ContentPermissionsMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_frozen_permissions_and_inheritance_are_present(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for permission in (
            "content.ban",
            "content.recommend",
            "content.official",
            "content.dict.read",
            "content.dict.write",
            "content.manage",
            "content.read",
        ):
            with self.subTest(permission=permission):
                self.assertIn(permission, sql)
        self.assertIn("ON CONFLICT (code) DO NOTHING", sql)
        self.assertIn("ON CONFLICT (role_id, permission_id) DO NOTHING", sql)

    def test_rollback_removes_r06_permissions_and_their_role_grants(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        for permission in (
            "content.ban",
            "content.recommend",
            "content.official",
            "content.dict.read",
            "content.dict.write",
        ):
            with self.subTest(permission=permission):
                self.assertIn(permission, sql)
        self.assertIn("content.manage", sql)
        self.assertIn("content.read", sql)


if __name__ == "__main__":
    unittest.main()
