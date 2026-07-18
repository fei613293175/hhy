package cc.orbexa.hhy.access.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.orbexa.hhy.access.user.UserAuthContracts.DeviceSummaryResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.RefreshRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserSessionResource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-user-auth-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthSuccessContractTest {
    private static final String REFRESH_TOKEN =
            "hhy_rt1_contract-refresh-token-value-with-enough-entropy";

    @Autowired MockMvc mvc;
    @MockitoBean UserAuthService service;

    @Test
    void refreshSuccessMatchesFrozenEnvelopeAndSessionShape() throws Exception {
        Instant expiresAt = Instant.parse("2026-07-18T01:15:00Z");
        when(service.refresh(any(RefreshRequest.class), anyString(), anyString()))
                .thenReturn(new UserSessionResource(
                        "access-token", "rotated-refresh-token", expiresAt,
                        "17", "23",
                        new DeviceSummaryResource(
                                "31", "Pixel 9", "ANDROID", "16", "1.2.3", expiresAt, true),
                        List.of("content.read")));

        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Request-Id", "contract-user-refresh")
                        .header("X-Refresh-Token", REFRESH_TOKEN)
                        .header("X-Idempotency-Key", "refresh-idem-key-0001")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s","deviceId":"31"}
                                """.formatted(REFRESH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-user-refresh"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("rotated-refresh-token"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-18T01:15:00Z"))
                .andExpect(jsonPath("$.data.userId").value("17"))
                .andExpect(jsonPath("$.data.sessionId").value("23"))
                .andExpect(jsonPath("$.data.device.deviceId").value("31"))
                .andExpect(jsonPath("$.data.device.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.capabilities[0]").value("content.read"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
