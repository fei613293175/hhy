package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.R13Contracts.InvalidFeedbackRequest;
import cc.orbexa.hhy.content.R13Service;
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

/** R13 favorites, deduplicated activity history, and invalid-contact feedback endpoints. */
@Validated
@RestController
public class R13Controller {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R13Service service;
    private final Clock clock;

    public R13Controller(R13Service service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @DeleteMapping("/api/v1/contents/{id}/favorite")
    public ApiResponse<CommandResult> contentDeleteContentsByIdFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.unfavorite(principal.userId(), id, key));
    }

    @GetMapping("/api/v1/me/favorites")
    public ApiResponse<ContentPage> contentGetMeFavorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.favorites(
                principal.userId(), page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/api/v1/me/history")
    public ApiResponse<ContentPage> contentGetMeHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.history(
                principal.userId(), page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/api/v1/contents/{id}/invalid-feedback")
    public ApiResponse<CommandResult> contentPostContentsByIdInvalidFeedback(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody InvalidFeedbackRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.invalidFeedback(principal.userId(), id, body, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
