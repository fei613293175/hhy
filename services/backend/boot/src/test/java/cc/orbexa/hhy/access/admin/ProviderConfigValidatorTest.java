package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProviderConfigValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ProviderConfigValidator validator = new ProviderConfigValidator();

    @Test
    void acceptsCatalogFieldsAndKeepsSecretsOutOfValues() throws Exception {
        var result = validator.validate(
                "SMS",
                "staging",
                json.readTree("""
                        {"sms.active_provider":"ALIYUN","sms.aliyun.region_id":"cn-hangzhou"}
                        """),
                json.readTree("""
                        {"sms.aliyun.access_key_id":"vault://hhy/staging/sms/access-key-id",
                         "sms.aliyun.access_key_secret":"kms://hhy/staging/sms/access-key-secret"}
                        """));

        assertEquals("sms", result.provider());
        assertEquals("STAGING", result.environment());
        assertEquals(2, result.values().size());
        assertEquals(2, result.secretRefs().size());
        assertFalse(result.values().containsKey("sms.aliyun.access_key_secret"));
    }

    @Test
    void rejectsRawSecretInPublicValues() throws Exception {
        BusinessException error = assertThrows(BusinessException.class, () -> validator.validate(
                "sms", "PROD",
                json.readTree("{\"sms.aliyun.access_key_secret\":\"raw-secret\"}"),
                json.createObjectNode()));

        assertEquals("COMMON-400-VALIDATION", error.code());
        assertTrue(error.getMessage().contains("secretRefs"));
    }

    @Test
    void rejectsUnknownFieldAndUnknownProvider() throws Exception {
        BusinessException field = assertThrows(BusinessException.class, () -> validator.validate(
                "identity", "TEST",
                json.readTree("{\"identity.provider.raw_appcode\":\"must-not-pass\"}"),
                null));
        BusinessException provider = assertThrows(BusinessException.class, () -> validator.validate(
                "unknown", "TEST", json.createObjectNode(), null));

        assertEquals(400, field.httpStatus());
        assertEquals(400, provider.httpStatus());
    }

    @Test
    void acceptsOnlyVaultOrKmsReferencesAndMasksMetadata() throws Exception {
        BusinessException raw = assertThrows(BusinessException.class, () -> validator.validate(
                "identity", "DEV", json.createObjectNode(),
                json.readTree("{\"identity.provider.appcode\":\"actual-appcode\"}")));
        BusinessException remote = assertThrows(BusinessException.class, () -> validator.validate(
                "identity", "DEV", json.createObjectNode(),
                json.readTree("{\"identity.provider.appcode\":\"https://example.com/secret\"}")));

        assertEquals("COMMON-400-VALIDATION", raw.code());
        assertEquals("COMMON-400-VALIDATION", remote.code());
        String masked = ProviderConfigValidator.maskReference("vault://hhy/prod/identity/appcode");
        assertEquals("vault://***/ap****", masked);
        assertFalse(masked.contains("appcode"));
    }

    @Test
    void supportsFrozenSmsStorageIdentityPaymentAndPayoutCatalogs() {
        assertEquals(5, validator.providers().size());
        assertTrue(validator.secretKeys("sms").contains("sms.aliyun.access_key_secret"));
        assertTrue(validator.secretKeys("storage").contains("storage.r2.secret_access_key"));
        assertTrue(validator.secretKeys("identity").contains("identity.provider.appcode"));
        assertTrue(validator.secretKeys("payment").contains("payment.caihong.merchant_key"));
        assertTrue(validator.secretKeys("payout").contains(
                "payout.alipay.private_key_certificate_id"));
    }

    @Test
    void acceptsPaymentOnlyWhenMerchantCredentialsRemainSecretReferences() throws Exception {
        var result = validator.validate(
                "payment", "STAGING",
                json.readTree("""
                        {"payment.active_gateway":"CAIHONG_EPAY",
                         "payment.caihong.base_url":"https://pay.example.test",
                         "payment.caihong.sign_type":"MD5",
                         "payment.notify_url":"https://api.orbexa.cc/callbacks/payment/caihong"}
                        """),
                json.readTree("""
                        {"payment.caihong.merchant_id":"vault://hhy/staging/payment/merchant-id",
                         "payment.caihong.merchant_key":"kms://hhy/staging/payment/merchant-key"}
                        """));

        assertEquals(4, result.values().size());
        assertEquals(2, result.secretRefs().size());
        assertFalse(result.values().containsKey("payment.caihong.merchant_key"));

        BusinessException raw = assertThrows(BusinessException.class, () -> validator.validate(
                "payment", "STAGING",
                json.readTree("{\"payment.caihong.merchant_key\":\"plaintext\"}"),
                json.createObjectNode()));
        assertTrue(raw.getMessage().contains("secretRefs"));
    }

    @Test
    void payoutCertificateIdentifiersNeverEnterPublicValues() throws Exception {
        var result = validator.validate(
                "payout", "PROD",
                json.readTree("""
                        {"payout.active_gateway":"ALIPAY_ENTERPRISE",
                         "payout.alipay.app_id":"app-id",
                         "payout.alipay.merchant_id":"merchant-id",
                         "payout.alipay.gateway_url":"https://openapi.alipay.com/gateway.do",
                         "payout.notify_url":"https://api.orbexa.cc/callbacks/payout/alipay"}
                        """),
                json.readTree("""
                        {"payout.alipay.private_key_certificate_id":"vault://hhy/prod/payout/private-cert",
                         "payout.alipay.app_public_certificate_id":"vault://hhy/prod/payout/app-public-cert",
                         "payout.alipay.alipay_public_certificate_id":"vault://hhy/prod/payout/alipay-public-cert",
                         "payout.alipay.root_certificate_id":"vault://hhy/prod/payout/root-cert"}
                        """));

        assertEquals(4, result.secretRefs().size());
        assertTrue(result.values().keySet().stream().noneMatch(key -> key.contains("certificate")));

        BusinessException raw = assertThrows(BusinessException.class, () -> validator.validate(
                "payout", "PROD",
                json.readTree("{\"payout.alipay.private_key_certificate_id\":\"cert-raw\"}"),
                json.createObjectNode()));
        assertTrue(raw.getMessage().contains("secretRefs"));
    }
}
