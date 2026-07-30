package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

    private AdminTokenService tokens;

    @BeforeEach
    void setUp() {
        tokens = new AdminTokenService(new ObjectMapper(), properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void accessTokenRoundTripsOnlyForTheExpectedTokenType() {
        var principal = new AdminPrincipal(17L, 23L, 4L, "access-jti-17", "root", Set.of("admin.self.read"));

        var issued = tokens.issueAccess(principal, NOW.plusSeconds(300));
        var claims = tokens.parseAccess(issued.value());

        assertEquals("test/admin", claims.iss());
        assertEquals("ACCESS", claims.typ());
        assertEquals("17", claims.sub());
        assertEquals(23L, claims.sid());
        assertEquals(4L, claims.ver());
        assertEquals("access-jti-17", claims.jti());
        assertThrows(AdminTokenService.InvalidAdminTokenException.class,
                () -> tokens.parseMfaTicket(issued.value()));
    }

    @Test
    void rejectsTamperedSignatureWithoutLeakingParsingDetails() {
        var principal = new AdminPrincipal(17L, 23L, 4L, "access-jti-17", "root", Set.of());
        String token = tokens.issueAccess(principal, NOW.plusSeconds(300)).value();
        char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThrows(AdminTokenService.InvalidAdminTokenException.class,
                () -> tokens.parseAccess(tampered));
    }

    @Test
    void rejectsTokenAtItsExpiryBoundary() {
        var principal = new AdminPrincipal(17L, 23L, 4L, "access-jti-17", "root", Set.of());
        String expired = tokens.issueAccess(principal, NOW).value();

        assertThrows(AdminTokenService.InvalidAdminTokenException.class,
                () -> tokens.parseAccess(expired));
    }

    private static AdminSecurityProperties properties() {
        return new AdminSecurityProperties(
                "test-only-admin-jwt-secret-at-least-32-characters",
                "test-only-admin-mfa-root-secret-at-least-32-characters",
                "test-only-idempotency-hmac-secret-at-least-32-chars",
                "test/admin",
                Duration.ofHours(8),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                8,
                72,
                5,
                900);
    }
}
