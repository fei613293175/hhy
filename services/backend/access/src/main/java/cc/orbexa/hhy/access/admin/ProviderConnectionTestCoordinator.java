package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Snapshot;
import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Status;
import cc.orbexa.hhy.access.admin.ProviderConfigValidator.ValidatedConfig;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runs a provider's real connector probe while keeping resolved secret material
 * inside a short-lived, zeroized scope. No provider response body or exception
 * message is allowed into the persisted connection-test summary.
 */
public final class ProviderConnectionTestCoordinator {
    private final Map<String, ProviderConnector> connectors;
    private final SecretResolver secretResolver;
    private final Clock clock;

    public ProviderConnectionTestCoordinator(
            Collection<ProviderConnector> connectors, SecretResolver secretResolver, Clock clock) {
        this.connectors = index(connectors);
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TestOutcome test(
            Snapshot lifecycle,
            ValidatedConfig config,
            String testRecipient,
            long expectedVersion) {
        requireMatchingLifecycle(lifecycle, config);
        ProviderTestPlan plan = plan(config);
        requireSecretRefs(config, plan.requiredSecretRefs());
        ProviderConnector connector = connectors.get(config.provider());
        Instant testedAt = clock.instant();
        ProbeCode code;

        if (connector == null) {
            code = ProbeCode.CONNECTOR_UNAVAILABLE;
        } else {
            try (ResolvedSecrets secrets = resolve(plan.requiredSecretRefs(), config.secretRefs())) {
                ProbeResult result = connector.probe(new ProbeRequest(
                        config.provider(),
                        config.environment(),
                        config.values(),
                        secrets,
                        normalizedRecipient(testRecipient)));
                code = requireSafeResult(result).code();
            } catch (SecretResolutionException ignored) {
                code = ProbeCode.SECRET_UNAVAILABLE;
            } catch (BusinessException failure) {
                throw failure;
            } catch (RuntimeException ignored) {
                code = ProbeCode.PROVIDER_ERROR;
            }
        }

        boolean successful = code == ProbeCode.OK;
        String maskedSummary = config.provider() + ":" + safeSummaryCode(code);
        Snapshot updated = ProviderConfigLifecycle.connectionTested(
                lifecycle, successful, maskedSummary, testedAt, expectedVersion);
        return new TestOutcome(updated, successful, code, testedAt);
    }

    private ResolvedSecrets resolve(
            Collection<String> requiredSecretRefs, Map<String, String> references) {
        Map<String, char[]> resolved = new LinkedHashMap<>();
        try {
            for (String key : requiredSecretRefs) {
                String reference = references.get(key);
                if (reference == null) throw validation("连接测试缺少秘密引用: " + key);
                char[] material;
                try {
                    material = secretResolver.resolve(reference);
                } catch (RuntimeException ignored) {
                    throw new SecretResolutionException();
                }
                if (material == null || material.length == 0) {
                    if (material != null) Arrays.fill(material, '\0');
                    throw new SecretResolutionException();
                }
                resolved.put(key, material);
            }
            return new ResolvedSecrets(resolved);
        } catch (RuntimeException failure) {
            resolved.values().forEach(value -> Arrays.fill(value, '\0'));
            throw failure;
        }
    }

    private static ProviderTestPlan plan(ValidatedConfig config) {
        return switch (config.provider()) {
            case "sms" -> {
                requireValue(config, "sms.active_provider", "ALIYUN");
                requireValues(config, "sms.aliyun.region_id", "sms.aliyun.endpoint", "sms.aliyun.sign_name");
                yield new ProviderTestPlan(java.util.List.of(
                        "sms.aliyun.access_key_id", "sms.aliyun.access_key_secret"));
            }
            case "storage" -> storagePlan(config);
            case "identity" -> {
                requireValue(config, "identity.active_provider", "ALIYUN_MARKET_FACE");
                requireHttpsValues(config, "identity.liveness.token_url",
                        "identity.liveness.result_url", "identity.face_compare.url");
                yield new ProviderTestPlan(java.util.List.of("identity.provider.appcode"));
            }
            default -> throw validation("不支持的供应商连接测试: " + config.provider());
        };
    }

    private static ProviderTestPlan storagePlan(ValidatedConfig config) {
        String selected = text(config, "storage.default_provider").toUpperCase(Locale.ROOT);
        return switch (selected) {
            case "CLOUDFLARE_R2" -> {
                requireValues(config, "storage.r2.account_id", "storage.r2.endpoint");
                requireHttpsValues(config, "storage.r2.endpoint");
                yield new ProviderTestPlan(java.util.List.of(
                        "storage.r2.access_key_id", "storage.r2.secret_access_key"));
            }
            case "ALIYUN_OSS" -> {
                requireValues(config, "storage.aliyun_oss.endpoint", "storage.aliyun_oss.region");
                yield new ProviderTestPlan(java.util.List.of(
                        "storage.aliyun_oss.access_key_id", "storage.aliyun_oss.access_key_secret"));
            }
            default -> throw validation("存储连接测试不支持当前服务商: " + selected);
        };
    }

    private static void requireSecretRefs(
            ValidatedConfig config, Collection<String> requiredSecretRefs) {
        for (String key : requiredSecretRefs) {
            if (!config.secretRefs().containsKey(key)) {
                throw validation("连接测试缺少秘密引用: " + key);
            }
        }
    }

    private static void requireMatchingLifecycle(Snapshot lifecycle, ValidatedConfig config) {
        if (lifecycle == null || config == null) throw validation("配置版本和校验结果不能为空");
        if (!Objects.equals(lifecycle.provider(), config.provider())) {
            throw validation("配置版本与连接测试供应商不匹配");
        }
        if (lifecycle.status() != Status.VALIDATED
                && lifecycle.status() != Status.CONNECTION_TESTED) {
            throw businessRule("只有已校验版本可以执行真实连接测试");
        }
    }

    private static void requireValue(ValidatedConfig config, String key, String expected) {
        if (!expected.equalsIgnoreCase(text(config, key))) {
            throw validation("连接测试配置值无效: " + key);
        }
    }

    private static void requireValues(ValidatedConfig config, String... keys) {
        for (String key : keys) text(config, key);
    }

    private static void requireHttpsValues(ValidatedConfig config, String... keys) {
        for (String key : keys) {
            if (!text(config, key).toLowerCase(Locale.ROOT).startsWith("https://")) {
                throw validation("连接测试地址必须使用 HTTPS: " + key);
            }
        }
    }

    private static String text(ValidatedConfig config, String key) {
        JsonNode node = config.values().get(key);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw validation("连接测试缺少公开配置: " + key);
        }
        return node.textValue().strip();
    }

