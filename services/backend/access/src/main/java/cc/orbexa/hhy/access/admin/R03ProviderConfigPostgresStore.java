package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Snapshot;
import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Status;
import cc.orbexa.hhy.access.admin.ProviderConfigValidator.ValidatedConfig;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.Approval;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.ApprovalStatus;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.AuditEvent;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.VersionAggregate;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.TestOutcome;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL transaction adapter for the R03 immutable provider-version lifecycle. */
@Component
public class R03ProviderConfigPostgresStore
        implements ProviderConfigVersionService.Store, ProviderConfigVersionService.TransactionRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final ProviderConfigValidator validator;

    public R03ProviderConfigPostgresStore(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            ProviderConfigValidator validator) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    @Override
    public String nextVersionId(String provider) {
        return UUID.randomUUID().toString();
    }

    public long currentVersion(String provider, String versionId) {
        Long version = jdbc.query("""
                SELECT version FROM hhy.provider_config_versions
                WHERE provider_code=? AND version_no=?
                """, (rs, row) -> rs.getLong("version"),
                provider.toLowerCase(java.util.Locale.ROOT), versionId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(
                        "COMMON-404-NOT_FOUND", "供应商配置版本不存在", 404, false));
        return version;
    }

    @Override
    public Optional<VersionAggregate> findForUpdate(String provider, String versionId) {
        return select(" WHERE provider_code=? AND version_no=? FOR UPDATE", provider, versionId);
    }

    @Override
    public Optional<VersionAggregate> activeForUpdate(String provider) {
        return select(" WHERE provider_code=? AND status='ACTIVE' FOR UPDATE", provider);
    }

    @Override
    public Optional<Approval> approvalForUpdate(String approvalId) {
        long id;
        try {
            id = Long.parseLong(approvalId);
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT id,requester,reviewer,status FROM hhy.admin_approval_requests
                WHERE id=? FOR UPDATE
                """, (rs, row) -> new Approval(
                Long.toString(rs.getLong("id")), actor(rs.getString("requester")),
                actor(rs.getString("reviewer")), ApprovalStatus.valueOf(rs.getString("status"))),
                id).stream().findFirst();
    }

    @Override
    public void insert(VersionAggregate aggregate, AuditEvent audit) {
        Snapshot lifecycle = aggregate.lifecycle();
        ValidatedConfig config = aggregate.config();
        jdbc.update("""
                INSERT INTO hhy.provider_config_versions(
                  provider_code,version_no,status,created_by,environment,values_json,
                  secret_refs_json,remark,version)
                VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?)
                """, config.provider(), lifecycle.versionId(), lifecycle.status().name(),
                Long.toString(lifecycle.creatorId()), config.environment(), json(config.values()),
                json(config.secretRefs()), aggregate.remark(), lifecycle.version());
        audit(audit);
    }

    @Override
    public void saveConnectionTest(
            VersionAggregate previous, VersionAggregate updated, TestOutcome test,
            long actorId, AuditEvent audit) {
        Snapshot before = previous.lifecycle();
        Snapshot after = updated.lifecycle();
        requireOne(jdbc.update("""
                UPDATE hhy.provider_config_versions
                SET status=?,connection_successful=?,masked_test_result=?,tested_at=?,version=?
                WHERE provider_code=? AND version_no=? AND version=?
                """, after.status().name(), after.connectionSuccessful(), after.maskedTestResult(),
                time(after.testedAt()), after.version(), after.provider(), after.versionId(), before.version()));
        Long configId = jdbc.queryForObject("""
                SELECT id FROM hhy.provider_config_versions
                WHERE provider_code=? AND version_no=?
                """, Long.class, after.provider(), after.versionId());
        jdbc.update("""
                INSERT INTO hhy.provider_connection_tests(
                  provider_code,config_version_id,test_type,status,masked_result,requested_by)
                VALUES (?,?,?,'COMPLETED',?,?)
                """, after.provider(), configId, "R03_SAFE_PROBE:" + test.code().name(),
                after.maskedTestResult(), Long.toString(actorId));
        audit(audit);
    }

    @Override
    public void activateAtomically(
            VersionAggregate previousActive, VersionAggregate updatedPreviousActive,
            VersionAggregate target, VersionAggregate activatedTarget, AuditEvent audit) {
        if (previousActive != null) saveLifecycle(previousActive.lifecycle(),
                updatedPreviousActive.lifecycle());
        saveLifecycle(target.lifecycle(), activatedTarget.lifecycle());
        audit(audit);
    }

    @Override
    public void rollbackAtomically(
            VersionAggregate current, VersionAggregate rolledBackCurrent,
            VersionAggregate target, VersionAggregate restoredTarget, AuditEvent audit) {
        saveLifecycle(current.lifecycle(), rolledBackCurrent.lifecycle());
        saveLifecycle(target.lifecycle(), restoredTarget.lifecycle());
        audit(audit);
    }

    private Optional<VersionAggregate> select(String suffix, Object... arguments) {
        return jdbc.query("""
                SELECT provider_code,version_no,status,created_by,environment,values_json,
                       secret_refs_json,remark,connection_successful,masked_test_result,tested_at,
                       approval_ref,approval_requester_id,approval_reviewer_id,activated_at,version
                FROM hhy.provider_config_versions
                """ + suffix, this::aggregate, arguments).stream().findFirst();
    }

    private VersionAggregate aggregate(ResultSet rs, int row) throws SQLException {
        String provider = rs.getString("provider_code");
        ValidatedConfig config = validator.validate(provider, rs.getString("environment"),
                tree(rs.getString("values_json")), tree(rs.getString("secret_refs_json")));
        Snapshot lifecycle = new Snapshot(
                provider, rs.getString("version_no"), Status.valueOf(rs.getString("status")),
                actor(rs.getString("created_by")), rs.getObject("connection_successful", Boolean.class),
                rs.getString("masked_test_result"), instant(rs.getObject("tested_at", OffsetDateTime.class)),
                rs.getString("approval_ref"), nullableLong(rs, "approval_requester_id"),
                nullableLong(rs, "approval_reviewer_id"),
                instant(rs.getObject("activated_at", OffsetDateTime.class)), rs.getLong("version"));
        return new VersionAggregate(lifecycle, config, rs.getString("remark"));
    }

    private void saveLifecycle(Snapshot before, Snapshot after) {
        requireOne(jdbc.update("""
                UPDATE hhy.provider_config_versions
                SET status=?,connection_successful=?,masked_test_result=?,tested_at=?,
                    approval_ref=?,approval_requester_id=?,approval_reviewer_id=?,
                    activated_at=?,version=?
                WHERE provider_code=? AND version_no=? AND version=?
                """, after.status().name(), after.connectionSuccessful(), after.maskedTestResult(),
                time(after.testedAt()), after.approvalId(), after.approvalRequesterId(),
                after.approvalReviewerId(), time(after.activatedAt()), after.version(),
                before.provider(), before.versionId(), before.version()));
    }

    private void audit(AuditEvent event) {
        Long resourceId = jdbc.queryForObject("""
                SELECT id FROM hhy.provider_config_versions
                WHERE provider_code=? AND version_no=?
                """, Long.class, event.provider(), event.versionId());
        jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,after_json)
                VALUES (?,?,?,?,?::jsonb)
                """, event.actorId(), event.action(), "PROVIDER_CONFIG_VERSION", resourceId,
                json(Map.of(
                        "provider", event.provider(), "versionId", event.versionId(),
                        "detail", event.detail() == null ? "" : event.detail(),
                        "createdAt", event.createdAt().toString())));
    }

    private JsonNode tree(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception invalidDatabaseJson) {
            throw new IllegalStateException("Stored provider configuration JSON is invalid", invalidDatabaseJson);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception serializationFailure) {
            throw new IllegalStateException("Provider configuration serialization failed", serializationFailure);
        }
    }

    private static long actor(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException invalidLegacyActor) {
            throw new IllegalStateException("Stored administrator reference is invalid", invalidLegacyActor);
        }
    }

    private static Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireOne(int changed) {
        if (changed != 1) throw new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "配置版本已变化，请刷新后重试", 409, false);
    }
}
