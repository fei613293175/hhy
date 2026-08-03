package cc.orbexa.hhy.boot.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderCallbackContext;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class R17PaymentSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String NONCE = "nonce_0123456789abcdef";
    private static final String BODY = "{\"orderNo\":\"ORD-17\",\"amountCent\":9900}";

    @Test
    void acceptsExactSignedBytes() {
        R17PaymentSignatureVerifier verifier = verifier();
        String timestamp = NOW.toString();
        String signature = R17PaymentSignatureVerifier.signForTest(
                SECRET, timestamp, NONCE, "CAIHONG", BODY);

        ProviderCallbackContext result = verifier.verify(
                "caihong", timestamp, NONCE, signature,
                "idem-0123456789abcdef", BODY);

        assertEquals("CAIHONG", result.gateway());
        assertEquals(BODY, result.rawBody());
    }

    @Test
    void rejectsTamperedBodyExpiredTimestampAndUnknownGateway() {
        String timestamp = NOW.toString();
        String signature = R17PaymentSignatureVerifier.signForTest(
                SECRET, timestamp, NONCE, "CAIHONG", BODY);
        R17PaymentSignatureVerifier verifier = verifier();

        assertThrows(BusinessException.class, () -> verifier.verify(
                "CAIHONG", timestamp, NONCE, signature,
                "idem-0123456789abcdef", BODY + " "));
        assertThrows(BusinessException.class, () -> verifier.verify(
                "CAIHONG", NOW.minusSeconds(301).toString(), NONCE, signature,
                "idem-0123456789abcdef", BODY));
        assertThrows(BusinessException.class, () -> verifier.verify(
                "UNKNOWN", timestamp, NONCE, signature,
                "idem-0123456789abcdef", BODY));
    }

    private static R17PaymentSignatureVerifier verifier() {
        String configured = "CAIHONG=" + Base64.getUrlEncoder().withoutPadding().encodeToString(SECRET);
        return new R17PaymentSignatureVerifier(configured, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
