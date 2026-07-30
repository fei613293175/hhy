package cc.orbexa.hhy.access.user;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-user-auth-public-rejection;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthPublicRejectionContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAuthService service;

    @Test
    void missingIdempotencyHeaderRejectsSmsSendBeforeServiceInvocation() throws Exception {
        mvc.perform(post("/api/v1/auth/sms/send")
                        .header("X-Request-Id", "reject-missing-idempotency")
                        .contentType("application/json")
                        .content("""
                                {"phone":"13800000000","scene":"LOGIN","challengeId":"challenge-r02","challengeProof":"proof-r02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("reject-missing-idempotency"))
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"))
                .andExpect(content().string(not(containsString("13800000000"))))
                .andExpect(content().string(not(containsString("proof-r02"))));

        verifyNoInteractions(service);
    }

    @Test
    void shortIdempotencyHeaderRejectsPasswordLoginBeforeServiceInvocation() throws Exception {
        mvc.perform(post("/api/v1/auth/password/login")
                        .header("X-Request-Id", "reject-short-idempotency")
                        .header("X-Idempotency-Key", "too-short")
                        .contentType("application/json")
                        .content("""
                                {"phone":"13800000000","password":"Correct99","challengeId":"challenge-r02","challengeProof":"proof-r02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("reject-short-idempotency"))
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"))
                .andExpect(content().string(not(containsString("Correct99"))))
                .andExpect(content().string(not(containsString("proof-r02"))));

        verifyNoInteractions(service);
    }

    @Test
    void malformedRegistrationPayloadReturnsSafeValidationEnvelopeBeforeServiceInvocation() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", "reject-malformed-registration")
                        .header("X-Idempotency-Key", "register-reject-idem-0001")
                        .contentType("application/json")
                        .content("{\"phone\":\"13900000000\",\"password\":\"Secret99\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("reject-malformed-registration"))
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"))
                .andExpect(content().string(not(containsString("13900000000"))))
                .andExpect(content().string(not(containsString("Secret99"))));

        verifyNoInteractions(service);
    }

    @Test
    void inviteValidationDoesNotRequireIdempotencyButStillValidatesPayload() throws Exception {
        mvc.perform(post("/api/v1/auth/invite-codes/validate")
                        .header("X-Request-Id", "reject-invalid-invite")
                        .contentType("application/json")
                        .content("{\"inviteCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("reject-invalid-invite"))
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"));

        verifyNoInteractions(service);
    }
}
