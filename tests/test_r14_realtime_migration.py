from pathlib import Path
import csv
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V044__r14_websocket_reliability.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V044__r14_websocket_reliability.sql"
ROLLBACK = ROOT / "database/rollback/U044__r14_websocket_reliability_DEV_ONLY.sql"
RUNNER = ROOT / "scripts/run_r14_database_invariants.sh"


class R14RealtimeMigrationTest(unittest.TestCase):
    def test_runtime_migration_is_exact_source_copy(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_v044_freezes_delivery_ack_and_gap_invariants(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "CREATE TABLE hhy.websocket_user_sequences",
            "CREATE TABLE hhy.websocket_deliveries",
            "CREATE TABLE hhy.websocket_gap_watermarks",
            "uq_r14_ws_delivery_event",
            "uq_r14_ws_delivery_sequence",
            "guard_r14_ws_sequence",
            "guard_r14_ws_delivery",
            "guard_r14_ws_gap_watermark",
            "interval '72 hours'",
            "delivery_attempts BETWEEN 0 AND 6",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_catalog_dictionary_and_traceability_register_exact_three_tables(self) -> None:
        expected = {
            "websocket_user_sequences",
            "websocket_deliveries",
            "websocket_gap_watermarks",
        }
        with (ROOT / "catalogs/data_tables.csv").open(encoding="utf-8-sig", newline="") as handle:
            catalog = {row["表名"] for row in csv.DictReader(handle) if row["表名"].startswith("websocket_")}
        with (ROOT / "database/schema_dictionary.csv").open(encoding="utf-8-sig", newline="") as handle:
            dictionary = {row["表名"] for row in csv.DictReader(handle) if row["表名"].startswith("websocket_")}
        with (ROOT / "database/schema_traceability.csv").open(encoding="utf-8-sig", newline="") as handle:
            traced = {row["表名"] for row in csv.DictReader(handle) if row["表名"].startswith("websocket_")}
        self.assertEqual(expected, catalog)
        self.assertEqual(expected, dictionary)
        self.assertEqual(expected, traced)

    def test_dev_rollback_and_postgres_runner_cover_replay_and_concurrency(self) -> None:
        rollback = ROLLBACK.read_text(encoding="utf-8")
        for table in (
            "websocket_gap_watermarks",
            "websocket_deliveries",
            "websocket_user_sequences",
        ):
            self.assertIn(f"DROP TABLE IF EXISTS hhy.{table}", rollback)
        runner = RUNNER.read_text(encoding="utf-8")
        for marker in (
            "R14_U044_ROLLBACK_V044_REPLAY PASS",
            "R14_WEBSOCKET_RELIABILITY_INVARIANTS PASS",
            "R14_WS_CONCURRENT_SEQUENCE_ALLOCATION PASS",
            "8|8|1|8",
        ):
            self.assertIn(marker, runner)


if __name__ == "__main__":
    unittest.main()
