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
