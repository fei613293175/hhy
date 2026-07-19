package cc.orbexa.hhy.access.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Loads the single activated provider version; registry defaults never become runtime defaults. */
@Component
public final class R05IdentityProviderSettings
        implements AliyunMarketIdentityProviderClient.ConfigurationSource {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public R05IdentityProviderSettings(
            JdbcTemplate jdbc,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public AliyunMarketIdentityProviderClient.Settings current() {
        return jdbc.query("""
                SELECT values_json,secret_refs_json FROM hhy.provider_config_versions
                WHERE provider_code='identity' AND status='ACTIVE'
                ORDER BY activated_at DESC NULLS LAST,id DESC LIMIT 1
                """, (rs, row) -> settings(
                rs.getString("values_json"), rs.getString("secret_refs_json")))
                .stream().findFirst().orElse(null);
    }

    AliyunMarketIdentityProviderClient.Settings settings(String valuesJson, String refsJson) {
        try {
            JsonNode values = objectMapper.readTree(valuesJson == null ? "{}" : valuesJson);
            JsonNode refs = objectMapper.readTree(refsJson == null ? "{}" : refsJson);
            return new AliyunMarketIdentityProviderClient.Settings(
                    text(values, "identity.active_provider"),
                    URI.create(text(values, "identity.liveness.token_url")),
                    URI.create(text(values, "identity.liveness.result_url")),
                    URI.create(text(values, "identity.face_compare.url")),
                    URI.create(text(values, "identity.liveness.return_url")),
                    integer(values, "identity.face_compare.auto_pass_code"),
                    integer(values, "identity.face_compare.manual_review_code"),
                    text(refs, "identity.provider.appcode"));
        } catch (RuntimeException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IllegalStateException("Activated identity provider configuration is invalid", invalid);
        }
    }

    private static String text(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("Activated identity provider configuration is incomplete");
        }
        return value.textValue().strip();
    }

    private static int integer(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalStateException("Activated identity provider configuration is incomplete");
        }
        return value.intValue();
    }
}
