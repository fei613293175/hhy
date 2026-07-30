package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.R07Contracts.CommandResult;
import cc.orbexa.hhy.content.R07Contracts.ContactAccess;
import cc.orbexa.hhy.content.R07Contracts.ContactAccessRequest;
import cc.orbexa.hhy.content.R07Contracts.PublisherSummary;
import cc.orbexa.hhy.content.R07Contracts.SearchResults;
import cc.orbexa.hhy.content.R07Contracts.SearchTerms;
import cc.orbexa.hhy.content.R07Service;
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
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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

/** Authenticated public endpoints bound to the seven frozen R07 operations. */
@Validated
@RestController
public class R07Controller {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R07Service service;
    private final Clock clock;

    public R07Controller(R07Service service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/search")
    public ApiResponse<SearchResults> searchGetSearch(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("q") @NotBlank @Size(max = 100) String query,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) @Size(max = 64) String categoryCode,
            @RequestParam(required = false) @Size(max = 32) String regionCode,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(defaultValue = "relevance:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.search(principal.userId(), query, contentType, categoryCode,
                regionCode, page, pageSize, cursor, sort));
    }

    @GetMapping("/api/v1/search/hot")
    public ApiResponse<SearchTerms> searchGetSearchHot(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "weight:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.hot(page, pageSize, cursor, keyword, sort));
    }

    @GetMapping("/api/v1/search/history")
    public ApiResponse<SearchTerms> searchGetSearchHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.history(
                principal.userId(), page, pageSize, cursor, keyword, sort));
    }

    @DeleteMapping("/api/v1/search/history")
    public ApiResponse<CommandResult> searchDeleteSearchHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.clearHistory(principal.userId(), key));
    }

    @GetMapping("/api/v1/publishers/{id}")
    public ApiResponse<PublisherSummary> userGetPublishersById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            HttpServletRequest request) {
        return success(request, service.publisher(principal.userId(), id));
    }

    @PostMapping("/api/v1/contents/{id}/contacts/{channel}/access")
    public ResponseEntity<ApiResponse<ContactAccess>> contentPostContentsByIdContactsByChannelAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @PathVariable @NotBlank @Size(max = 128) String channel,
            @Valid @RequestBody ContactAccessRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        ApiResponse<ContactAccess> response = success(request,
                service.contact(principal.userId(), id, channel, body, key));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
