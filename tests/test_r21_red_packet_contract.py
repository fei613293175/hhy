from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V054__r21_red_packet_raise_and_lifecycle.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V054__r21_red_packet_raise_and_lifecycle.sql"
STORE = ROOT / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/R20RedPacketPostgresStore.java"
SERVICE = ROOT / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/R20RedPacketService.java"
CONTRACTS = ROOT / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/R20RedPacketContracts.java"
ANDROID_API = ROOT / "apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR20RedPacketApi.kt"
ANDROID_STATE = ROOT / "apps/android/feature/red-packet/src/main/java/cc/orbexa/hhy/redpacket/R21RedPacketState.kt"
ANDROID_SCREENS = ROOT / "apps/android/feature/red-packet/src/main/java/cc/orbexa/hhy/redpacket/R21RedPacketScreens.kt"


class R21RedPacketContractTest(unittest.TestCase):
    def test_runtime_migration_has_the_same_behavioral_markers(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        runtime = RUNTIME.read_text(encoding="utf-8")
        for marker in (
            "ck_r21_campaign_version_change_kind",
            "source_quote_id",
            "uq_r21_increase_order_campaign_version",
            "guard_r20_campaign_status",
            "r21_fulfill_red_packet_order",
            "R21_RED_PACKET_INCREASE_REMAINING_CHANGED",
            "R21_RED_PACKET_INCREASE_QUOTE_CHANGED",
            "trg_r21_red_packet_order_fulfillment",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, source)
                self.assertIn(marker, runtime)

    def test_payment_and_increase_paths_return_server_quote_facts(self) -> None:
        contracts = CONTRACTS.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("Long principalCent", contracts)
        self.assertIn("Long payableCent", contracts)
        self.assertIn("Long amountPerClaimCent", contracts)
        self.assertIn("q.principal", store)
        self.assertIn("q.service_fee", store)
        self.assertIn("q.expires_at", store)
        self.assertIn("R21-RP-", store)

    def test_owner_actions_and_android_routes_are_version_gated(self) -> None:
        service = SERVICE.read_text(encoding="utf-8")
        api = ANDROID_API.read_text(encoding="utf-8")
        state = ANDROID_STATE.read_text(encoding="utf-8")
        screens = ANDROID_SCREENS.read_text(encoding="utf-8")
        self.assertIn("redPacketPostRedPacketCampaignsByIdPause", service)
        self.assertIn("redPacketPostRedPacketCampaignsByIdIncreaseQuotes", service)
        for action in ("\"pause\"", "\"resume\"", "\"close\""):
            self.assertIn(action, api)
        self.assertIn('"/api/v1/red-packet-campaigns/${requireR20Id(id)}/$action"', api)
        for route in ("/increase-quotes", "/increase-orders"):
            self.assertIn(route, api)
        self.assertIn('status == "ACTIVE"', state)
        self.assertIn("r21QuoteReady", state)
        self.assertIn("scr-rp-adv-005", screens)
        self.assertIn("scr-rp-adv-006", screens)


if __name__ == "__main__":
    unittest.main()
