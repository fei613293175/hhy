package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/** Bounded Aliyun Market APPCODE adapter for the purchased R05 identity APIs. */
public final class AliyunMarketIdentityProviderClient implements IdentityProviderClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_FACE_IMAGE_BYTES = 100 * 1024;
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
        return parseToken(post(settings.tokenEndpoint(), settings.appCodeReference(), body));
    }

    @Override
    public ProviderLivenessOutcome queryLiveness(ProviderLivenessQuery query) {
        Settings settings = configuration.current();
        requireQuery(query, settings);
        String body = "orderNo=" + encode(query.providerOrderNo());
        return parseLivenessResult(
                post(settings.resultEndpoint(), settings.appCodeReference(), body),
                query.providerOrderNo());
    }

    @Override
    public ProviderFaceComparison compareFace(ProviderFaceComparisonCommand command) {
        Settings settings = configuration.current();
        requireFaceCommand(command, settings);
        String body = "idcard=" + encode(command.idNumber())
                + "&name=" + encode(command.realName())
                + "&image=" + encode(Base64.getEncoder().encodeToString(command.image()));
        return parseFaceComparison(
                post(settings.faceCompareEndpoint(), settings.appCodeReference(), body), settings);
    }

    private Response post(URI endpoint, String appCodeReference, String body) {
        char[] appCode = resolve(appCodeReference);
        try {
            Response response = transport.postForm(
                    endpoint, appCode, body, REQUEST_TIMEOUT, MAX_RESPONSE_BYTES);
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body() == null || response.body().length == 0
                    || response.body().length > MAX_RESPONSE_BYTES) {
                throw unavailable();
            }
            return response;
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

    private ProviderLivenessResult parseToken(Response response) {
        try {
            JsonNode data = successfulData(response);
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

    private ProviderLivenessOutcome parseLivenessResult(Response response, String expectedOrderNo) {
        try {
            JsonNode data = successfulData(response);
            String orderNo = firstText(data, "orderNo");
            if (!expectedOrderNo.equals(orderNo)) throw unavailable();
            int result = requiredInt(data, "result");
            return switch (result) {
                case 0 -> new ProviderLivenessOutcome(
                        LivenessDecision.PASSED, orderNo,
                        safeHttps(URI.create(firstText(data, "faceImageUrl")), true), response.body());
                case 1 -> new ProviderLivenessOutcome(
                        LivenessDecision.REJECTED, orderNo, null, response.body());
                case 2 -> new ProviderLivenessOutcome(
                        LivenessDecision.PENDING, orderNo, null, response.body());
                default -> throw unavailable();
            };
        } catch (BusinessException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw unavailable();
        }
    }

    private ProviderFaceComparison parseFaceComparison(Response response, Settings settings) {
        try {
            JsonNode data = successfulData(response);
            int resultCode = requiredInt(data, "resultCode");
            FaceDecision decision;
            if (resultCode == settings.autoPassCode()) {
                decision = FaceDecision.VERIFIED;
            } else if (resultCode == settings.manualReviewCode()) {
                decision = FaceDecision.MANUAL_REVIEW;
            } else if (resultCode == 1003 || resultCode == 1004) {
                decision = FaceDecision.REJECTED;
            } else {
                throw unavailable();
            }
            JsonNode rawScore = data.get("score");
            BigDecimal score = rawScore != null && rawScore.isNumber()
                    ? rawScore.decimalValue() : null;
            if (score != null && (score.compareTo(BigDecimal.ZERO) < 0
                    || score.compareTo(BigDecimal.ONE) > 0)) {
                throw unavailable();
            }
            String orderNo = firstText(data, "orderNo");
            return new ProviderFaceComparison(
                    decision, resultCode, score, orderNo, response.body());
        } catch (BusinessException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw unavailable();
        }
    }

    private JsonNode successfulData(Response response) throws Exception {
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("data");
        if (!root.path("success").asBoolean(false) || root.path("code").asInt(0) != 200
                || !data.isObject()) {
            throw unavailable();
        }
        return data;
    }

    private static void requireCommand(ProviderLivenessCommand command, Settings settings) {
        requireSettings(settings);
        if (command == null
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

    private static void requireQuery(ProviderLivenessQuery query, Settings settings) {
        requireSettings(settings);
        if (query == null || query.provider() == null
                || !query.provider().equalsIgnoreCase(settings.provider())) {
            throw unavailable();
        }
        requireOrderNo(query.providerOrderNo());
        safeHttps(settings.resultEndpoint(), false);
    }

    private static void requireFaceCommand(
            ProviderFaceComparisonCommand command, Settings settings) {
        requireSettings(settings);
        if (command == null || command.provider() == null
                || !command.provider().equalsIgnoreCase(settings.provider())
                || command.realName() == null || command.realName().isBlank()
                || command.realName().length() > 64 || hasControl(command.realName())
                || command.idNumber() == null || !command.idNumber().matches("^[0-9]{17}[0-9Xx]$")
                || command.image().length == 0 || command.image().length > MAX_FACE_IMAGE_BYTES) {
            throw unavailable();
        }
        safeHttps(settings.faceCompareEndpoint(), false);
    }

    private static void requireSettings(Settings settings) {
        if (settings == null || settings.provider() == null || settings.provider().isBlank()
                || settings.appCodeReference() == null || settings.appCodeReference().isBlank()
                || settings.autoPassCode() == settings.manualReviewCode()
                || !isKnownFaceCode(settings.autoPassCode())
                || !isKnownFaceCode(settings.manualReviewCode())) {
            throw unavailable();
        }
    }

    private static boolean isKnownFaceCode(int code) {
        return code >= 1001 && code <= 1004;
    }

    private static void requireOrderNo(String value) {
        if (value == null || value.length() > 128 || !value.matches("^[A-Za-z0-9_-]+$")) {
            throw unavailable();
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

    private static int requiredInt(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isIntegralNumber()) throw unavailable();
        return value.intValue();
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
            String provider, URI tokenEndpoint, URI resultEndpoint, URI faceCompareEndpoint,
            URI returnUrl, int autoPassCode, int manualReviewCode, String appCodeReference) { }

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
