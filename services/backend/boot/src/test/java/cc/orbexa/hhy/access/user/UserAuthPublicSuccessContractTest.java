package cc.orbexa.hhy.access.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.orbexa.hhy.access.user.UserAuthContracts.ChallengeResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.InviteCodeValidateRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.InviteRegistrationConfigPageResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.PasswordResetRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.PublicPageBlockResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.PublicPageMetaResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.PublicPageResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.RegistrationAgreementVersionResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.RegistrationConfigResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.SecurityChallengeRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SmsSendRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hhy-user-auth-public-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthPublicSuccessContractTest {
    private static final Instant ACCEPTED_AT = Instant.parse("2026-07-18T02:30:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean UserAuthService service;

    @Test
    void securityChallengeBindsFrozenRequestAndIdempotencyHeader() throws Exception {
        Instant expiresAt = Instant.parse("2026-07-18T02:45:00Z");
        when(service.createChallenge(any(SecurityChallengeRequest.class), eq("challenge-idem-key-0001")))
                .thenReturn(new ChallengeResource(
                        "challenge-302", "IMAGE", expiresAt, "base64-image", "challenge-token"));

        mvc.perform(post("/api/v1/auth/security-challenges")
                        .header("X-Request-Id", "contract-security-challenge")
                        .header("X-Idempotency-Key", "challenge-idem-key-0001")
                        .contentType("application/json")
                        .content("""
                                {"scene":"REGISTER","clientNonce":"nonce-r02-001","deviceFingerprint":"install-fingerprint"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-security-challenge"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.challengeId").value("challenge-302"))
                .andExpect(jsonPath("$.data.challengeType").value("IMAGE"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-18T02:45:00Z"))
                .andExpect(jsonPath("$.data.imageBase64").value("base64-image"))
                .andExpect(jsonPath("$.data.token").value("challenge-token"))
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<SecurityChallengeRequest> request = ArgumentCaptor.forClass(SecurityChallengeRequest.class);
        verify(service).createChallenge(request.capture(), eq("challenge-idem-key-0001"));
        Assertions.assertEquals(UserAuthContracts.AuthScene.REGISTER, request.getValue().scene());
        Assertions.assertEquals("nonce-r02-001", request.getValue().clientNonce());
        Assertions.assertEquals("install-fingerprint", request.getValue().deviceFingerprint());
    }

    @Test
    void smsSendBindsFrozenChallengeFieldsAndIdempotencyHeader() throws Exception {
        when(service.sendSms(any(SmsSendRequest.class), eq("sms-send-idem-key-0001"), anyString()))
                .thenReturn(commandResult("sms-command-302", "SMS_ACCEPTED"));

        mvc.perform(post("/api/v1/auth/sms/send")
                        .header("X-Request-Id", "contract-sms-send")
                        .header("X-Idempotency-Key", "sms-send-idem-key-0001")
                        .contentType("application/json")
                        .content("""
                                {"phone":"13800000000","scene":"RESET_PASSWORD","challengeId":"challenge-302","challengeProof":"proof-r02-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-sms-send"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.resourceId").value("sms-command-302"))
                .andExpect(jsonPath("$.data.status").value("SMS_ACCEPTED"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-07-18T02:30:00Z"))
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<SmsSendRequest> request = ArgumentCaptor.forClass(SmsSendRequest.class);
        verify(service).sendSms(request.capture(), eq("sms-send-idem-key-0001"), anyString());
        Assertions.assertEquals("13800000000", request.getValue().phone());
        Assertions.assertEquals(UserAuthContracts.AuthScene.RESET_PASSWORD, request.getValue().scene());
        Assertions.assertEquals("challenge-302", request.getValue().challengeId());
        Assertions.assertEquals("proof-r02-001", request.getValue().challengeProof());
    }

    @Test
    void inviteValidationBindsFrozenRequestWithoutIdempotencyHeader() throws Exception {
        when(service.validateInvite(any(InviteCodeValidateRequest.class)))
                .thenReturn(commandResult("42", "VALID"));

        mvc.perform(post("/api/v1/auth/invite-codes/validate")
                        .header("X-Request-Id", "contract-invite-validate")
                        .contentType("application/json")
                        .content("""
                                {"inviteCode":"INVITE-R02"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-invite-validate"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.resourceId").value("42"))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-07-18T02:30:00Z"))
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<InviteCodeValidateRequest> request = ArgumentCaptor.forClass(InviteCodeValidateRequest.class);
        verify(service).validateInvite(request.capture());
        Assertions.assertEquals("INVITE-R02", request.getValue().inviteCode());
    }

    @Test
    void registrationConfigIsPublicAndReturnsOnlyCurrentAgreementVersionIdentifiers() throws Exception {
        when(service.registrationConfig()).thenReturn(new RegistrationConfigResource(List.of(
                new RegistrationAgreementVersionResource("91", "USER_SERVICE", ACCEPTED_AT))));

        mvc.perform(get("/api/v1/auth/registration-config")
                        .header("X-Request-Id", "contract-registration-config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-registration-config"))
                .andExpect(jsonPath("$.data.agreementVersions[0].versionId").value("91"))
                .andExpect(jsonPath("$.data.agreementVersions[0].code").value("USER_SERVICE"))
                .andExpect(jsonPath("$.data.agreementVersions[0].effectiveAt").value("2026-07-18T02:30:00Z"));

        verify(service).registrationConfig();
    }

    @Test
    void inviteRegistrationConfigUsesFrozenPublicPageEnvelope() throws Exception {
        when(service.inviteRegistrationConfig("INVITE-R02", 1, 20)).thenReturn(
                new InviteRegistrationConfigPageResource(
                        List.of(new PublicPageResource(
                                "INVITE-R02", "加入合伙云", "完成安全验证后注册",
                                List.of(new PublicPageBlockResource(
                                        "91", "RICH_TEXT", "USER_SERVICE",
                                        "2026-07-18T02:30:00Z", List.of(), null, 0)),
                                null, null, null, 7)),
                        new PublicPageMetaResource(1, 20, "1", null, "false")));

        mvc.perform(get("/public-api/v1/invite/INVITE-R02/registration-config")
                        .header("X-Request-Id", "contract-invite-registration-config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-invite-registration-config"))
                .andExpect(jsonPath("$.data.items[0].code").value("INVITE-R02"))
                .andExpect(jsonPath("$.data.items[0].content[0].blockId").value("91"))
                .andExpect(jsonPath("$.data.items[0].content[0].blockType").value("RICH_TEXT"))
                .andExpect(jsonPath("$.data.items[0].version").value(7))
                .andExpect(jsonPath("$.data.page.page").value(1))
                .andExpect(jsonPath("$.data.page.pageSize").value(20))
                .andExpect(jsonPath("$.data.page.total").value("1"))
                .andExpect(jsonPath("$.data.page.hasMore").value("false"));

        verify(service).inviteRegistrationConfig("INVITE-R02", 1, 20);
    }

    @Test
    void passwordResetBindsFrozenRequestAndIdempotencyHeader() throws Exception {
        when(service.resetPassword(any(PasswordResetRequest.class), eq("password-reset-idem-0001")))
                .thenReturn(commandResult("17", "PASSWORD_RESET"));

        mvc.perform(post("/api/v1/auth/password/reset")
                        .header("X-Request-Id", "contract-password-reset")
                        .header("X-Idempotency-Key", "password-reset-idem-0001")
                        .contentType("application/json")
                        .content("""
                                {"phone":"13900000000","smsCode":"481516","newPassword":"ResetPass99"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-password-reset"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.resourceId").value("17"))
                .andExpect(jsonPath("$.data.status").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-07-18T02:30:00Z"))
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<PasswordResetRequest> request = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(service).resetPassword(request.capture(), eq("password-reset-idem-0001"));
        Assertions.assertEquals("13900000000", request.getValue().phone());
        Assertions.assertEquals("481516", request.getValue().smsCode());
        Assertions.assertEquals("ResetPass99", request.getValue().newPassword());
    }

    private static CommandResultResource commandResult(String resourceId, String status) {
        return new CommandResultResource(resourceId, "business-r02-001", status, 0L, ACCEPTED_AT);
    }
}
