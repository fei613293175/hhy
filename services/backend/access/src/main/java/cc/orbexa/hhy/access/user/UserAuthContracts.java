package cc.orbexa.hhy.access.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class UserAuthContracts {
    private UserAuthContracts() { }

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
