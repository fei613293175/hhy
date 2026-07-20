package cc.orbexa.hhy.access.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CiAutomationStore {
    private static final String SCOPE = "ci-android-bootstrap";
    private final JdbcTemplate jdbc;

    public CiAutomationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(long userId, String codeHash, String repository, String workflow,
                       String commit, String runId, Instant expiresAt) {
        jdbc.update("DELETE FROM hhy.idempotency_records WHERE scope=? AND expires_at<=clock_timestamp()", SCOPE);
        jdbc.update("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,response_ref,expires_at)
                VALUES (?,?,?,?,?)
                """, SCOPE, codeHash, sha256(repository + "\n" + workflow),
                userId + "|" + commit + "|" + runId, time(expiresAt));
    }

    public Optional<BootstrapRow> findForUpdate(String codeHash) {
        return jdbc.query("""
                SELECT id,response_ref,expires_at FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? FOR UPDATE
                """, (rs, row) -> {
            String[] reference = rs.getString("response_ref").split("\\|", 3);
            return new BootstrapRow(rs.getLong("id"), Long.parseLong(reference[0]), reference[1], reference[2],
                    instant(rs.getObject("expires_at", OffsetDateTime.class)));
        }, SCOPE, codeHash).stream().findFirst();
    }

    public boolean consume(long id, Instant now) {
        return jdbc.update("DELETE FROM hhy.idempotency_records WHERE id=? AND scope=? AND expires_at>?",
                id, SCOPE, time(now)) == 1;
    }

    private static OffsetDateTime time(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record BootstrapRow(long id, long userId, String commit, String runId, Instant expiresAt) { }
}
