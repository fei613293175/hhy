package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.identity.IdentityContracts.CreateLivenessTokenRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.CreateSessionRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentityConsentResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.RetrySessionRequest;
import cc.orbexa.hhy.access.identity.IdentityService;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin orchestration for the frozen R05 authenticated client operationIds. */
@Validated
@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {
    private final IdentityService service;
    private final Clock clock;

    public IdentityController(IdentityService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/consent")
    public ApiResponse<IdentityConsentResource> identityGetIdentityConsent(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        return success(request, service.consent(principal));
    }

    @PostMapping("/sessions")
    public ApiResponse<IdentitySessionResource> identityPostIdentitySessions(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSessionRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.create(principal, body, key));
    }

    @PostMapping("/sessions/{id}/liveness-token")
    public ApiResponse<IdentitySessionResource> identityPostIdentitySessionsByIdLivenessToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody CreateLivenessTokenRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.livenessToken(principal, id, body, key));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<IdentitySessionResource> identityGetIdentitySessionsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            HttpServletRequest request) {
        return success(request, service.get(principal, id));
    }

    @PostMapping("/sessions/{id}/retry")
    public ApiResponse<IdentitySessionResource> identityPostIdentitySessionsByIdRetry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody RetrySessionRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.retry(principal, id, body, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(requestId == null ? "missing" : requestId.toString(),
                data, Instant.now(clock));
    }
}
