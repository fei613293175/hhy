from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V055__r22_red_packet_view_sessions.sql"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/db/migration/V055__r22_red_packet_view_sessions.sql"
STORE = ROOT / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/R20RedPacketPostgresStore.java"
CONTRACTS = ROOT / "services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/R20RedPacketContracts.java"
OPENAPI = ROOT / "contracts/openapi.yaml"
ANDROID_API = ROOT / "apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR20RedPacketApi.kt"
ANDROID_MODELS = ROOT / "apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt"
ANDROID_SCREENS = ROOT / "apps/android/feature/red-packet/src/main/java/cc/orbexa/hhy/redpacket/R22RedPacketScreens.kt"
POSTGRES_TEST = ROOT / "services/backend/boot/src/test/java/cc/orbexa/hhy/incentive/R22RedPacketPostgresStoreTest.java"


class R22RedPacketContractTest(unittest.TestCase):
    def test_authoritative_and_runtime_migrations_are_identical(self) -> None:
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())
        migration = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "ck_r22_view_session_status",
            "ck_r22_view_session_seconds",
            "uq_r22_active_view_session",
            "uq_r22_claim_campaign_user",
            "uq_r22_claim_request",
            "fk_r22_claim_session",
            "fk_r22_reservation_claim",
            "red_packet.default_view_seconds",
            "red_packet.reservation_min_seconds",
            "required_seconds = 20",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, migration)

    def test_server_timer_uses_config_and_has_reservation_grace(self) -> None:
        store = STORE.read_text(encoding="utf-8")
        self.assertIn('r22ConfigSeconds(connection, "red_packet.default_view_seconds")', store)
        self.assertIn('r22ConfigSeconds(connection, "red_packet.reservation_min_seconds")', store)
        self.assertIn("Math.max(requiredSeconds,", store)
        self.assertIn("currentTime.plusSeconds(reservationSeconds)", store)
        self.assertNotIn("VALUES (?,?,true,20,'VIEWING'", store)
        self.assertNotIn("VALUES (?,?, 'DRAFT',?,?,10,", store)

    def test_public_surface_masks_owner_and_only_exposes_active(self) -> None:
        store = STORE.read_text(encoding="utf-8")
        self.assertIn('page(query, "c.status=\'ACTIVE\'"', store)
        self.assertIn("WHERE c.id=? AND c.status='ACTIVE'", store)
        self.assertIn("Long.toString(rows.getLong(1)), Long.toString(rows.getLong(2)),\n                        null,", store)

    def test_contract_routes_and_server_authoritative_fields_are_present(self) -> None:
        openapi = OPENAPI.read_text(encoding="utf-8")
        for operation in (
            "redPacketPostRedPacketCampaignsByIdViewSessions",
            "redPacketPostRedPacketViewSessionsByIdHeartbeat",
            "redPacketPostRedPacketViewSessionsByIdClaim",
            "redPacketPostRedPacketViewSessionsByIdCancel",
        ):
            with self.subTest(operation=operation):
                self.assertIn(operation, openapi)
        contracts = CONTRACTS.read_text(encoding="utf-8")
        models = ANDROID_MODELS.read_text(encoding="utf-8")
        for field in ("requiredSeconds", "accumulatedSeconds", "lastHeartbeatSequence", "lastServerTime"):
            self.assertIn(field, contracts)
            self.assertIn(field, models)

    def test_idempotency_and_android_state_mapping_are_bound(self) -> None:
        store = STORE.read_text(encoding="utf-8")
        api = ANDROID_API.read_text(encoding="utf-8")
        screens = ANDROID_SCREENS.read_text(encoding="utf-8")
        for marker in (
            "claim(connection, userScope(command), command.idempotencyKey(), requestHash)",
            "outbox(connection, claimId, \"red_packet_claim\"",
            '"VIEW_COMPLETE".equals(session.status())',
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, store)
        for route in (
            "/view-sessions",
            "/heartbeat",
            "/claim",
            "/cancel",
        ):
            self.assertIn(route, api)
        for marker in (
            "REDPACKET-422-VIEW_INVALID",
            "服务端有效浏览",
            "最近心跳序号",
            "重复提交不会重复入账",
        ):
            self.assertIn(marker, screens)

    def test_postgres_regression_paths_and_release_status_are_bound(self) -> None:
        store = STORE.read_text(encoding="utf-8")
        integration = POSTGRES_TEST.read_text(encoding="utf-8")
        self.assertIn('releaseReservation(connection, session, "RELEASED"', store)
        for marker in (
            "concurrentReservationNeverOversellsAndCancelReleasesStock",
            "serverClockClaimReplayAndDuplicateClaimUseDurableInvariants",
            "identityRejectionAndTimeoutRecoveryReleaseTheReservedSlot",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, integration)


if __name__ == "__main__":
    unittest.main()
