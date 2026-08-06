from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'database/migrations/V056__r23_analytics_event_idempotency.sql'
RUNTIME = ROOT / 'services/backend/boot/src/main/resources/db/migration/V056__r23_analytics_event_idempotency.sql'
STORE = ROOT / 'services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/R20RedPacketPostgresStore.java'
ADMIN_OPENAPI = ROOT / 'contracts/admin-openapi.yaml'
ADMIN_CONTROLLER = ROOT / 'services/backend/boot/src/main/java/cc/orbexa/hhy/boot/admin/R20RedPacketAdminController.java'
POSTGRES_TEST = ROOT / 'services/backend/boot/src/test/java/cc/orbexa/hhy/incentive/R22RedPacketPostgresStoreTest.java'


class R23RedPacketAnalyticsContractTest(unittest.TestCase):
    def test_authoritative_and_runtime_migrations_are_identical(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())
        migration = SOURCE.read_text(encoding='utf-8')
        for marker in ('event_key', 'uq_r23_analytics_events_event_key',
                       'INCENTIVIZED_RED_PACKET_TRAFFIC'):
            with self.subTest(marker=marker):
                self.assertIn(marker, migration)

    def test_admin_read_models_are_precise_and_bound(self) -> None:
        openapi = ADMIN_OPENAPI.read_text(encoding='utf-8')
        for marker in ('RedPacketViewSessionResource', 'RedPacketClaimResource',
                       'RedPacketLedgerEntryResource',
                       'adminRedPacketGetRedPacketCampaignsByIdSessions',
                       'adminRedPacketGetRedPacketCampaignsByIdClaims',
                       'adminRedPacketGetRedPacketCampaignsByIdLedger'):
            with self.subTest(marker=marker):
                self.assertIn(marker, openapi)
        controller = ADMIN_CONTROLLER.read_text(encoding='utf-8')
        self.assertIn("hasAuthority('redpacket.finance')", controller)
        self.assertIn('service.adminSessions', controller)
        self.assertIn('service.adminClaims', controller)
        self.assertIn('service.adminLedger', controller)

    def test_incentivized_events_are_deduplicated_and_rolled_up(self) -> None:
        store = STORE.read_text(encoding='utf-8')
        for marker in ('ON CONFLICT (event_key)', 'INCENTIVIZED_RED_PACKET_TRAFFIC',
                       'INSERT INTO hhy.daily_kpis', 'red_packet_view_started',
                       'red_packet_claimed'):
            with self.subTest(marker=marker):
                self.assertIn(marker, store)
        integration = POSTGRES_TEST.read_text(encoding='utf-8')
        self.assertIn('r23AdminReadModelsStayCampaignScopedAndAnalyticsIsIdempotent', integration)
        self.assertIn('daily_kpis', integration)


if __name__ == '__main__':
    unittest.main()
