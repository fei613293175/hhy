package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageMigrationService.AuditEvent;
import cc.orbexa.hhy.access.storage.StorageMigrationService.BindingRecord;
import cc.orbexa.hhy.access.storage.StorageMigrationService.Job;
import cc.orbexa.hhy.access.storage.StorageMigrationService.JobStatus;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL state and audit adapter for resumable R04 storage migrations. */
@Component
public class R04StoragePostgresStore
        implements StorageMigrationService.Store, StorageMigrationService.TransactionRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public R04StoragePostgresStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
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
    public Optional<Job> findJobForUpdate(long jobId) {
        return jdbc.query("""
                SELECT id,source_binding_id,target_binding_id,scope_code,cursor,total,
                       COALESCE(success,0) success,COALESCE(failed,0) failed,status
                FROM hhy.storage_migration_jobs WHERE id=? FOR UPDATE
                """, this::job, jobId).stream().findFirst();
    }

    @Override
    public Optional<BindingRecord> findBinding(long bindingId) {
        return jdbc.query("""
                SELECT binding.id,binding.scope_code,binding.provider_code,binding.bucket,
                       COALESCE(NULLIF(binding.public_domain,''),
                         CASE binding.provider_code
                           WHEN 'CLOUDFLARE_R2' THEN config.values_json->>'storage.r2.public_domain'
                           WHEN 'ALIYUN_OSS' THEN config.values_json->>'storage.aliyun_oss.public_domain'
                         END) AS public_domain,
                       CASE binding.provider_code
                         WHEN 'CLOUDFLARE_R2' THEN config.values_json->>'storage.r2.endpoint'
                         WHEN 'ALIYUN_OSS' THEN config.values_json->>'storage.aliyun_oss.endpoint'
                       END AS endpoint
                FROM hhy.storage_scope_bindings binding
                JOIN hhy.provider_config_versions config ON config.id=binding.config_version_id
                WHERE binding.id=? AND binding.status IN ('VERIFIED','ACTIVE','INACTIVE')
                """, this::binding, bindingId).stream().findFirst();
    }

    @Override
    public void save(Job previous, Job updated) {
        int changed = jdbc.update("""
                UPDATE hhy.storage_migration_jobs
                SET cursor=?,total=?,success=?,failed=?,status=?,updated_at=now()
                WHERE id=? AND status=? AND cursor IS NOT DISTINCT FROM ?
                """, updated.cursor(), updated.total(), updated.success(), updated.failed(),
                updated.status().name(), previous.id(), previous.status().name(), previous.cursor());
        if (changed != 1) throw new IllegalStateException("storage migration job changed concurrently");
    }

    @Override
    public void audit(AuditEvent event) {
        jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,after_json)
                VALUES (?,?,?,?,?::jsonb)
                """, event.actorId(), event.action(), "STORAGE_MIGRATION_JOB", event.jobId(), json(Map.of(
                "scope", event.scope().name().toLowerCase(java.util.Locale.ROOT),
                "batchObjects", event.batchObjects(), "success", event.success(),
                "failed", event.failed(), "createdAt", event.createdAt().toString())));
    }

    private Job job(ResultSet rs, int row) throws SQLException {
        return new Job(rs.getLong("id"), rs.getLong("source_binding_id"),
                rs.getLong("target_binding_id"), scope(rs.getString("scope_code")),
                rs.getString("cursor"), rs.getInt("total"), rs.getInt("success"),
                rs.getInt("failed"), JobStatus.valueOf(rs.getString("status")));
    }

    private BindingRecord binding(ResultSet rs, int row) throws SQLException {
        Scope scope = scope(rs.getString("scope_code"));
        URI publicBase = scope.privateAccess() ? null : publicUri(rs.getString("public_domain"));
        return new BindingRecord(rs.getLong("id"), new Binding(scope,
                Provider.valueOf(rs.getString("provider_code")), rs.getString("bucket"),
                uri(rs.getString("endpoint"), "storage endpoint"), publicBase));
    }

    private static Scope scope(String value) {
        return Scope.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }

    private static URI publicUri(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("public storage domain is missing");
        String normalized = value.contains("://") ? value : "https://" + value;
        if (!normalized.endsWith("/")) normalized += "/";
        return uri(normalized, "public storage domain");
    }

    private static URI uri(String value, String label) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(label + " is invalid", invalid);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("storage audit serialization failed", failure);
        }
    }
}
