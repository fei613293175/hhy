package cc.orbexa.hhy.access.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hhy.user-security")
public record UserAuthProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotBlank @Size(min = 32) String tokenHmacSecret,
        @NotBlank @Size(min = 32) String snapshotRootSecret,
        @NotBlank String issuer,
        Duration accessTtl,
        Duration refreshTtl,
        Duration idempotencyTtl) {

    public UserAuthProperties {
        requirePositive(accessTtl, "access-ttl");
        requirePositive(refreshTtl, "refresh-ttl");
        requirePositive(idempotencyTtl, "idempotency-ttl");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("hhy.user-security." + name + " must be positive");
        }
    }
}
