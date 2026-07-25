package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.content.R12ReviewContracts.ReviewAssignRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewActorContext;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewDecisionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R12ReviewPostgresStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-25T04:00:00Z");

    @Test
    void reviewAssignmentDecisionAuditOutboxAndReadsUseRealPostgres() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var store = new R12ReviewPostgresStore(jdbc);
        var service = new R12ReviewService(store,
                new ContentContactCipher("r12-review-postgres-root-secret-at-least-32-characters"),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
        String suffix = UUID.randomUUID().toString().replace("-", "");

        Fixture fixture = transaction.execute(status -> seed(jdbc, suffix));
        if (fixture == null) throw new IllegalStateException("R12 review fixture was not created");

        var assigned = transaction.execute(status -> service.assign(
                fixture.actor("review.assign"), Long.toString(fixture.contentId()),
                new ReviewAssignRequest(Long.toString(fixture.assigneeId()), "轮值分配", 1L),
                "r12-assign-" + suffix));
        assertEquals("REVIEWING", assigned.status());
        assertEquals(2, assigned.version());
        assertEquals(Long.toString(fixture.assigneeId()), assigned.assigneeId());

        var escalated = transaction.execute(status -> service.decide(
                fixture.assignee("review.decide"), Long.toString(fixture.contentId()),
                new ReviewDecisionRequest("ESCALATE", "申请二审", 2L, List.of("evidence-1")),
                "r12-escalate-" + suffix));
        assertEquals("REVIEWING", escalated.status());
        assertEquals("ESCALATE", escalated.decision());
        assertEquals(3, escalated.version());
        assertEquals("ESCALATE", service.detail(Long.toString(fixture.contentId())).decision());

        var reassigned = transaction.execute(status -> service.assign(
                fixture.assignee("review.assign"), Long.toString(fixture.contentId()),
                new ReviewAssignRequest(Long.toString(fixture.actorId()), "二审分配", 3L),
                "r12-reassign-" + suffix));
        assertEquals("REVIEWING", reassigned.status());
        assertEquals(4, reassigned.version());

        var approved = transaction.execute(status -> service.decide(
                fixture.actor("review.decide"), Long.toString(fixture.contentId()),
                new ReviewDecisionRequest("APPROVE", "二审材料真实完整", 4L, List.of("evidence-2")),
                "r12-decide-" + suffix));
        assertEquals("APPROVED", approved.status());
        assertEquals("APPROVE", approved.decision());
        assertEquals(5, approved.version());

        var replay = transaction.execute(status -> service.assign(
                fixture.actor("review.assign"), Long.toString(fixture.contentId()),
                new ReviewAssignRequest(Long.toString(fixture.assigneeId()), "轮值分配", 1L),
                "r12-assign-" + suffix));
        assertEquals(assigned, replay);

        assertEquals(5, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.content_review_records
                WHERE content_id=? AND decision IN ('ASSIGN','CLAIM','ESCALATE','APPROVE')
                """, Integer.class, fixture.contentId()));
        assertEquals(4, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_operation_logs
                WHERE resource='CONTENT_REVIEW' AND resource_id=?
                """, Integer.class, fixture.contentId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.outbox_events
                WHERE aggregate_id=? AND event_type='content.review.assigned.v1'
                  AND payload->>'contentVersion'='2'
                """, Integer.class, Long.toString(fixture.contentId())));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.outbox_events
                WHERE aggregate_id=? AND event_type='content.review.escalated.v1'
                  AND payload->>'contentVersion'='3'
                  AND payload->>'device'=?
                """, Integer.class, Long.toString(fixture.contentId()), "device-assignee-" + suffix));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.outbox_events
                WHERE aggregate_id=? AND event_type='content.review.decided.v1'
                  AND payload->>'contentVersion'='5'
                """, Integer.class, Long.toString(fixture.contentId())));

        assertEquals(1, service.reports(1, 20, null, "OPEN", suffix.substring(0, 8),
                "createdAt:desc").items().size());
        assertEquals("CONTENT", service.appeals(1, 20, null, "OPEN", null,
                "createdAt:desc").items().getFirst().subjectType());

        assertThrows(DataAccessException.class, () -> transaction.executeWithoutResult(status ->
                jdbc.update("UPDATE hhy.content_review_records SET reason='tampered' WHERE content_id=?",
                        fixture.contentId())));
    }

    private static Fixture seed(JdbcTemplate jdbc, String suffix) {
        long ownerId = user(jdbc, "18" + suffix.substring(0, 9), "R12OWN" + suffix.substring(0, 12));
        long reporterId = user(jdbc, "19" + suffix.substring(0, 9), "R12REP" + suffix.substring(0, 12));
        long actorId = admin(jdbc, "r12-actor-" + suffix.substring(0, 12));
        long assigneeId = admin(jdbc, "r12-assignee-" + suffix.substring(0, 12));
        long actorSessionId = session(jdbc, actorId, "device-actor-" + suffix, suffix + "-actor");
        long assigneeSessionId = session(
                jdbc, assigneeId, "device-assignee-" + suffix, suffix + "-assignee");
        R08PostgresStore projects = new R08PostgresStore(jdbc);
        long contentId = projects.createProject(
                ownerId, "R12审核项目" + suffix.substring(0, 8), "待审核摘要", "合作说明",
                "COOP", "CN-11", "实名材料", "https://example.invalid",
                "{\"description\":\"合作说明\",\"categoryCode\":\"COOP\",\"regionCode\":\"CN-11\"}",
                List.of(), NOW);
        jdbc.update("""
                UPDATE hhy.content_posts SET status='PENDING_REVIEW',version=version+1,updated_at=?
                WHERE id=? AND version=0
                """, java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), contentId);
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                SELECT content.id,content.version::text,latest.snapshot_json,'test:submit',?
                FROM hhy.content_posts content
                JOIN LATERAL (
                  SELECT snapshot_json FROM hhy.content_versions
                  WHERE content_id=content.id ORDER BY version_no::bigint DESC LIMIT 1
                ) latest ON true WHERE content.id=?
                """, java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), contentId);
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,operator,created_at,transition_version
                ) VALUES (?,'DRAFT','PENDING_REVIEW','test:owner',?,1)
                """, contentId, java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO hhy.content_reports(reporter_id,content_id,type,description,status)
                VALUES (?,?,'MISLEADING',?,'OPEN')
                """, reporterId, contentId, "举报-" + suffix);
        jdbc.update("""
                INSERT INTO hhy.content_appeals(content_id,owner_id,status)
                VALUES (?,?,'OPEN')
                """, contentId, ownerId);
        return new Fixture(contentId, actorId, actorSessionId, assigneeId, assigneeSessionId, suffix);
    }

    private static long user(JdbcTemplate jdbc, String phone, String inviteCode) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code) VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, phone, inviteCode);
        if (id == null) throw new IllegalStateException("R12 review test user id missing");
        jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname) VALUES (?,?)", id, "用户" + id);
        return id;
    }

    private static long admin(JdbcTemplate jdbc, String username) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.admin_users(username,password_hash,status)
                VALUES (?,repeat('a',60),'ACTIVE') RETURNING id
                """, Long.class, username);
        if (id == null) throw new IllegalStateException("R12 review test administrator id missing");
        return id;
    }

    private static long session(JdbcTemplate jdbc, long adminId, String device, String suffix) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.admin_sessions(
                  admin_user_id,access_jti,refresh_hash,mfa_level,device_fingerprint,ip,
                  expires_at,last_active_at,created_at,updated_at
                ) VALUES (?,?,?,'NONE',?,'127.0.0.1',?,?,?,?) RETURNING id
                """, Long.class, adminId, "jti-" + suffix, "refresh-" + suffix, device,
                java.time.OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        if (id == null) throw new IllegalStateException("R12 review administrator session missing");
        jdbc.update("""
                UPDATE hhy.admin_sessions
                SET mfa_level='VERIFIED',version=version+1,last_active_at=?
                WHERE id=? AND admin_user_id=? AND version=0
                """, java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), id, adminId);
        return id;
    }

    private record Fixture(
            long contentId, long actorId, long actorSessionId,
            long assigneeId, long assigneeSessionId, String suffix) {
        private ReviewActorContext actor(String permission) {
            return new ReviewActorContext(
                    actorId, actorSessionId, "r12-actor", permission,
                    "request-actor-" + suffix, "127.0.0.1");
        }

        private ReviewActorContext assignee(String permission) {
            return new ReviewActorContext(
                    assigneeId, assigneeSessionId, "r12-assignee", permission,
                    "request-assignee-" + suffix, "127.0.0.1");
        }
    }
}
