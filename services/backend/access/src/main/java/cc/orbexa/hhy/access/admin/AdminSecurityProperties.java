package cc.orbexa.hhy.access.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hhy.admin-security")
public record AdminSecurityProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotBlank @Size(min = 32) String mfaRootSecret,
        @NotBlank String issuer,
        Duration accessTtl,
        Duration mfaTicketTtl,
        Duration enrollmentTtl,
        @Min(8) @Max(72) int passwordMinLength,
        @Min(8) @Max(72) int passwordMaxLength,
        @Min(1) @Max(100) int passwordMaxFailures,
        @Min(1) @Max(2_592_000) int passwordLockSeconds) {

    public AdminSecurityProperties {
        if (accessTtl == null || accessTtl.isNegative() || accessTtl.isZero()) {
            throw new IllegalArgumentException("hhy.admin-security.access-ttl must be positive");
        }
        if (mfaTicketTtl == null || mfaTicketTtl.isNegative() || mfaTicketTtl.isZero()) {
            throw new IllegalArgumentException("hhy.admin-security.mfa-ticket-ttl must be positive");
        }
        if (enrollmentTtl == null || enrollmentTtl.isNegative() || enrollmentTtl.isZero()) {
            throw new IllegalArgumentException("hhy.admin-security.enrollment-ttl must be positive");
        }
        if (passwordMaxLength < passwordMinLength) {
            throw new IllegalArgumentException("password max length must be >= min length");
        }
    }
}
