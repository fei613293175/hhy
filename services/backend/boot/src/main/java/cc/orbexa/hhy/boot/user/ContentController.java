package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated client view of public content; non-ONLINE rows are never exposed. */
@Validated
@RestController
public class ContentController {
    private final ContentService service;
    private final Clock clock;

    public ContentController(ContentService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/contents")
    public ApiResponse<ContentPage> contentGetContents(
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
            @RequestParam(required = false) @Size(max = 64) String publisherId,
            HttpServletRequest request) {
        if (status != null && !status.isBlank() && !"ONLINE".equalsIgnoreCase(status.strip())) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "公开内容只允许查询ONLINE状态", 400, false);
        }
        ContentPage result = service.list(page, pageSize, cursor, "ONLINE", keyword, sort,
                contentType, categoryCode, regionCode, publisherId);
        return ApiResponse.success(requestId(request), result, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
