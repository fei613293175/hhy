package cc.orbexa.hhy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicEndpointsTest {
    @Autowired MockMvc mvc;

    @Test void platformStatusIsPublicAndTyped() throws Exception {
        mvc.perform(get("/public-api/v1/platform/status").header("X-Request-Id", "request_status_001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("request_status_001"))
                .andExpect(jsonPath("$.data.maintenance").isBoolean())
                .andExpect(jsonPath("$.data.maintenanceMessage").isString())
                .andExpect(jsonPath("$.data.capabilities.registration").value(true))
                .andExpect(jsonPath("$.data.capabilities.publishing").value(true))
                .andExpect(jsonPath("$.data.capabilities.redPacket").value(false))
                .andExpect(jsonPath("$.data.capabilities.withdrawal").value(false))
                .andExpect(jsonPath("$.data.featureFlags").doesNotExist())
                .andExpect(jsonPath("$.data.serverTime").exists())
                .andExpect(header().string("X-Request-Id", "request_status_001"));
    }

    @Test void publicVersionCheckReadsPublishedRecordAndArtifact() throws Exception {
        mvc.perform(post("/public-api/v1/app/version-check")
                        .contentType("application/json")
                        .content("""
                                {"platform":"ANDROID","versionCode":120100,"versionName":"1.2.1","channel":"official","environment":"TEST"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.latestVersionCode").value(120300))
                .andExpect(jsonPath("$.data.latestVersionName").value("1.2.3"))
                .andExpect(jsonPath("$.data.updateType").value("OPTIONAL"))
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("https://downloads.example.test/apps/official-test-1.2.3.apk"))
                .andExpect(jsonPath("$.data.sha256")
                        .value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(jsonPath("$.data.releaseNotes").value("测试环境稳定版"))
                .andExpect(jsonPath("$.data.minSupportedVersionCode").value(120000))
                .andExpect(jsonPath("$.data.serverTime").exists());
    }

    @Test void publicLatestReturnsThePublishedProductionApk() throws Exception {
        mvc.perform(get("/public-api/v1/app/latest")
                        .header("X-Request-Id", "request_latest_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("request_latest_001"))
                .andExpect(jsonPath("$.data.code").value("app-latest"))
                .andExpect(jsonPath("$.data.title").value("合伙云 Pro 2.2.0"))
                .andExpect(jsonPath("$.data.description").value("生产环境稳定版"))
                .andExpect(jsonPath("$.data.version").value(4_294_967_296L))
                .andExpect(jsonPath("$.data.download.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.download.versionName").value("2.2.0"))
                .andExpect(jsonPath("$.data.download.versionCode").value(4_294_967_296L))
                .andExpect(jsonPath("$.data.download.downloadUrl")
                        .value("https://downloads.example.com/apps/official-prod-2.2.0.apk"))
                .andExpect(jsonPath("$.data.download.sha256")
                        .value("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"));
    }

    @Test void versionPolicyEnforcesMinimumAndReturnsNoneForCurrentClient() throws Exception {
        mvc.perform(post("/public-api/v1/app/version-check")
                        .contentType("application/json")
                        .content("""
                                {"platform":"ANDROID","versionCode":119999,"channel":"official","environment":"TEST"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateType").value("FORCED"));

        mvc.perform(post("/public-api/v1/app/version-check")
                        .contentType("application/json")
                        .content("""
                                {"platform":"ANDROID","versionCode":120300,"channel":"official","environment":"TEST"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateType").value("NONE"));
    }

    @Test void compatibilityGetAllowsAnonymousAndAuthenticatedRequests() throws Exception {
        mvc.perform(get("/api/v1/app/version-check")
                        .param("platform", "ANDROID")
                        .param("versionCode", "4100000000")
                        .param("channel", "official")
                        .param("environment", "PROD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersionCode").value(4294967296L))
                .andExpect(jsonPath("$.data.updateType").value("FORCED"))
                .andExpect(jsonPath("$.data.items").doesNotExist());

        mvc.perform(get("/api/v1/app/version-check")
                        .with(user("version-reader"))
                        .param("platform", "ANDROID")
                        .param("versionCode", "120500")
                        .param("channel", "beta")
                        .param("environment", "TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersionCode").value(130000));
    }

    @Test void versionCheckRejectsInvalidPayload() throws Exception {
        mvc.perform(post("/public-api/v1/app/version-check").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"));

        mvc.perform(post("/public-api/v1/app/version-check")
                        .contentType("application/json")
                        .content("""
                                {"platform":"ANDROID","versionCode":120100,"channel":"official","environment":"TEST","unknown":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"));

        mvc.perform(get("/api/v1/app/version-check")
                        .param("platform", "ANDROID")
                        .param("versionCode", "0")
                        .param("channel", "official")
                        .param("environment", "TEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"));
    }

    @Test void missingPublishedChannelReturnsContractError() throws Exception {
        mvc.perform(post("/public-api/v1/app/version-check")
                        .contentType("application/json")
                        .content("""
                                {"platform":"ANDROID","versionCode":1,"channel":"missing","environment":"DEV"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-404-NOT_FOUND"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test void protectedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-Request-Id", "request_auth_001"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("request_auth_001"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error.code").value("COMMON-401-UNAUTHENTICATED"));
    }
}
