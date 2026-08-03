package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.r15.R15Contracts.PublicPageResource;
import cc.orbexa.hhy.access.r15.R15Service;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/public-api/v1")
public class R15PublicController {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R15Service service;
    private final Clock clock;

    public R15PublicController(R15Service service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/help/articles/{id}")
    public ApiResponse<PublicPageResource> helpArticle(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id, HttpServletRequest request) {
        return ApiResponse.success(requestId(request), service.publicHelpArticle(id), Instant.now(clock));
    }

    @GetMapping("/agreements/{code}")
    public ApiResponse<PublicPageResource> agreement(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String code, HttpServletRequest request) {
        return ApiResponse.success(requestId(request), service.publicAgreement(code), Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
