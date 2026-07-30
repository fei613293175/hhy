import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V026__r05_identity_callback_consumption.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V026__r05_identity_callback_consumption.sql"
ROLLBACK = ROOT / "database/rollback/U026__r05_identity_callback_consumption.sql"


class R05IdentityCallbackMigrationTest(unittest.TestCase):
    def test_source_and_runtime_are_identical_and_add_one_time_consumption_state(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(source, RUNTIME.read_text(encoding="utf-8"))
        self.assertIn("ADD COLUMN callback_consumed_at timestamptz", source)
        self.assertIn("WHERE callback_consumed_at IS NULL", source)
        self.assertIn("ix_r05_identity_callback_unconsumed", source)

    def test_rollback_removes_index_before_column(self) -> None:
        sql = ROLLBACK.read_text(encoding="utf-8")
        index_position = sql.index("DROP INDEX")
        column_position = sql.index("DROP COLUMN")
        self.assertLess(index_position, column_position)
        self.assertIn("callback_consumed_at", sql)


if __name__ == "__main__":
    unittest.main()
