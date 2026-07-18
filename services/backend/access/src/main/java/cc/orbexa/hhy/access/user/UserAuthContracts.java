package cc.orbexa.hhy.access.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class UserAuthContracts {
    private UserAuthContracts() { }

    public enum AuthScene { LOGIN, REGISTER, RESET_PASSWORD, SENSITIVE_OPERATION }

    public record SecurityChallengeRequest(
            @NotNull AuthScene scene,
            @NotBlank @Size(max = 2000) String clientNonce,
            @Size(max = 2000) String deviceFingerprint) { }

    public record ChallengeResource(
            String challengeId,
            String challengeType,
            Instant expiresAt,
            String imageBase64,
            String token) { }

    public record PasswordLoginRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 64) String challengeId,
            @NotBlank @Size(max = 2000) String challengeProof,
            Map<String, Object> device) { }

    public record SmsSendRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @NotNull AuthScene scene,
            @NotBlank @Size(max = 64) String challengeId,
            @NotBlank @Size(max = 2000) String challengeProof) { }

    public record SmsLoginRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @NotBlank @Size(min = 4, max = 10) String smsCode,
            Map<String, Object> device) { }

    public record InviteCodeValidateRequest(
            @NotBlank @Size(max = 2000) String inviteCode) { }

    public record RegistrationAgreementVersionResource(
            String versionId,
            String code,
            Instant effectiveAt) { }

    public record RegistrationConfigResource(
            List<RegistrationAgreementVersionResource> agreementVersions) { }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @NotBlank @Size(min = 4, max = 10) String smsCode,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 2000) String inviteCode,
            @NotEmpty @Size(max = 100) List<@NotBlank String> agreementVersions,
            Map<String, Object> device) { }

    public record PasswordResetRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @NotBlank @Size(min = 4, max = 10) String smsCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword) { }

    public record CommandResultResource(
            String resourceId,
            String businessNo,
            String status,
            Long version,
            Instant acceptedAt) { }

    public record RefreshRequest(
            @NotBlank @Size(max = 2000) String refreshToken,
            @NotBlank @Size(max = 64) String deviceId) { }

    public record DeviceSummaryResource(
            String deviceId,
            String deviceName,
            String platform,
            String osVersion,
            String appVersion,
            Instant lastActiveAt,
            Boolean trusted) { }

    public record UserSessionResource(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            String userId,
            String sessionId,
            DeviceSummaryResource device,
            List<String> capabilities) { }
}
