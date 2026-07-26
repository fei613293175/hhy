package cc.orbexa.hhy.boot.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class BusinessGaugeBinder implements MeterBinder {
    static final String OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String OUTBOX_DEAD_LETTER_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE status = 'DEAD_LETTER'
            """;
    static final String LEDGER_IMBALANCE_SQL = """
            SELECT COUNT(*)
            FROM (
                SELECT transaction_row.id
                FROM hhy.accounting_transactions transaction_row
                LEFT JOIN hhy.accounting_entries entry_row
                  ON entry_row.transaction_id = transaction_row.id
                LEFT JOIN hhy.ledger_accounts account_row
                  ON account_row.id = entry_row.account_id
                WHERE transaction_row.status = 'POSTED'
                GROUP BY transaction_row.id
                HAVING COUNT(entry_row.id) < 2
                    OR COALESCE(SUM(CASE WHEN entry_row.direction = 'DEBIT' THEN entry_row.amount_cent ELSE 0 END), 0)
                       <> COALESCE(SUM(CASE WHEN entry_row.direction = 'CREDIT' THEN entry_row.amount_cent ELSE 0 END), 0)
                    OR COUNT(DISTINCT entry_row.currency) <> 1
                    OR COALESCE(SUM(CASE
                        WHEN entry_row.id IS NOT NULL AND (
                            account_row.id IS NULL OR account_row.currency <> entry_row.currency
                        ) THEN 1 ELSE 0 END), 0) <> 0
            ) imbalance
            """;
    static final String RECONCILIATION_OPEN_DIFFERENCES_SQL = """
            SELECT COUNT(*)
            FROM hhy.reconciliation_differences
            WHERE status = 'OPEN'
            """;
    static final String ADMIN_ACTIVE_SESSIONS_SQL = """
            SELECT COUNT(*)
            FROM hhy.admin_sessions
            WHERE revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
            """;
    static final String ADMIN_AUTH_FAILURES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.admin_login_logs
            WHERE upper(result) NOT IN ('SUCCESS', 'SUCCEEDED')
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String ADMIN_MFA_ACTIVE_METHODS_SQL = """
            SELECT COUNT(*)
            FROM hhy.admin_mfa_methods
            WHERE status = 'ACTIVE'
            """;
    static final String ADMIN_IDEMPOTENCY_INCOMPLETE_SNAPSHOTS_SQL = """
            SELECT COUNT(*)
            FROM hhy.idempotency_records
            WHERE scope LIKE 'admin.%'
              AND ((response_type IS NULL AND response_payload_ciphertext IS NOT NULL)
                OR (response_type IS NOT NULL AND response_payload_ciphertext IS NULL))
            """;
    static final String USER_ACTIVE_SESSIONS_SQL = """
            SELECT COUNT(*)
            FROM hhy.user_sessions
            WHERE refresh_hash IS NOT NULL AND expires_at > CURRENT_TIMESTAMP
            """;
    static final String USER_SECURITY_CHALLENGE_FAILURES_5M_SQL = """
            SELECT COALESCE(SUM(attempts), 0)
            FROM hhy.auth_security_challenges
            WHERE attempts > 0
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String USER_EXPIRED_UNUSED_SMS_CODES_SQL = """
            SELECT COUNT(*)
            FROM hhy.sms_verification_codes
            WHERE used_at IS NULL AND expires_at <= CURRENT_TIMESTAMP
            """;
    static final String PROVIDER_CONNECTION_TEST_FAILURES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.provider_connection_tests
            WHERE test_type LIKE 'R03_SAFE_PROBE:%'
              AND test_type <> 'R03_SAFE_PROBE:OK'
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String PROVIDER_UNTESTED_ACTIVE_CONFIGS_SQL = """
            SELECT COUNT(*)
            FROM hhy.provider_config_versions
            WHERE status = 'ACTIVE'
              AND connection_successful IS DISTINCT FROM TRUE
            """;
    static final String PROVIDER_CERTIFICATES_EXPIRING_30D_SQL = """
            SELECT COUNT(*)
            FROM hhy.provider_certificates
            WHERE status = 'ACTIVE'
              AND valid_to IS NOT NULL
              AND valid_to <= CURRENT_TIMESTAMP + INTERVAL '30' DAY
            """;
    static final String DOMAIN_VERIFICATION_FAILURES_SQL = """
            SELECT COUNT(*)
            FROM hhy.domain_configs
            WHERE dns_status = 'FAILED'
               OR https_status = 'FAILED'
               OR certificate_status = 'INVALID'
               OR service_health_status = 'FAILED'
            """;
    static final String MEDIA_UPLOAD_FAILURES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.upload_sessions
            WHERE status = 'FAILED'
              AND updated_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String MEDIA_UPLOAD_EXPIRED_OPEN_SQL = """
            SELECT COUNT(*)
            FROM hhy.upload_sessions
            WHERE status IN ('CREATED', 'UPLOADING', 'COMPLETING')
              AND expires_at <= CURRENT_TIMESTAMP
            """;
    static final String MEDIA_DELETE_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.media_objects
            WHERE status = 'DELETE_PENDING'
            """;
    static final String STORAGE_MIGRATION_BLOCKED_SQL = """
            SELECT COUNT(*)
            FROM hhy.storage_migration_jobs
            WHERE status IN ('PAUSED', 'FAILED')
            """;
    static final String IDENTITY_ACTIVE_SESSIONS_SQL = """
            SELECT COUNT(*)
            FROM hhy.identity_verification_sessions
            WHERE status IN ('SESSION_CREATED','LIVENESS_PENDING','PROVIDER_PROCESSING','MANUAL_REVIEW')
            """;
    static final String IDENTITY_PROVIDER_FAILURES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.identity_provider_requests
            WHERE status IN ('FAILED','TIMED_OUT')
              AND completed_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String IDENTITY_MANUAL_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.identity_verification_sessions
            WHERE status = 'MANUAL_REVIEW'
            """;
    static final String IDENTITY_PRIVATE_MEDIA_INVALID_SQL = """
            SELECT COUNT(*)
            FROM hhy.identity_media identity_media
            LEFT JOIN hhy.media_objects media ON media.id = identity_media.media_object_id
            WHERE media.id IS NULL OR media.visibility <> 'PRIVATE' OR media.status <> 'READY'
            """;
    static final String CONTENT_ONLINE_TOTAL_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status = 'ONLINE'
            """;
    static final String CONTENT_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE review_status = 'PENDING'
            """;
    static final String CONTENT_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE aggregate_type = 'CONTENT'
              AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String HOME_ENABLED_MODULES_SQL = """
            SELECT COUNT(*)
            FROM hhy.home_modules
            WHERE enabled = TRUE
              AND source_type IS DISTINCT FROM 'DICTIONARY'
            """;
    static final String SEARCH_HISTORY_ROWS_SQL = """
            SELECT COUNT(*)
            FROM hhy.search_histories
            """;
    static final String SEARCH_HOT_TERMS_ACTIVE_SQL = """
            SELECT COUNT(*)
            FROM hhy.hot_search_terms
            WHERE enabled = TRUE
              AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
              AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)
            """;
    static final String PUBLISHER_ACTIVE_COUNT_SQL = """
            SELECT COUNT(DISTINCT post.owner_id)
            FROM hhy.content_posts post
            JOIN hhy.users publisher ON publisher.id = post.owner_id
            WHERE post.status = 'ONLINE'
              AND publisher.status = 'ACTIVE'
            """;
    static final String CONTACT_ACCESSES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs
            WHERE action IN ('VIEW', 'COPY', 'REPLAY')
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String CONTACT_REJECTIONS_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs
            WHERE action LIKE 'REJECTED_%'
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String R07_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE aggregate_type IN ('CONTENT', 'SEARCH_HISTORY')
              AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String PROJECT_TOTAL_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'PROJECT' AND status <> 'DELETED'
            """;
    static final String PROJECT_ONLINE_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'PROJECT' AND status = 'ONLINE'
            """;
    static final String PROJECT_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'PROJECT' AND status <> 'DELETED' AND review_status = 'PENDING'
            """;
    static final String PROJECT_FAVORITES_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_favorites favorite
            JOIN hhy.content_posts post ON post.id = favorite.content_id
            WHERE post.type = 'PROJECT' AND post.status <> 'DELETED'
            """;
    static final String PROJECT_CONTACT_ACCESSES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'PROJECT'
              AND access_log.action IN ('VIEW', 'COPY', 'REPLAY')
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String PROJECT_CONTACT_REJECTIONS_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'PROJECT'
              AND access_log.action LIKE 'REJECTED_%'
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String R08_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE event_type IN (
                'content.project.created.v1', 'content.project.updated.v1',
                'content.favorited.v1', 'content.favorite.replayed.v1',
                'content.shared.v1', 'chat.direct.created.v1',
                'content.project.stage.alert.v1'
            ) AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String APP_TOTAL_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'APP' AND status <> 'DELETED'
            """;
    static final String APP_ONLINE_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'APP' AND status = 'ONLINE'
            """;
    static final String APP_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'APP' AND status <> 'DELETED' AND review_status = 'PENDING'
            """;
    static final String APP_FAVORITES_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_favorites favorite
            JOIN hhy.content_posts post ON post.id = favorite.content_id
            WHERE post.type = 'APP' AND post.status <> 'DELETED'
            """;
    static final String APP_CONTACT_ACCESSES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'APP'
              AND access_log.action IN ('VIEW', 'COPY', 'REPLAY')
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String APP_CONTACT_REJECTIONS_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'APP'
              AND access_log.action LIKE 'REJECTED_%'
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String R09_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE event_type IN (
                'content.app.created.v1', 'content.app.updated.v1',
                'content.favorited.v1', 'content.favorite.replayed.v1',
                'content.shared.v1', 'chat.direct.created.v1',
                'content.app.stage.alert.v1'
            ) AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String GROUP_TOTAL_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'GROUP' AND status <> 'DELETED'
            """;
    static final String GROUP_ONLINE_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'GROUP' AND status = 'ONLINE'
            """;
    static final String GROUP_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'GROUP' AND status <> 'DELETED' AND review_status = 'PENDING'
            """;
    static final String GROUP_FAVORITES_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_favorites favorite
            JOIN hhy.content_posts post ON post.id = favorite.content_id
            WHERE post.type = 'GROUP' AND post.status <> 'DELETED'
            """;
    static final String GROUP_CONTACT_ACCESSES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'GROUP'
              AND access_log.action IN ('VIEW', 'COPY', 'REPLAY')
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String GROUP_CONTACT_REJECTIONS_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'GROUP'
              AND access_log.action LIKE 'REJECTED_%'
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String R10_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE event_type IN (
                'content.group.created.v1', 'content.group.updated.v1',
                'content.favorited.v1', 'content.favorite.replayed.v1',
                'content.shared.v1', 'chat.direct.created.v1',
                'content.group.stage.alert.v1'
            ) AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String TEAM_LEADER_TOTAL_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'TEAM_LEADER' AND status <> 'DELETED'
            """;
    static final String TEAM_LEADER_ONLINE_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'TEAM_LEADER' AND status = 'ONLINE'
            """;
    static final String TEAM_LEADER_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE type = 'TEAM_LEADER' AND status <> 'DELETED' AND review_status = 'PENDING'
            """;
    static final String TEAM_LEADER_FAVORITES_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_favorites favorite
            JOIN hhy.content_posts post ON post.id = favorite.content_id
            WHERE post.type = 'TEAM_LEADER' AND post.status <> 'DELETED'
            """;
    static final String TEAM_LEADER_CONTACT_ACCESSES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'TEAM_LEADER'
              AND access_log.action IN ('VIEW', 'COPY', 'REPLAY')
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String TEAM_LEADER_CONTACT_REJECTIONS_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs access_log
            JOIN hhy.content_posts post ON post.id = access_log.content_id
            WHERE post.type = 'TEAM_LEADER'
              AND access_log.action LIKE 'REJECTED_%'
              AND access_log.created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String R11_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE event_type IN (
                'content.team-leader.created.v1', 'content.team-leader.updated.v1',
                'content.favorited.v1', 'content.favorite.replayed.v1',
                'content.shared.v1', 'chat.direct.created.v1',
                'content.team-leader.stage.alert.v1'
            ) AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String PUBLISH_TOTAL_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status <> 'DELETED'
            """;
    static final String PUBLISH_DRAFT_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status = 'DRAFT'
            """;
    static final String PUBLISH_REVIEW_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status = 'PENDING_REVIEW'
            """;
    static final String PUBLISH_REVIEWING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status = 'REVIEWING'
            """;
    static final String PUBLISH_REJECTED_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status = 'REJECTED'
            """;
    static final String PUBLISH_ONLINE_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_posts
            WHERE status = 'ONLINE'
            """;
    static final String R12_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE event_type IN (
                'content.created.v1', 'content.updated.v1', 'content.status.changed.v1',
                'content.submitted.v1', 'content.review.assigned.v1',
                'content.review.escalated.v1', 'content.review.decided.v1',
                'content.r12.stage.alert.v1'
            ) AND status IN ('PENDING', 'RETRY_WAIT')
            """;
    static final String ACTIVITY_FAVORITES_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_favorites
            """;
    static final String ACTIVITY_HISTORY_ROWS_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_view_logs
            WHERE traffic_type <> 'SHARE'
            """;
    static final String ACTIVITY_SHARES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_view_logs
            WHERE traffic_type = 'SHARE'
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String ACTIVITY_CONTACT_ACCESSES_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs
            WHERE action IN ('VIEW', 'COPY', 'REPLAY')
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String ACTIVITY_CONTACT_REJECTIONS_5M_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_contact_access_logs
            WHERE action LIKE 'REJECTED_%'
              AND created_at >= CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
            """;
    static final String ACTIVITY_INVALID_FEEDBACK_PENDING_SQL = """
            SELECT COUNT(*)
            FROM hhy.content_reports
            WHERE status = 'PENDING'
            """;
    static final String R13_OUTBOX_BACKLOG_SQL = """
            SELECT COUNT(*)
            FROM hhy.outbox_events
            WHERE event_type IN (
                'content.favorited.v1', 'content.favorite.replayed.v1',
                'content.unfavorited.v1', 'content.shared.v1',
                'content.contact.accessed.v1', 'content.invalid-feedback.created.v1',
                'content.r13.stage.alert.v1'
            ) AND status IN ('PENDING', 'RETRY_WAIT')
            """;

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessGaugeBinder.class);
    private final JdbcTemplate jdbcTemplate;

    public BusinessGaugeBinder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        register(registry, "hhy.outbox.backlog", "Outbox events waiting for delivery", OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.outbox.dead.letter", "Outbox events in dead-letter state", OUTBOX_DEAD_LETTER_SQL);
        register(registry, "hhy.ledger.unbalanced.transactions", "Posted ledger transactions violating balance invariants", LEDGER_IMBALANCE_SQL);
        register(registry, "hhy.reconciliation.open.differences", "Reconciliation differences awaiting resolution", RECONCILIATION_OPEN_DIFFERENCES_SQL);
        register(registry, "hhy.admin.active.sessions", "Non-revoked administrator sessions that have not expired", ADMIN_ACTIVE_SESSIONS_SQL);
        register(registry, "hhy.admin.auth.failures.5m", "Rejected administrator authentication attempts in the last five minutes", ADMIN_AUTH_FAILURES_5M_SQL);
        register(registry, "hhy.admin.mfa.active.methods", "Active administrator MFA methods", ADMIN_MFA_ACTIVE_METHODS_SQL);
        register(registry, "hhy.admin.idempotency.incomplete.snapshots", "Administrator idempotency records with a partial encrypted response snapshot", ADMIN_IDEMPOTENCY_INCOMPLETE_SNAPSHOTS_SQL);
        register(registry, "hhy.user.active.sessions", "Active user refresh sessions that have not expired", USER_ACTIVE_SESSIONS_SQL);
        register(registry, "hhy.user.security.challenge.failures.5m", "Rejected user security challenge proofs in the last five minutes", USER_SECURITY_CHALLENGE_FAILURES_5M_SQL);
        register(registry, "hhy.user.sms.expired.unused", "Expired user SMS verification codes that were never consumed", USER_EXPIRED_UNUSED_SMS_CODES_SQL);
        register(registry, "hhy.provider.connection.test.failures.5m", "Failed R03 provider safe probes in the last five minutes", PROVIDER_CONNECTION_TEST_FAILURES_5M_SQL);
        register(registry, "hhy.provider.config.untested.active", "Active provider configurations without a successful connection test", PROVIDER_UNTESTED_ACTIVE_CONFIGS_SQL);
        register(registry, "hhy.provider.certificates.expiring.30d", "Active provider certificates expiring in the next thirty days", PROVIDER_CERTIFICATES_EXPIRING_30D_SQL);
        register(registry, "hhy.domain.verification.failures", "Domains failing DNS, HTTPS, certificate, or service-health verification", DOMAIN_VERIFICATION_FAILURES_SQL);
        register(registry, "hhy.media.upload.failures.5m", "Failed media upload sessions in the last five minutes", MEDIA_UPLOAD_FAILURES_5M_SQL);
        register(registry, "hhy.media.upload.expired.open", "Expired media upload sessions still in an open state", MEDIA_UPLOAD_EXPIRED_OPEN_SQL);
        register(registry, "hhy.media.delete.pending", "Media objects waiting for provider deletion", MEDIA_DELETE_PENDING_SQL);
        register(registry, "hhy.storage.migration.blocked", "Storage migration jobs paused or failed", STORAGE_MIGRATION_BLOCKED_SQL);
        register(registry, "hhy.identity.active.sessions", "Identity sessions requiring further processing", IDENTITY_ACTIVE_SESSIONS_SQL);
        register(registry, "hhy.identity.provider.failures.5m", "Identity provider failures or timeouts in the last five minutes", IDENTITY_PROVIDER_FAILURES_5M_SQL);
        register(registry, "hhy.identity.manual.review.pending", "Identity sessions waiting for manual review", IDENTITY_MANUAL_REVIEW_PENDING_SQL);
        register(registry, "hhy.identity.private.media.invalid", "Identity media missing a private ready media object", IDENTITY_PRIVATE_MEDIA_INVALID_SQL);
        register(registry, "hhy.content.online.count", "Content records currently available online", CONTENT_ONLINE_TOTAL_SQL);
        register(registry, "hhy.content.review.pending", "Content records waiting for review", CONTENT_REVIEW_PENDING_SQL);
        register(registry, "hhy.content.outbox.backlog", "Content outbox events waiting for delivery", CONTENT_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.home.enabled.modules", "Enabled non-dictionary home modules", HOME_ENABLED_MODULES_SQL);
        register(registry, "hhy.search.history.rows", "Stored search-history rows", SEARCH_HISTORY_ROWS_SQL);
        register(registry, "hhy.search.hot.terms.active", "Enabled hot-search terms active now", SEARCH_HOT_TERMS_ACTIVE_SQL);
        register(registry, "hhy.publisher.active.count", "Active publishers with online content", PUBLISHER_ACTIVE_COUNT_SQL);
        register(registry, "hhy.contact.accesses.5m", "Successful contact accesses in the last five minutes", CONTACT_ACCESSES_5M_SQL);
        register(registry, "hhy.contact.rejections.5m", "Rejected contact accesses in the last five minutes", CONTACT_REJECTIONS_5M_SQL);
        register(registry, "hhy.r07.outbox.backlog", "R07 content and search-history events waiting for delivery", R07_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.project.total.count", "Non-deleted project records", PROJECT_TOTAL_SQL);
        register(registry, "hhy.project.online.count", "Project records currently online", PROJECT_ONLINE_SQL);
        register(registry, "hhy.project.review.pending", "Project records waiting for review", PROJECT_REVIEW_PENDING_SQL);
        register(registry, "hhy.project.favorites.count", "Favorite rows attached to non-deleted projects", PROJECT_FAVORITES_SQL);
        register(registry, "hhy.project.contact.accesses.5m", "Successful project contact accesses in the last five minutes", PROJECT_CONTACT_ACCESSES_5M_SQL);
        register(registry, "hhy.project.contact.rejections.5m", "Rejected project contact accesses in the last five minutes", PROJECT_CONTACT_REJECTIONS_5M_SQL);
        register(registry, "hhy.r08.outbox.backlog", "R08 project events waiting for delivery", R08_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.app.total.count", "Non-deleted App records", APP_TOTAL_SQL);
        register(registry, "hhy.app.online.count", "App records currently online", APP_ONLINE_SQL);
        register(registry, "hhy.app.review.pending", "App records waiting for review", APP_REVIEW_PENDING_SQL);
        register(registry, "hhy.app.favorites.count", "Favorite rows attached to non-deleted Apps", APP_FAVORITES_SQL);
        register(registry, "hhy.app.contact.accesses.5m", "Successful App contact accesses in the last five minutes", APP_CONTACT_ACCESSES_5M_SQL);
        register(registry, "hhy.app.contact.rejections.5m", "Rejected App contact accesses in the last five minutes", APP_CONTACT_REJECTIONS_5M_SQL);
        register(registry, "hhy.r09.outbox.backlog", "R09 App events waiting for delivery", R09_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.group.total.count", "Non-deleted group records", GROUP_TOTAL_SQL);
        register(registry, "hhy.group.online.count", "Group records currently online", GROUP_ONLINE_SQL);
        register(registry, "hhy.group.review.pending", "Group records waiting for review", GROUP_REVIEW_PENDING_SQL);
        register(registry, "hhy.group.favorites.count", "Favorite rows attached to non-deleted groups", GROUP_FAVORITES_SQL);
        register(registry, "hhy.group.contact.accesses.5m", "Successful group contact accesses in the last five minutes", GROUP_CONTACT_ACCESSES_5M_SQL);
        register(registry, "hhy.group.contact.rejections.5m", "Rejected group contact accesses in the last five minutes", GROUP_CONTACT_REJECTIONS_5M_SQL);
        register(registry, "hhy.r10.outbox.backlog", "R10 group events waiting for delivery", R10_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.team.leader.total.count", "Non-deleted team leader profiles", TEAM_LEADER_TOTAL_SQL);
        register(registry, "hhy.team.leader.online.count", "Team leader profiles currently online", TEAM_LEADER_ONLINE_SQL);
        register(registry, "hhy.team.leader.review.pending", "Team leader profiles waiting for review", TEAM_LEADER_REVIEW_PENDING_SQL);
        register(registry, "hhy.team.leader.favorites.count", "Favorite rows attached to non-deleted team leader profiles", TEAM_LEADER_FAVORITES_SQL);
        register(registry, "hhy.team.leader.contact.accesses.5m", "Successful team leader contact accesses in the last five minutes", TEAM_LEADER_CONTACT_ACCESSES_5M_SQL);
        register(registry, "hhy.team.leader.contact.rejections.5m", "Rejected team leader contact accesses in the last five minutes", TEAM_LEADER_CONTACT_REJECTIONS_5M_SQL);
        register(registry, "hhy.r11.outbox.backlog", "R11 team leader events waiting for delivery", R11_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.publish.total.count", "Non-deleted content managed by unified publishing", PUBLISH_TOTAL_SQL);
        register(registry, "hhy.publish.draft.count", "Unified publishing drafts", PUBLISH_DRAFT_SQL);
        register(registry, "hhy.publish.review.pending", "Unified publishing submissions waiting for review", PUBLISH_REVIEW_PENDING_SQL);
        register(registry, "hhy.publish.reviewing.count", "Unified publishing submissions under active review", PUBLISH_REVIEWING_SQL);
        register(registry, "hhy.publish.rejected.count", "Unified publishing submissions rejected by review", PUBLISH_REJECTED_SQL);
        register(registry, "hhy.publish.online.count", "Unified publishing content currently online", PUBLISH_ONLINE_SQL);
        register(registry, "hhy.r12.outbox.backlog", "R12 publishing and review events waiting for delivery", R12_OUTBOX_BACKLOG_SQL);
        register(registry, "hhy.activity.favorites.count", "Favorite rows across content activity", ACTIVITY_FAVORITES_SQL);
        register(registry, "hhy.activity.history.rows", "Non-share content activity history rows", ACTIVITY_HISTORY_ROWS_SQL);
        register(registry, "hhy.activity.shares.5m", "Content share events in the last five minutes", ACTIVITY_SHARES_5M_SQL);
        register(registry, "hhy.activity.contact.accesses.5m", "Successful activity contact accesses in the last five minutes", ACTIVITY_CONTACT_ACCESSES_5M_SQL);
        register(registry, "hhy.activity.contact.rejections.5m", "Rejected activity contact accesses in the last five minutes", ACTIVITY_CONTACT_REJECTIONS_5M_SQL);
        register(registry, "hhy.activity.invalid.feedback.pending", "Invalid-contact feedback awaiting handling", ACTIVITY_INVALID_FEEDBACK_PENDING_SQL);
        register(registry, "hhy.r13.outbox.backlog", "R13 activity events waiting for delivery", R13_OUTBOX_BACKLOG_SQL);
    }

    private void register(MeterRegistry registry, String name, String description, String sql) {
        Counter queryFailures = Counter.builder("hhy.business.metric.query.failures")
                .description("Business gauge database query failures")
                .tag("metric", name)
                .register(registry);
        Gauge.builder(name, this, ignored -> query(name, sql, queryFailures))
                .description(description)
                .strongReference(true)
                .register(registry);
    }

    private double query(String metric, String sql, Counter queryFailures) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0.0 : value.doubleValue();
        } catch (DataAccessException exception) {
            queryFailures.increment();
            LOGGER.atWarn()
                    .addKeyValue("metric", metric)
                    .addKeyValue("errorType", exception.getClass().getSimpleName())
                    .log("business_metric_query_failed");
            return Double.NaN;
        }
    }
}
