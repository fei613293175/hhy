package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Status;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeCode;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeRequest;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeResult;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProviderConnector;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ProviderConnectionTestCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-07-18T14:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ProviderConfigValidator validator = new ProviderConfigValidator();

    @Test
    void successfulSmsProbeAdvancesLifecycleAndZeroizesResolvedSecrets() {
        AtomicReference<char[]> observedSecret = new AtomicReference<>();
        ProviderConnector sms = connector("sms", request -> {
            observedSecret.set(request.secrets().secret("sms.aliyun.access_key_secret"));
            assertArrayEquals("resolved-secret".toCharArray(), observedSecret.get());
            assertEquals("13800000000", request.testRecipient());
            return ProbeResult.success();
        });
        var coordinator = new ProviderConnectionTestCoordinator(
                List.of(sms), reference -> "resolved-secret".toCharArray(), CLOCK);

        var outcome = coordinator.test(validatedLifecycle("sms"), validatedSms(),
                "13800000000", 1L);

        assertTrue(outcome.successful());
        assertEquals(ProbeCode.OK, outcome.code());
        assertEquals(Status.CONNECTION_TESTED, outcome.snapshot().status());
        assertEquals("sms:OK", outcome.snapshot().maskedTestResult());
        assertEquals(NOW, outcome.testedAt());
        assertTrue(allZero(observedSecret.get()));
    }

    @Test
    void providerExceptionIsReducedToSafeFailureCodeAndCannotAdvance() {
        ProviderConnector sms = connector("sms", request -> {
            throw new IllegalStateException("Authorization secret should never escape");
        });
        var coordinator = new ProviderConnectionTestCoordinator(
                List.of(sms), reference -> "resolved-secret".toCharArray(), CLOCK);

        var outcome = coordinator.test(validatedLifecycle("sms"), validatedSms(), null, 1L);

        assertFalse(outcome.successful());
        assertEquals(ProbeCode.PROVIDER_ERROR, outcome.code());
        assertEquals(Status.VALIDATED, outcome.snapshot().status());
        assertEquals("sms:PROVIDER_ERROR", outcome.snapshot().maskedTestResult());
        assertFalse(outcome.snapshot().maskedTestResult().contains("Authorization"));
    }

    @Test
    void unavailableSecretIsRecordedWithoutCallingConnector() {
        ProviderConnector sms = connector("sms", request -> {
            throw new AssertionError("connector must not run when a secret cannot be resolved");
        });
        var coordinator = new ProviderConnectionTestCoordinator(List.of(sms), reference -> {
            throw new IllegalStateException("vault token unavailable");
        }, CLOCK);

        var outcome = coordinator.test(validatedLifecycle("sms"), validatedSms(), null, 1L);

        assertEquals(ProbeCode.SECRET_UNAVAILABLE, outcome.code());
        assertEquals(Status.VALIDATED, outcome.snapshot().status());
        assertEquals("sms:CREDENTIAL_UNAVAILABLE", outcome.snapshot().maskedTestResult());
    }

    @Test
    void missingRequiredSecretRefIsRejectedBeforeProbe() {
        ObjectNode values = smsValues();
        ObjectNode incompleteSecrets = JSON.createObjectNode()
                .put("sms.aliyun.access_key_id", "vault://staging/sms/id");
        var config = validator.validate("sms", "STAGING", values, incompleteSecrets);
        var coordinator = new ProviderConnectionTestCoordinator(List.of(),
                reference -> "unused".toCharArray(), CLOCK);

        BusinessException error = assertThrows(BusinessException.class,
                () -> coordinator.test(validatedLifecycle("sms"), config, null, 1L));

        assertEquals("COMMON-400-VALIDATION", error.code());
        assertTrue(error.getMessage().contains("access_key_secret"));
    }

    @Test
    void storageAndIdentityPlansRejectNonHttpsOrUnsupportedProviderValues() {
        ObjectNode storageValues = JSON.createObjectNode()
                .put("storage.default_provider", "CLOUDFLARE_R2")
                .put("storage.r2.account_id", "account-1")
                .put("storage.r2.endpoint", "http://r2.example.test");
        ObjectNode storageSecrets = JSON.createObjectNode()
                .put("storage.r2.access_key_id", "vault://staging/r2/id")
                .put("storage.r2.secret_access_key", "vault://staging/r2/secret");
        var storage = validator.validate("storage", "STAGING", storageValues, storageSecrets);
        var coordinator = new ProviderConnectionTestCoordinator(List.of(),
                reference -> "unused".toCharArray(), CLOCK);

        BusinessException storageError = assertThrows(BusinessException.class,
                () -> coordinator.test(validatedLifecycle("storage"), storage, null, 1L));
        assertTrue(storageError.getMessage().contains("HTTPS"));

        ObjectNode identityValues = JSON.createObjectNode()
                .put("identity.active_provider", "UNSUPPORTED")
                .put("identity.liveness.token_url", "https://identity.example/token")
                .put("identity.liveness.result_url", "https://identity.example/result")
                .put("identity.face_compare.url", "https://identity.example/compare");
        ObjectNode identitySecrets = JSON.createObjectNode()
                .put("identity.provider.appcode", "kms://staging/identity/appcode");
        var identity = validator.validate("identity", "STAGING", identityValues, identitySecrets);
        BusinessException identityError = assertThrows(BusinessException.class,
                () -> coordinator.test(validatedLifecycle("identity"), identity, null, 1L));
        assertTrue(identityError.getMessage().contains("active_provider"));
    }

    private ProviderConfigValidator.ValidatedConfig validatedSms() {
        return validator.validate("sms", "STAGING", smsValues(), JSON.createObjectNode()
                .put("sms.aliyun.access_key_id", "vault://staging/sms/id")
                .put("sms.aliyun.access_key_secret", "vault://staging/sms/secret"));
    }

    private static ObjectNode smsValues() {
        return JSON.createObjectNode()
                .put("sms.active_provider", "ALIYUN")
                .put("sms.aliyun.region_id", "cn-hangzhou")
                .put("sms.aliyun.endpoint", "dysmsapi.aliyuncs.com")
                .put("sms.aliyun.sign_name", "合伙云");
    }

    private static ProviderConfigLifecycle.Snapshot validatedLifecycle(String provider) {
        return ProviderConfigLifecycle.validated(
                ProviderConfigLifecycle.draft(provider, provider + "-v1", 11L), 0L);
    }

    private static ProviderConnector connector(
            String provider, Function<ProbeRequest, ProbeResult> function) {
        return new ProviderConnector() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public ProbeResult probe(ProbeRequest request) {
                return function.apply(request);
            }
        };
    }

    private static boolean allZero(char[] value) {
        if (value == null) return false;
        for (char character : value) if (character != '\0') return false;
        return true;
    }
}
