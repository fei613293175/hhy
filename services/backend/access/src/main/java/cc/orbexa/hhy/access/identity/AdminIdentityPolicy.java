package cc.orbexa.hhy.access.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Activated administrator identity policy; there are no code-side business defaults. */
@Component
public class AdminIdentityPolicy {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminIdentityPolicy(
            JdbcTemplate jdbc,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Duration previewTtl() {
        String json = jdbc.query("""
                SELECT values_json FROM hhy.provider_config_versions
                WHERE provider_code='identity' AND status='ACTIVE'
                ORDER BY activated_at DESC NULLS LAST,id DESC LIMIT 1
                """, (rs, row) -> rs.getString("values_json")).stream().findFirst().orElse(null);
        try {
            JsonNode values = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode ttl = values.get("identity.admin_preview_ttl_seconds");
            if (ttl == null || !ttl.canConvertToLong() || ttl.longValue() < 1) {
                throw new IllegalStateException("Activated identity preview policy is incomplete");
            }
            return Duration.ofSeconds(ttl.longValue());
        } catch (RuntimeException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IllegalStateException("Activated identity preview policy is invalid", invalid);
        }
    }
}
