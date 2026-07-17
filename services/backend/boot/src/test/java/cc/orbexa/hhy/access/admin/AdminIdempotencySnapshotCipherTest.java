package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AdminIdempotencySnapshotCipherTest {
    private static final String ROOT_V1 = "snapshot-root-v1-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ROOT_V2 = "snapshot-root-v2-9876543210-ZYXWVUTSRQPONMLKJIHGFEDCBA";
    private static final String SCOPE = "admin.password:17";
    private static final String KEY = "idem-key-00000001";
    private static final String HASH = "a".repeat(64);
    private static final String TYPE = "r01.admin-self-security.v1";
    private static final byte[] PLAIN = "{\"adminId\":\"17\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void randomNinetySixBitNonceProducesIndependentAes256GcmEnvelopes() {
        var cipher = new AdminIdempotencySnapshotCipher("v1", ROOT_V1, "");
        String first = cipher.encrypt(SCOPE, KEY, HASH, TYPE, PLAIN);
        String second = cipher.encrypt(SCOPE, KEY, HASH, TYPE, PLAIN);

        assertNotEquals(first, second);
        String[] parts = first.split("\\.");
        assertEquals("hhy-idem-v1", parts[0]);
        assertEquals("A256GCM", parts[1]);
        assertEquals("v1", parts[2]);
        assertEquals(12, Base64.getUrlDecoder().decode(parts[3]).length);
        assertArrayEquals(PLAIN, cipher.decrypt(SCOPE, KEY, HASH, TYPE, first));
    }

    @Test
    void everyCanonicalAadFieldAndCiphertextAreAuthenticated() {
        var cipher = new AdminIdempotencySnapshotCipher("v1", ROOT_V1, "");
        String envelope = cipher.encrypt(SCOPE, KEY, HASH, TYPE, PLAIN);

        assertClosed(() -> cipher.decrypt(SCOPE + "x", KEY, HASH, TYPE, envelope));
        assertClosed(() -> cipher.decrypt(SCOPE, KEY + "x", HASH, TYPE, envelope));
        assertClosed(() -> cipher.decrypt(SCOPE, KEY, "b".repeat(64), TYPE, envelope));
        assertClosed(() -> cipher.decrypt(SCOPE, KEY, HASH, TYPE + "x", envelope));

        String[] parts = envelope.split("\\.", -1);
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[4]);
        ciphertext[0] ^= 1;
        parts[4] = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        assertClosed(() -> cipher.decrypt(SCOPE, KEY, HASH, TYPE, String.join(".", parts)));
    }

    @Test
    void previousVersionDecryptsDuringRotationAndUnknownVersionFailsClosed() {
        var oldCipher = new AdminIdempotencySnapshotCipher("v1", ROOT_V1, "");
        String oldEnvelope = oldCipher.encrypt(SCOPE, KEY, HASH, TYPE, PLAIN);
        var rotated = new AdminIdempotencySnapshotCipher("v2", ROOT_V2, "v1=" + ROOT_V1);

        assertArrayEquals(PLAIN, rotated.decrypt(SCOPE, KEY, HASH, TYPE, oldEnvelope));
        assertEquals("v2", rotated.encrypt(SCOPE, KEY, HASH, TYPE, PLAIN).split("\\.")[2]);

        String unknown = oldEnvelope.replaceFirst("\\.v1\\.", ".unknown.");
        assertClosed(() -> rotated.decrypt(SCOPE, KEY, HASH, TYPE, unknown));
        assertThrows(IllegalArgumentException.class,
                () -> new AdminIdempotencySnapshotCipher(
                        "v2", ROOT_V2, "v2=" + ROOT_V1));
    }

    private static void assertClosed(org.junit.jupiter.api.function.Executable executable) {
        assertThrows(AdminIdempotencySnapshotCipher.SnapshotIntegrityException.class, executable);
    }
}
