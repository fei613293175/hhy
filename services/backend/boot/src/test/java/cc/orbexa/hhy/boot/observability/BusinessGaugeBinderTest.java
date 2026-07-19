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
        jdbc.execute("CREATE TABLE hhy.user_sessions (id bigint PRIMARY KEY, refresh_hash varchar(128), expires_at timestamp with time zone)");
        jdbc.execute("CREATE TABLE hhy.auth_security_challenges (id bigint PRIMARY KEY, attempts integer NOT NULL, created_at timestamp with time zone NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.sms_verification_codes (id bigint PRIMARY KEY, used_at timestamp with time zone, expires_at timestamp with time zone NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.provider_connection_tests (id bigint PRIMARY KEY, test_type varchar(255), created_at timestamp with time zone NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.provider_config_versions (id bigint PRIMARY KEY, status varchar(64) NOT NULL, connection_successful boolean)");
        jdbc.execute("CREATE TABLE hhy.provider_certificates (id bigint PRIMARY KEY, status varchar(64) NOT NULL, valid_to timestamp with time zone)");
        jdbc.execute("CREATE TABLE hhy.domain_configs (id bigint PRIMARY KEY, dns_status varchar(32) NOT NULL, https_status varchar(32) NOT NULL, certificate_status varchar(32) NOT NULL, service_health_status varchar(32) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.upload_sessions (id bigint PRIMARY KEY, status varchar(32) NOT NULL, expires_at timestamp with time zone NOT NULL, updated_at timestamp with time zone NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.media_objects (id bigint PRIMARY KEY, status varchar(32), visibility varchar(32))");
        jdbc.execute("CREATE TABLE hhy.storage_migration_jobs (id bigint PRIMARY KEY, status varchar(32) NOT NULL)");
        jdbc.execute("CREATE TABLE hhy.identity_verification_sessions (id bigint PRIMARY KEY, status varchar(32) NOT NULL, completed_at timestamp with time zone)");
        jdbc.execute("CREATE TABLE hhy.identity_provider_requests (id bigint PRIMARY KEY, status varchar(32), completed_at timestamp with time zone)");
        jdbc.execute("CREATE TABLE hhy.identity_media (id bigint PRIMARY KEY, media_object_id bigint)");

        jdbc.update("INSERT INTO hhy.outbox_events(id, status) VALUES (1, 'PENDING'), (2, 'RETRY_WAIT'), (3, 'DEAD_LETTER'), (4, 'PUBLISHED')");
        jdbc.update("INSERT INTO hhy.ledger_accounts(id, currency) VALUES (10, 'CNY'), (11, 'CNY')");
        jdbc.update("INSERT INTO hhy.accounting_transactions(id, status) VALUES (20, 'POSTED'), (21, 'POSTED')");
        jdbc.update("INSERT INTO hhy.accounting_entries(id, transaction_id, account_id, amount_cent, currency, direction) VALUES "
                + "(100, 20, 10, 500, 'CNY', 'DEBIT'), (101, 20, 11, 500, 'CNY', 'CREDIT'), "
                + "(102, 21, 10, 700, 'CNY', 'DEBIT'), (103, 21, 11, 600, 'CNY', 'CREDIT')");
        jdbc.update("INSERT INTO hhy.reconciliation_differences(id, status) VALUES (30, 'OPEN'), (31, 'RESOLVED')");
        jdbc.update("INSERT INTO hhy.admin_sessions(id, expires_at, revoked_at) VALUES "
                + "(40, CURRENT_TIMESTAMP + INTERVAL '1' HOUR, NULL), "
                + "(41, CURRENT_TIMESTAMP - INTERVAL '1' HOUR, NULL), "
                + "(42, CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO hhy.admin_login_logs(id, result, created_at) VALUES "
                + "(50, 'FAILED', CURRENT_TIMESTAMP), "
                + "(51, 'SUCCESS', CURRENT_TIMESTAMP), "
                + "(52, 'FAILED', CURRENT_TIMESTAMP - INTERVAL '10' MINUTE)");
        jdbc.update("INSERT INTO hhy.admin_mfa_methods(id, status) VALUES (60, 'ACTIVE'), (61, 'DISABLED')");
        jdbc.update("INSERT INTO hhy.idempotency_records(id, scope, response_type, response_payload_ciphertext) VALUES "
                + "(70, 'admin.login', 'LoginResponse', 'ciphertext'), "
                + "(71, 'admin.logout', 'LogoutResponse', NULL), "
                + "(72, 'public.status', 'StatusResponse', NULL)");
        jdbc.update("INSERT INTO hhy.user_sessions(id, refresh_hash, expires_at) VALUES "
                + "(80, 'active-refresh', CURRENT_TIMESTAMP + INTERVAL '1' HOUR), "
                + "(81, NULL, CURRENT_TIMESTAMP + INTERVAL '1' HOUR), "
                + "(82, 'expired-refresh', CURRENT_TIMESTAMP - INTERVAL '1' HOUR)");
        jdbc.update("INSERT INTO hhy.auth_security_challenges(id, attempts, created_at) VALUES "
                + "(90, 2, CURRENT_TIMESTAMP), (91, 0, CURRENT_TIMESTAMP), "
                + "(92, 4, CURRENT_TIMESTAMP - INTERVAL '10' MINUTE)");
        jdbc.update("INSERT INTO hhy.sms_verification_codes(id, used_at, expires_at) VALUES "
                + "(100, NULL, CURRENT_TIMESTAMP - INTERVAL '1' MINUTE), "
                + "(101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1' MINUTE), "
                + "(102, NULL, CURRENT_TIMESTAMP + INTERVAL '1' MINUTE)");
        jdbc.update("INSERT INTO hhy.provider_connection_tests(id, test_type, created_at) VALUES "
                + "(110, 'R03_SAFE_PROBE:TIMEOUT', CURRENT_TIMESTAMP), "
                + "(111, 'R03_SAFE_PROBE:OK', CURRENT_TIMESTAMP), "
                + "(112, 'R03_SAFE_PROBE:PROVIDER_ERROR', CURRENT_TIMESTAMP - INTERVAL '10' MINUTE)");
        jdbc.update("INSERT INTO hhy.provider_config_versions(id, status, connection_successful) VALUES "
                + "(120, 'ACTIVE', FALSE), (121, 'ACTIVE', TRUE), (122, 'DRAFT', NULL)");
        jdbc.update("INSERT INTO hhy.provider_certificates(id, status, valid_to) VALUES "
                + "(130, 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '20' DAY), "
                + "(131, 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '60' DAY), "
                + "(132, 'ROTATED', CURRENT_TIMESTAMP + INTERVAL '5' DAY)");
        jdbc.update("INSERT INTO hhy.domain_configs(id, dns_status, https_status, certificate_status, service_health_status) VALUES "
                + "(140, 'FAILED', 'PENDING', 'PENDING', 'PENDING'), "
                + "(141, 'PASSED', 'PASSED', 'VALID', 'PASSED')");
        jdbc.update("INSERT INTO hhy.upload_sessions(id, status, expires_at, updated_at) VALUES "
                + "(150, 'FAILED', CURRENT_TIMESTAMP + INTERVAL '5' MINUTE, CURRENT_TIMESTAMP), "
                + "(151, 'CREATED', CURRENT_TIMESTAMP - INTERVAL '1' MINUTE, CURRENT_TIMESTAMP), "
                + "(152, 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1' MINUTE, CURRENT_TIMESTAMP), "
                + "(153, 'FAILED', CURRENT_TIMESTAMP + INTERVAL '5' MINUTE, CURRENT_TIMESTAMP - INTERVAL '10' MINUTE)");
        jdbc.update("INSERT INTO hhy.media_objects(id, status, visibility) VALUES "
                + "(160, 'DELETE_PENDING', 'PRIVATE'), (161, 'READY', 'PRIVATE'), (162, 'READY', 'PUBLIC')");
        jdbc.update("INSERT INTO hhy.storage_migration_jobs(id, status) VALUES "
                + "(170, 'PAUSED'), (171, 'FAILED'), (172, 'RUNNING')");
        jdbc.update("INSERT INTO hhy.identity_verification_sessions(id, status, completed_at) VALUES "
                + "(180, 'LIVENESS_PENDING', NULL), (181, 'MANUAL_REVIEW', NULL), "
                + "(182, 'VERIFIED', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO hhy.identity_provider_requests(id, status, completed_at) VALUES "
                + "(190, 'FAILED', CURRENT_TIMESTAMP), "
                + "(191, 'TIMED_OUT', CURRENT_TIMESTAMP - INTERVAL '10' MINUTE), "
                + "(192, 'SUCCEEDED', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO hhy.identity_media(id, media_object_id) VALUES (200, 161), (201, 162)");

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
        assertThat(registry.get("hhy.user.active.sessions").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.user.security.challenge.failures.5m").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("hhy.user.sms.expired.unused").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.provider.connection.test.failures.5m").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.provider.config.untested.active").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.provider.certificates.expiring.30d").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.domain.verification.failures").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.media.upload.failures.5m").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.media.upload.expired.open").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.media.delete.pending").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.storage.migration.blocked").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("hhy.identity.active.sessions").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("hhy.identity.provider.failures.5m").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.identity.manual.review.pending").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("hhy.identity.private.media.invalid").gauge().value()).isEqualTo(1.0);
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
