package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Bounded Aliyun Market APPCODE adapter for creating the provider H5 liveness order. */
public final class AliyunMarketIdentityProviderClient implements IdentityProviderClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final ConfigurationSource configuration;
    private final SecretResolver secrets;
    private final ObjectMapper objectMapper;
    private final Transport transport;

    public AliyunMarketIdentityProviderClient(
            ConfigurationSource configuration, SecretResolver secrets, ObjectMapper objectMapper) {
        this(configuration, secrets, objectMapper, new JdkTransport());
    }

    AliyunMarketIdentityProviderClient(
            ConfigurationSource configuration, SecretResolver secrets,
            ObjectMapper objectMapper, Transport transport) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public ProviderLivenessResult createLiveness(ProviderLivenessCommand command) {
        Settings settings = configuration.current();
        requireCommand(command, settings);
        URI callback = callback(settings.returnUrl(), command.state());
        String body = "returnUrl=" + encode(callback.toString());
        char[] appCode = resolve(settings.appCodeReference());
        try {
            Response response = transport.postForm(
                    settings.tokenEndpoint(), appCode, body, REQUEST_TIMEOUT, MAX_RESPONSE_BYTES);
            return parse(response);
        } catch (BusinessException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception unavailable) {
            throw unavailable();
        } finally {
            Arrays.fill(appCode, '\0');
        }
    }

    private ProviderLivenessResult parse(Response response) {
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300
                || response.body() == null || response.body().length == 0
                || response.body().length > MAX_RESPONSE_BYTES) {
            throw unavailable();
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!root.path("success").asBoolean(false) || root.path("code").asInt(0) != 200
                    || !data.isObject()) {
                throw unavailable();
            }
            String orderNo = firstText(data, "orderNo", "order_no");
            String url = firstText(data, "url", "h5Url", "h5_url", "livenessUrl", "tokenUrl");
            URI liveness = safeHttps(URI.create(url), true);
            if (orderNo.length() > 128 || !orderNo.matches("^[A-Za-z0-9_-]+$")) {
                throw unavailable();
            }
            return new ProviderLivenessResult(orderNo, liveness);
        } catch (BusinessException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw unavailable();
        }
    }

    private static void requireCommand(ProviderLivenessCommand command, Settings settings) {
        if (command == null || settings == null
                || command.provider() == null
                || !command.provider().equalsIgnoreCase(settings.provider())
                || command.sessionId() < 1 || command.userId() < 1
                || command.state() == null || command.state().length() < 16
                || command.state().length() > 255 || hasControl(command.state())) {
            throw unavailable();
        }
        URI tokenEndpoint = safeHttps(settings.tokenEndpoint(), false);
        URI configuredReturn = safeHttps(settings.returnUrl(), false);
        URI requestedReturn = safeHttps(command.returnUrl(), false);
        if (!configuredReturn.equals(requestedReturn) || tokenEndpoint.equals(configuredReturn)) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "回跳地址不符合要求", 400, false);
        }
    }

    private char[] resolve(String reference) {
        try {
            char[] value = secrets.resolve(reference);
            if (value == null || value.length < 8 || value.length > 512 || hasControl(value)) {
                if (value != null) Arrays.fill(value, '\0');
                throw unavailable();
            }
            return value;
        } catch (BusinessException known) {
            throw known;
        } catch (RuntimeException unresolved) {
            throw unavailable();
        }
    }

    private static URI callback(URI base, String state) {
        return URI.create(base + "?state=" + encode(state));
    }

    private static URI safeHttps(URI uri, boolean allowQuery) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() != -1
                || (!allowQuery && uri.getQuery() != null)) {
            throw unavailable();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            throw unavailable();
        }
        if (host.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$") || host.contains(":")) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw unavailable();
                }
            } catch (BusinessException unsafe) {
                throw unsafe;
            } catch (Exception invalid) {
                throw unavailable();
            }
        }
        return uri.normalize();
    }

    private static String firstText(JsonNode object, String... keys) {
        for (String key : keys) {
            JsonNode value = object.get(key);
            if (value != null && value.isTextual() && !value.textValue().isBlank()) {
                return value.textValue().strip();
            }
        }
        throw unavailable();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static boolean hasControl(char[] value) {
        for (char character : value) if (Character.isISOControl(character)) return true;
        return false;
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                "COMMON-500-INTERNAL", "活体检测服务暂时不可用", 500, true);
    }

    public interface ConfigurationSource {
        Settings current();
    }

    interface Transport {
        Response postForm(
                URI endpoint, char[] appCode, String body,
                Duration timeout, int maximumResponseBytes) throws Exception;
    }

    public record Settings(
            String provider, URI tokenEndpoint, URI returnUrl, String appCodeReference) { }

    record Response(int statusCode, byte[] body) { }

    private static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        @Override
        public Response postForm(
                URI endpoint, char[] appCode, String body,
                Duration timeout, int maximumResponseBytes) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "APPCODE " + new String(appCode))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("User-Agent", "HHY-R05-Identity/1")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                byte[] content = input.readNBytes(maximumResponseBytes + 1);
                return new Response(response.statusCode(), content);
            }
        }
    }
}
