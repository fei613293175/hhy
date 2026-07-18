package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class UserTokenServiceTest {
    private static final String BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    @Test
    void accessTokenRoundTripsWithSessionBindingClaims() {
        UserTokenService tokens = new UserTokenService(new ObjectMapper(), properties());
        String token = tokens.issueAccess(17L, 23L, 4L, "access-jti-17", Instant.now().plusSeconds(300));

        UserTokenService.AccessClaims claims = tokens.parseAccess(token);

        assertEquals("test/user", claims.iss());
        assertEquals("ACCESS", claims.typ());
        assertEquals("17", claims.sub());
        assertEquals(23L, claims.sid());
        assertEquals(4L, claims.ver());
        assertEquals("access-jti-17", claims.jti());
    }

    @Test
    void rejectsNonCanonicalSignatureEncodingEvenWhenBytesAreUnchanged() {
        UserTokenService tokens = new UserTokenService(new ObjectMapper(), properties());
        String token = tokens.issueAccess(17L, 23L, 4L, "access-jti-17", Instant.now().plusSeconds(300));
        String[] parts = token.split("\\.");
        String signature = parts[2];
        int canonicalIndex = BASE64_URL_ALPHABET.indexOf(signature.charAt(signature.length() - 1));
        assertEquals(0, canonicalIndex & 3);
        String nonCanonicalSignature = signature.substring(0, signature.length() - 1)
                + BASE64_URL_ALPHABET.charAt(canonicalIndex | 1);
        assertArrayEquals(
                Base64.getUrlDecoder().decode(signature),
                Base64.getUrlDecoder().decode(nonCanonicalSignature));

        assertThrows(UserTokenService.InvalidAccessTokenException.class,
                () -> tokens.parseAccess(parts[0] + "." + parts[1] + "." + nonCanonicalSignature));
    }

    private static UserAuthProperties properties() {
        return new UserAuthProperties(
                "test-only-user-jwt-secret-at-least-32-characters",
                "test-only-user-token-hmac-secret-at-least-32-characters",
                "test-only-user-snapshot-root-secret-at-least-32-characters",
                "test/user", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24));
    }
}
