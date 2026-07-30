package cc.orbexa.hhy.content;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Domain-separated AES-GCM for contact values and immutable idempotency snapshots. */
@Component
public final class ContentContactCipher {
    private static final byte[] CONTACT_DOMAIN =
            "cc.orbexa.hhy/content-contact/key/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SNAPSHOT_DOMAIN =
            "cc.orbexa.hhy/content-contact/snapshot/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONTACT_AAD =
            "cc.orbexa.hhy/content-contact/aad/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SNAPSHOT_AAD =
            "cc.orbexa.hhy/content-contact/snapshot-aad/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec contactKey;
    private final SecretKeySpec snapshotKey;
    private final SecureRandom random;

    @Autowired
    public ContentContactCipher(
            @Value("${hhy.content-security.contact-root-secret}") String rootSecret) {
        this(rootSecret, new SecureRandom());
    }

    ContentContactCipher(String rootSecret, SecureRandom random) {
        if (rootSecret == null || rootSecret.length() < 32) {
            throw new IllegalArgumentException("Content contact root key must contain at least 32 characters");
        }
        this.contactKey = new SecretKeySpec(derive(CONTACT_DOMAIN, rootSecret), "AES");
        this.snapshotKey = new SecretKeySpec(derive(SNAPSHOT_DOMAIN, rootSecret), "AES");
        this.random = random;
    }

    public String encrypt(long contentId, String channel, String plain) {
        return encrypt("hhy-contact-v1", contactKey, aadContact(contentId, channel),
                plain.getBytes(StandardCharsets.UTF_8));
    }

    public String decrypt(long contentId, String channel, String envelope) {
        return new String(decrypt("hhy-contact-v1", contactKey, aadContact(contentId, channel), envelope),
                StandardCharsets.UTF_8);
    }

    String encryptSnapshot(String scope, String key, String requestHash, String type, byte[] plain) {
        return encrypt("hhy-contact-snapshot-v1", snapshotKey,
                aadSnapshot(scope, key, requestHash, type), plain);
    }

    byte[] decryptSnapshot(String scope, String key, String requestHash, String type, String envelope) {
        return decrypt("hhy-contact-snapshot-v1", snapshotKey,
                aadSnapshot(scope, key, requestHash, type), envelope);
    }

    private String encrypt(String version, SecretKeySpec key, byte[] aad, byte[] plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return version + "." + encoder.encodeToString(nonce) + "."
                    + encoder.encodeToString(cipher.doFinal(plain));
        } catch (Exception failure) {
            throw new IllegalStateException("Content contact encryption unavailable", failure);
        }
    }

    private static byte[] decrypt(
            String version, SecretKeySpec key, byte[] aad, String envelope) {
        try {
            String[] parts = envelope == null ? new String[0] : envelope.split("\\.", -1);
            if (parts.length != 3 || !version.equals(parts[0])) throw new ContactIntegrityException();
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getUrlDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES || cipherText.length < 16) throw new ContactIntegrityException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(cipherText);
        } catch (ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ContactIntegrityException();
        }
    }

    private static byte[] aadContact(long contentId, String channel) {
        byte[] channelBytes = channel.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(CONTACT_AAD.length + Long.BYTES + channelBytes.length)
                .put(CONTACT_AAD).putLong(contentId).put(channelBytes).array();
    }

    private static byte[] aadSnapshot(String scope, String key, String hash, String type) {
        return (new String(SNAPSHOT_AAD, StandardCharsets.US_ASCII) + "\u0000" + scope + "\u0000" + key
                + "\u0000" + hash + "\u0000" + type).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] derive(byte[] domain, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            digest.update((byte) 0);
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception failure) {
            throw new IllegalStateException("Content contact key derivation unavailable", failure);
        }
    }

    public static final class ContactIntegrityException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        ContactIntegrityException() { super("Contact value is unavailable"); }
    }
}
