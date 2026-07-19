package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.identity.AliyunMarketIdentityProviderClient.Response;
import cc.orbexa.hhy.access.identity.AliyunMarketIdentityProviderClient.Settings;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessCommand;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessQuery;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderFaceComparisonCommand;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.LivenessDecision;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.FaceDecision;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AliyunMarketIdentityProviderClientTest {
    private static final URI TOKEN = URI.create(
            "https://kzlive.market.alicloudapi.com/api/liveness/h5/token");
    private static final URI RESULT = URI.create(
            "https://kzlive.market.alicloudapi.com/api/liveness/h5/result");
    private static final URI FACE = URI.create(
            "https://sdfaceid.market.alicloudapi.com/face_id_card/check");
    private static final URI CALLBACK = URI.create("https://h5.orbexa.cc/identity/callback");

    @Test
    void createsBoundedProviderOrderAndAppendsServerSessionState() {
        char[] material = "provider-appcode-123".toCharArray();
        RecordingTransport transport = new RecordingTransport(new Response(200, """
                {"success":true,"code":200,"data":{
                  "orderNo":"202607200001","h5Url":"https://provider.example/liveness/start?token=opaque"
                }}
                """.getBytes(StandardCharsets.UTF_8)));
        var client = client(material, transport);

        var result = client.createLiveness(command(CALLBACK));

        assertEquals("202607200001", result.providerOrderNo());
        assertEquals("https://provider.example/liveness/start?token=opaque",
                result.livenessUrl().toString());
        assertTrue(transport.body.contains(
                "returnUrl=https%3A%2F%2Fh5.orbexa.cc%2Fidentity%2Fcallback%3Fstate%3Dsession-state-123456"));
        assertTrue(Arrays.equals(new char[material.length], material),
                "caller-owned secret material must be zeroized after the request");
        assertFalse(transport.body.contains("provider-appcode"));
    }

    @Test
    void rejectsUnconfiguredReturnUrlBeforeResolvingSecret() {
        int[] resolutions = {0};
        var client = new AliyunMarketIdentityProviderClient(
                () -> settings(), reference -> {
                    resolutions[0]++;
                    return "provider-appcode-123".toCharArray();
                }, new ObjectMapper(), new RecordingTransport(null));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> client.createLiveness(command(
                        URI.create("https://attacker.example/callback"))));

        assertEquals(400, failure.httpStatus());
        assertEquals(0, resolutions[0]);
    }

    @Test
    void mapsMalformedOrOversizedProviderPayloadToRetryableFailure() {
        char[] material = "provider-appcode-123".toCharArray();
        var malformed = client(material, new RecordingTransport(new Response(
                200, "{\"success\":true,\"code\":200,\"data\":{}}"
                .getBytes(StandardCharsets.UTF_8))));
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> malformed.createLiveness(command(CALLBACK)));
        assertEquals(500, invalid.httpStatus());
        assertTrue(invalid.retryable());

        char[] secondMaterial = "provider-appcode-456".toCharArray();
        var oversized = client(secondMaterial, new RecordingTransport(
                new Response(200, new byte[64 * 1024 + 1])));
        BusinessException large = assertThrows(BusinessException.class,
                () -> oversized.createLiveness(command(CALLBACK)));
        assertEquals(500, large.httpStatus());
        assertTrue(Arrays.equals(new char[secondMaterial.length], secondMaterial));
    }

    @Test
    void mapsPurchasedLivenessResultWithoutExposingProviderDescription() {
        char[] material = "provider-appcode-123".toCharArray();
        RecordingTransport transport = new RecordingTransport(new Response(200, """
                {"msg":"成功","success":true,"code":200,"data":{
                  "requestTime":"2026-07-20T04:00:00","result":0,
                  "faceImageUrl":"https://provider.example/private/face.jpg?token=opaque",
                  "orderNo":"202607200001","desc":"活体检测成功"
                }}
                """.getBytes(StandardCharsets.UTF_8)));
        var client = client(material, transport);

        var result = client.queryLiveness(new ProviderLivenessQuery(
                "ALIYUN_MARKET_FACE", "202607200001"));

        assertEquals(LivenessDecision.PASSED, result.decision());
        assertEquals("202607200001", result.providerOrderNo());
        assertEquals("https://provider.example/private/face.jpg?token=opaque",
                result.faceImageUrl().toString());
        assertEquals("orderNo=202607200001", transport.body);
        assertEquals(RESULT, transport.endpoint);
        assertTrue(result.rawResponse().length > 0);
        assertTrue(Arrays.equals(new char[material.length], material));
    }

    @Test
    void distinguishesPendingAndRejectedLivenessResults() {
        var pending = client("provider-appcode-123".toCharArray(),
                new RecordingTransport(livenessResult(2)))
                .queryLiveness(new ProviderLivenessQuery("ALIYUN_MARKET_FACE", "ORDER_2"));
        var rejected = client("provider-appcode-456".toCharArray(),
                new RecordingTransport(livenessResult(1)))
                .queryLiveness(new ProviderLivenessQuery("ALIYUN_MARKET_FACE", "ORDER_1"));

        assertEquals(LivenessDecision.PENDING, pending.decision());
        assertEquals(LivenessDecision.REJECTED, rejected.decision());
        assertEquals(null, pending.faceImageUrl());
        assertEquals(null, rejected.faceImageUrl());
    }

    @Test
    void mapsPurchasedFaceCodesAndUsesBase64ImageOnly() {
        char[] material = "provider-appcode-123".toCharArray();
        RecordingTransport transport = new RecordingTransport(new Response(200, """
                {"msg":"成功","success":true,"code":200,"data":{
                  "birthday":"19991201","msg":"人脸判断为同一人","score":0.98,
                  "address":"上海市某区","orderNo":"FACE20260720001","sex":"女",
                  "resultCode":1001
                }}
                """.getBytes(StandardCharsets.UTF_8)));
        var client = client(material, transport);

        var result = client.compareFace(new ProviderFaceComparisonCommand(
                "ALIYUN_MARKET_FACE", "测试用户", "31010119991201001X",
                new byte[]{1, 2, 3, 4}));

        assertEquals(FaceDecision.VERIFIED, result.decision());
        assertEquals(1001, result.resultCode());
        assertEquals(new BigDecimal("0.98"), result.score());
        assertEquals("FACE20260720001", result.providerOrderNo());
        assertEquals(FACE, transport.endpoint);
        assertTrue(transport.body.contains("idcard=31010119991201001X"));
        assertTrue(transport.body.contains("name=%E6%B5%8B%E8%AF%95%E7%94%A8%E6%88%B7"));
        assertTrue(transport.body.contains("image=AQIDBA%3D%3D"));
        assertFalse(transport.body.contains("url="));
        assertTrue(Arrays.equals(new char[material.length], material));
    }

    @Test
    void mapsConfiguredManualReviewAndFrozenRejectCodes() {
        var manual = client("provider-appcode-123".toCharArray(),
                new RecordingTransport(faceResult(1002, "0.44")))
                .compareFace(faceCommand());
        var rejected = client("provider-appcode-456".toCharArray(),
                new RecordingTransport(faceResult(1003, "0.20")))
                .compareFace(faceCommand());

        assertEquals(FaceDecision.MANUAL_REVIEW, manual.decision());
        assertEquals(FaceDecision.REJECTED, rejected.decision());
    }

    private static AliyunMarketIdentityProviderClient client(
            char[] material, RecordingTransport transport) {
        return new AliyunMarketIdentityProviderClient(
                AliyunMarketIdentityProviderClientTest::settings,
                reference -> material, new ObjectMapper(), transport);
    }

    private static Settings settings() {
        return new Settings("ALIYUN_MARKET_FACE", TOKEN, RESULT, FACE, CALLBACK,
                1001, 1002, "kms://production/identity/appcode");
    }

    private static ProviderFaceComparisonCommand faceCommand() {
        return new ProviderFaceComparisonCommand(
                "ALIYUN_MARKET_FACE", "测试用户", "31010119991201001X", new byte[]{1});
    }

    private static Response livenessResult(int result) {
        String order = "ORDER_" + result;
        return new Response(200, ("{\"success\":true,\"code\":200,\"data\":{" +
                "\"result\":" + result + ",\"orderNo\":\"" + order + "\"}}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Response faceResult(int resultCode, String score) {
        return new Response(200, ("{\"success\":true,\"code\":200,\"data\":{" +
                "\"resultCode\":" + resultCode + ",\"score\":" + score +
                ",\"orderNo\":\"FACE_ORDER\"}}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static ProviderLivenessCommand command(URI returnUrl) {
        return new ProviderLivenessCommand(
                "ALIYUN_MARKET_FACE", 41L, 17L, "session-state-123456",
                returnUrl, "identity-live-key-0001");
    }

    private static final class RecordingTransport
            implements AliyunMarketIdentityProviderClient.Transport {
        private final Response response;
        private String body;
        private URI endpoint;

        private RecordingTransport(Response response) {
            this.response = response;
        }

        @Override
        public Response postForm(
                URI endpoint, char[] appCode, String body,
                Duration timeout, int maximumResponseBytes) {
            assertTrue(endpoint.equals(TOKEN) || endpoint.equals(RESULT) || endpoint.equals(FACE));
            assertTrue(appCode.length >= 8);
            assertEquals(Duration.ofSeconds(8), timeout);
            assertEquals(64 * 1024, maximumResponseBytes);
            this.body = body;
            this.endpoint = endpoint;
            return response;
        }
    }
}
