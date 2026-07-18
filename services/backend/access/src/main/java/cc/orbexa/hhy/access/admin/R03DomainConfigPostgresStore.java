package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.DomainConfigPolicy.CertificateMode;
import cc.orbexa.hhy.access.admin.DomainConfigPolicy.Environment;
import cc.orbexa.hhy.access.admin.DomainConfigPolicy.ValidatedDomain;
import cc.orbexa.hhy.access.admin.DomainConfigService.AuditEvent;
import cc.orbexa.hhy.access.admin.DomainConfigService.DnsAction;
import cc.orbexa.hhy.access.admin.DomainConfigService.DomainAggregate;
import cc.orbexa.hhy.access.admin.DomainConfigService.DomainStatus;
import cc.orbexa.hhy.access.admin.DomainConfigService.UpdateReceipt;
import cc.orbexa.hhy.access.admin.DomainConfigService.VerificationReceipt;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.LayerStatus;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.VerificationResult;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable domain-plan adapter with immutable per-command replay snapshots. */
@Component
public class R03DomainConfigPostgresStore
        implements DomainConfigService.Store, DomainConfigService.TransactionRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public R03DomainConfigPostgresStore(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    @Override
    public Optional<DomainAggregate> findForUpdate(String code) {
        return jdbc.query("""
                SELECT id,code,environment,host,certificate_mode,status,verified_at,version
                FROM hhy.domain_configs WHERE code=? FOR UPDATE
                """, (rs, row) -> aggregate(rs, row), code).stream().findFirst();
    }

    @Override
    public Optional<UpdateReceipt> updateReceiptForUpdate(
            String code, String idempotencyKey) {
        return receipt(code, "UPDATE", idempotencyKey)
                .map(value -> new UpdateReceipt(
                        value.idempotencyKey(), value.fingerprint(), value.result()));
    }

    @Override
    public Optional<VerificationReceipt> verificationReceiptForUpdate(
            String code, String idempotencyKey) {
        return receipt(code, "VERIFY", idempotencyKey)
                .map(value -> new VerificationReceipt(
                        value.idempotencyKey(), value.fingerprint(), value.result()));
    }

    @Override
    public void saveUpdate(
            DomainAggregate previous, DomainAggregate updated, DnsAction action,
            UpdateReceipt receipt, AuditEvent audit) {
        long domainId = id(previous.config().code());
        requireOne(jdbc.update("""
                UPDATE hhy.domain_configs
                SET host=?,certificate_mode=?,status=?,dns_status='PENDING',https_status='PENDING',
                    certificate_status='PENDING',service_health_status='PENDING',
                    last_probe_code=NULL,verified_at=NULL,last_verified_at=NULL,version=?
                WHERE id=? AND version=?
                """, updated.config().hostname(), updated.config().certificateMode().name(),
                updated.status().name(), updated.version(), domainId, previous.version()));
        jdbc.update("""
                INSERT INTO hhy.dns_action_items(
                  domain_config_id,record_type,name,record_value,status,user_action_required,
                  owner_admin_id,last_error_code)
                VALUES (?,?,?,?,?,?,?,NULL)
                """, domainId, action.recordType(), action.hostname(), action.hostname(),
                action.status(), action.userActionRequired(), audit.actorId());
        insertReceipt(domainId, "UPDATE", receipt.idempotencyKey(),
                receipt.fingerprint(), receipt.result());
        audit(audit, domainId);
    }

    @Override
    public void saveVerification(
            DomainAggregate previous, DomainAggregate updated, VerificationResult verification,
            VerificationReceipt receipt, AuditEvent audit) {
        long domainId = id(previous.config().code());
        requireOne(jdbc.update("""
                UPDATE hhy.domain_configs
                SET status=?,dns_status=?,https_status=?,certificate_status=?,
                    service_health_status=?,last_probe_code=?,verified_at=?,last_verified_at=?,version=?
                WHERE id=? AND version=?
                """, updated.status().name(), layer(verification.dns().status()),
                layer(verification.tls().status()), verification.certificateStatus(),
                layer(verification.service().status()), lastCode(verification),
                time(updated.verifiedAt()), time(verification.attemptedAt()),
                updated.version(), domainId, previous.version()));
        if (verification.dns().status() == LayerStatus.PASSED) {
            jdbc.update("""
                    UPDATE hhy.dns_action_items
                    SET status='VERIFIED',last_error_code=NULL
                    WHERE domain_config_id=? AND user_action_required=true
                      AND status NOT IN ('VERIFIED','COMPLETED','CANCELLED')
                    """, domainId);
        } else {
            jdbc.update("""
                    UPDATE hhy.dns_action_items SET last_error_code=?
                    WHERE domain_config_id=? AND user_action_required=true
                      AND status NOT IN ('VERIFIED','COMPLETED','CANCELLED')
                    """, verification.dns().code(), domainId);
        }
        insertReceipt(domainId, "VERIFY", receipt.idempotencyKey(),
                receipt.fingerprint(), receipt.result());
        audit(audit, domainId);
    }

    private Optional<ReceiptRow> receipt(String code, String operation, String key) {
        return jdbc.query("""
                SELECT receipt.idempotency_key,receipt.fingerprint,receipt.result_json
                FROM hhy.domain_command_receipts receipt
                JOIN hhy.domain_configs domain ON domain.id=receipt.domain_config_id
                WHERE domain.code=? AND receipt.operation=? AND receipt.idempotency_key=?
                FOR UPDATE OF receipt
                """, (rs, row) -> new ReceiptRow(
                rs.getString("idempotency_key"), rs.getString("fingerprint"),
                aggregate(rs.getString("result_json"))), code, operation, key)
                .stream().findFirst();
    }

    private void insertReceipt(
            long domainId, String operation, String key,
            String fingerprint, DomainAggregate result) {
        jdbc.update("""
                INSERT INTO hhy.domain_command_receipts(
                  domain_config_id,operation,idempotency_key,fingerprint,result_json,expires_at)
                VALUES (?,?,?,?,?::jsonb,clock_timestamp()+interval '100 years')
                """, domainId, operation, key, fingerprint, json(result));
    }

    private DomainAggregate aggregate(ResultSet rs, int row) throws SQLException {
        ValidatedDomain config = new ValidatedDomain(
                rs.getString("code"), Environment.valueOf(rs.getString("environment")),
                rs.getString("host"), CertificateMode.valueOf(rs.getString("certificate_mode")),
                "orbexa.cc");
        return new DomainAggregate(config, DomainStatus.valueOf(rs.getString("status")), null,
                instant(rs.getObject("verified_at", OffsetDateTime.class)), rs.getLong("version"));
    }

    private DomainAggregate aggregate(String json) {
        try {
            return objectMapper.readValue(json, DomainAggregate.class);
        } catch (Exception invalidSnapshot) {
            throw new IllegalStateException("Stored domain command receipt is invalid", invalidSnapshot);
        }
    }

    private long id(String code) {
        Long id = jdbc.queryForObject(
                "SELECT id FROM hhy.domain_configs WHERE code=?", Long.class, code);
        if (id == null) throw new BusinessException(
                "COMMON-404-NOT_FOUND", "域名配置不存在", 404, false);
        return id;
    }

    private void audit(AuditEvent event, long domainId) {
        jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,after_json)
                VALUES (?,?,?,?,?::jsonb)
                """, event.actorId(), event.action(), "DOMAIN_CONFIG", domainId,
                json(Map.of(
                        "code", event.code(), "detail", event.detail(),
                        "createdAt", event.createdAt().toString())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Domain command serialization failed", failure);
        }
    }

    private static String layer(LayerStatus value) {
        return value == LayerStatus.NOT_RUN ? "PENDING" : value.name();
    }

    private static String lastCode(VerificationResult result) {
        if (result.dns().status() == LayerStatus.FAILED) return result.dns().code();
        if (result.tls().status() == LayerStatus.FAILED) return result.tls().code();
        return result.service().code();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireOne(int changed) {
        if (changed != 1) throw new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "域名配置已变化，请刷新后重试", 409, false);
    }

    private record ReceiptRow(
            String idempotencyKey, String fingerprint, DomainAggregate result) { }
}
