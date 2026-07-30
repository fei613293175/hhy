package cc.orbexa.hhy.access.admin;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class AdminTotpService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final long STEP_SECONDS = 30L;

    private final AdminSecurityProperties properties;
    private final Clock clock;
    private final AdminMfaSecretStore secrets;

    public AdminTotpService(
            AdminSecurityProperties properties, Clock clock, AdminMfaSecretStore secrets) {
        this.properties = properties;
        this.clock = clock;
        this.secrets = secrets;
    }

    public EnrollmentMaterial createEnrollment(
            long adminId, String username, String enrollmentId, Instant startedAt) {
        AdminMfaSecretStore.StoredSecret stored = secrets.create(adminId, enrollmentId);
        return enrollment(username, enrollmentId, stored.reference(), stored.secret(), startedAt);
    }

    public EnrollmentMaterial enrollmentFromReference(
            long adminId, String username, String secretRef, Instant startedAt) {
        AdminMfaSecretStore.StoredSecret stored = secrets.resolve(adminId, secretRef);
        return enrollment(username, secrets.enrollmentId(secretRef), secretRef, stored.secret(), startedAt);
    }

    private EnrollmentMaterial enrollment(
            String username, String enrollmentId, String secretRef, String secret, Instant startedAt) {
        String issuer = properties.issuer();
        String label = encode(issuer + ":" + username);
        String qr = "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
        String masked = secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
        return new EnrollmentMaterial(
                enrollmentId, secretRef, qr, masked,
                startedAt.plus(properties.enrollmentTtl()));
    }

    public boolean verify(long adminId, String secretRef, String code) {
        return matchingStep(adminId, secretRef, code).isPresent();
    }

    OptionalLong matchingStep(long adminId, String secretRef, String code) {
        if (code == null || !code.matches("^[0-9]{6}$")) {
            return OptionalLong.empty();
        }
        String secret;
        try {
            secret = secrets.resolve(adminId, secretRef).secret();
        } catch (RuntimeException exception) {
            return OptionalLong.empty();
        }
        long counter = Instant.now(clock).getEpochSecond() / STEP_SECONDS;
        for (long offset = 1; offset >= -1; offset--) {
            if (totp(secret, counter + offset).equals(code)) {
                return OptionalLong.of(counter + offset);
            }
        }
        return OptionalLong.empty();
    }

    String codeAt(long adminId, String secretRef, Instant instant) {
        return totp(secrets.resolve(adminId, secretRef).secret(), instant.getEpochSecond() / STEP_SECONDS);
    }

    String enrollmentId(String secretRef) {
        return secrets.enrollmentId(secretRef);
    }

    void revoke(long adminId, String secretRef) {
        secrets.revoke(adminId, secretRef);
    }

    private static String totp(String base32Secret, long counter) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] value = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(value);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify administrator MFA code", exception);
        }
    }

    static String base32(byte[] input) {
        StringBuilder output = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                output.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            output.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
        }
        return output.toString();
    }

    private static byte[] decodeBase32(String value) {
        int buffer = 0;
        int bitsLeft = 0;
        byte[] output = new byte[value.length() * 5 / 8];
        int index = 0;
        for (char c : value.toCharArray()) {
            int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base32 secret");
            }
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record EnrollmentMaterial(
            String enrollmentId,
            String secretRef,
            String qrCodeUrl,
            String manualKeyMasked,
            Instant expiresAt) { }
}
