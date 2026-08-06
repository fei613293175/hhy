package cc.orbexa.hhy.incentive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.CancelRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ClaimRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.HeartbeatRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.LifecycleRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.UserCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ViewSessionRequest;
import cc.orbexa.hhy.incentive.R20RedPacketStore.Kind;
import cc.orbexa.hhy.incentive.R20RedPacketStore.StoreException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R22RedPacketPostgresStoreTest {
    private static final int CLIENT_REQUESTS = 100;
    private static final int DATABASE_SLOTS = 12;

    @Test
    void concurrentHundredRequestsNeverOversellOrDuplicateSettlement() throws Exception {
        Fixture fixture = fixture();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long campaignId = fixture.campaign(1, base);
        List<Long> users = new ArrayList<>(CLIENT_REQUESTS);
        for (int i = 0; i < CLIENT_REQUESTS; i++) users.add(fixture.user(true));
        CyclicBarrier barrier = new CyclicBarrier(CLIENT_REQUESTS);
        Semaphore databaseSlots = new Semaphore(DATABASE_SLOTS);
        ExecutorService executor = Executors.newFixedThreadPool(CLIENT_REQUESTS);
        try {
            List<Future<Attempt>> futures = users.stream()
                    .map(userId -> executor.submit(
                            () -> fixture.startAttempt(userId, campaignId, base, barrier, databaseSlots)))
                    .toList();
            List<Attempt> attempts = new ArrayList<>(CLIENT_REQUESTS);
            for (Future<Attempt> future : futures) attempts.add(future.get(60, TimeUnit.SECONDS));
            List<Attempt> successes = attempts.stream()
                    .filter(attempt -> attempt.result() != null).toList();
            List<Attempt> failures = attempts.stream()
                    .filter(attempt -> attempt.failure() != null).toList();
            assertEquals(1, successes.size());
            assertEquals(CLIENT_REQUESTS - 1, failures.size());
            assertEquals(CLIENT_REQUESTS - 1, failures.stream()
                    .filter(attempt -> attempt.failure() == Kind.STOCK_EXHAUSTED).count());
            assertStock(fixture, campaignId, 1, 0, 1);
            assertEquals(1L, fixture.count(
                    "SELECT count(*) FROM hhy.red_packet_view_sessions WHERE campaign_id=?", campaignId));
            assertEquals(1L, fixture.count(
                    "SELECT count(*) FROM hhy.red_packet_reservations WHERE campaign_id=? AND status='LOCKED'",
                    campaignId));
            assertEquals(1L, fixture.count(
                    "SELECT count(*) FROM hhy.outbox_events WHERE aggregate_type='red_packet_view_session' "
                            + "AND event_type='red.packet.view.started.v1' AND aggregate_id IN "
                            + "(SELECT id::text FROM hhy.red_packet_view_sessions WHERE campaign_id=?)",
                    campaignId));

            Attempt winner = successes.getFirst();
            long sessionId = Long.parseLong(winner.result().resourceId());
            CommandResultResource complete = fixture.store(base.plusSeconds(30)).heartbeat(
                    command(winner.userId(), "heartbeat-concurrent"), sessionId,
                    new HeartbeatRequest(1, 1, true), hash("heartbeat-concurrent"));
            assertEquals("VIEW_COMPLETE", complete.status());

            UserCommand claimCommand = command(winner.userId(), "claim-concurrent");
            ClaimRequest claimRequest = new ClaimRequest("nonce-" + winner.userId(), 1);
            CyclicBarrier claimBarrier = new CyclicBarrier(CLIENT_REQUESTS);
            List<Future<CommandResultResource>> claimFutures = new ArrayList<>(CLIENT_REQUESTS);
            for (int i = 0; i < CLIENT_REQUESTS; i++) {
                claimFutures.add(executor.submit(() -> {
                    claimBarrier.await(60, TimeUnit.SECONDS);
                    databaseSlots.acquire();
                    try {
                        return fixture.store(base.plusSeconds(31)).claim(
                                claimCommand, sessionId, claimRequest, hash("claim-concurrent"));
                    } finally {
                        databaseSlots.release();
                    }
                }));
            }
            List<CommandResultResource> claims = new ArrayList<>(CLIENT_REQUESTS);
            for (Future<CommandResultResource> future : claimFutures) {
                claims.add(future.get(60, TimeUnit.SECONDS));
            }
            CommandResultResource claimed = claims.getFirst();
            claims.forEach(replay -> assertEquals(claimed, replay));
            long claimId = Long.parseLong(claimed.resourceId());
            assertEquals("PENDING", claimed.status());
            assertStock(fixture, campaignId, 1, 1, 0);
            assertEquals(1L, fixture.count(
                    "SELECT count(*) FROM hhy.red_packet_claims WHERE campaign_id=? AND user_id=?",
                    campaignId, winner.userId()));
            assertEquals(1L, fixture.count(
                    "SELECT count(*) FROM hhy.outbox_events WHERE aggregate_type='red_packet_claim' "
                            + "AND aggregate_id=? AND event_type='red.packet.claim.created.v1'",
                    Long.toString(claimId)));
            assertEquals(0L, fixture.count(
                    "SELECT count(*) FROM hhy.red_packet_ledger WHERE campaign_id=?", campaignId));
            assertEquals(0L, fixture.count(
                    "SELECT count(*) FROM hhy.reward_ledger WHERE biz_id=?", claimId));
            assertEquals(0L, fixture.count(
                    "SELECT count(*) FROM hhy.balance_snapshots WHERE available_cent < 0 OR frozen_cent < 0"));

            StoreException changedRequest = assertThrows(StoreException.class, () ->
                    fixture.store(base.plusSeconds(32)).claim(
                            claimCommand, sessionId,
                            new ClaimRequest("nonce-" + winner.userId(), 2), hash("claim-concurrent-changed")));
            assertEquals(Kind.CONFLICT, changedRequest.kind());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void serverClockClaimReplayAndDuplicateClaimUseDurableInvariants() {
        Fixture fixture = fixture();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long campaignId = fixture.campaign(2, base);
        long userId = fixture.user(true);
        CommandResultResource session = fixture.store(base).startViewSession(
                command(userId, "start-claim"), campaignId,
                new ViewSessionRequest("nonce-claim", "android-ci"), hash("start-claim"));
        long sessionId = Long.parseLong(session.resourceId());

        CommandResultResource invisible = fixture.store(base.plusSeconds(10)).heartbeat(
                command(userId, "heartbeat-hidden"), sessionId,
                new HeartbeatRequest(0, 999, false), hash("heartbeat-hidden"));
        assertEquals(0L, invisible.accumulatedSeconds());
        assertEquals("VIEWING", invisible.status());
        CommandResultResource complete = fixture.store(base.plusSeconds(30)).heartbeat(
                command(userId, "heartbeat-visible"), sessionId,
                new HeartbeatRequest(1, 1, true), hash("heartbeat-visible"));
        assertEquals(20L, complete.accumulatedSeconds());
        assertEquals("VIEW_COMPLETE", complete.status());

        StoreException wrongNonce = assertThrows(StoreException.class, () ->
                fixture.store(base.plusSeconds(31)).claim(
                        command(userId, "claim-wrong"), sessionId,
                        new ClaimRequest("wrong-nonce", 1), hash("claim-wrong")));
        assertEquals(Kind.VIEW_INVALID, wrongNonce.kind());

        UserCommand claimCommand = command(userId, "claim-ok");
        ClaimRequest claimRequest = new ClaimRequest("nonce-claim", 1);
        CommandResultResource claimed = fixture.store(base.plusSeconds(31)).claim(
                claimCommand, sessionId, claimRequest, hash("claim-ok"));
        CommandResultResource replay = fixture.store(base.plusSeconds(32)).claim(
                claimCommand, sessionId, claimRequest, hash("claim-ok"));
        assertEquals(claimed, replay);
        assertEquals("PENDING", claimed.status());
        assertStock(fixture, campaignId, 2, 1, 0);
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.red_packet_claims WHERE campaign_id=? AND user_id=?",
                campaignId, userId));
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.outbox_events WHERE aggregate_type='red_packet_claim' "
                        + "AND aggregate_id=? AND event_type='red.packet.claim.created.v1'",
                claimed.resourceId()));

        StoreException changedRequest = assertThrows(StoreException.class, () ->
                fixture.store(base.plusSeconds(33)).claim(
                        claimCommand, sessionId,
                        new ClaimRequest("nonce-claim", 2), hash("claim-ok-changed")));
        assertEquals(Kind.CONFLICT, changedRequest.kind());

        CommandResultResource duplicateSession = fixture.store(base.plusSeconds(33)).startViewSession(
                command(userId, "start-duplicate"), campaignId,
                new ViewSessionRequest("nonce-duplicate", "android-ci"), hash("start-duplicate"));
        long duplicateSessionId = Long.parseLong(duplicateSession.resourceId());
        fixture.store(base.plusSeconds(53)).heartbeat(
                command(userId, "heartbeat-duplicate"), duplicateSessionId,
                new HeartbeatRequest(0, 20, true), hash("heartbeat-duplicate"));
        StoreException duplicate = assertThrows(StoreException.class, () ->
                fixture.store(base.plusSeconds(54)).claim(
                        command(userId, "claim-duplicate"), duplicateSessionId,
                        new ClaimRequest("nonce-duplicate", 0), hash("claim-duplicate")));
        assertEquals(Kind.ALREADY_CLAIMED, duplicate.kind());
        fixture.store(base.plusSeconds(55)).cancel(
                command(userId, "cancel-duplicate"), duplicateSessionId,
                new CancelRequest("TEST_CLEANUP"), hash("cancel-duplicate"));
        assertStock(fixture, campaignId, 2, 1, 0);
    }

    @Test
    void identityRejectionAndTimeoutRecoveryReleaseTheReservedSlot() {
        Fixture fixture = fixture();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long campaignId = fixture.campaign(1, base);
        long unverified = fixture.user(false);
        StoreException identity = assertThrows(StoreException.class, () ->
                fixture.store(base).startViewSession(
                        command(unverified, "start-unverified"), campaignId,
                        new ViewSessionRequest("nonce-unverified", "android-ci"),
                        hash("start-unverified")));
        assertEquals(Kind.IDENTITY_NOT_VERIFIED, identity.kind());
        assertStock(fixture, campaignId, 1, 0, 0);

        long firstUser = fixture.user(true);
        long secondUser = fixture.user(true);
        CommandResultResource expired = fixture.store(base).startViewSession(
                command(firstUser, "start-expiring"), campaignId,
                new ViewSessionRequest("nonce-expiring", "android-ci"), hash("start-expiring"));
        CommandResultResource recovered = fixture.store(base.plusSeconds(91)).startViewSession(
                command(secondUser, "start-after-expiry"), campaignId,
                new ViewSessionRequest("nonce-recovered", "android-ci"), hash("start-after-expiry"));
        assertNotNull(recovered.resourceId());
        assertEquals("EXPIRED", fixture.jdbc().queryForObject(
                "SELECT status FROM hhy.red_packet_view_sessions WHERE id=?",
                String.class, Long.parseLong(expired.resourceId())));
        assertEquals("EXPIRED", fixture.jdbc().queryForObject(
                "SELECT status FROM hhy.red_packet_reservations WHERE session_id=?",
                String.class, Long.parseLong(expired.resourceId())));
        assertStock(fixture, campaignId, 1, 0, 1);
        fixture.store(base.plusSeconds(92)).cancel(
                command(secondUser, "cancel-recovered"), Long.parseLong(recovered.resourceId()),
                new CancelRequest("TEST_CLEANUP"), hash("cancel-recovered"));
        assertStock(fixture, campaignId, 1, 0, 0);
    }

    @Test
    void platformLifecycleIsAuditedIdempotentAndNeverUsesOwnerScope() {
        Fixture fixture = fixture();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long campaignId = fixture.campaign(3, base);
        AdminCommand pause = adminCommand("pause");
        LifecycleRequest pauseRequest = new LifecycleRequest("风控命中", 0L);
        var paused = fixture.store(base).adminPause(pause, campaignId, pauseRequest, hash("admin-pause"));
        assertEquals("PAUSED_BY_RISK", paused.status());
        assertEquals(paused, fixture.store(base.plusSeconds(1)).adminPause(
                pause, campaignId, pauseRequest, hash("admin-pause")));
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.admin_operation_logs WHERE action='PLATFORM_PAUSE' AND resource_id=?", campaignId));
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.outbox_events WHERE aggregate_id=? AND event_type='red.packet.campaign.platform_pause.v1'",
                Long.toString(campaignId)));

        var resumed = fixture.store(base.plusSeconds(2)).adminResume(
                adminCommand("resume"), campaignId, new LifecycleRequest("已复核", paused.version()), hash("admin-resume"));
        assertEquals("ACTIVE", resumed.status());
        var terminated = fixture.store(base.plusSeconds(3)).adminTerminate(
                adminCommand("terminate"), campaignId, new LifecycleRequest("确认违规且不退款", resumed.version()), hash("admin-terminate"));
        assertEquals("TERMINATED_BY_PLATFORM", terminated.status());
        assertEquals(3L, fixture.count(
                "SELECT count(*) FROM hhy.admin_operation_logs WHERE resource='red_packet_campaign' AND resource_id=?", campaignId));
        StoreException rejected = assertThrows(StoreException.class, () -> fixture.store(base.plusSeconds(4)).adminResume(
                adminCommand("resume-after-terminate"), campaignId, new LifecycleRequest("错误恢复", terminated.version()), hash("admin-resume-terminated")));
        assertEquals(Kind.BUSINESS_RULE, rejected.kind());
    }

    @Test
    void r23AdminReadModelsStayCampaignScopedAndAnalyticsIsIdempotent() {
        Fixture fixture = fixture();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long campaignId = fixture.campaign(2, base);
        long otherCampaignId = fixture.campaign(1, base);
        long userId = fixture.user(true);
        CommandResultResource session = fixture.store(base).startViewSession(
                command(userId, "r23-read-session"), campaignId,
                new ViewSessionRequest("nonce-r23", "android-ci"), hash("r23-read-session"));
        fixture.store(base.plusSeconds(20)).heartbeat(
                command(userId, "r23-read-heartbeat"), Long.parseLong(session.resourceId()),
                new HeartbeatRequest(1, 20, true), hash("r23-read-heartbeat"));
        fixture.store(base.plusSeconds(21)).claim(
                command(userId, "r23-read-claim"), Long.parseLong(session.resourceId()),
                new ClaimRequest("nonce-r23", 1), hash("r23-read-claim"));

        R20RedPacketStore.PageQuery page = new R20RedPacketStore.PageQuery(1, 20, 0, null, null, "createdAt:desc");
        assertEquals(1, fixture.store(base.plusSeconds(22)).adminSessions(campaignId, page).items().size());
        assertEquals(1, fixture.store(base.plusSeconds(22)).adminClaims(campaignId, page).items().size());
        assertEquals(0, fixture.store(base.plusSeconds(22)).adminSessions(otherCampaignId, page).items().size());
        assertEquals(0, fixture.store(base.plusSeconds(22)).adminClaims(otherCampaignId, page).items().size());
        assertEquals(2L, fixture.count(
                "SELECT count(*) FROM hhy.analytics_events WHERE traffic_type='INCENTIVIZED_RED_PACKET_TRAFFIC'"));
        assertEquals(2L, fixture.count(
                "SELECT sum(value) FROM hhy.daily_kpis WHERE dimensions @> '{\"trafficType\":\"INCENTIVIZED_RED_PACKET_TRAFFIC\"}'::jsonb"));
        assertEquals(0, fixture.store(base.plusSeconds(22)).adminLedger(campaignId).size());
    }

    private static Fixture fixture() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long ownerId = user(jdbc, true);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        long contentId = transaction.execute(status -> {
            long id = jdbc.queryForObject("""
                    INSERT INTO hhy.content_posts(owner_id,type,title,status)
                    VALUES (?,'PROJECT',?,'DRAFT') RETURNING id
                    """, Long.class, ownerId, "R22 integration " + suffix());
            jdbc.update("INSERT INTO hhy.project_details(content_id,cooperation) VALUES (?,?)",
                    id, "R22 integration fixture");
            return id;
        });
        return new Fixture(dataSource, jdbc, ownerId, contentId);
    }

    private static long user(JdbcTemplate jdbc, boolean verified) {
        String suffix = suffix();
        long userId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, "22" + suffix, "R22" + suffix);
        if (verified) {
            jdbc.update("""
                    INSERT INTO hhy.identity_profiles(
                      user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
                    VALUES (?,'r22-name','r22-id',?,'VERIFIED',clock_timestamp())
                    """, userId, suffix.repeat(4));
        }
        return userId;
    }

    private static UserCommand command(long userId, String operation) {
        String suffix = suffix();
        return new UserCommand(userId, "r22-" + operation + "-" + suffix,
                "r22-idem-" + operation + "-" + suffix,
                "r22-request-" + operation + "-" + suffix);
    }

    private static AdminCommand adminCommand(String operation) {
        String suffix = suffix();
        return new AdminCommand(99L, 199L, "r23-admin", "r23-admin-" + operation,
                "r23-admin-request-" + suffix, "127.0.0.1", "r23-admin-idem-" + operation + "-" + suffix);
    }

    private static String hash(String value) {
        return "r22-hash-" + value;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static void assertStock(
            Fixture fixture, long campaignId, int total, int claimed, int reserved) {
        Map<String, Object> stock = fixture.jdbc().queryForMap(
                "SELECT total,claimed,reserved FROM hhy.red_packet_stock WHERE campaign_id=?",
                campaignId);
        assertEquals(total, ((Number) stock.get("total")).intValue());
        assertEquals(claimed, ((Number) stock.get("claimed")).intValue());
        assertEquals(reserved, ((Number) stock.get("reserved")).intValue());
    }

    private record Attempt(long userId, CommandResultResource result, Kind failure) { }

    private record Fixture(
            DriverManagerDataSource dataSource, JdbcTemplate jdbc, long ownerId, long contentId) {
        long user(boolean verified) {
            return R22RedPacketPostgresStoreTest.user(jdbc, verified);
        }

        long campaign(int total, Instant base) {
            long campaignId = jdbc.queryForObject("""
                    INSERT INTO hhy.red_packet_campaigns(
                      content_id,owner_id,current_amount,total_count,status,required_seconds,
                      amount_per_claim_cent,principal_cent,service_fee_cent,start_at,end_at,
                      targeting_json,version)
                    VALUES (?,?,123,?,'ACTIVE',20,123,?,0,?,?,CAST('{}' AS jsonb),0)
                    RETURNING id
                    """, Long.class, contentId, ownerId, total, total * 123L,
                    Timestamp.from(base.minusSeconds(60)), Timestamp.from(base.plusSeconds(3600)));
            jdbc.update("""
                    INSERT INTO hhy.red_packet_stock(campaign_id,total,claimed,reserved,version)
                    VALUES (?,?,0,0,0)
                    """, campaignId, total);
            return campaignId;
        }

        R20RedPacketPostgresStore store(Instant instant) {
            return new R20RedPacketPostgresStore(
                    dataSource, new Codec(), Clock.fixed(instant, java.time.ZoneOffset.UTC));
        }

        Attempt startAttempt(
                long userId, long campaignId, Instant instant, CyclicBarrier barrier, Semaphore databaseSlots)
                throws Exception {
            barrier.await(60, TimeUnit.SECONDS);
            databaseSlots.acquire();
            try {
                CommandResultResource result = store(instant).startViewSession(
                        command(userId, "start-concurrent"), campaignId,
                        new ViewSessionRequest("nonce-" + userId, "android-ci"),
                        hash("start-concurrent-" + userId));
                return new Attempt(userId, result, null);
            } catch (StoreException failure) {
                return new Attempt(userId, null, failure.kind());
            } finally {
                databaseSlots.release();
            }
        }

        long count(String sql, Object... args) {
            return jdbc.queryForObject(sql, Long.class, args);
        }
    }

    private static final class Codec implements R20RedPacketContracts.Codec {
        @Override
        public byte[] canonicalBytes(Object value) {
            return new byte[] { 1 };
        }

        @Override
        public String json(Object value) {
            return "{}";
        }

        @Override
        public Map<String, Object> object(String json) {
            return Map.of();
        }
    }
}
