from pathlib import Path
import csv
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V045__r16_commerce_order_invariants.sql"
RUNTIME = (
    ROOT
    / "services/backend/boot/src/main/resources/db/migration"
    / "V045__r16_commerce_order_invariants.sql"
)
ROLLBACK = ROOT / "database/rollback/U045__r16_commerce_order_invariants_DEV_ONLY.sql"
INVARIANTS = ROOT / "database/tests/r16_commerce_order_invariants.sql"
RUNNER = ROOT / "scripts/run_r16_database_invariants.sh"
STATE_MACHINES = ROOT / "database/state_machines.yaml"


class R16DatabaseContractTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertTrue(SOURCE.is_file(), SOURCE)
        self.assertTrue(RUNTIME.is_file(), RUNTIME)
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_v045_freezes_explicit_product_sku_and_order_fields(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "product_code varchar(64)",
            "member_price_cent bigint",
            "duration_days integer",
            "benefits_json jsonb",
            "sale_starts_at timestamptz",
            "paid_amount_cent bigint",
            "no_refund_agreement_version varchar(64)",
            "idempotency_key varchar(128)",
            "request_hash varchar(64)",
            "legacy_without_idempotency boolean",
            "item_name varchar(255)",
            "subtotal_amount_cent bigint",
            "rule_versions_json jsonb",
            "r16_benefits_valid",
            "uq_r16_orders_creation_idempotency",
            "prevent_immutable_mutation",
            "assert_r16_order_quote",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertIn(
            "ON hhy.orders(user_id, biz_type, idempotency_key)",
            sql,
        )
        self.assertNotRegex(
            sql,
            r"(?i)UNIQUE\s*\(\s*user_id\s*,\s*coupon_id\s*\)",
        )

    def test_dirty_upgrade_never_invents_display_facts(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        self.assertIn("attributes_json->>'name'", sql)
        self.assertIn("attributes_json->'benefits'", sql)
        self.assertIn("snapshot_json->>'name'", sql)
        self.assertNotIn("SET name = code", sql)
        for marker in (
            "R16_DIRTY_UPGRADE_PRODUCT_REQUIRED_FACT_MISSING",
            "R16_DIRTY_UPGRADE_SKU_EXPLICIT_FACT_MISSING",
            "R16_DIRTY_UPGRADE_ORDER_ITEM_FACT_MISSING",
            "R16_DIRTY_UPGRADE_PRICE_SNAPSHOT_INVALID",
            "R16_DIRTY_UPGRADE_ORDER_QUOTE_MISSING",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_order_transition_projection_matches_frozen_state_machine(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        state_machine = STATE_MACHINES.read_text(encoding="utf-8")
        expected_edges = {
            ("PENDING_PAYMENT", "PAYMENT_PROCESSING"),
            ("PENDING_PAYMENT", "CLOSED"),
            ("PAYMENT_PROCESSING", "PAID"),
            ("PAYMENT_PROCESSING", "PAYMENT_FAILED"),
            ("PAID", "FULFILLING"),
            ("PAID", "CHANNEL_REVERSAL"),
            ("FULFILLING", "COMPLETED"),
        }
        projected_edges = set(
            re.findall(
                r"OLD\.status = '([A-Z_]+)' AND NEW\.status "
                r"(?:IN \('([A-Z_]+)', '([A-Z_]+)'\)|= '([A-Z_]+)')",
                sql,
            )
        )
        normalized = {
            (source, target)
            for source, target_a, target_b, target_single in projected_edges
            for target in (target_a, target_b, target_single)
            if target
        }
        self.assertEqual(expected_edges, normalized)
        for source, target in expected_edges:
            self.assertIn(f"from: {source}", state_machine)
            self.assertIn(f"to: {target}", state_machine)
        self.assertIn("NEW.status = 'PAYMENT_PROCESSING'", sql)
        self.assertIn("R16_ORDER_NO_REFUND_EVIDENCE_REQUIRED", sql)
        self.assertIn("record_platform_status_history", sql)

    def test_catalog_dictionary_registers_every_v045_column(self) -> None:
        expected = {
            "products": {"product_code", "description", "version"},
            "product_skus": {
                "name",
                "member_price_cent",
                "duration_days",
                "benefits_json",
                "sale_starts_at",
                "sale_ends_at",
                "version",
            },
            "orders": {
                "currency",
                "paid_amount_cent",
                "paid_at",
                "no_refund_confirmed",
                "no_refund_agreement_version",
                "no_refund_confirmed_at",
                "idempotency_key",
                "request_hash",
                "legacy_without_idempotency",
            },
            "order_items": {"item_name", "subtotal_amount_cent"},
            "order_price_snapshots": {"rule_versions_json"},
        }
        with (ROOT / "database/schema_dictionary.csv").open(
            encoding="utf-8-sig", newline=""
        ) as handle:
            rows = list(csv.DictReader(handle))
        for table, columns in expected.items():
            actual = {row["字段名"] for row in rows if row["表名"] == table}
            self.assertTrue(columns <= actual, (table, columns - actual))

    def test_rollback_and_postgres_runner_cover_required_matrices(self) -> None:
        rollback = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("DEV/TEST only", rollback)
        self.assertIn("R16_U045_BUSINESS_FACTS_PRESENT", rollback)
        self.assertNotRegex(
            rollback,
            r"(?im)^\s*(DELETE|TRUNCATE)\s+(FROM\s+)?hhy\.",
        )
        invariant_sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R16_INVALID_BENEFIT_SHAPE_WAS_ACCEPTED",
            "R16_INVALID_INITIAL_STATUS_WAS_ACCEPTED",
            "R16_DUPLICATE_IDEMPOTENCY_WAS_ACCEPTED",
            "R16_PAYMENT_WITHOUT_NO_REFUND_EVIDENCE_WAS_ACCEPTED",
            "R16_ILLEGAL_ORDER_TRANSITION_WAS_ACCEPTED",
            "R16_FAILED_TRANSITION_LEFT_HISTORY",
            "R16_ROLLED_BACK_TRANSITION_LEFT_HISTORY",
            "R16_TERMINAL_ORDER_MUTATION_WAS_ACCEPTED",
            "R16_ORDER_ITEM_MUTATION_WAS_ACCEPTED",
            "R16_PRICE_SNAPSHOT_MUTATION_WAS_ACCEPTED",
            "R16_COMMERCE_ORDER_INVARIANT_PROPERTY_MATRIX PASS",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, invariant_sql)
        runner = RUNNER.read_text(encoding="utf-8")
        for marker in (
            "server_version >= 170000",
            "R16_EMPTY_DATABASE_MIGRATION PASS",
            "R16_V044_UPGRADE_NO_FABRICATION PASS",
            "R16_DIRTY_UPGRADE_ATOMIC_MATRIX PASS",
            "R16_U045_ROLLBACK_WITH_FACTS_REJECTED_ATOMICALLY PASS",
            "R16_U045_ROLLBACK_V045_REPLAY PASS",
            "R16_ORDER_CONCURRENT_IDEMPOTENCY PASS",
            "R16_DATABASE_INVARIANTS PASS",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, runner)


if __name__ == "__main__":
    unittest.main()
