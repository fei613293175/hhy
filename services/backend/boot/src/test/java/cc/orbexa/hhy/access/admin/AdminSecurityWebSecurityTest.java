package cc.orbexa.hhy.access.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LoginRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-admin-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityWebSecurityTest {
    @Autowired MockMvc mvc;

    @MockitoBean AdminSecurityService service;

    @Test
    void anonymousAndTamperedBearerRequestsAreRejectedWithSafeEnvelope() throws Exception {
        mvc.perform(get("/admin-api/v1/me/security").header("X-Request-Id", "request-anonymous-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("request-anonymous-1"))
                .andExpect(jsonPath("$.error.code").value("COMMON-401-UNAUTHENTICATED"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("secret"))));

        mvc.perform(get("/admin-api/v1/me/security")
                        .header("Authorization", "Bearer tampered.jwt.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-401-UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedAdministratorWithoutRequiredAuthorityGetsForbiddenWithoutResourceLeak() throws Exception {
        mvc.perform(get("/admin-api/v1/me/security")
                        .with(adminAuthentication()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-403-FORBIDDEN"))
                .andExpect(content().string(not(containsString("root"))))
                .andExpect(content().string(not(containsString("adminId"))));
        verifyNoInteractions(service);
    }

    @Test
    void readAuthorityCannotInvokeSecurityWriteAndSecretsAreNotReflected() throws Exception {
        mvc.perform(post("/admin-api/v1/me/security/password/change")
                        .with(adminAuthentication("admin.self.read"))
                        .header("X-Idempotency-Key", "idem-key-00000003")
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"Current!234","newPassword":"New!56789","mfaCode":"123456"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-403-FORBIDDEN"))
                .andExpect(content().string(not(containsString("Current!234"))))
                .andExpect(content().string(not(containsString("New!56789"))))
                .andExpect(content().string(not(containsString("123456"))));
        verifyNoInteractions(service);
    }

    @Test
    void rateLimitedLoginReturnsRetryAfterAndDoesNotEchoCredentials() throws Exception {
        when(service.login(
                any(LoginRequest.class), anyString(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(
                        "COMMON-429-RATE_LIMITED", "登录失败次数过多，请稍后重试", 429, true, 37L));

        mvc.perform(post("/admin-api/v1/auth/login")
                        .header("X-Request-Id", "request-rate-limit-1")
                        .header("X-Idempotency-Key", "idem-key-00000004")
                        .contentType("application/json")
                        .content("""
                                {"username":"root@example.test","password":"Current!234"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(jsonPath("$.requestId").value("request-rate-limit-1"))
                .andExpect(jsonPath("$.error.code").value("COMMON-429-RATE_LIMITED"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(content().string(not(containsString("root@example.test"))))
                .andExpect(content().string(not(containsString("Current!234"))));
    }

    @Test
    void missingRequiredIdempotencyHeaderReturnsValidationEnvelope() throws Exception {
        mvc.perform(post("/admin-api/v1/auth/login")
                        .header("X-Request-Id", "request-missing-idempotency")
                        .contentType("application/json")
                        .content("""
                                {"username":"root@example.test","password":"Current!234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("request-missing-idempotency"))
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"))
                .andExpect(content().string(not(containsString("root@example.test"))))
                .andExpect(content().string(not(containsString("Current!234"))));
        verifyNoInteractions(service);
    }

    private static RequestPostProcessor adminAuthentication(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.copyOf(Arrays.asList(authorities)));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, granted));
    }

}
