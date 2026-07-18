package cc.orbexa.hhy.access.admin;

import static cc.orbexa.hhy.access.admin.R03ConfigurationContracts.*;

import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only PostgreSQL adapter for all frozen R03 configuration projections. */
@Component
public class R03ConfigurationQueryStore {
    private static final Map<String, String> PROVIDER_SORTS = Map.of(
            "provider:asc", "provider_code ASC",
            "provider:desc", "provider_code DESC",
            "createdAt:asc", "created_at ASC,provider_code ASC",
            "createdAt:desc", "created_at DESC,provider_code ASC");
    private static final Map<String, String> CERTIFICATE_SORTS = Map.of(
            "provider:asc", "provider_code ASC,id ASC",
            "provider:desc", "provider_code DESC,id DESC",
            "expiresAt:asc", "valid_to ASC NULLS LAST,id ASC",
            "expiresAt:desc", "valid_to DESC NULLS LAST,id DESC",
            "createdAt:asc", "created_at ASC,id ASC",
            "createdAt:desc", "created_at DESC,id DESC");
    private static final Map<String, String> DOMAIN_SORTS = Map.of(
            "code:asc", "code ASC",
            "code:desc", "code DESC",
            "createdAt:asc", "created_at ASC,code ASC",
            "createdAt:desc", "created_at DESC,code ASC");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public R03ConfigurationQueryStore(
            JdbcTemplate jdbc,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Page<ProviderConfigResource> providerConfigs(
            int page, int pageSize, String status, String keyword, String sort) {
        requirePage(page, pageSize);
        Filter filter = providerFilter(status, keyword);
        String source = providerProjectionSource();
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM (" + source + ") provider_rows" + filter.where(),
                Long.class, filter.arguments().toArray());
        List<Object> arguments = pageArguments(filter, page, pageSize);
        List<ProviderConfigResource> items = jdbc.query(
                "SELECT * FROM (" + source + ") provider_rows" + filter.where()
                        + " ORDER BY " + order(PROVIDER_SORTS, sort, "createdAt:desc")
                        + " LIMIT ? OFFSET ?",
                (rs, row) -> providerResource(
                        rs.getString("provider_code"), rs.getString("environment"),
                        rs.getString("active_version"), rs.getString("draft_version"),
                        rs.getString("secret_refs_json"), rs.getObject("connection_successful", Boolean.class),
                        instant(rs.getObject("tested_at", OffsetDateTime.class)), rs.getLong("version")),
                arguments.toArray());
        return page(items, page, pageSize, total);
    }

    public Optional<ProviderConfigResource> providerConfig(String provider) {
        String normalized = provider(provider);
        return jdbc.query(
                "SELECT * FROM (" + providerProjectionSource()
                        + ") provider_rows WHERE provider_code=?",
                (rs, row) -> providerResource(
                        rs.getString("provider_code"), rs.getString("environment"),
                        rs.getString("active_version"), rs.getString("draft_version"),
                        rs.getString("secret_refs_json"), rs.getObject("connection_successful", Boolean.class),
                        instant(rs.getObject("tested_at", OffsetDateTime.class)), rs.getLong("version")),
                normalized).stream().findFirst();
    }

