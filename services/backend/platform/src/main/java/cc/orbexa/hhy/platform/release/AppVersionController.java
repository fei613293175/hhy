package cc.orbexa.hhy.platform.release;

import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public final class AppVersionController {
    private final AppVersionService service;

    public AppVersionController(AppVersionService service) {
        this.service = service;
    }

    @PostMapping("/public-api/v1/app/version-check")
    public ApiResponse<AppVersionPolicyView> publicCheck(
            @Valid @RequestBody AppVersionCheckRequest command,
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        return ApiResponse.success(requestId, service.check(command));
    }

    @GetMapping("/api/v1/app/version-check")
    public ApiResponse<AppVersionPolicyView> authenticatedCompatibility(
            @RequestParam String platform,
            @RequestParam int versionCode,
            @RequestParam(required = false) String versionName,
            @RequestParam String channel,
            @RequestParam String environment,
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        var command = new AppVersionCheckRequest(platform, versionCode, versionName, channel, environment);
        return ApiResponse.success(requestId, service.check(command));
    }
}