    private static String normalizedRecipient(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > 256 || containsControlCharacter(normalized)) {
            throw validation("连接测试接收方格式无效");
        }
        return normalized;
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static ProbeResult requireSafeResult(ProbeResult result) {
        if (result == null || result.code() == null) return new ProbeResult(ProbeCode.PROVIDER_ERROR);
        return result;
    }

    private static String safeSummaryCode(ProbeCode code) {
        return code == ProbeCode.SECRET_UNAVAILABLE ? "CREDENTIAL_UNAVAILABLE" : code.name();
    }

    private static Map<String, ProviderConnector> index(Collection<ProviderConnector> connectors) {
        if (connectors == null) throw validation("供应商连接器集合不能为空");
        Map<String, ProviderConnector> result = new LinkedHashMap<>();
        for (ProviderConnector connector : connectors) {
            if (connector == null || connector.provider() == null) {
                throw validation("供应商连接器定义无效");
            }
            String provider = connector.provider().strip().toLowerCase(Locale.ROOT);
            if (!provider.matches("^(sms|storage|identity)$")) {
                throw validation("供应商连接器标识不在当前切片范围内");
            }
            if (result.putIfAbsent(provider, connector) != null) {
                throw validation("供应商连接器重复: " + provider);
            }
        }
        return Map.copyOf(result);
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException businessRule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    public interface ProviderConnector {
        String provider();

        /** Performs a real provider operation; implementations must not log the request. */
        ProbeResult probe(ProbeRequest request);
    }

    public interface SecretResolver {
        /** Returns caller-owned secret material that will be overwritten immediately after the probe. */
        char[] resolve(String secretReference);
    }

    public record ProbeRequest(
            String provider,
            String environment,
            Map<String, JsonNode> values,
            ResolvedSecrets secrets,
            String testRecipient) {
        public ProbeRequest {
            values = Map.copyOf(values);
        }
    }

    public record ProbeResult(ProbeCode code) {
        public static ProbeResult success() {
            return new ProbeResult(ProbeCode.OK);
        }

        public static ProbeResult failure(ProbeCode code) {
            if (code == null || code == ProbeCode.OK) {
                throw validation("失败连接测试必须提供安全错误分类");
            }
            return new ProbeResult(code);
        }
    }

    public enum ProbeCode {
        OK,
        AUTHENTICATION_FAILED,
        ENDPOINT_UNREACHABLE,
        CONFIGURATION_REJECTED,
        TIMEOUT,
        SECRET_UNAVAILABLE,
        CONNECTOR_UNAVAILABLE,
        PROVIDER_ERROR
    }

    public record TestOutcome(
            Snapshot snapshot, boolean successful, ProbeCode code, Instant testedAt) { }

    public static final class ResolvedSecrets implements AutoCloseable {
        private final Map<String, char[]> material;
        private boolean closed;

        private ResolvedSecrets(Map<String, char[]> material) {
            this.material = Map.copyOf(material);
        }

        public char[] secret(String key) {
            if (closed) throw new IllegalStateException("秘密材料作用域已关闭");
            char[] value = material.get(key);
            if (value == null) throw validation("连接器请求了未声明的秘密字段");
            return value;
        }

        @Override
        public void close() {
            if (closed) return;
            material.values().forEach(value -> Arrays.fill(value, '\0'));
            closed = true;
        }
    }

    private record ProviderTestPlan(Collection<String> requiredSecretRefs) {
        private ProviderTestPlan {
            requiredSecretRefs = java.util.List.copyOf(requiredSecretRefs);
        }
    }

    private static final class SecretResolutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
