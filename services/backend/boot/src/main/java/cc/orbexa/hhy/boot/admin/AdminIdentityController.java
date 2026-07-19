package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.FreezeRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.IdentityPageResource;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.MediaAccessRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.ReviewRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityService;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller for the five frozen R05 administrator identity operationIds. */
@Validated
@RestController
public class AdminIdentityController {
    private final AdminIdentityService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public AdminIdentityController(
            AdminIdentityService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/admin-api/v1/identities")
    @PreAuthorize("hasAuthority('identity.read')")
    public ApiResponse<IdentityPageResource> adminIdentityGetIdentities(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.list(page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/admin-api/v1/identities/{userId}")
    @PreAuthorize("hasAuthority('identity.read')")
    public ApiResponse<IdentitySessionResource> adminIdentityGetIdentitiesByUserid(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String userId,
            HttpServletRequest request) {
        return success(request, service.detail(
                principal, userId, requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/admin-api/v1/identities/{userId}/media-access")
    @PreAuthorize("hasAuthority('identity.media.view')")
    public ApiResponse<Object> adminIdentityPostIdentitiesByUseridMediaAccess(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String userId,
            @Valid @RequestBody MediaAccessRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.mediaAccess(principal, userId, body, key,
                requestId(request), clientIpResolver.resolve(request)).payload());
    }

    @PostMapping("/admin-api/v1/identity-sessions/{id}/review")
    @PreAuthorize("hasAuthority('identity.review')")
    public ApiResponse<Object> adminIdentityPostIdentitySessionsByIdReview(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody ReviewRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.review(principal, id, body, key,
                requestId(request), clientIpResolver.resolve(request)).payload());
    }

    @PostMapping("/admin-api/v1/identities/{userId}/freeze")
    @PreAuthorize("hasAuthority('identity.freeze')")
    public ApiResponse<Object> adminIdentityPostIdentitiesByUseridFreeze(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String userId,
            @Valid @RequestBody FreezeRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.freeze(principal, userId, body, key,
                requestId(request), clientIpResolver.resolve(request)).payload());
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
