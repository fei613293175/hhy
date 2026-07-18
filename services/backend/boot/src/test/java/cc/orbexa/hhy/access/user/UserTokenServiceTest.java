package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTokenServiceTest {
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
    void rejectsTamperedAccessTokenWithoutParsingDetails() {
        UserTokenService tokens = new UserTokenService(new ObjectMapper(), properties());
        String token = tokens.issueAccess(17L, 23L, 4L, "access-jti-17", Instant.now().plusSeconds(300));
        char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';

        assertThrows(UserTokenService.InvalidAccessTokenException.class,
                () -> tokens.parseAccess(token.substring(0, token.length() - 1) + replacement));
    }

    private static UserAuthProperties properties() {
        return new UserAuthProperties(
                "test-only-user-jwt-secret-at-least-32-characters",
                "test-only-user-token-hmac-secret-at-least-32-characters",
                "test-only-user-snapshot-root-secret-at-least-32-characters",
                "test/user", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24));
    }
}
