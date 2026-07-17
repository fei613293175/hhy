package cc.orbexa.hhy.access.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminSecurityContracts {
    private AdminSecurityContracts() { }

    public record LoginRequest(
            @NotBlank @Size(max = 2000) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 2000) String captchaToken) { }

    public record MfaVerifyRequest(
            @NotBlank @Size(max = 2000) String mfaTicket,
            @NotBlank @Size(min = 4, max = 10) String code) { }

    public record LogoutRequest(
            @Size(max = 2000) String reason,
            Long expectedVersion,
            Map<String, Object> payload) { }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(max = 72) String newPassword,
            @NotBlank @Size(max = 10) String mfaCode) { }

    public record MfaConfirmRequest(
            @NotBlank @Size(max = 64) String enrollmentId,
            @NotBlank @Size(max = 10) String code) { }

    public record MfaDisableRequest(
            @NotBlank @Size(max = 10) String code,
            @NotBlank @Size(max = 500) String reason) { }

    public record AdminSessionResource(
            String accessToken,
            Instant expiresAt,
            String adminUserId,
            String displayName,
            List<String> permissionCodes,
            String mfaRequired,
            String mfaTicket) { }

    public record MfaEnrollmentResource(
            String enrollmentId,
            String method,
            String secretQrCodeUrl,
            String manualKeyMasked,
            Instant expiresAt) { }

    public record AdminSelfSecurityResource(
            String adminId,
            String username,
            boolean mfaEnabled,
            List<String> mfaMethods,
            long activeSessionCount,
            Instant lastPasswordChangedAt,
            Instant lastLoginAt,
            String lastLoginIpMasked,
            long recoveryCodesRemaining) { }

    public record CommandResultResource(
            String resourceId,
            String businessNo,
            String status,
            Long version,
            Instant acceptedAt) { }
}
