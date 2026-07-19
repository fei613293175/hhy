package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.identity.IdentityCallbackService;
import cc.orbexa.hhy.access.identity.IdentityCallbackService.CallbackConsumeRequest;
import cc.orbexa.hhy.access.identity.IdentityCallbackService.CallbackStatusResource;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public one-time browser handoff. The response intentionally contains only business status. */
@Validated
@RestController
@RequestMapping("/public-api/v1/identity/callback")
public class PublicIdentityCallbackController {
    private final IdentityCallbackService service;
    private final Clock clock;

    public PublicIdentityCallbackController(IdentityCallbackService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/consume")
    public ApiResponse<CallbackStatusResource> publicPostIdentityCallbackConsume(
            @Valid @RequestBody CallbackConsumeRequest body, HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(requestId == null ? "missing" : requestId.toString(),
                service.consume(body), Instant.now(clock));
    }
}
