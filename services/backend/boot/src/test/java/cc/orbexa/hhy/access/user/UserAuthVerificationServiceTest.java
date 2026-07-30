package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class UserAuthVerificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");
    private static final String PHONE = "13800000000";

    @Mock UserAuthStore repository;
    @Mock UserAuthPolicy policy;
    @Mock ObjectProvider<SmsProvider> smsProviders;
    @Mock SmsProvider smsProvider;

    private UserTokenService tokens;
    private UserAuthVerificationService verification;

    @BeforeEach
    void setUp() {
        UserAuthProperties properties = new UserAuthProperties(
                "test-only-user-jwt-secret-at-least-32-characters",
                "test-only-user-token-hmac-secret-at-least-32-characters",
                "test-only-user-snapshot-root-secret-at-least-32-characters",
                "test/user", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24));
        tokens = new UserTokenService(new ObjectMapper().findAndRegisterModules(), properties);
        verification = new UserAuthVerificationService(repository, policy, tokens, smsProviders,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void challengeImageIsARealPngThatRasterClientsCanDecode() {
        byte[] image = Base64.getDecoder().decode(UserAuthVerificationService.renderChallenge("6406"));

        assertEquals((byte) 0x89, image[0]);
        assertEquals('P', image[1]);
        assertEquals('N', image[2]);
        assertEquals('G', image[3]);
        assertEquals(160, ByteBuffer.wrap(image, 16, 4).getInt());
        assertEquals(56, ByteBuffer.wrap(image, 20, 4).getInt());
        assertEquals(8, image[24]);
        assertEquals(2, image[25]);
    }

    @Test
    void challengeRendererRejectsUnexpectedNonNumericContent() {
        assertThrows(IllegalArgumentException.class,
                () -> UserAuthVerificationService.renderChallenge("<svg"));
    }

    @Test
    void invalidSmsCodeRecordsFailureWithoutConsumingTheCode() {
        String validHash = tokens.intentHash("sms:" + AuthScene.LOGIN, PHONE, "481516");
        when(repository.findSmsCodeForUpdate(PHONE, AuthScene.LOGIN.name())).thenReturn(Optional.of(
                new UserAuthStore.SmsCodeRow(61L, validHash, NOW.plus(Duration.ofMinutes(5)), null, 0, 3)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> verification.verifySms(PHONE, AuthScene.LOGIN, "000000"));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        verify(repository).failSmsCode(61L);
        verify(repository, never()).consumeSmsCode(anyLong());
    }

    @Test
    void validSmsCodeIsConsumedExactlyOnce() {
        String validHash = tokens.intentHash("sms:" + AuthScene.LOGIN, PHONE, "481516");
        when(repository.findSmsCodeForUpdate(PHONE, AuthScene.LOGIN.name())).thenReturn(Optional.of(
                new UserAuthStore.SmsCodeRow(61L, validHash, NOW.plus(Duration.ofMinutes(5)), null, 0, 3)));
        when(repository.consumeSmsCode(61L)).thenReturn(true);

        verification.verifySms(PHONE, AuthScene.LOGIN, "481516");

        verify(repository).consumeSmsCode(61L);
        verify(repository, never()).failSmsCode(anyLong());
    }

    @Test
    void challengeCannotBeReusedAcrossAuthenticationScenes() {
        when(repository.findChallengeForUpdate(71L)).thenReturn(Optional.of(
                new UserAuthStore.ChallengeRow(
                        71L, AuthScene.REGISTER.name(), "challenge-hash", NOW.plus(Duration.ofMinutes(5)), null, 0, 3)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> verification.verifyChallenge("71", "proof", AuthScene.LOGIN));

        assertEquals("AUTH-422-SECURITY_CHALLENGE_INVALID", error.code());
        verify(repository, never()).failChallenge(anyLong());
        verify(repository, never()).consumeChallenge(anyLong());
    }

    @Test
    void incorrectChallengeProofUsesDedicatedErrorCodeAndRecordsFailure() {
        String validHash = tokens.intentHash("challenge:" + AuthScene.LOGIN, "4815");
        when(repository.findChallengeForUpdate(71L)).thenReturn(Optional.of(
                new UserAuthStore.ChallengeRow(
                        71L, AuthScene.LOGIN.name(), validHash,
                        NOW.plus(Duration.ofMinutes(5)), null, 0, 3)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> verification.verifyChallenge("71", "0000", AuthScene.LOGIN));

        assertEquals("AUTH-422-SECURITY_CHALLENGE_INVALID", error.code());
        verify(repository).failChallenge(71L);
        verify(repository, never()).consumeChallenge(anyLong());
    }

    @Test
    void missingSmsProviderFailsClosedBeforeChallengeOrCodePersistence() {
        when(smsProviders.getIfAvailable()).thenReturn(null);
        var request = new UserAuthContracts.SmsSendRequest(
                PHONE, AuthScene.LOGIN, "71", "challenge-proof");

        BusinessException error = assertThrows(BusinessException.class,
                () -> verification.sendSms(request, "127.0.0.1"));

        assertEquals("COMMON-500-INTERNAL", error.code());
        verifyNoInteractions(repository, policy);
    }

    @Test
    void smsProviderTimeoutDoesNotRecordAFalseDeliveryReceipt() {
        String challengeHash = tokens.intentHash("challenge:" + AuthScene.LOGIN, "challenge-proof");
        when(smsProviders.getIfAvailable()).thenReturn(smsProvider);
        when(repository.findChallengeForUpdate(71L)).thenReturn(Optional.of(
                new UserAuthStore.ChallengeRow(
                        71L, AuthScene.LOGIN.name(), challengeHash,
                        NOW.plus(Duration.ofMinutes(5)), null, 0, 3)));
        when(repository.consumeChallenge(71L)).thenReturn(true);
        when(policy.smsCooldown()).thenReturn(Duration.ofSeconds(60));
        when(policy.smsDailyPhoneLimit()).thenReturn(5);
        when(policy.smsDailyIpLimit()).thenReturn(5);
        when(policy.smsCodeLength()).thenReturn(6);
        when(policy.smsTtl()).thenReturn(Duration.ofMinutes(5));
        when(policy.smsMaxAttempts()).thenReturn(3);
        when(repository.createSmsCode(eq(PHONE), eq(AuthScene.LOGIN.name()), anyString(),
                eq(NOW.plus(Duration.ofMinutes(5))), eq(3))).thenReturn(81L);
        when(smsProvider.sendVerificationCode(eq(PHONE), eq(AuthScene.LOGIN), anyString()))
                .thenThrow(new RuntimeException("provider timeout"));
        var request = new UserAuthContracts.SmsSendRequest(
                PHONE, AuthScene.LOGIN, "71", "challenge-proof");

        assertThrows(RuntimeException.class, () -> verification.sendSms(request, "127.0.0.1"));

        verify(repository, never()).recordSmsDelivery(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
