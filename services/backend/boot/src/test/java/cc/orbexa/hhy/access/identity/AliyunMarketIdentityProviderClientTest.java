package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.identity.AliyunMarketIdentityProviderClient.Response;
import cc.orbexa.hhy.access.identity.AliyunMarketIdentityProviderClient.Settings;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessCommand;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AliyunMarketIdentityProviderClientTest {
    private static final URI TOKEN = URI.create(
            "https://kzlive.market.alicloudapi.com/api/liveness/h5/token");
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

    private static AliyunMarketIdentityProviderClient client(
            char[] material, RecordingTransport transport) {
        return new AliyunMarketIdentityProviderClient(
                AliyunMarketIdentityProviderClientTest::settings,
                reference -> material, new ObjectMapper(), transport);
    }

    private static Settings settings() {
        return new Settings("ALIYUN_MARKET_FACE", TOKEN, CALLBACK,
                "kms://production/identity/appcode");
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

        private RecordingTransport(Response response) {
            this.response = response;
        }

        @Override
        public Response postForm(
                URI endpoint, char[] appCode, String body,
                Duration timeout, int maximumResponseBytes) {
            assertEquals(TOKEN, endpoint);
            assertTrue(appCode.length >= 8);
            assertEquals(Duration.ofSeconds(8), timeout);
            assertEquals(64 * 1024, maximumResponseBytes);
            this.body = body;
            return response;
        }
    }
}
