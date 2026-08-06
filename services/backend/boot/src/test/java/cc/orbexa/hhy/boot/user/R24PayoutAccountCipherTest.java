package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.user.UserAuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class R24PayoutAccountCipherTest {
    private final R24PayoutAccountCipher cipher = new R24PayoutAccountCipher(new UserAuthProperties(
            "jwt-secret-for-r24-tests-000000000000",
            "hmac-secret-for-r24-tests-0000000000",
            "snapshot-secret-for-r24-tests-000000",
            "hhy-r24-test", Duration.ofMinutes(5), Duration.ofDays(7), Duration.ofHours(1)));

    @Test
    void encryptsAndDecryptsOnlyForTheBoundUserAndField() {
        String first = cipher.encrypt(42L, "alipayAccount", "member@example.com");
        String second = cipher.encrypt(42L, "alipayAccount", "member@example.com");

        assertNotEquals(first, second);
        assertEquals("member@example.com", cipher.decrypt(42L, "alipayAccount", first));
        assertThrows(IllegalArgumentException.class,
                () -> cipher.decrypt(43L, "alipayAccount", first));
        assertThrows(IllegalArgumentException.class,
                () -> cipher.decrypt(42L, "otherField", first));
    }

    @Test
    void createsStablePrivateDigestsAndMasksOnlyTheTail() {
        assertEquals(cipher.digest("13800138000"), cipher.digest("13800138000"));
        assertNotEquals(cipher.digest("13800138000"), cipher.digest("13900139000"));
        assertEquals("****8000", R24PayoutAccountCipher.mask("13800138000"));
        assertEquals("****abc", R24PayoutAccountCipher.mask("abc"));
    }
}
