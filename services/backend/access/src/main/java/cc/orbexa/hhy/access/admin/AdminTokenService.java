package cc.orbexa.hhy.access.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

@Component
public final class AdminTokenService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;
    private final AdminSecurityProperties properties;
    private final Clock clock;
    private final byte[] signingKey;

    public AdminTokenService(
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            AdminSecurityProperties properties,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.signingKey = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
    }

    public IssuedToken issueAccess(AdminPrincipal principal, Instant expiresAt) {
        var claims = new TokenClaims(
                properties.issuer(), "ACCESS", Long.toString(principal.adminId()),
                principal.sessionId(), principal.sessionVersion(), principal.accessJti(), expiresAt.getEpochSecond());
        return new IssuedToken(encode(claims), expiresAt);
    }

    public IssuedToken issueMfaTicket(
            long adminId, long sessionId, long sessionVersion, String accessJti, Instant expiresAt) {
        var claims = new TokenClaims(
                properties.issuer(), "MFA", Long.toString(adminId),
                sessionId, sessionVersion, accessJti, expiresAt.getEpochSecond());
        return new IssuedToken(encode(claims), expiresAt);
    }

    public TokenClaims parseAccess(String token) {
        return parse(token, "ACCESS");
    }

    public TokenClaims parseMfaTicket(String token) {
        return parse(token, "MFA");
    }

    private String encode(TokenClaims claims) {
        try {
            String payload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String unsigned = HEADER + "." + payload;
            return unsigned + "." + ENCODER.encodeToString(hmac(unsigned));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue administrator token", exception);
        }
    }

    private TokenClaims parse(String token, String expectedType) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3 || !HEADER.equals(parts[0])) {
                throw invalidToken();
            }
            byte[] actual = DECODER.decode(parts[2]);
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw invalidToken();
            }
            TokenClaims claims = objectMapper.readValue(DECODER.decode(parts[1]), TokenClaims.class);
            if (!properties.issuer().equals(claims.iss())
                    || !expectedType.equals(claims.typ())
                    || claims.exp() <= Instant.now(clock).getEpochSecond()
                    || claims.sid() < 1
                    || claims.ver() < 0
                    || claims.jti() == null
                    || claims.jti().isBlank()) {
                throw invalidToken();
            }
            Long.parseLong(claims.sub());
            return claims;
        } catch (InvalidAdminTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static InvalidAdminTokenException invalidToken() {
        return new InvalidAdminTokenException();
    }

    public record TokenClaims(String iss, String typ, String sub, long sid, long ver, String jti, long exp) { }
    public record IssuedToken(String value, Instant expiresAt) { }

    public static final class InvalidAdminTokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
