from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V053__r20_red_packet_invariants.sql"
RUNTIME = (
    ROOT
    / "services/backend/boot/src/main/resources/db/migration"
    / "V053__r20_red_packet_invariants.sql"
)
STATE_MACHINES = ROOT / "database/state_machines.yaml"
BASELINE = ROOT / "database/migrations/V004__commerce_redpacket_and_finance.sql"
STORE = (
    ROOT
    / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive"
    / "R20RedPacketPostgresStore.java"
)
SERVICE = (
    ROOT
    / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive"
    / "R20RedPacketService.java"
)
ANDROID_STATE = (
    ROOT
    / "apps/android/feature/red-packet/src/main/java/cc/orbexa/hhy/redpacket"
    / "R20RedPacketState.kt"
)
ANDROID_SCREENS = (
    ROOT
    / "apps/android/feature/red-packet/src/main/java/cc/orbexa/hhy/redpacket"
    / "R20RedPacketScreens.kt"
)


class R20DatabaseContractTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertTrue(SOURCE.is_file(), SOURCE)
        self.assertTrue(RUNTIME.is_file(), RUNTIME)
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_campaign_shape_preserves_legacy_fields_and_formal_statuses(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        baseline = BASELINE.read_text(encoding="utf-8")
        for marker in (
            "current_amount",
            "amount_per_claim_cent",
            "principal_cent",
            "service_fee_cent",
            "targeting_json",
            "review_decision",
            "review_evidence_json",
            "status IN ('DRAFT','PRE_REVIEWING','PRE_REVIEW_REJECTED','PRE_REVIEW_APPROVED',",
            "'WAITING_PAYMENT','PAYMENT_PROCESSING','ACTIVE'",
            "'PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_OWNER','PAUSED_BY_RISK'",
            "'SOLD_OUT','CLOSED_BY_OWNER','TERMINATED_BY_PLATFORM'",
            "record_platform_status_history",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertIn("current_amount", sql)
        self.assertIn("required_seconds", baseline)

        state_machine = STATE_MACHINES.read_text(encoding="utf-8")
        for status in (
            "DRAFT", "PRE_REVIEWING", "PRE_REVIEW_REJECTED", "PRE_REVIEW_APPROVED",
            "WAITING_PAYMENT", "PAYMENT_PROCESSING", "ACTIVE", "PAUSED_BY_CONTENT_OFFLINE",
            "PAUSED_BY_OWNER", "PAUSED_BY_RISK", "SOLD_OUT", "CLOSED_BY_OWNER",
            "TERMINATED_BY_PLATFORM",
        ):
            self.assertIn(status, state_machine)

    def test_status_guard_keeps_escalation_in_pre_reviewing(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        self.assertIn("OLD.status = 'PRE_REVIEWING' AND NEW.status IN ('PRE_REVIEW_APPROVED','PRE_REVIEW_REJECTED')", sql)
        self.assertNotIn("NEW.status = 'ESCALATE'", sql)
        self.assertIn("case \"ESCALATE\" -> \"PRE_REVIEWING\"", STORE.read_text(encoding="utf-8"))

    def test_quote_and_order_facts_are_unique_and_quote_is_review_gated(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "uq_r20_initial_quote_campaign",
            "ON hhy.red_packet_quotes(campaign_id)",
            "WHERE quote_type = 'INITIAL'",
            "uq_r20_red_packet_order_quote",
            "ON hhy.red_packet_orders(quote_id)",
            "WHERE quote_id IS NOT NULL",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        store = STORE.read_text(encoding="utf-8")
        self.assertIn('if (!"PRE_REVIEW_APPROVED".equals(current.status()))', store)
        self.assertIn("红包初始报价已存在", store)
        self.assertIn("红包报价已关联订单", store)
        self.assertIn("PENDING_PAYMENT", store)

    def test_idempotency_claim_is_insert_first_locking_replay(self) -> None:
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("ON CONFLICT (scope,idem_key) DO NOTHING RETURNING id", store)
        self.assertIn("WHERE scope=? AND idem_key=? FOR UPDATE", store)
        self.assertIn("response_ref IS NULL", store)
        self.assertIn("r20rp:user:", store)
        self.assertIn("r20rp:admin:", store)

    def test_application_limits_match_database_contract(self) -> None:
        service = SERVICE.read_text(encoding="utf-8")
        for marker in (
            "MAX_TOTAL_COUNT = 1_000_000L",
            "MAX_AMOUNT_PER_CLAIM_CENT = 100_000_000L",
            "MAX_EVIDENCE_IDS = 100",
            "amounts(total, amount)",
            "审核证据最多100项",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, service)

    def test_new_and_edited_writes_sync_legacy_amount_and_required_seconds(self) -> None:
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("total_count,current_amount,required_seconds,", store)
        self.assertIn("r22ConfigSeconds(connection, \"red_packet.default_view_seconds\")", store)
        self.assertIn("VALUES (?,?, 'DRAFT',?,?,?,?,?,?,?,?,?::jsonb,0)", store)
        self.assertIn("SET total_count=?, current_amount=?, amount_per_claim_cent=?", store)

    def test_review_queue_sort_is_a_deterministic_database_order(self) -> None:
        service = SERVICE.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        self.assertIn('"priority:desc,createdAt:asc"', service)
        self.assertIn(
            '"CASE WHEN c.status=\'PRE_REVIEWING\' THEN 1 ELSE 0 END DESC,'
            'c.created_at ASC,c.id ASC"',
            store,
        )

    def test_android_uses_the_formal_status_machine_and_action_gates(self) -> None:
        state = ANDROID_STATE.read_text(encoding="utf-8")
        screens = ANDROID_SCREENS.read_text(encoding="utf-8")
        labels = {
            "DRAFT": "草稿",
            "PRE_REVIEWING": "预审核中",
            "PRE_REVIEW_REJECTED": "预审核未通过",
            "PRE_REVIEW_APPROVED": "预审核已通过",
            "WAITING_PAYMENT": "待支付",
            "PAYMENT_PROCESSING": "支付处理中",
            "ACTIVE": "进行中",
            "PAUSED_BY_CONTENT_OFFLINE": "内容下线暂停",
            "PAUSED_BY_OWNER": "发起人暂停",
            "PAUSED_BY_RISK": "风控暂停",
            "SOLD_OUT": "已领完",
            "CLOSED_BY_OWNER": "已关闭",
            "TERMINATED_BY_PLATFORM": "平台终止",
        }
        for status, label in labels.items():
            with self.subTest(status=status):
                self.assertIn(f'"{status}" -> "{label}"', state)
        self.assertIn('status in setOf("DRAFT", "PRE_REVIEW_REJECTED")', state)
        self.assertIn('status == "PRE_REVIEW_APPROVED"', state)
        self.assertIn('quoteStatus == "QUOTED" && campaignStatus == "PRE_REVIEW_APPROVED"', state)
        self.assertIn('"DRAFT" to "未开始"', screens)
        self.assertIn('"CLOSED_BY_OWNER" to "已结束"', screens)
        self.assertNotIn("PENDING_REVIEW", state + screens)


if __name__ == "__main__":
    unittest.main()
