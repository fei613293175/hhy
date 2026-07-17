package cc.orbexa.hhy.boot.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessGaugeBinderTest {
    @Test
    void gaugesReflectOperationalFactsWithoutReadingSensitiveColumns() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:business-gauges;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS hhy");
        jdbc.execute("CREATE TABLE hhy.outbox_events (id bigint PRIMARY KEY, status varchar(64) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.ledger_accounts (id bigint PRIMARY KEY, currency varchar(8) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.accounting_transactions (id bigint PRIMARY KEY, status varchar(64) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.accounting_entries (id bigint PRIMARY KEY, transaction_id bigint NOT NULL, account_id bigint NOT NULL, amount_cent bigint NOT NULL, currency varchar(8) NOT NULL, direction varchar(64) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.reconciliation_differences (id bigint PRIMARY KEY, status varchar(64) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.admin_sessions (id bigint PRIMARY KEY, expires_at timestamp with time zone NOT NULL, revoked_at timestamp with time zone)");
        jdbc.execute("CREATE TABLE hhy.admin_login_logs (id bigint PRIMARY KEY, result varchar(32) NOT NULL, created_at timestamp with time zone NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.admin_mfa_methods (id bigint PRIMARY KEY, status varchar(32) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.idempotency_records (id bigint PRIMARY KEY, scope varchar(64) NOT NULL, response_type varchar(128), response_payload_ciphertext text)");

        jdbc.update("INSERT INTO hhy.outbox_events(id, status) VALUES (1, 'PENDING'), (2, 'RETRY_WAIT'), (3, 'DEAD_LETTER'), (4, 'PUBLISHED')");
        jdbc.update("INSERT INTO hhy.ledger_accounts(id, currency) VALUES (10, 'CNY'), (11, 'CNY')");
        jdbc.update("INSERT INTO hhy.accounting_transactions(id, status) VALUES (20, 'POSTED'), (21, 'POSTED')");
        jdbc.update("INSERT INTO hhy.accounting_entries(id, transaction_id, account_id, amount_cent, currency, direction) VALUES "
                + "(100, 20, 10, 500, 'CNY', 'DEBIT'), (101, 20, 11, 500, 'CNY', 'CREDIT'), "
                + "(102, 21, 10, 700, 'CNY', 'DEBIT'), (103, 21, 11, 600, 'CNY', 'CREDIT')");
        jdbc.update("INSERT INTO hhy.reconciliation_differences(id, status) VALUES (30, 'OPEN'), (31, 'RESOLVED')");
        jdbc.update("INSERT INTO hhy.admin_sessions(id, expires_at, revoked_at) VALUES "
                + "(40, CURRENT_TIMESTAMP + INTERVAL '1 hour', NULL), "
                + "(41, CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL), "
                + "(42, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO hhy.admin_login_logs(id, result, created_at) VALUES "
                + "(50, 'FAILED', CURRENT_TIMESTAMP), "
                + "(51, 'SUCCESS', CURRENT_TIMESTAMP), "
                + "(52, 'FAILED', CURRENT_TIMESTAMP - INTERVAL '10 minutes')");
        jdbc.update("INSERT INTO hhy.admin_mfa_methods(id, status) VALUES (60, 'ACTIVE'), (61, 'DISABLED')");
        jdbc.update("INSERT INTO hhy.idempotency_records(id, scope, response_type, response_payload_ciphertext) VALUES "
                + "(70, 'admin.login', 'LoginResponse', 'ciphertext'), "
                + "(71, 'admin.logout', 'LogoutResponse', NULL), "
                + "(72, 'public.status', 'StatusResponse', NULL)");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new BusinessGaugeBinder(jdbc).bindTo(registry);

        assertThat(registry.get("hhy.outbox.backlog").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("hhy.outbox.dead.letter").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.ledger.unbalanced.transactions").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.reconciliation.open.differences").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.admin.active.sessions").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.admin.auth.failures.5m").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.admin.mfa.active.methods").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.admin.idempotency.incomplete.snapshots").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.business.metric.query.failures")
                .tag("metric", "hhy.admin.active.sessions").counter().count()).isZero();
    }

    @Test
    void failedGaugeQueryIsObservableWithoutLeakingDatabaseDetails() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:business-gauge-failure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new BusinessGaugeBinder(new JdbcTemplate(dataSource)).bindTo(registry);

        assertThat(registry.get("hhy.outbox.backlog").gauge().value()).isNaN();
        assertThat(registry.get("hhy.business.metric.query.failures")
                .tag("metric", "hhy.outbox.backlog").counter().count()).isEqualTo(1.0);
    }
}
