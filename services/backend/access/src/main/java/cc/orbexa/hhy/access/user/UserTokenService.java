package cc.orbexa.hhy.access.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class UserTokenService {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = BASE64.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.US_ASCII));
    private static final String REFRESH_PREFIX = "hhy_rt1_";

    private final ObjectMapper objectMapper;
    private final UserAuthProperties properties;
    private final SecureRandom random;

    @Autowired
    public UserTokenService(ObjectMapper objectMapper, UserAuthProperties properties) {
        this(objectMapper, properties, new SecureRandom());
    }

    UserTokenService(ObjectMapper objectMapper, UserAuthProperties properties, SecureRandom random) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.random = random;
    }

    public String issueAccess(
            long userId, long sessionId, long sessionVersion, String accessJti, Instant expiresAt) {
        try {
            var claims = new AccessClaims(
                    properties.issuer(), "ACCESS", Long.toString(userId), sessionId,
                    sessionVersion, accessJti, expiresAt.getEpochSecond());
            String payload = BASE64.encodeToString(objectMapper.writeValueAsBytes(claims));
            String unsigned = HEADER + "." + payload;
            return unsigned + "." + BASE64.encodeToString(hmac(
                    properties.jwtSecret(), unsigned.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue user access token", exception);
        }
    }

    public String newRefreshToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return REFRESH_PREFIX + BASE64.encodeToString(value);
    }

    public String newNumericCode(int length) {
        if (length < 4 || length > 8) throw new IllegalArgumentException("Verification code length out of range");
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append(random.nextInt(10));
        return value.toString();
    }

    public String refreshHash(String refreshToken) {
        return HexFormat.of().formatHex(hmac(
                properties.tokenHmacSecret(), refreshToken.getBytes(StandardCharsets.UTF_8)));
    }

    public String intentHash(String operation, String... values) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(buffer)) {
                write(data, operation);
                for (String value : values) write(data, value);
            }
            return HexFormat.of().formatHex(hmac(properties.tokenHmacSecret(), buffer.toByteArray()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash user authentication intent", exception);
        }
    }

    public boolean sameSecret(String left, String right) {
        byte[] leftBytes = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private static void write(DataOutputStream data, String value) throws Exception {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(encoded.length);
        data.write(encoded);
    }

    private static byte[] hmac(String secret, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate user token digest", exception);
        }
    }

    private record AccessClaims(
            String iss, String typ, String sub, long sid, long ver, String jti, long exp) { }
}