    public Page<CertificateResource> certificates(
            int page, int pageSize, String status, String keyword, String sort) {
        requirePage(page, pageSize);
        Filter filter = certificateFilter(status, keyword);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.provider_certificates" + filter.where(),
                Long.class, filter.arguments().toArray());
        List<Object> arguments = pageArguments(filter, page, pageSize);
        List<CertificateResource> items = jdbc.query("""
                SELECT id,provider_code,cert_type,alias,fingerprint,valid_from,valid_to,status,version
                FROM hhy.provider_certificates
                """ + filter.where() + " ORDER BY "
                        + order(CERTIFICATE_SORTS, sort, "createdAt:desc") + " LIMIT ? OFFSET ?",
                (rs, row) -> new CertificateResource(
                        Long.toString(rs.getLong("id")), rs.getString("provider_code"),
                        rs.getString("cert_type"), rs.getString("alias"),
                        rs.getString("fingerprint"), stringTime(rs.getObject("valid_from", OffsetDateTime.class)),
                        instant(rs.getObject("valid_to", OffsetDateTime.class)),
                        rs.getString("status"), rs.getLong("version")),
                arguments.toArray());
        return page(items, page, pageSize, total);
    }

    public Page<DomainResource> domains(
            int page, int pageSize, String status, String keyword, String sort) {
        return domainPage(page, pageSize, status, keyword, sort, false);
    }

    public Page<DomainResource> dnsActions(
            int page, int pageSize, String status, String keyword, String sort) {
        return domainPage(page, pageSize, status, keyword, sort, true);
    }

    private Page<DomainResource> domainPage(
            int page, int pageSize, String status, String keyword, String sort,
            boolean onlyActionable) {
        requirePage(page, pageSize);
        Filter filter = domainFilter(status, keyword, onlyActionable);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.domain_configs d" + filter.where(),
                Long.class, filter.arguments().toArray());
        List<Object> arguments = pageArguments(filter, page, pageSize);
        List<DomainResource> items = jdbc.query("""
                SELECT d.code,d.environment,d.host,d.dns_status,d.https_status,
                       d.certificate_status,d.last_verified_at,d.version,d.created_at
                FROM hhy.domain_configs d
                """ + filter.where() + " ORDER BY "
                        + order(DOMAIN_SORTS, sort, "createdAt:desc") + " LIMIT ? OFFSET ?",
                (rs, row) -> new DomainResource(
                        rs.getString("code"), rs.getString("environment"), rs.getString("host"),
                        rs.getString("dns_status"), rs.getString("https_status"),
                        rs.getString("certificate_status"),
                        instant(rs.getObject("last_verified_at", OffsetDateTime.class)),
                        rs.getLong("version")), arguments.toArray());
        return page(items, page, pageSize, total);
    }

    static String order(Map<String, String> allowed, String requested, String fallback) {
        if (requested == null || requested.isBlank()) return allowed.get(fallback);
        String result = allowed.get(requested.strip());
        if (result == null) throw validation("排序字段不在白名单中");
        return result;
    }

    static String maskSecretRef(String value) {
        if (value == null) return null;
        if (value.startsWith("vault://")) return "vault://***";
        if (value.startsWith("kms://")) return "kms://***";
        return "***";
    }

    private ProviderConfigResource providerResource(
            String provider, String environment, String activeVersion, String draftVersion,
            String secretJson, Boolean connected, Instant testedAt, long version) {
        List<ConfiguredSecret> secrets = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(secretJson == null ? "{}" : secretJson);
            root.properties().forEach(field -> secrets.add(new ConfiguredSecret(
                    field.getKey(), true, maskSecretRef(field.getValue().asText()))));
        } catch (Exception malformedDatabaseValue) {
            throw new IllegalStateException("Stored provider secret metadata is invalid", malformedDatabaseValue);
        }
        secrets.sort(Comparator.comparing(ConfiguredSecret::key));
        String connection = connected == null ? "NOT_TESTED" : connected ? "PASSED" : "FAILED";
        return new ProviderConfigResource(provider, environment, activeVersion, draftVersion,
                secrets, connection, testedAt, version);
    }

    private static String providerProjectionSource() {
        return """
                WITH providers AS (
                  SELECT provider_code FROM hhy.provider_config_definitions
                  UNION SELECT provider_code FROM hhy.provider_config_versions
                )
                SELECT p.provider_code,COALESCE(a.environment,d.environment,'STAGING') environment,
                  a.version_no active_version,d.version_no draft_version,
                  COALESCE(a.secret_refs_json,d.secret_refs_json,'{}'::jsonb) secret_refs_json,
                  COALESCE(a.connection_successful,d.connection_successful) connection_successful,
                  COALESCE(a.tested_at,d.tested_at) tested_at,
                  GREATEST(COALESCE(a.version,0),COALESCE(d.version,0)) version,
                  COALESCE(a.status,d.status,'UNCONFIGURED') row_status,
                  COALESCE(a.created_at,d.created_at,to_timestamp(0)) created_at
                FROM providers p
                LEFT JOIN LATERAL (
                  SELECT * FROM hhy.provider_config_versions v
                  WHERE v.provider_code=p.provider_code AND v.status='ACTIVE'
                  ORDER BY v.created_at DESC,v.id DESC LIMIT 1
                ) a ON true
                LEFT JOIN LATERAL (
                  SELECT * FROM hhy.provider_config_versions v
                  WHERE v.provider_code=p.provider_code
                    AND v.status IN ('DRAFT','VALIDATED','CONNECTION_TESTED','PENDING_APPROVAL')
                  ORDER BY v.created_at DESC,v.id DESC LIMIT 1
                ) d ON true
                """;
    }

    private static Filter providerFilter(String status, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        addStatus(where, arguments, "row_status", status);
        addKeyword(where, arguments, keyword, "provider_code");
        return new Filter(where.toString(), arguments);
    }

    private static Filter certificateFilter(String status, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        addStatus(where, arguments, "status", status);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(provider_code) LIKE ? OR LOWER(COALESCE(alias,'')) LIKE ?")
                    .append(" OR LOWER(COALESCE(fingerprint,'')) LIKE ?)");
            String pattern = pattern(keyword);
            arguments.add(pattern);
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return new Filter(where.toString(), arguments);
    }

    private static Filter domainFilter(String status, String keyword, boolean onlyActionable) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        addStatus(where, arguments, "d.status", status);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(d.code) LIKE ? OR LOWER(COALESCE(d.host,'')) LIKE ?)");
            String pattern = pattern(keyword);
            arguments.add(pattern);
            arguments.add(pattern);
        }
        if (onlyActionable) {
            where.append(" AND EXISTS (SELECT 1 FROM hhy.dns_action_items action")
                    .append(" WHERE action.domain_config_id=d.id")
                    .append(" AND action.user_action_required=true")
                    .append(" AND action.status NOT IN ('VERIFIED','COMPLETED','CANCELLED'))");
        }
        return new Filter(where.toString(), arguments);
    }

    private static void addStatus(
            StringBuilder where, List<Object> arguments, String column, String status) {
        if (status != null && !status.isBlank()) {
            where.append(" AND ").append(column).append("=?");
            arguments.add(status.strip().toUpperCase(Locale.ROOT));
        }
    }

    private static void addKeyword(
            StringBuilder where, List<Object> arguments, String keyword, String column) {
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND LOWER(").append(column).append(") LIKE ?");
            arguments.add(pattern(keyword));
        }
    }

    private static String pattern(String value) {
        return "%" + value.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private static String provider(String value) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")) {
            throw validation("供应商标识无效");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw validation("分页参数无效");
    }

    private static List<Object> pageArguments(Filter filter, int page, int pageSize) {
        List<Object> result = new ArrayList<>(filter.arguments());
        result.add(pageSize);
        result.add((page - 1L) * pageSize);
        return result;
    }

    private static <T> Page<T> page(
            List<T> items, int page, int pageSize, Long totalValue) {
        long total = totalValue == null ? 0 : totalValue;
        return new Page<>(items, new PageMeta(page, pageSize, total, (long) page * pageSize < total));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String stringTime(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private record Filter(String where, List<Object> arguments) {
        private Filter {
            arguments = List.copyOf(arguments);
        }
    }
}
