package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.SupportTicketCreateRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SupportTicketResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.AccountCancellationRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserResource;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class UserSelfServiceController {
    private final UserAuthService service;
    private final Clock clock;

    public UserSelfServiceController(UserAuthService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/me")
    public ApiResponse<UserResource> self(@AuthenticationPrincipal UserPrincipal principal,
                                          HttpServletRequest request) {
        return success(request, service.self(principal));
    }

    @PostMapping("/api/v1/support/tickets")
    public ApiResponse<SupportTicketResource> createTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SupportTicketCreateRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.createSupportTicket(principal, body, key, requestId(request)));
    }

    @PostMapping("/api/v1/me/cancellation")
    public ApiResponse<CommandResultResource> requestCancellation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AccountCancellationRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.requestCancellation(principal, body, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
