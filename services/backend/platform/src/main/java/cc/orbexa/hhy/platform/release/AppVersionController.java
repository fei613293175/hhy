package cc.orbexa.hhy.platform.release;

import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;

@RestController
public final class AppVersionController {
    private final AppVersionService service;
    private final Clock clock;

    public AppVersionController(AppVersionService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/public-api/v1/app/version-check")
    public ApiResponse<AppVersionPolicyView> publicCheck(
            @Valid @RequestBody AppVersionCheckRequest command,
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        return ApiResponse.success(requestId, service.check(command), Instant.now(clock));
    }

    @GetMapping("/api/v1/app/version-check")
    public ApiResponse<AppVersionPolicyView> authenticatedCompatibility(
            @RequestParam @Pattern(regexp = "ANDROID") String platform,
            @RequestParam @Min(1) long versionCode,
            @RequestParam @NotBlank @Size(max = 64) String channel,
            @RequestParam @Pattern(regexp = "DEV|TEST|STAGING|PROD") String environment,
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        var command = new AppVersionCheckRequest(platform, versionCode, null, channel, environment);
        return ApiResponse.success(requestId, service.check(command), Instant.now(clock));
    }

    @GetMapping("/public-api/v1/app/latest")
    public ApiResponse<AppLatestView> latestPublic(
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        return ApiResponse.success(requestId, service.latestPublic(), Instant.now(clock));
    }
}
