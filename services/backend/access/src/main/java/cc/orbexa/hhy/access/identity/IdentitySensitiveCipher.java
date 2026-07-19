package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityService.ProtectedIdentity;
import cc.orbexa.hhy.access.user.UserAuthProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Separate-domain AEAD encryption and keyed lookup hash for identity payloads. */
@Component
public final class IdentitySensitiveCipher implements IdentityService.SensitiveData {
    private static final byte[] KEY_DOMAIN =
            "cc.orbexa.hhy/identity-sensitive/key/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HASH_DOMAIN =
            "cc.orbexa.hhy/identity-sensitive/hash/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD_DOMAIN =
            "cc.orbexa.hhy/identity-sensitive/aad/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hashKey;
    private final SecureRandom random;

    public IdentitySensitiveCipher(UserAuthProperties properties) {
        this(properties.snapshotRootSecret(), new SecureRandom());
    }

    IdentitySensitiveCipher(String rootSecret, SecureRandom random) {
        if (rootSecret == null || rootSecret.length() < 32) {
            throw new IllegalArgumentException("Identity root key must contain at least 32 characters");
        }
        this.encryptionKey = new SecretKeySpec(derive(KEY_DOMAIN, rootSecret), "AES");
        this.hashKey = new SecretKeySpec(derive(HASH_DOMAIN, rootSecret), "HmacSHA256");
        this.random = random;
    }

    @Override
    public ProtectedIdentity protect(long userId, String realName, String idNumber) {
        return new ProtectedIdentity(
                encrypt(userId, "name", realName),
                encrypt(userId, "id-number", idNumber),
                keyedHash(idNumber));
    }

    String decrypt(long userId, String field, String envelope) {
        try {
            String[] parts = envelope == null ? new String[0] : envelope.split("\\.", -1);
            if (parts.length != 3 || !"hhy-id-v1".equals(parts[0])) throw integrityFailure();
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getUrlDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES || cipherText.length < 16) throw integrityFailure();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(userId, field));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IdentityPayloadIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw integrityFailure();
        }
    }

    private String encrypt(long userId, String field, String plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(userId, field));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return "hhy-id-v1." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(encrypted);
        } catch (Exception failure) {
            throw new IllegalStateException("Identity encryption unavailable", failure);
        }
    }

    private String keyedHash(String idNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(idNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Identity lookup hash unavailable", failure);
        }
    }

    private static byte[] aad(long userId, String field) {
        byte[] fieldBytes = field.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(AAD_DOMAIN.length + Long.BYTES + fieldBytes.length)
                .put(AAD_DOMAIN).putLong(userId).put(fieldBytes).array();
    }

    private static byte[] derive(byte[] domain, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            digest.update((byte) 0);
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception failure) {
            throw new IllegalStateException("Identity key derivation unavailable", failure);
        }
    }

    private static IdentityPayloadIntegrityException integrityFailure() {
        return new IdentityPayloadIntegrityException();
    }

    public static final class IdentityPayloadIntegrityException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private IdentityPayloadIntegrityException() {
            super("Identity payload is unavailable");
        }
    }
}
