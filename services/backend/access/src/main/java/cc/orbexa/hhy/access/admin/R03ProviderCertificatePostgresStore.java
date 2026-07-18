package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderCertificateService.Approval;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.ApprovalStatus;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.AuditEvent;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.Status;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.StoredCertificate;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL metadata adapter; certificate bytes and private keys never enter SQL. */
@Component
public class R03ProviderCertificatePostgresStore
        implements ProviderCertificateService.Store, ProviderCertificateService.TransactionRunner {
    private static final String CERTIFICATE_PREFIX = "cert_";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public R03ProviderCertificatePostgresStore(
            JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    @Override
    public String nextId() {
        Long id = jdbc.queryForObject(
                "SELECT nextval(pg_get_serial_sequence('hhy.provider_certificates','id'))",
                Long.class);
        if (id == null) throw new IllegalStateException("Certificate identity sequence unavailable");
        return externalId(id);
    }

    @Override
    public Optional<StoredCertificate> findForUpdate(String id) {
        return jdbc.query("""
                SELECT id,provider_code,cert_type,alias,fingerprint,secret_ref,
                       valid_from,valid_to,status,version
                FROM hhy.provider_certificates WHERE id=? FOR UPDATE
                """, this::certificate, internalId(id)).stream().findFirst();
    }

    @Override
    public Optional<StoredCertificate> activeForUpdate(
            String provider, String certificateType) {
        return jdbc.query("""
                SELECT id,provider_code,cert_type,alias,fingerprint,secret_ref,
                       valid_from,valid_to,status,version
                FROM hhy.provider_certificates
                WHERE provider_code=? AND cert_type=? AND status='ACTIVE' FOR UPDATE
                """, this::certificate, provider, certificateType).stream().findFirst();
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
                WHERE id=? AND type='PROVIDER_CERTIFICATE_ROTATE' FOR UPDATE
                """, (rs, row) -> new Approval(
                Long.toString(rs.getLong("id")), actor(rs.getString("requester")),
                actor(rs.getString("reviewer")), ApprovalStatus.valueOf(rs.getString("status"))),
                id).stream().findFirst();
    }

    @Override
    public void insert(StoredCertificate certificate, AuditEvent audit) {
        jdbc.update("""
                INSERT INTO hhy.provider_certificates(
                  id,provider_code,cert_type,alias,fingerprint,secret_ref,valid_from,
                  valid_to,status,created_by,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, internalId(certificate.id()), certificate.provider(),
                certificate.certificateType(), certificate.alias(), certificate.fingerprint(),
                certificate.secretRef(), time(certificate.notBefore()), time(certificate.expiresAt()),
                certificate.status().name(), audit.actorId(), certificate.version());
        audit(audit);
    }

    @Override
    public void rotateAtomically(
            StoredCertificate current, StoredCertificate retiredCurrent,
            StoredCertificate replacement, StoredCertificate activatedReplacement,
            AuditEvent audit) {
        long replacementId = internalId(activatedReplacement.id());
        requireOne(jdbc.update("""
                UPDATE hhy.provider_certificates
                SET status=?,rotated_to_id=?,approval_ref=?,version=?
                WHERE id=? AND version=? AND status=?
                """, retiredCurrent.status().name(), replacementId, approvalId(audit.detail()),
                retiredCurrent.version(), internalId(current.id()), current.version(),
                current.status().name()));
        requireOne(jdbc.update("""
                UPDATE hhy.provider_certificates
                SET status=?,approval_ref=?,version=?
                WHERE id=? AND version=? AND status=?
                """, activatedReplacement.status().name(), approvalId(audit.detail()),
                activatedReplacement.version(), replacementId, replacement.version(),
                replacement.status().name()));
        audit(audit);
    }

    private StoredCertificate certificate(ResultSet rs, int row) throws SQLException {
        return new StoredCertificate(
                externalId(rs.getLong("id")), rs.getString("provider_code"),
                rs.getString("cert_type"), rs.getString("alias"), rs.getString("fingerprint"),
                rs.getString("secret_ref"), instant(rs.getObject("valid_from", OffsetDateTime.class)),
                instant(rs.getObject("valid_to", OffsetDateTime.class)),
                Status.valueOf(rs.getString("status")), rs.getLong("version"));
    }

    private void audit(AuditEvent event) {
        jdbc.update("""
                INSERT INTO hhy.provider_certificate_access_logs(
                  certificate_id,admin_id,operation,reason)
                VALUES (?,?,?,?)
                """, internalId(event.certificateId()), event.actorId(), event.action(),
                limited(event.detail()));
    }

    private static String approvalId(String detail) {
        if (detail == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|;)approval=([A-Za-z0-9_-]{1,64})(?:;|$)").matcher(detail);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String externalId(long id) {
        return CERTIFICATE_PREFIX + id;
    }

    static long internalId(String id) {
        if (id == null || !id.matches("^cert_[1-9][0-9]{0,18}$")) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "证书标识无效", 400, false);
        }
        try {
            return Long.parseLong(id.substring(CERTIFICATE_PREFIX.length()));
        } catch (NumberFormatException overflow) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "证书标识无效", 400, false);
        }
    }

    private static String limited(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(1_000, value.length()));
    }

    private static long actor(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("Stored certificate approval actor is invalid", invalid);
        }
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireOne(int changed) {
        if (changed != 1) throw new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "证书版本已变化，请刷新后重试", 409, false);
    }
}
