package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityService.PolicySnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads the activated R03-managed identity configuration without code-side business defaults. */
@Component
public final class R05IdentityRuntimePolicy implements IdentityService.Policy {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdentitySandboxProperties sandbox;

    public R05IdentityRuntimePolicy(
            JdbcTemplate jdbc,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            IdentitySandboxProperties sandbox) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.sandbox = sandbox;
    }

    @Override
    public PolicySnapshot current() {
        if (sandbox.enabled()) {
            sandbox.requireValid();
            return new PolicySnapshot(
                    IdentitySandboxProperties.PROVIDER,
                    sandbox.maxDailyAttempts(), sandbox.sessionTtl());
        }
        return jdbc.query("""
                SELECT values_json FROM hhy.provider_config_versions
                WHERE provider_code='identity' AND status='ACTIVE'
                ORDER BY activated_at DESC NULLS LAST,id DESC LIMIT 1
                """, (rs, row) -> policy(rs.getString("values_json"))).stream().findFirst().orElse(null);
    }

    private PolicySnapshot policy(String json) {
        try {
            JsonNode values = objectMapper.readTree(json == null ? "{}" : json);
            String provider = requiredText(values, "identity.active_provider");
            int attempts = requiredPositiveInt(values, "identity.max_daily_attempts");
            int timeoutSeconds = requiredPositiveInt(values, "identity.liveness.poll_timeout_seconds");
            return new PolicySnapshot(provider, attempts, Duration.ofSeconds(timeoutSeconds));
        } catch (RuntimeException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IllegalStateException("Activated identity configuration is invalid", invalid);
        }
    }

    private static String requiredText(JsonNode values, String key) {
        JsonNode value = values.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("Activated identity configuration is incomplete");
        }
        return value.textValue().strip();
    }

    private static int requiredPositiveInt(JsonNode values, String key) {
        JsonNode value = values.get(key);
        if (value == null || !value.canConvertToInt() || value.intValue() < 1) {
            throw new IllegalStateException("Activated identity configuration is incomplete");
        }
        return value.intValue();
    }
}
