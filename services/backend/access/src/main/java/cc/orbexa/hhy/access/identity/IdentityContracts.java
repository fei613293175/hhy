package cc.orbexa.hhy.access.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/** Canonical Java representation of the four frozen R05 client operations. */
public final class IdentityContracts {
    private IdentityContracts() { }

    public record CreateSessionRequest(
            @NotBlank @Size(max = 2000) String realName,
            @NotBlank @Size(max = 2000) String idNumber,
            @NotBlank @Size(max = 2000) String consentVersion) { }

    public record CreateLivenessTokenRequest(
            @NotBlank @Size(max = 2048) String returnUrl) { }

    public record RetrySessionRequest(
            @Size(max = 2000) String reason,
            Long expectedVersion,
            Map<String, Object> payload) {
        public RetrySessionRequest {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record IdentitySessionResource(
            String id,
            String userId,
            String status,
            String provider,
            String livenessUrl,
            String failureCode,
            Instant expiresAt,
            long version) { }

    public record IdentityOverviewResource(
            @NotBlank String status,
            IdentitySessionResource activeSession) { }

    public record IdentityConsentResource(
            @NotBlank @Size(max = 2000) String consentVersion,
            @NotBlank @Size(max = 2000) String title,
            @NotBlank @Size(max = 20000) String content) { }
}
