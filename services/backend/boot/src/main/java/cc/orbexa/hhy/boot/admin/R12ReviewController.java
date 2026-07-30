package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.content.R12ReviewContracts.AppealPage;
import cc.orbexa.hhy.content.R12ReviewContracts.ReportPage;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewAssignRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewActorContext;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewDecisionRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewPage;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewResource;
import cc.orbexa.hhy.content.R12ReviewService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1")
public class R12ReviewController {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R12ReviewService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R12ReviewController(
            R12ReviewService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/reviews/queue")
    @PreAuthorize("hasAuthority('review.read')")
    public ApiResponse<ReviewPage> adminReviewGetReviewsQueue(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.queue(page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/reviews/{id}")
    @PreAuthorize("hasAuthority('review.read')")
    public ApiResponse<ReviewResource> adminReviewGetReviewsById(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            HttpServletRequest request) {
        return success(request, service.detail(id));
    }

    @GetMapping("/content-reports")
    @PreAuthorize("hasAuthority('report.read')")
    public ApiResponse<ReportPage> adminReportsGetContentReports(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.reports(page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/appeals")
    @PreAuthorize("hasAuthority('appeal.read')")
    public ApiResponse<AppealPage> adminAppealsGetAppeals(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.appeals(page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/reviews/{id}/decide")
    @PreAuthorize("hasAuthority('review.decide')")
    public ApiResponse<ReviewResource> adminReviewPostReviewsByIdDecide(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ReviewDecisionRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.decide(
                actor(principal, "review.decide", request), id, body, key));
    }

    @PostMapping("/reviews/{id}/assign")
    @PreAuthorize("hasAuthority('review.assign')")
    public ApiResponse<ReviewResource> adminReviewPostReviewsByIdAssign(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ReviewAssignRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.assign(
                actor(principal, "review.assign", request), id, body, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }

    private ReviewActorContext actor(
            AdminPrincipal principal, String permission, HttpServletRequest request) {
        return new ReviewActorContext(
                principal.adminId(), principal.sessionId(), principal.username(), permission,
                requestId(request), clientIpResolver.resolve(request));
    }
}
