package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class R04StorageProviderSettingsTest {
    private static final URI ENDPOINT = URI.create("https://account.r2.cloudflarestorage.com");

    @Test
    void exactBindingReceivesOnlyReferencesAndBoundPublicSettings() {
        var source = new R04StorageProviderSettings(null, new ObjectMapper());
        var result = source.settings(binding(91L, ENDPOINT, "hhy-private-kyc"), values(), refs());

        assertEquals(ENDPOINT, result.endpoint());
        assertEquals("auto", result.region());
        assertEquals("hhy-private-kyc", result.bucket());
        assertEquals(120, result.signedUrlTtl().toSeconds());
        assertEquals("vault://r2/access", result.accessKeyReference());
        assertEquals("vault://r2/secret", result.secretAccessKeyReference());
    }

    @Test
    void ossScopeUsesItsOwnProviderSettingsAndReferences() {
        var source = new R04StorageProviderSettings(null, new ObjectMapper());
        URI endpoint = URI.create("https://oss-cn-hangzhou.aliyuncs.com");
        Binding binding = new Binding(Scope.PRIVATE_KYC, Provider.ALIYUN_OSS,
                "hhy-private-kyc-oss", endpoint, null, 92L);
        var result = source.settings(binding, """
                {"storage.default_provider":"CLOUDFLARE_R2",
                 "storage.scope.private_kyc.provider":"ALIYUN_OSS",
                 "storage.aliyun_oss.endpoint":"https://oss-cn-hangzhou.aliyuncs.com",
                 "storage.aliyun_oss.region":"cn-hangzhou",
                 "storage.aliyun_oss.bucket.private_kyc":"hhy-private-kyc-oss",
                 "storage.signed_url.ttl_seconds":120}
                """, """
                {"storage.aliyun_oss.access_key_id":"vault://oss/access",
                 "storage.aliyun_oss.access_key_secret":"vault://oss/secret"}
                """);

        assertEquals(endpoint, result.endpoint());
        assertEquals("cn-hangzhou", result.region());
        assertEquals("vault://oss/access", result.accessKeyReference());
        assertEquals("vault://oss/secret", result.secretAccessKeyReference());
    }

    @Test
    void bindingCannotDriftFromItsExactActivatedVersion() {
        var source = new R04StorageProviderSettings(null, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> source.settings(
                binding(91L, ENDPOINT, "another-bucket"), values(), refs()));
        assertThrows(IllegalStateException.class, () -> source.settings(
                binding(91L, URI.create("https://other.r2.cloudflarestorage.com"), "hhy-private-kyc"),
                values(), refs()));
    }

    @Test
    void invalidTtlAndInlineCredentialsFailClosed() {
        var source = new R04StorageProviderSettings(null, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> source.settings(
                binding(91L, ENDPOINT, "hhy-private-kyc"), values().replace("120", "3600"), refs()));
        assertThrows(IllegalStateException.class, () -> source.settings(
                binding(91L, ENDPOINT, "hhy-private-kyc"), values(), "{}"));
    }

    @Test
    void postgresLoadsOnlyTheExactActiveConnectionTestedVersion() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String bucket = "hhy-private-kyc-" + suffix;
        String configuredValues = values().replace("hhy-private-kyc", bucket);
        Long configId = jdbc.queryForObject("""
                INSERT INTO hhy.provider_config_versions(
                  provider_code,version_no,status,environment,values_json,secret_refs_json,
                  remark,connection_successful,masked_test_result,tested_at)
                VALUES ('storage',?,'ACTIVE','TEST',?::jsonb,?::jsonb,
                        'r2 runtime test',TRUE,'OK',now())
                RETURNING id
                """, Long.class, "r2-" + suffix, configuredValues, refs());

        var source = new R04StorageProviderSettings(jdbc, new ObjectMapper());
        var result = source.current(binding(configId, ENDPOINT, bucket));

        assertEquals(bucket, result.bucket());
        jdbc.update("UPDATE hhy.provider_config_versions SET status='SUPERSEDED' WHERE id=?", configId);
        assertThrows(IllegalStateException.class,
                () -> source.current(binding(configId, ENDPOINT, bucket)));
    }

    private static Binding binding(long versionId, URI endpoint, String bucket) {
        return new Binding(Scope.PRIVATE_KYC, Provider.CLOUDFLARE_R2,
                bucket, endpoint, null, versionId);
    }

    private static String values() {
        return """
                {"storage.default_provider":"CLOUDFLARE_R2",
                 "storage.scope.private_kyc.provider":"CLOUDFLARE_R2",
                 "storage.r2.endpoint":"https://account.r2.cloudflarestorage.com",
                 "storage.r2.bucket.private_kyc":"hhy-private-kyc",
                 "storage.signed_url.ttl_seconds":120}
                """;
    }

    private static String refs() {
        return """
                {"storage.r2.access_key_id":"vault://r2/access",
                 "storage.r2.secret_access_key":"vault://r2/secret"}
                """;
    }
}
