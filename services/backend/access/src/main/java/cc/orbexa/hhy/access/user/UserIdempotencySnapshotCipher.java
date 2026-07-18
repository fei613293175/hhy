package cc.orbexa.hhy.access.user;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class UserIdempotencySnapshotCipher {
    private static final String ENVELOPE_VERSION = "hhy-user-idem-v1";
    private static final String ALGORITHM = "A256GCM";
    private static final String KEY_VERSION = "v1";
    private static final byte[] KEY_DOMAIN =
            "cc.orbexa.hhy/user-idempotency-snapshot/key/v1".getBytes(StandardCharsets.US_ASCII);
    private static final String AAD_DOMAIN = "cc.orbexa.hhy/user-idempotency-snapshot/aad/v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random;
    private final SecretKeySpec key;

    @Autowired
    public UserIdempotencySnapshotCipher(UserAuthProperties properties) {
        this(properties.snapshotRootSecret(), new SecureRandom());
    }

    UserIdempotencySnapshotCipher(String rootSecret, SecureRandom random) {
        this.random = random;
        this.key = deriveKey(rootSecret);
    }

    public String encrypt(
            String scope, String idempotencyKey, String requestHash,
            String responseType, byte[] plaintext) {
        requireBinding(scope, idempotencyKey, requestHash, responseType);
        if (plaintext == null) throw integrityFailure();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(scope, idempotencyKey, requestHash, responseType));
            byte[] ciphertext = cipher.doFinal(plaintext);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return String.join(".", ENVELOPE_VERSION, ALGORITHM, KEY_VERSION,
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
                    || !ALGORITHM.equals(parts[1])
                    || !KEY_VERSION.equals(parts[2])) {
                throw integrityFailure();
            }
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
            throw new IllegalArgumentException("User snapshot root key must contain at least 32 characters");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_DOMAIN);
            digest.update((byte) 0);
            digest.update(rootSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest.digest(), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize user snapshot key", exception);
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

    private static SnapshotIntegrityException integrityFailure() {
        return new SnapshotIntegrityException();
    }

    public static final class SnapshotIntegrityException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private SnapshotIntegrityException() {
            super("User idempotency snapshot is unavailable");
        }
    }
}
