package cc.orbexa.hhy.access.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSelfSecurityResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSessionResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.CommandResultResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LoginRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaConfirmRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaDisableRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaEnrollmentResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaVerifyRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.PasswordChangeRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-admin-success;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecuritySuccessContractTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-17T10:00:00Z");
    private static final String IDEMPOTENCY_KEY = "contract-idem-key-0001";

    @Autowired MockMvc mvc;
    @MockitoBean AdminSecurityService service;

    @Test
    void loginSuccessMatchesFrozenEnvelopeAndSessionShape() throws Exception {
        when(service.login(any(LoginRequest.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(session("access-token", "NONE", null));

        envelope(mvc.perform(post("/admin-api/v1/auth/login")
                        .header("X-Request-Id", "contract-login")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"username":"root","password":"Current!234"}
                                """)), "contract-login")
                .andExpect(jsonPath("$.data.adminUserId").value("17"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.mfaRequired").value("NONE"))
                .andExpect(jsonPath("$.data.permissionCodes", hasSize(2)));
    }

    @Test
    void mfaVerifySuccessMatchesFrozenEnvelopeAndSessionShape() throws Exception {
        when(service.verifyMfa(
                any(MfaVerifyRequest.class), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(session("verified-access-token", "NONE", null));

        envelope(mvc.perform(post("/admin-api/v1/auth/mfa/verify")
                        .header("X-Request-Id", "contract-mfa-verify")
                        .header("X-MFA-Ticket", "mfa-ticket-00000001")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"mfaTicket":"mfa-ticket-00000001","code":"123456"}
                                """)), "contract-mfa-verify")
                .andExpect(jsonPath("$.data.accessToken").value("verified-access-token"))
                .andExpect(jsonPath("$.data.adminUserId").value("17"))
                .andExpect(jsonPath("$.data.mfaRequired").value("NONE"));
    }

    @Test
    void logoutSuccessAcceptsOmittedBodyAndMatchesFrozenCommandShape() throws Exception {
        when(service.logout(any(AdminPrincipal.class), isNull(), isNull(), anyString(), anyString(), anyString()))
                .thenReturn(new CommandResultResource("23", "ADMIN-23", "REVOKED", 5L, EXPIRES_AT));

        envelope(mvc.perform(post("/admin-api/v1/auth/logout")
                        .with(adminAuthentication("admin.self.read"))
                        .header("X-Request-Id", "contract-logout")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)), "contract-logout")
                .andExpect(jsonPath("$.data.resourceId").value("23"))
                .andExpect(jsonPath("$.data.businessNo").value("ADMIN-23"))
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.version").value(5));
    }

    @Test
    void overviewSuccessMatchesFrozenSecurityResourceShape() throws Exception {
        when(service.overview(any(AdminPrincipal.class))).thenReturn(security(true));

        envelope(mvc.perform(get("/admin-api/v1/me/security")
                        .with(adminAuthentication("admin.self.read"))
                        .header("X-Request-Id", "contract-security")), "contract-security")
                .andExpect(jsonPath("$.data.adminId").value("17"))
                .andExpect(jsonPath("$.data.username").value("root"))
                .andExpect(jsonPath("$.data.mfaEnabled").value(true))
                .andExpect(jsonPath("$.data.mfaMethods[0]").value("TOTP"))
                .andExpect(jsonPath("$.data.activeSessionCount").value(2))
                .andExpect(jsonPath("$.data.recoveryCodesRemaining").value(6));
    }

    @Test
    void passwordChangeSuccessReturnsFrozenSecurityResourceWithoutCredentials() throws Exception {
        when(service.changePassword(
                any(AdminPrincipal.class), any(PasswordChangeRequest.class), anyString(), anyString(),
                anyString(), any()))
                .thenReturn(security(true));

        envelope(mvc.perform(post("/admin-api/v1/me/security/password/change")
                        .with(adminAuthentication("admin.self.security"))
                        .header("X-Request-Id", "contract-password")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"Current!234","newPassword":"New!56789","mfaCode":"123456"}
                                """)), "contract-password")
                .andExpect(jsonPath("$.data.adminId").value("17"))
                .andExpect(jsonPath("$.data.mfaEnabled").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("New!56789"))));
    }

    @Test
    void mfaEnrollSuccessMatchesFrozenOneTimeEnrollmentShape() throws Exception {
        when(service.enrollMfa(any(AdminPrincipal.class), anyString(), anyString(), anyString()))
                .thenReturn(new MfaEnrollmentResource(
                        "enrollment-17", "TOTP", "otpauth://totp/test", "ABCD****WXYZ", EXPIRES_AT));

        envelope(mvc.perform(post("/admin-api/v1/me/security/mfa/enroll")
                        .with(adminAuthentication("admin.self.security"))
                        .header("X-Request-Id", "contract-enroll")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)), "contract-enroll")
                .andExpect(jsonPath("$.data.enrollmentId").value("enrollment-17"))
                .andExpect(jsonPath("$.data.method").value("TOTP"))
                .andExpect(jsonPath("$.data.secretQrCodeUrl").isString())
                .andExpect(jsonPath("$.data.manualKeyMasked").value("ABCD****WXYZ"))
                .andExpect(jsonPath("$.data.expiresAt").isString());
    }

    @Test
    void mfaConfirmSuccessReturnsFrozenSecurityResource() throws Exception {
        when(service.confirmMfa(
                any(AdminPrincipal.class), any(MfaConfirmRequest.class), anyString(), anyString(),
                anyString(), any()))
                .thenReturn(security(true));

        envelope(mvc.perform(post("/admin-api/v1/me/security/mfa/confirm")
                        .with(adminAuthentication("admin.self.security"))
                        .header("X-Request-Id", "contract-confirm")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"enrollmentId":"enrollment-17","code":"123456"}
                                """)), "contract-confirm")
                .andExpect(jsonPath("$.data.adminId").value("17"))
                .andExpect(jsonPath("$.data.mfaEnabled").value(true))
                .andExpect(jsonPath("$.data.mfaMethods[0]").value("TOTP"));
    }

    @Test
    void mfaDisableSuccessReturnsFrozenSecurityResource() throws Exception {
        when(service.disableMfa(
                any(AdminPrincipal.class), any(MfaDisableRequest.class), anyString(), anyString(),
                anyString(), any()))
                .thenReturn(security(false));

        envelope(mvc.perform(post("/admin-api/v1/me/security/mfa/disable")
                        .with(adminAuthentication("admin.self.security"))
                        .header("X-Request-Id", "contract-disable")
                        .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"code":"123456","reason":"device replaced"}
                                """)), "contract-disable")
                .andExpect(jsonPath("$.data.adminId").value("17"))
                .andExpect(jsonPath("$.data.mfaEnabled").value(false))
                .andExpect(jsonPath("$.data.mfaMethods", hasSize(0)));
    }

    private static ResultActions envelope(ResultActions actions, String requestId) throws Exception {
        return actions
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    private static AdminSessionResource session(String accessToken, String mfaRequired, String mfaTicket) {
        return new AdminSessionResource(
                accessToken, EXPIRES_AT, "17", "root",
                List.of("admin.self.read", "admin.self.security"), mfaRequired, mfaTicket);
    }

    private static AdminSelfSecurityResource security(boolean mfaEnabled) {
        return new AdminSelfSecurityResource(
                "17", "root", mfaEnabled, mfaEnabled ? List.of("TOTP") : List.of(), 2L,
                Instant.parse("2026-07-16T08:00:00Z"), Instant.parse("2026-07-17T07:00:00Z"),
                "203.0.113.*", 6L);
    }

    private static RequestPostProcessor adminAuthentication(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.copyOf(Arrays.asList(authorities)));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, granted));
    }

}
