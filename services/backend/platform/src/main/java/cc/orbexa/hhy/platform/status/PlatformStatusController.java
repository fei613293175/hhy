package cc.orbexa.hhy.platform.status;

import cc.orbexa.hhy.shared.api.ApiResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public-api/v1/platform")
public final class PlatformStatusController {
    private final PlatformStatusService service;
    private final Clock clock;

    public PlatformStatusController(PlatformStatusService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/status")
    public ApiResponse<PlatformStatusView> status(
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        return ApiResponse.success(requestId, service.current(), Instant.now(clock));
    }
}
