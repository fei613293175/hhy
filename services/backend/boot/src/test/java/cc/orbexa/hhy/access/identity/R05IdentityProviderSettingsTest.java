package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class R05IdentityProviderSettingsTest {
    private final R05IdentityProviderSettings loader =
            new R05IdentityProviderSettings(null, new ObjectMapper());

    @Test
    void loadsEveryPurchasedProviderEndpointAndResultMappingFromActivatedVersion() {
        var settings = loader.settings("""
                {
                  "identity.active_provider":"ALIYUN_MARKET_FACE",
                  "identity.liveness.token_url":"https://kzlive.market.alicloudapi.com/api/liveness/h5/token",
                  "identity.liveness.result_url":"https://kzlive.market.alicloudapi.com/api/liveness/h5/result",
                  "identity.face_compare.url":"https://sdfaceid.market.alicloudapi.com/face_id_card/check",
                  "identity.liveness.return_url":"https://h5.orbexa.cc/identity/callback",
                  "identity.face_compare.auto_pass_code":1001,
                  "identity.face_compare.manual_review_code":1002
                }
                """, """
                {"identity.provider.appcode":"kms://production/identity/appcode"}
                """);

        assertEquals("ALIYUN_MARKET_FACE", settings.provider());
        assertEquals("/api/liveness/h5/token", settings.tokenEndpoint().getPath());
        assertEquals("/api/liveness/h5/result", settings.resultEndpoint().getPath());
        assertEquals("/face_id_card/check", settings.faceCompareEndpoint().getPath());
        assertEquals(1001, settings.autoPassCode());
        assertEquals(1002, settings.manualReviewCode());
        assertEquals("kms://production/identity/appcode", settings.appCodeReference());
    }

    @Test
    void refusesAnActivatedVersionMissingAResultMapping() {
        assertThrows(IllegalStateException.class, () -> loader.settings("""
                {
                  "identity.active_provider":"ALIYUN_MARKET_FACE",
                  "identity.liveness.token_url":"https://provider.example/token",
                  "identity.liveness.result_url":"https://provider.example/result",
                  "identity.face_compare.url":"https://provider.example/compare",
                  "identity.liveness.return_url":"https://h5.orbexa.cc/identity/callback",
                  "identity.face_compare.auto_pass_code":1001
                }
                """, """
                {"identity.provider.appcode":"kms://production/identity/appcode"}
                """));
    }
}
