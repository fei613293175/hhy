package cc.orbexa.hhy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class P00FlywayAutoConfigurationTest {

    @Test
    void springBootFlywayAutoConfigurationModuleIsOnClasspath() {
        assertDoesNotThrow(() -> Class.forName(
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"));
    }
}
