package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.admin.ProviderConnectorAdapters.ProbeCommand;
import cc.orbexa.hhy.access.admin.ProviderConnectorAdapters.ProbeOperation;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeResult;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProviderConnectorAdaptersTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T14:20:00Z"), ZoneOffset.UTC);
    private final ProviderConfigValidator validator = new ProviderConfigValidator();

    @Test
    void smsAdapterCreatesFixedAccountProbeAndNormalizesHostOnlyEndpoint() {
        AtomicReference<ProbeCommand> observed = new AtomicReference<>();
        var connector = ProviderConnectorAdapters.sms((command, secrets) -> {
            observed.set(command);
            assertTrue(secrets.secret("sms.aliyun.access_key_id").length > 0);
            return ProbeResult.success();
        });
        var coordinator = coordinator(connector);

        var outcome = coordinator.test(validated("sms", smsValues("dysmsapi.aliyuncs.com"),
                JSON.createObjectNode()
                        .put("sms.aliyun.access_key_id", "vault://staging/sms/id")
                        .put("sms.aliyun.access_key_secret", "vault://staging/sms/secret")),
                config("sms", smsValues("dysmsapi.aliyuncs.com"), JSON.createObjectNode()
                        .put("sms.aliyun.access_key_id", "vault://staging/sms/id")
                        .put("sms.aliyun.access_key_secret", "vault://staging/sms/secret")),
                "13800000000", 1L);

        assertTrue(outcome.successful());
        assertEquals(ProbeOperation.ALIYUN_SMS_ACCOUNT, observed.get().operation());
        assertEquals("https://dysmsapi.aliyuncs.com", observed.get().endpoint().toString());
        assertEquals("cn-hangzhou", observed.get().parameters().get("region"));
        assertEquals("13800000000", observed.get().testRecipient());
    }

    @Test
    void storageAdapterSelectsR2BucketProbeFromFrozenProviderValue() {
        AtomicReference<ProbeCommand> observed = new AtomicReference<>();
        var connector = ProviderConnectorAdapters.storage((command, secrets) -> {
            observed.set(command);
            return ProbeResult.success();
        });
        ObjectNode values = JSON.createObjectNode()
                .put("storage.default_provider", "CLOUDFLARE_R2")
                .put("storage.r2.account_id", "account-1")
                .put("storage.r2.endpoint", "https://account-1.r2.cloudflarestorage.com")
                .put("storage.r2.bucket.public_media", "hhy-public-media");
        ObjectNode refs = JSON.createObjectNode()
                .put("storage.r2.access_key_id", "vault://staging/r2/id")
                .put("storage.r2.secret_access_key", "vault://staging/r2/secret");

        var outcome = coordinator(connector).test(
                validated("storage", values, refs), config("storage", values, refs), null, 1L);

        assertTrue(outcome.successful());
        assertEquals(ProbeOperation.CLOUDFLARE_R2_BUCKET_ACCESS, observed.get().operation());
        assertEquals("hhy-public-media", observed.get().parameters().get("bucket"));
    }

    @Test
    void identityAdapterUsesOnlyFrozenHttpsProbeEndpoints() {
        AtomicReference<ProbeCommand> observed = new AtomicReference<>();
        var connector = ProviderConnectorAdapters.identity((command, secrets) -> {
            observed.set(command);
            return ProbeResult.success();
        });
        ObjectNode values = JSON.createObjectNode()
                .put("identity.active_provider", "ALIYUN_MARKET_FACE")
                .put("identity.liveness.token_url", "https://identity.example.test/token")
                .put("identity.liveness.result_url", "https://identity.example.test/result")
                .put("identity.face_compare.url", "https://identity.example.test/compare");
        ObjectNode refs = JSON.createObjectNode()
                .put("identity.provider.appcode", "kms://staging/identity/appcode");

        var outcome = coordinator(connector).test(
                validated("identity", values, refs), config("identity", values, refs), null, 1L);

        assertTrue(outcome.successful());
        assertEquals(ProbeOperation.ALIYUN_MARKET_IDENTITY_AUTH, observed.get().operation());
        assertEquals("https://identity.example.test/token", observed.get().endpoint().toString());
        assertEquals("https://identity.example.test/compare",
                observed.get().parameters().get("faceCompareEndpoint"));
    }

    @Test
    void adapterRejectsHttpCredentialAndPrivateNetworkEndpoints() {
        var connector = ProviderConnectorAdapters.sms((command, secrets) -> ProbeResult.success());
        for (String endpoint : List.of(
                "http://dysmsapi.aliyuncs.com",
                "https://user:password@dysmsapi.aliyuncs.com",
                "https://127.0.0.1")) {
            ObjectNode values = smsValues(endpoint);
            ObjectNode refs = JSON.createObjectNode()
                    .put("sms.aliyun.access_key_id", "vault://staging/sms/id")
                    .put("sms.aliyun.access_key_secret", "vault://staging/sms/secret");
            BusinessException error = assertThrows(BusinessException.class,
                    () -> coordinator(connector).test(
                            validated("sms", values, refs), config("sms", values, refs), null, 1L));
            assertEquals("COMMON-400-VALIDATION", error.code());
        }
    }

    @Test
    void paymentAdapterCreatesOnlyReadOnlySignedAccountQuery() {
        AtomicReference<ProbeCommand> observed = new AtomicReference<>();
        var connector = ProviderConnectorAdapters.payment((command, secrets) -> {
            observed.set(command);
            assertTrue(secrets.secret("payment.caihong.merchant_key").length > 0);
            return ProbeResult.success();
        });
        ObjectNode values = JSON.createObjectNode()
                .put("payment.active_gateway", "CAIHONG_EPAY")
                .put("payment.caihong.base_url", "https://pay.example.test/api")
                .put("payment.caihong.sign_type", "MD5");
        ObjectNode refs = JSON.createObjectNode()
                .put("payment.caihong.merchant_id", "vault://staging/payment/merchant-id")
                .put("payment.caihong.merchant_key", "kms://staging/payment/merchant-key");

        var outcome = coordinator(connector).test(
                validated("payment", values, refs), config("payment", values, refs), null, 1L);

        assertTrue(outcome.successful());
        assertEquals(ProbeOperation.CAIHONG_SIGNED_ACCOUNT_QUERY, observed.get().operation());
        assertEquals("https://pay.example.test/api", observed.get().endpoint().toString());
        assertEquals("MD5", observed.get().parameters().get("signType"));
    }

    @Test
    void payoutAdapterUsesAllCertificateReferencesForReadOnlyAuthentication() {
        AtomicReference<ProbeCommand> observed = new AtomicReference<>();
        var connector = ProviderConnectorAdapters.payout((command, secrets) -> {
            observed.set(command);
            assertTrue(secrets.secret("payout.alipay.private_key_certificate_id").length > 0);
            assertTrue(secrets.secret("payout.alipay.root_certificate_id").length > 0);
            return ProbeResult.success();
        });
        ObjectNode values = JSON.createObjectNode()
                .put("payout.active_gateway", "ALIPAY_ENTERPRISE")
                .put("payout.alipay.app_id", "app-id")
                .put("payout.alipay.merchant_id", "merchant-id")
                .put("payout.alipay.gateway_url", "https://openapi.alipay.com/gateway.do");
        ObjectNode refs = payoutCertificateRefs();

        var outcome = coordinator(connector).test(
                validated("payout", values, refs), config("payout", values, refs), null, 1L);

        assertTrue(outcome.successful());
        assertEquals(ProbeOperation.ALIPAY_CERTIFICATE_AUTH_QUERY, observed.get().operation());
        assertEquals("https://openapi.alipay.com/gateway.do", observed.get().endpoint().toString());
        assertEquals("app-id", observed.get().parameters().get("appId"));
    }

    @Test
    void financialAdaptersRejectUnsafeEndpointsBeforeTransport() {
        ObjectNode paymentValues = JSON.createObjectNode()
                .put("payment.active_gateway", "CAIHONG_EPAY")
                .put("payment.caihong.base_url", "https://127.0.0.1/query")
                .put("payment.caihong.sign_type", "MD5");
        ObjectNode paymentRefs = JSON.createObjectNode()
                .put("payment.caihong.merchant_id", "vault://staging/payment/id")
                .put("payment.caihong.merchant_key", "vault://staging/payment/key");
        BusinessException paymentError = assertThrows(BusinessException.class,
                () -> coordinator(ProviderConnectorAdapters.payment((command, secrets) -> {
                    throw new AssertionError("unsafe endpoint must not reach transport");
                })).test(validated("payment", paymentValues, paymentRefs),
                        config("payment", paymentValues, paymentRefs), null, 1L));
        assertEquals("COMMON-400-VALIDATION", paymentError.code());

        ObjectNode payoutValues = JSON.createObjectNode()
                .put("payout.active_gateway", "ALIPAY_ENTERPRISE")
                .put("payout.alipay.app_id", "app-id")
                .put("payout.alipay.merchant_id", "merchant-id")
                .put("payout.alipay.gateway_url", "https://user:pass@openapi.alipay.com/gateway.do");
        BusinessException payoutError = assertThrows(BusinessException.class,
                () -> coordinator(ProviderConnectorAdapters.payout((command, secrets) -> {
                    throw new AssertionError("unsafe endpoint must not reach transport");
                })).test(validated("payout", payoutValues, payoutCertificateRefs()),
                        config("payout", payoutValues, payoutCertificateRefs()), null, 1L));
        assertEquals("COMMON-400-VALIDATION", payoutError.code());
    }

    private ProviderConnectionTestCoordinator coordinator(
            ProviderConnectionTestCoordinator.ProviderConnector connector) {
        return new ProviderConnectionTestCoordinator(List.of(connector),
                reference -> "resolved-value".toCharArray(), CLOCK);
    }

    private ProviderConfigLifecycle.Snapshot validated(
            String provider, ObjectNode values, ObjectNode refs) {
        config(provider, values, refs);
        return ProviderConfigLifecycle.validated(
                ProviderConfigLifecycle.draft(provider, provider + "-v1", 11L), 0L);
    }

    private ProviderConfigValidator.ValidatedConfig config(
            String provider, ObjectNode values, ObjectNode refs) {
        return validator.validate(provider, "STAGING", values, refs);
    }

    private static ObjectNode smsValues(String endpoint) {
        return JSON.createObjectNode()
                .put("sms.active_provider", "ALIYUN")
                .put("sms.aliyun.region_id", "cn-hangzhou")
                .put("sms.aliyun.endpoint", endpoint)
                .put("sms.aliyun.sign_name", "合伙云");
    }

    private static ObjectNode payoutCertificateRefs() {
        return JSON.createObjectNode()
                .put("payout.alipay.private_key_certificate_id", "vault://staging/payout/private")
                .put("payout.alipay.app_public_certificate_id", "vault://staging/payout/app-public")
                .put("payout.alipay.alipay_public_certificate_id", "vault://staging/payout/alipay-public")
                .put("payout.alipay.root_certificate_id", "vault://staging/payout/root");
    }
}
