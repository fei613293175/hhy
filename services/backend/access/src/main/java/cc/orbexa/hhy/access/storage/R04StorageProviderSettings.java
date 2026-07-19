package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Loads the exact activated and connection-tested storage configuration bound to an object scope. */
@Component
public final class R04StorageProviderSettings implements
        R04S3StorageTransport.ConfigurationSource, R04OssStorageTransport.ConfigurationSource {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public R04StorageProviderSettings(
            JdbcTemplate jdbc,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Settings current(Binding binding) {
        if (binding == null || binding.provider() == null || binding.configVersionId() <= 0) {
            throw unavailable();
        }
        return jdbc.query("""
                SELECT values_json,secret_refs_json
                FROM hhy.provider_config_versions
                WHERE id=? AND provider_code='storage' AND status='ACTIVE'
                  AND connection_successful=TRUE
                """, (rs, row) -> settings(
                binding, rs.getString("values_json"), rs.getString("secret_refs_json")),
                binding.configVersionId()).stream().findFirst().orElseThrow(R04StorageProviderSettings::unavailable);
    }

    Settings settings(Binding binding, String valuesJson, String refsJson) {
        try {
            JsonNode values = objectMapper.readTree(valuesJson == null ? "{}" : valuesJson);
            JsonNode refs = objectMapper.readTree(refsJson == null ? "{}" : refsJson);
            String provider = binding.provider().name();
            if (!provider.equalsIgnoreCase(text(
                    values, "storage.scope." + scopeCode(binding) + ".provider"))) {
                throw unavailable();
            }
            String prefix = binding.provider() == Provider.CLOUDFLARE_R2
                    ? "storage.r2" : "storage.aliyun_oss";
            URI endpoint = safeEndpoint(text(values, prefix + ".endpoint"));
            String bucket = text(values, prefix + ".bucket." + scopeCode(binding));
            if (!endpoint.equals(binding.endpoint()) || !bucket.equals(binding.bucket())) {
                throw unavailable();
            }
            int ttl = integer(values, "storage.signed_url.ttl_seconds");
            if (ttl < 30 || ttl > 1800) throw unavailable();
            return new Settings(
                    endpoint,
                    binding.provider() == Provider.CLOUDFLARE_R2
                            ? "auto" : text(values, "storage.aliyun_oss.region"),
                    bucket, Duration.ofSeconds(ttl),
                    text(refs, prefix + ".access_key_id"),
                    text(refs, prefix + (binding.provider() == Provider.CLOUDFLARE_R2
                            ? ".secret_access_key" : ".access_key_secret")));
        } catch (IllegalStateException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw unavailable();
        }
    }

    private static String scopeCode(Binding binding) {
        if (binding == null || binding.scope() == null) throw unavailable();
        return binding.scope().name().toLowerCase(Locale.ROOT);
    }

    private static URI safeEndpoint(String value) {
        URI endpoint = URI.create(value);
        String host = endpoint.getHost();
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || host == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null || host.equalsIgnoreCase("localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            throw unavailable();
        }
        return endpoint;
    }

    private static String text(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw unavailable();
        return value.textValue().strip();
    }

    private static int integer(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.canConvertToInt()) throw unavailable();
        return value.intValue();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Activated storage configuration is unavailable");
    }

    public record Settings(
            URI endpoint, String region, String bucket, Duration signedUrlTtl,
            String accessKeyReference, String secretAccessKeyReference) { }
}
