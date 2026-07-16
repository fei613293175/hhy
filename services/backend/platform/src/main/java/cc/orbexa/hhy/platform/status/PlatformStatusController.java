package cc.orbexa.hhy.platform.status;

import cc.orbexa.hhy.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public-api/v1/platform")
public final class PlatformStatusController {
    private final PlatformStatusService service;

    public PlatformStatusController(PlatformStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApiResponse<PlatformStatusView> status(
            @RequestAttribute(name = "requestId", required = false) String requestId) {
        return ApiResponse.success(requestId, service.current());
    }
}
