package cc.orbexa.hhy.access.admin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Versioned authenticated encryption for immutable administrator idempotency
 * responses. The envelope and its inputs must never be written to logs.
 */
@Component
public final class AdminIdempotencySnapshotCipher {
    private static final String ENVELOPE_VERSION = "hhy-idem-v1";
    private static final String ALGORITHM = "A256GCM";
    private static final byte[] KEY_DOMAIN =
            "cc.orbexa.hhy/admin-idempotency-snapshot/key/v1".getBytes(StandardCharsets.US_ASCII);
    private static final String AAD_DOMAIN = "cc.orbexa.hhy/admin-idempotency-snapshot/aad/v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random;
    private final String currentVersion;
    private final Map<String, SecretKeySpec> keys;

    @Autowired
    public AdminIdempotencySnapshotCipher(
            AdminSecurityProperties properties,
            @Value("${hhy.admin-security.mfa-root-secret-version:v1}") String currentVersion,
            @Value("${hhy.admin-security.mfa-previous-root-secrets:}") String previousSecrets) {
        this(currentVersion, properties.mfaRootSecret(), previousSecrets, new SecureRandom());
    }

    AdminIdempotencySnapshotCipher(
            String currentVersion, String currentSecret, String previousSecrets) {
        this(currentVersion, currentSecret, previousSecrets, new SecureRandom());
    }

    AdminIdempotencySnapshotCipher(
            String currentVersion, String currentSecret, String previousSecrets, SecureRandom random) {
        this.random = random;
        this.currentVersion = validateVersion(currentVersion);
        Map<String, SecretKeySpec> configured = new LinkedHashMap<>();
        configured.put(this.currentVersion, deriveKey(currentSecret));
        if (previousSecrets != null && !previousSecrets.isBlank()) {
            for (String entry : previousSecrets.split(";")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2 || pair[1].length() < 32) {
                    throw new IllegalArgumentException("Invalid previous administrator root key entry");
                }
                String version = validateVersion(pair[0]);
                if (configured.putIfAbsent(version, deriveKey(pair[1])) != null) {
                    throw new IllegalArgumentException("Duplicate administrator root key version");
                }
            }
        }
        this.keys = Map.copyOf(configured);
    }

    public String encrypt(
            String scope, String idempotencyKey, String requestHash, String responseType, byte[] plaintext) {
        requireBinding(scope, idempotencyKey, requestHash, responseType);
        if (plaintext == null) throw integrityFailure();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(scope, idempotencyKey, requestHash, responseType));
            byte[] ciphertext = cipher.doFinal(plaintext);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return String.join(".", ENVELOPE_VERSION, ALGORITHM, currentVersion,
                    encoder.encodeToString(nonce), encoder.encodeToString(ciphertext));
        } catch (SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw integrityFailure();
        }
    }

    public byte[] decrypt(
            String scope, String idempotencyKey, String requestHash,
            String responseType, String envelope) {
        requireBinding(scope, idempotencyKey, requestHash, responseType);
        try {
            String[] parts = envelope == null ? new String[0] : envelope.split("\\.", -1);
            if (parts.length != 5
                    || !ENVELOPE_VERSION.equals(parts[0])
                    || !ALGORITHM.equals(parts[1])) {
                throw integrityFailure();
            }
            String keyVersion = validateVersion(parts[2]);
            SecretKeySpec key = keys.get(keyVersion);
            if (key == null) throw integrityFailure();
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[3]);
            byte[] ciphertext = decoder.decode(parts[4]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < TAG_BITS / 8) {
                throw integrityFailure();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(scope, idempotencyKey, requestHash, responseType));
            return cipher.doFinal(ciphertext);
        } catch (SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw integrityFailure();
        }
    }

    private static SecretKeySpec deriveKey(String rootSecret) {
        if (rootSecret == null || rootSecret.length() < 32) {
            throw new IllegalArgumentException("Administrator root key must contain at least 32 characters");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_DOMAIN);
            digest.update((byte) 0);
            digest.update(rootSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest.digest(), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize administrator snapshot key", exception);
        }
    }

    private static byte[] aad(
            String scope, String idempotencyKey, String requestHash, String responseType) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(buffer)) {
            for (String value : new String[] {
                    AAD_DOMAIN, scope, idempotencyKey, requestHash, responseType}) {
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                data.writeInt(encoded.length);
                data.write(encoded);
            }
        }
        return buffer.toByteArray();
    }

    private static void requireBinding(
            String scope, String idempotencyKey, String requestHash, String responseType) {
        if (scope == null || scope.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()
                || requestHash == null || requestHash.isBlank()
                || responseType == null || responseType.isBlank()) {
            throw integrityFailure();
        }
    }

    private static String validateVersion(String value) {
        if (value == null || !value.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw new IllegalArgumentException("Administrator root key version is invalid");
        }
        return value;
    }

    private static SnapshotIntegrityException integrityFailure() {
        return new SnapshotIntegrityException();
    }

    public static final class SnapshotIntegrityException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private SnapshotIntegrityException() {
            super("Administrator idempotency snapshot is unavailable");
        }
    }
}
