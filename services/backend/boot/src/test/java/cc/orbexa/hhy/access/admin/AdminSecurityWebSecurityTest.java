package cc.orbexa.hhy.access.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
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
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSelfSecurityResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.CommandResultResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
import org.springframework.mock.web.MockHttpServletRequest;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-admin-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityWebSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AdminTokenService tokens;
    @Autowired Clock clock;

    @MockitoBean AdminSecurityService service;
    @MockitoBean AdminSecurityStore repository;

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

    @Test
    void unsupportedLoginContentTypeReturnsValidationEnvelopeInsteadOfServerFailure() throws Exception {
        mvc.perform(post("/admin-api/v1/auth/login")
                        .header("X-Request-Id", "request-unsupported-content-type")
                        .header("X-Idempotency-Key", "idem-unsupported-content-type-1")
                        .contentType("application/octet-stream")
                        .content("must-not-appear"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("request-unsupported-content-type"))
                .andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"))
                .andExpect(content().string(not(containsString("must-not-appear"))));
        verifyNoInteractions(service);
    }

    @Test
    void revokedBearerCanOnlyReplayWhitelistedPostWithIdempotencyKey() throws Exception {
        AdminPrincipal revoked = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of(), true);
        String bearer = tokens.issueAccess(
                new AdminPrincipal(17L, 23L, 4L, "access-jti-17", "root", Set.of()),
                Instant.now(clock).plusSeconds(3600)).value();
        when(repository.authenticate(any(), any())).thenReturn(Optional.empty());
        when(repository.authenticateRevokedReplay(any(), any())).thenReturn(Optional.of(revoked));
        when(service.changePassword(any(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(securityResource());
        when(service.logout(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(new CommandResultResource(
                        "23", "23", "REVOKED", 5L, Instant.now(clock)));

        mvc.perform(post("/admin-api/v1/me/security/password/change")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Idempotency-Key", "idem-replay-password-0001")
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"Current!234","newPassword":"New!56789","mfaCode":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminId").value("17"));
        mvc.perform(post("/admin-api/v1/auth/logout")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Idempotency-Key", "idem-replay-logout-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        verify(service).changePassword(
                argThat(AdminPrincipal::replayOnly), any(), anyString(), anyString(), any(), any());
        verify(service).logout(
                argThat(AdminPrincipal::replayOnly), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void revokedBearerCannotUseGetNonWhitelistedPostOrPostWithoutIdempotencyKey() throws Exception {
        AdminPrincipal revoked = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of(), true);
        String bearer = tokens.issueAccess(
                new AdminPrincipal(17L, 23L, 4L, "access-jti-17", "root", Set.of()),
                Instant.now(clock).plusSeconds(3600)).value();
        when(repository.authenticate(any(), any())).thenReturn(Optional.empty());
        when(repository.authenticateRevokedReplay(any(), any())).thenReturn(Optional.of(revoked));

        mvc.perform(get("/admin-api/v1/me/security")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Idempotency-Key", "idem-replay-get-000000001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin-api/v1/auth/logout")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin-api/v1/me/security/not-approved")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Idempotency-Key", "idem-replay-nonwhite-0001")
                        .contentType("application/json"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void replayMarkerHasNoReadOrBusinessAuthority() throws Exception {
        mvc.perform(get("/admin-api/v1/me/security")
                        .with(replayAuthentication()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-403-FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void replayAuthorityStringCannotTurnANormalDatabasePrincipalIntoReplayOnly() throws Exception {
        AdminPrincipal normal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of());
        RequestPostProcessor forgedMarker = authentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        normal, null, List.of(new SimpleGrantedAuthority(
                                AdminPrincipal.IDEMPOTENCY_REPLAY_MARKER))));

        mvc.perform(post("/admin-api/v1/me/security/mfa/enroll")
                        .with(forgedMarker)
                        .header("X-Idempotency-Key", "idem-forged-marker-000001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-403-FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void revokedBearerWithDatabaseIdentityMismatchIsRejected() throws Exception {
        String bearer = tokens.issueAccess(
                new AdminPrincipal(17L, 23L, 4L, "access-jti-17", "root", Set.of()),
                Instant.now(clock).plusSeconds(3600)).value();
        when(repository.authenticate(any(), any())).thenReturn(Optional.empty());
        when(repository.authenticateRevokedReplay(any(), any())).thenReturn(Optional.empty());

        mvc.perform(post("/admin-api/v1/auth/logout")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Idempotency-Key", "idem-replay-mismatch-0001"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void replayWhitelistUsesContextRelativeRequestUriAndStillRequiresAnExactPath() {
        var request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContextPath("/gateway");
        request.setRequestURI("/gateway/admin-api/v1/auth/logout");
        request.addHeader("X-Idempotency-Key", "idem-context-path-000001");

        assertTrue(AdminBearerAuthenticationFilter.replayOnlyRequest(request));

        request.setRequestURI("/gateway/admin-api/v1/auth/logout/extra");
        assertFalse(AdminBearerAuthenticationFilter.replayOnlyRequest(request));
        request.setRequestURI("/gateway/admin-api/v1/auth/logout");
        request.setMethod("GET");
        assertFalse(AdminBearerAuthenticationFilter.replayOnlyRequest(request));
    }

    private static RequestPostProcessor adminAuthentication(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.copyOf(Arrays.asList(authorities)));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, granted));
    }

    private static RequestPostProcessor replayAuthentication() {
        AdminPrincipal principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of(), true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null,
                List.of(new SimpleGrantedAuthority(AdminPrincipal.IDEMPOTENCY_REPLAY_MARKER))));
    }

    private static AdminSelfSecurityResource securityResource() {
        return new AdminSelfSecurityResource(
                "17", "root", true, List.of("TOTP"), 0,
                null, null, null, 0);
    }

}
