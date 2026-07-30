package cc.orbexa.hhy.access.user;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.PageMetaResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.SessionPageResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.SecuritySessionResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.SupportTicketResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserResource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-user-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSecurityWebSecurityTest {
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(17L, 23L, 4L, "access-jti-17", "ACTIVE");
    private static final String IDEMPOTENCY_KEY = "idem-user-security-0001";

    @Autowired MockMvc mvc;
    @MockitoBean UserAuthService service;
    @MockitoBean UserAuthStore store;

    @Test
    void anonymousDeviceAndPasswordRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/auth/sessions").header("X-Request-Id", "anonymous-device-list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-401-UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/me/security/password/change")
                        .header("X-Request-Id", "anonymous-password-change")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Current!234\",\"newPassword\":\"New!56789\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-401-UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedUserGetsSafeSessionListWithoutCredentials() throws Exception {
        when(service.sessions(eq(PRINCIPAL), anyInt(), anyInt())).thenReturn(new SessionPageResource(
                List.of(new SecuritySessionResource("23", null, Instant.parse("2026-07-18T04:00:00Z"),
                        Instant.parse("2026-08-18T04:00:00Z"), "ACTIVE", true)),
                new PageMetaResource(1, 20, 1, false)));

        mvc.perform(get("/api/v1/auth/sessions").with(userAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value("23"))
                .andExpect(jsonPath("$.data.items[0].current").value(true))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
        verify(service).sessions(PRINCIPAL, 1, 20);
    }

    @Test
    void authenticatedUserCanRevokeDeviceAndChangePasswordWithoutEchoingSecrets() throws Exception {
        when(service.revokeSession(eq(PRINCIPAL), eq("24"), anyString()))
                .thenReturn(new CommandResultResource("24", null, "REVOKED", 2L, Instant.now()));
        when(service.changePassword(eq(PRINCIPAL), any(), anyString()))
                .thenReturn(new CommandResultResource("17", null, "PASSWORD_CHANGED", null, Instant.now()));

        mvc.perform(delete("/api/v1/auth/sessions/24").with(userAuthentication())
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        mvc.perform(post("/api/v1/me/security/password/change").with(userAuthentication())
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Current!234\",\"newPassword\":\"New!56789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PASSWORD_CHANGED"))
                .andExpect(content().string(not(containsString("Current!234"))))
                .andExpect(content().string(not(containsString("New!56789"))));
        verify(service).revokeSession(PRINCIPAL, "24", IDEMPOTENCY_KEY);
        verify(service).changePassword(eq(PRINCIPAL), any(), eq(IDEMPOTENCY_KEY));
    }

    @Test
    void frozenUserCanOnlyReadSelfAndSubmitAppeal() throws Exception {
        UserPrincipal restricted = new UserPrincipal(17L, 23L, 4L, "access-jti-17", "FROZEN");
        when(service.self(restricted)).thenReturn(new UserResource(
                "17", "138****0000", "合伙人17", null, null, "FROZEN", null, null,
                Instant.parse("2026-07-18T04:00:00Z"), 2L));
        when(service.createSupportTicket(eq(restricted), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new SupportTicketResource(
                        "81", "HHY81", "ACCOUNT_APPEAL", "账号冻结申诉", "OPEN", null,
                        Instant.parse("2026-07-18T04:01:00Z"),
                        Instant.parse("2026-07-18T04:01:00Z"), 0L));

        var restrictedAuth = authentication(UsernamePasswordAuthenticationToken.authenticated(
                restricted, null, List.of(new SimpleGrantedAuthority("ROLE_RESTRICTED_USER"))));
        mvc.perform(get("/api/v1/me").with(restrictedAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****0000"));
        mvc.perform(post("/api/v1/support/tickets").with(restrictedAuth)
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("{\"category\":\"ACCOUNT_APPEAL\",\"subject\":\"账号冻结申诉\",\"content\":\"请复核账号状态\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketNo").value("HHY81"));
        mvc.perform(get("/api/v1/auth/sessions").with(restrictedAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-403-FORBIDDEN"));
    }

    @Test
    void activeUserCanRequestVersionBoundCancellationWithoutSmsEcho() throws Exception {
        when(service.requestCancellation(eq(PRINCIPAL), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new CommandResultResource(
                        "17", null, "CANCELLATION_PENDING", 3L, Instant.parse("2026-07-18T04:02:00Z")));

        mvc.perform(post("/api/v1/me/cancellation").with(userAuthentication())
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("{\"reason\":\"不再使用\",\"smsCode\":\"481516\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLATION_PENDING"))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(content().string(not(containsString("481516"))));
        verify(service).requestCancellation(eq(PRINCIPAL), any(), eq(IDEMPOTENCY_KEY));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor userAuthentication() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
