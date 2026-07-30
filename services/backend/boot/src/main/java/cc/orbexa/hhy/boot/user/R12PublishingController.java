package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
import cc.orbexa.hhy.content.R12PublishingService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class R12PublishingController {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R12PublishingService service;
    private final Clock clock;

    public R12PublishingController(R12PublishingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/me/contents")
    public ApiResponse<ContentPage> contentGetMeContents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) @Size(max = 64) String categoryCode,
            @RequestParam(required = false) @Size(max = 32) String regionCode,
            HttpServletRequest request) {
        return success(request, service.mine(principal.userId(), page, pageSize, cursor, status,
                keyword, sort, contentType, categoryCode, regionCode));
    }

    @GetMapping("/api/v1/me/drafts")
    public ApiResponse<ContentPage> contentGetMeDrafts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.drafts(
                principal.userId(), page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/api/v1/contents/{id}/reviews")
    public ApiResponse<ContentPage> contentGetContentsByIdReviews(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) @Size(max = 64) String categoryCode,
            @RequestParam(required = false) @Size(max = 32) String regionCode,
            HttpServletRequest request) {
        return success(request, service.reviews(principal.userId(), id, page, pageSize, cursor,
                status, keyword, sort, contentType, categoryCode, regionCode));
    }

    @GetMapping("/api/v1/contents/{id}/analytics")
    public ApiResponse<ContentPage> contentGetContentsByIdAnalytics(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) @Size(max = 64) String categoryCode,
            @RequestParam(required = false) @Size(max = 32) String regionCode,
            HttpServletRequest request) {
        return success(request, service.analytics(principal.userId(), id, page, pageSize, cursor,
                status, keyword, sort, contentType, categoryCode, regionCode));
    }

    @PostMapping("/api/v1/contents/{id}/submit")
    public ApiResponse<CommandResult> contentPostContentsByIdSubmit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody StatusRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.submit(principal.userId(), id, body, key, context(request)));
    }

    @PostMapping("/api/v1/contents/{id}/online")
    public ApiResponse<CommandResult> contentPostContentsByIdOnline(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody StatusRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.online(principal.userId(), id, body, key, context(request)));
    }

    @PostMapping("/api/v1/contents/{id}/offline")
    public ApiResponse<CommandResult> contentPostContentsByIdOffline(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody StatusRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.offline(principal.userId(), id, body, key, context(request)));
    }

    @PostMapping("/api/v1/contents/{id}/copy")
    public ApiResponse<CommandResult> contentPostContentsByIdCopy(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody StatusRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.copy(principal.userId(), id, body, key, context(request)));
    }

    @DeleteMapping("/api/v1/contents/{id}")
    public ApiResponse<CommandResult> contentDeleteContentsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestParam @PositiveOrZero long expectedVersion,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.delete(
                principal.userId(), id, expectedVersion, key, context(request)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(
                requestId == null ? "missing" : requestId.toString(), data, Instant.now(clock));
    }

    private static R12PublishingService.CommandContext context(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        String device = clean(request.getHeader("X-Device-Fingerprint"));
        if (device == null) device = clean(request.getHeader("User-Agent"));
        return new R12PublishingService.CommandContext(
                requestId == null ? "missing" : requestId.toString(),
                limited(request.getRemoteAddr()), limited(device));
    }

    private static String limited(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return "unknown";
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(0, 255);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
