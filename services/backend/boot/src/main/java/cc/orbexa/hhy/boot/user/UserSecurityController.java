package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.PasswordChangeRequest;
import cc.orbexa.hhy.access.user.UserAuthService;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/me/security")
public class UserSecurityController {
    private final UserAuthService service;
    private final Clock clock;

    public UserSecurityController(UserAuthService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/password/change")
    public ApiResponse<CommandResultResource> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(requestId == null ? "missing" : requestId.toString(),
                service.changePassword(principal, body, key), Instant.now(clock));
    }
}
