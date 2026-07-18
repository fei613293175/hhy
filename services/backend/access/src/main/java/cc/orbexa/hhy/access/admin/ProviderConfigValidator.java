package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Enforces the frozen R03 provider-field and SecretRef boundary before a
 * provider configuration version can reach persistence or a connector.
 */
@Component
public final class ProviderConfigValidator {
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "STAGING", "PROD");
    private static final Pattern SECRET_REFERENCE = Pattern.compile(
            "^(?:vault|kms)://[A-Za-z0-9](?:[A-Za-z0-9._/-]{1,253}[A-Za-z0-9])?$");

    private static final Map<String, Definition> DEFINITIONS = definitions();

    public ValidatedConfig validate(
            String provider, String environment, JsonNode values, JsonNode secretRefs) {
        String normalizedProvider = normalizeProvider(provider);
        Definition definition = DEFINITIONS.get(normalizedProvider);
        if (definition == null) {
            throw validation("不支持的供应商配置: " + normalizedProvider);
        }

        String normalizedEnvironment = normalizeEnvironment(environment);
        requireObject(values, "values", false);
        requireObject(secretRefs, "secretRefs", true);

        Map<String, JsonNode> safeValues = fields(values);
        Map<String, String> safeSecretRefs = secretFields(secretRefs);

        for (String key : safeValues.keySet()) {
            if (definition.secretKeys().contains(key)) {
                throw validation("秘密字段必须通过 secretRefs 提交: " + key);
            }
            if (!definition.valueKeys().contains(key)) {
                throw validation("供应商公开配置字段不在允许范围内: " + key);
            }
        }
        for (String key : safeSecretRefs.keySet()) {
            if (!definition.secretKeys().contains(key)) {
                throw validation("供应商秘密引用字段不在允许范围内: " + key);
            }
        }

        return new ValidatedConfig(
                normalizedProvider,
                normalizedEnvironment,
                Map.copyOf(safeValues),
                Map.copyOf(safeSecretRefs));
    }

    public Set<String> providers() {
        return DEFINITIONS.keySet();
    }

    public Set<String> secretKeys(String provider) {
        Definition definition = DEFINITIONS.get(normalizeProvider(provider));
        if (definition == null) throw validation("不支持的供应商配置");
        return definition.secretKeys();
    }

    public static String maskReference(String reference) {
        if (reference == null || !SECRET_REFERENCE.matcher(reference).matches()) {
            throw validation("SecretRef 必须使用 vault:// 或 kms:// 引用");
        }
        int scheme = reference.indexOf("://") + 3;
        int lastSlash = reference.lastIndexOf('/');
        String prefix = reference.substring(0, scheme);
        String leaf = lastSlash >= scheme ? reference.substring(lastSlash + 1) : reference.substring(scheme);
        String maskedLeaf = leaf.length() <= 4 ? "****" : leaf.substring(0, 2) + "****";
        return prefix + "***/" + maskedLeaf;
    }

    private static Map<String, JsonNode> fields(JsonNode node) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            String key = normalizeField(entry.getKey());
            JsonNode value = entry.getValue();
            if (value == null || value.isNull() || value.isContainerNode()) {
                throw validation("供应商配置值必须是非空标量: " + key);
            }
            result.put(key, value.deepCopy());
        });
        return result;
    }

    private static Map<String, String> secretFields(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node == null || node.isNull()) return result;
        node.properties().forEach(entry -> {
            String key = normalizeField(entry.getKey());
            JsonNode value = entry.getValue();
            if (value == null || !value.isTextual()) {
                throw validation("SecretRef 必须是字符串引用: " + key);
            }
            String reference = value.textValue().strip();
            if (!SECRET_REFERENCE.matcher(reference).matches()) {
                throw validation("SecretRef 必须使用 vault:// 或 kms:// 引用: " + key);
            }
            result.put(key, reference);
        });
        return result;
    }

    private static void requireObject(JsonNode node, String name, boolean nullable) {
        if (node == null || node.isNull()) {
            if (nullable) return;
            throw validation(name + " 必须是对象");
        }
        if (!node.isObject()) throw validation(name + " 必须是对象");
    }

    private static String normalizeProvider(String value) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")) {
            throw validation("供应商标识无效");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeEnvironment(String value) {
        if (value == null || value.isBlank()) throw validation("环境不能为空");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(normalized)) {
            throw validation("环境必须是 DEV、TEST、STAGING 或 PROD");
        }
        return normalized;
    }

    private static String normalizeField(String value) {
        if (value == null || !value.matches("^[a-z][a-z0-9_.]{0,127}$")) {
            throw validation("供应商配置字段标识无效");
        }
        return value;
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static Map<String, Definition> definitions() {
        return Map.of(
                "sms", new Definition(Set.of(
                        "sms.active_provider", "sms.aliyun.region_id", "sms.aliyun.endpoint",
                        "sms.aliyun.sign_name", "sms.aliyun.template.login",
                        "sms.aliyun.template.register", "sms.aliyun.template.reset_password",
                        "sms.aliyun.template.security_notice", "sms.code.length",
                        "sms.code.ttl_seconds", "sms.send.cooldown_seconds",
                        "sms.send.daily_phone_limit", "sms.send.daily_ip_limit",
                        "sms.provider.timeout_ms", "sms.provider.retry_count",
                        "sms.verify.max_attempts", "sms.send.daily_device_limit",
                        "sms.send.same_scene_resend_new_code", "sms.delivery_receipt.enabled",
                        "sms.aliyun.callback_url"), Set.of(
                        "sms.aliyun.access_key_id", "sms.aliyun.access_key_secret")),
                "storage", new Definition(Set.of(
                        "storage.default_provider", "storage.scope.public_media.provider",
                        "storage.scope.private_kyc.provider", "storage.scope.private_chat.provider",
                        "storage.scope.audit_evidence.provider", "storage.scope.apk_release.provider",
                        "storage.scope.backup.provider", "storage.r2.account_id",
                        "storage.r2.endpoint", "storage.r2.public_domain",
                        "storage.r2.bucket.public_media", "storage.r2.bucket.private_kyc",
                        "storage.r2.bucket.private_chat", "storage.r2.bucket.audit_evidence",
                        "storage.r2.bucket.apk_release", "storage.r2.bucket.backup",
                        "storage.aliyun_oss.endpoint", "storage.aliyun_oss.region",
                        "storage.aliyun_oss.public_domain", "storage.aliyun_oss.bucket.public_media",
                        "storage.aliyun_oss.bucket.private_kyc",
                        "storage.aliyun_oss.bucket.private_chat",
                        "storage.aliyun_oss.bucket.audit_evidence",
                        "storage.aliyun_oss.bucket.apk_release", "storage.aliyun_oss.bucket.backup",
                        "storage.upload.max_image_mb", "storage.signed_url.ttl_seconds",
                        "storage.upload.image.allowed_mime", "storage.upload.image.max_mb",
                        "storage.upload.chat_image.max_mb", "storage.upload.kyc_image.max_mb",
                        "storage.upload.apk.max_mb", "storage.private_preview.ttl_seconds",
                        "storage.delete.delay_hours", "storage.migration.batch_size"), Set.of(
                        "storage.r2.access_key_id", "storage.r2.secret_access_key",
                        "storage.aliyun_oss.access_key_id",
                        "storage.aliyun_oss.access_key_secret")),
                "identity", new Definition(Set.of(
                        "identity.active_provider", "identity.liveness.token_url",
                        "identity.liveness.result_url", "identity.face_compare.url",
                        "identity.max_daily_attempts", "identity.one_id_one_account",
                        "identity.liveness.poll_interval_ms",
                        "identity.liveness.poll_timeout_seconds", "identity.liveness.return_url",
                        "identity.face_compare.auto_pass_code",
                        "identity.face_compare.manual_review_code",
                        "identity.photo.preview_watermark", "identity.photo.retention_days",
                        "identity.admin_preview_ttl_seconds"), Set.of(
                        "identity.provider.appcode")));
    }

    private record Definition(Set<String> valueKeys, Set<String> secretKeys) {
        private Definition {
            valueKeys = Set.copyOf(valueKeys);
            secretKeys = Set.copyOf(secretKeys);
        }
    }

    public record ValidatedConfig(
            String provider,
            String environment,
            Map<String, JsonNode> values,
            Map<String, String> secretRefs) { }
}
