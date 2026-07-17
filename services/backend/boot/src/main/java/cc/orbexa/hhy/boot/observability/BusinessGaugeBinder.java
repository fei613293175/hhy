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
