package cc.orbexa.hhy.incentive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.CancelRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ClaimRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.HeartbeatRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.UserCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ViewSessionRequest;
import cc.orbexa.hhy.incentive.R20RedPacketStore.Kind;
import cc.orbexa.hhy.incentive.R20RedPacketStore.StoreException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R22RedPacketPostgresStoreTest {
    @Test
    void concurrentReservationNeverOversellsAndCancelReleasesStock() throws Exception {
        Fixture fixture = fixture();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long campaignId = fixture.campaign(1, base);
        long firstUser = fixture.user(true);
        long secondUser = fixture.user(true);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Attempt>> futures;
        try {
            futures = List.of(
                    executor.submit(() -> fixture.startAttempt(firstUser, campaignId, base, barrier)),
                    executor.submit(() -> fixture.startAttempt(secondUser, campaignId, base, barrier)));
            Attempt first = futures.get(0).get(30, TimeUnit.SECONDS);
            Attempt second = futures.get(1).get(30, TimeUnit.SECONDS);
            List<Attempt> successes = List.of(first, second).stream()
                    .filter(attempt -> attempt.result() != null).toList();
            List<Attempt> failures = List.of(first, second).stream()
                    .filter(attempt -> attempt.failure() != null).toList();
            assertEquals(1, successes.size());
            assertEquals(1, failures.size());
            assertEquals(Kind.STOCK_EXHAUSTED, failures.getFirst().failure());
            assertStock(fixture, campaignId, 1, 0, 1);

            Attempt winner = successes.getFirst();
            CommandResultResource cancelled = fixture.store(base.plusSeconds(1)).cancel(
                    command(winner.userId(), "cancel-winner"),
                    Long.parseLong(winner.result().resourceId()), new CancelRequest("USER_LEFT"),
                    hash("cancel-winner"));
            assertEquals("CANCELLED", cancelled.status());
            assertEquals("RELEASED", fixture.jdbc().queryForObject(
                    "SELECT status FROM hhy.red_packet_reservations WHERE session_id=?",
                    String.class, Long.parseLong(winner.result().resourceId())));
            assertStock(fixture, campaignId, 1, 0, 0);

            long retryUser = failures.getFirst().userId();
            CommandResultResource retry = fixture.store(base.plusSeconds(2)).startViewSession(
                    command(retryUser, "start-retry"), campaignId,
                    new ViewSessionRequest("nonce-retry", "android-ci"), hash("start-retry"));
            assertEquals("VIEWING", retry.status());
            assertStock(fixture, campaignId, 1, 0, 1);
            fixture.store(base.plusSeconds(3)).cancel(
                    command(retryUser, "cancel-retry"), Long.parseLong(retry.resourceId()),
                    new CancelRequest("TEST_CLEANUP"), hash("cancel-retry"));
            assertStock(fixture, campaignId, 1, 0, 0);
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

        Attempt startAttempt(long userId, long campaignId, Instant instant, CyclicBarrier barrier)
                throws Exception {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                CommandResultResource result = store(instant).startViewSession(
                        command(userId, "start-concurrent"), campaignId,
                        new ViewSessionRequest("nonce-" + userId, "android-ci"),
                        hash("start-concurrent-" + userId));
                return new Attempt(userId, result, null);
            } catch (StoreException failure) {
                return new Attempt(userId, null, failure.kind());
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
