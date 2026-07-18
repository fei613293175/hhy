package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeCode;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeRequest;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeResult;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProviderConnector;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Provider-specific adapters that translate frozen R03 fields into safe probe commands. */
public final class ProviderConnectorAdapters {
    private ProviderConnectorAdapters() { }

    public static ProviderConnector sms(ProviderProbeTransport transport) {
        Objects.requireNonNull(transport, "transport");
        return connector("sms", request -> {
            URI endpoint = safeEndpoint(text(request, "sms.aliyun.endpoint"), true);
            ProbeCommand command = new ProbeCommand(
                    ProbeOperation.ALIYUN_SMS_ACCOUNT,
                    endpoint,
                    Map.of(
                            "region", text(request, "sms.aliyun.region_id"),
                            "signName", text(request, "sms.aliyun.sign_name")),
                    request.testRecipient());
            requireSecret(request, "sms.aliyun.access_key_id");
            requireSecret(request, "sms.aliyun.access_key_secret");
            return transport.probe(command, request.secrets());
        });
    }

    public static ProviderConnector storage(ProviderProbeTransport transport) {
        Objects.requireNonNull(transport, "transport");
        return connector("storage", request -> {
            String selected = text(request, "storage.default_provider").toUpperCase(Locale.ROOT);
            if ("CLOUDFLARE_R2".equals(selected)) {
                ProbeCommand command = new ProbeCommand(
                        ProbeOperation.CLOUDFLARE_R2_BUCKET_ACCESS,
                        safeEndpoint(text(request, "storage.r2.endpoint"), false),
                        parameters(request, "accountId", "storage.r2.account_id",
                                "bucket", "storage.r2.bucket.public_media"),
                        null);
                requireSecret(request, "storage.r2.access_key_id");
                requireSecret(request, "storage.r2.secret_access_key");
                return transport.probe(command, request.secrets());
            }
            if ("ALIYUN_OSS".equals(selected)) {
                ProbeCommand command = new ProbeCommand(
                        ProbeOperation.ALIYUN_OSS_BUCKET_ACCESS,
                        safeEndpoint(text(request, "storage.aliyun_oss.endpoint"), true),
                        parameters(request, "region", "storage.aliyun_oss.region",
                                "bucket", "storage.aliyun_oss.bucket.public_media"),
                        null);
                requireSecret(request, "storage.aliyun_oss.access_key_id");
                requireSecret(request, "storage.aliyun_oss.access_key_secret");
                return transport.probe(command, request.secrets());
            }
            throw validation("不支持的存储连接器: " + selected);
        });
    }

    public static ProviderConnector identity(ProviderProbeTransport transport) {
        Objects.requireNonNull(transport, "transport");
        return connector("identity", request -> {
            requireSecret(request, "identity.provider.appcode");
            ProbeCommand command = new ProbeCommand(
                    ProbeOperation.ALIYUN_MARKET_IDENTITY_AUTH,
                    safeEndpoint(text(request, "identity.liveness.token_url"), false),
                    Map.of(
                            "resultEndpoint", safeEndpoint(
                                    text(request, "identity.liveness.result_url"), false).toString(),
                            "faceCompareEndpoint", safeEndpoint(
                                    text(request, "identity.face_compare.url"), false).toString()),
                    null);
            return transport.probe(command, request.secrets());
        });
    }

    private static ProviderConnector connector(
            String provider, java.util.function.Function<ProbeRequest, ProbeResult> probe) {
        return new ProviderConnector() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public ProbeResult probe(ProbeRequest request) {
                ProbeResult result = probe.apply(request);
                if (result == null || result.code() == null) {
                    return ProbeResult.failure(ProbeCode.PROVIDER_ERROR);
                }
                return result;
            }
        };
    }

    private static Map<String, String> parameters(
            ProbeRequest request, String firstName, String firstKey,
            String secondName, String secondKey) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(firstName, text(request, firstKey));
        parameters.put(secondName, text(request, secondKey));
        return Map.copyOf(parameters);
    }

    private static String text(ProbeRequest request, String key) {
        JsonNode node = request.values().get(key);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw validation("连接器缺少公开配置: " + key);
        }
        return node.textValue().strip();
    }

    private static void requireSecret(ProbeRequest request, String key) {
        char[] value = request.secrets().secret(key);
        if (value.length == 0) throw validation("连接器秘密材料为空");
    }

    private static URI safeEndpoint(String value, boolean allowHostOnly) {
        String candidate = allowHostOnly && !value.contains("://") ? "https://" + value : value;
        final URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException error) {
            throw validation("供应商连接地址格式无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getQuery() != null || uri.getPort() != -1) {
            throw validation("供应商连接地址必须是无凭据、无查询、默认端口的 HTTPS 地址");
        }
        rejectPrivateHost(uri.getHost());
        return uri.normalize();
    }

    private static void rejectPrivateHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")
                || normalized.endsWith(".local") || normalized.endsWith(".internal")) {
            throw validation("供应商连接地址不能指向本地或内部主机");
        }
        if (!isIpLiteral(normalized)) return;
        try {
            InetAddress address = InetAddress.getByName(normalized);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw validation("供应商连接地址不能指向私有或保留地址");
            }
        } catch (UnknownHostException error) {
            throw validation("供应商连接地址主机无效");
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$") || host.contains(":");
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    public interface ProviderProbeTransport {
        /** Executes the command against the real provider without logging secrets or target data. */
        ProbeResult probe(
                ProbeCommand command,
                ProviderConnectionTestCoordinator.ResolvedSecrets secrets);
    }

    public record ProbeCommand(
            ProbeOperation operation,
            URI endpoint,
            Map<String, String> parameters,
            String testRecipient) {
        public ProbeCommand {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(endpoint, "endpoint");
            parameters = Map.copyOf(parameters);
        }
    }

    public enum ProbeOperation {
        ALIYUN_SMS_ACCOUNT,
        CLOUDFLARE_R2_BUCKET_ACCESS,
        ALIYUN_OSS_BUCKET_ACCESS,
        ALIYUN_MARKET_IDENTITY_AUTH
    }
}
