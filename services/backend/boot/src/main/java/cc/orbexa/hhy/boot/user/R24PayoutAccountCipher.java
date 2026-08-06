package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserAuthProperties;
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

@Component
public final class R24PayoutAccountCipher {
    private static final String VERSION = "hhy-r24-payout-v1";
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec digestKey;
    private final SecureRandom random = new SecureRandom();

    public R24PayoutAccountCipher(UserAuthProperties properties) {
        this.encryptionKey = new SecretKeySpec(
                derive("encryption", properties.snapshotRootSecret()), "AES");
        this.digestKey = new SecretKeySpec(
                derive("digest", properties.tokenHmacSecret()), "HmacSHA256");
    }

    public String encrypt(long userId, String field, String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(userId, field));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return String.join(".", VERSION, encoder.encodeToString(nonce),
                    encoder.encodeToString(cipher.doFinal(
                            plaintext.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception failure) {
            throw new IllegalStateException("R24 payout account encryption unavailable", failure);
        }
    }

    public String digest(String account) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(digestKey);
            return HexFormat.of().formatHex(
                    mac.doFinal(account.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R24 payout account digest unavailable", failure);
        }
    }

    public String decrypt(long userId, String field, String envelope) {
        try {
            String[] parts = envelope == null ? new String[0] : envelope.split("\\.", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) throw invalidEnvelope();
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < 16) throw invalidEnvelope();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(userId, field));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalidEnvelope();
        }
    }

    public static String mask(String account) {
        String value = account == null ? "" : account.strip();
        int visible = Math.min(4, value.length());
        return "****" + value.substring(value.length() - visible);
    }

    private static byte[] derive(String purpose, String rootSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((VERSION + "/" + purpose).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            return digest.digest(rootSecret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("R24 payout key derivation unavailable", failure);
        }
    }

    private static byte[] aad(long userId, String field) {
        return (VERSION + "|" + userId + "|" + field).getBytes(StandardCharsets.UTF_8);
    }

    private static IllegalArgumentException invalidEnvelope() {
        return new IllegalArgumentException("R24 payout account envelope is invalid");
    }
}
