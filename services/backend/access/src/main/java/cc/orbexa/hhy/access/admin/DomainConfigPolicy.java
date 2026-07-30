package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.IDN;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Validates the frozen orbexa.cc domain plan without accepting arbitrary public hosts. */
public final class DomainConfigPolicy {
    private static final String ROOT_DOMAIN = "orbexa.cc";
    private static final Map<String, PlanEntry> PLAN = Map.ofEntries(
            entry("www", Environment.PRODUCTION, "www.orbexa.cc"),
            entry("api", Environment.PRODUCTION, "api.orbexa.cc"),
            entry("ws", Environment.PRODUCTION, "ws.orbexa.cc"),
            entry("admin", Environment.PRODUCTION, "admin.orbexa.cc"),
            entry("h5", Environment.PRODUCTION, "h5.orbexa.cc"),
            entry("download", Environment.PRODUCTION, "download.orbexa.cc"),
            entry("assets", Environment.PRODUCTION, "assets.orbexa.cc"),
            entry("stg_api", Environment.STAGING, "stg-api.orbexa.cc"),
            entry("stg_ws", Environment.STAGING, "stg-ws.orbexa.cc"),
            entry("stg_admin", Environment.STAGING, "stg-admin.orbexa.cc"),
            entry("stg_h5", Environment.STAGING, "stg-h5.orbexa.cc"),
            entry("stg_download", Environment.STAGING, "stg-download.orbexa.cc"));

    public ValidatedDomain validate(
            String code, String environment, String hostname, String certificateMode) {
        String normalizedCode = normalizeCode(code);
        PlanEntry plan = PLAN.get(normalizedCode);
        if (plan == null) throw validation("域名代码不在受控计划中");

        Environment normalizedEnvironment = Environment.parse(environment);
        if (normalizedEnvironment != plan.environment()) {
            throw validation("域名代码与环境不匹配");
        }

        String normalizedHostname = normalizeHostname(hostname);
        if (!normalizedHostname.equals(plan.defaultHostname())) {
            throw validation("主机名必须与受控域名计划一致");
        }
        CertificateMode normalizedCertificateMode = CertificateMode.parse(certificateMode);
        return new ValidatedDomain(
                normalizedCode, normalizedEnvironment, normalizedHostname,
                normalizedCertificateMode, ROOT_DOMAIN);
    }

    public PlanEntry planFor(String code) {
        PlanEntry entry = PLAN.get(normalizeCode(code));
        if (entry == null) throw validation("域名代码不在受控计划中");
        return entry;
    }

    private static Map.Entry<String, PlanEntry> entry(
            String code, Environment environment, String hostname) {
        return Map.entry(code, new PlanEntry(code, environment, hostname));
    }

    private static String normalizeCode(String code) {
        if (code == null) throw validation("域名代码不能为空");
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z][a-z0-9_]{1,31}$")) {
            throw validation("域名代码格式无效");
        }
        return normalized;
    }

    private static String normalizeHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) throw validation("主机名不能为空");
        String candidate = hostname.strip().toLowerCase(Locale.ROOT);
        if (candidate.endsWith(".")) candidate = candidate.substring(0, candidate.length() - 1);
        if (candidate.length() > 253
                || candidate.contains(":")
                || candidate.contains("/")
                || candidate.contains("@")
                || candidate.contains("*")
                || candidate.chars().anyMatch(Character::isWhitespace)) {
            throw validation("主机名格式无效");
        }
        final String ascii;
        try {
            ascii = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException error) {
            throw validation("主机名格式无效");
        }
        if (!ascii.endsWith("." + ROOT_DOMAIN) || ascii.equals(ROOT_DOMAIN)) {
            throw validation("主机名必须为 orbexa.cc 的受控二级域名");
        }
        return ascii;
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    public enum Environment {
        STAGING, PRODUCTION;

        static Environment parse(String value) {
            if (value == null) throw validation("环境不能为空");
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw validation("环境只能为 STAGING 或 PRODUCTION");
            }
        }
    }

    public enum CertificateMode {
        MANAGED, EXTERNAL;

        static CertificateMode parse(String value) {
            if (value == null || value.isBlank()) return MANAGED;
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw validation("证书模式只能为 MANAGED 或 EXTERNAL");
            }
        }
    }

    public record PlanEntry(String code, Environment environment, String defaultHostname) {
        public PlanEntry {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(defaultHostname, "defaultHostname");
        }
    }

    public record ValidatedDomain(
            String code,
            Environment environment,
            String hostname,
            CertificateMode certificateMode,
            String rootDomain) {
        public ValidatedDomain {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(hostname, "hostname");
            Objects.requireNonNull(certificateMode, "certificateMode");
            Objects.requireNonNull(rootDomain, "rootDomain");
        }
    }
}
