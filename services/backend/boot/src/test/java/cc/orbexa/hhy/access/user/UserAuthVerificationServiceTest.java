package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
}
