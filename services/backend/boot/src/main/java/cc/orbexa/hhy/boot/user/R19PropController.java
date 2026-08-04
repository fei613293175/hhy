package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropOrderRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPage;
import cc.orbexa.hhy.commerce.R19PropContracts.PropUseRequest;
import cc.orbexa.hhy.commerce.R19PropService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class R19PropController {
    private final R19PropService service;
    private final Clock clock;

    public R19PropController(R19PropService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/props/store")
    public ApiResponse<PropPage> propGetPropsStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "name:asc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.propStore(
                activeUserId(principal), page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/api/v1/me/props")
    public ApiResponse<PropPage> propGetMeProps(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "expiresAt:asc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.userProps(
                activeUserId(principal), page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/api/v1/props/orders")
    public ApiResponse<CommandResultResource> propPostPropsOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PropOrderRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128)
            String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.order(
                activeUserId(principal), body, idempotencyKey, requestId(request)));
    }

    @PostMapping("/api/v1/me/props/{id}/use")
    public ApiResponse<CommandResultResource> propPostMePropsByIdUse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Size(min = 1, max = 64) String id,
            @Valid @RequestBody PropUseRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128)
            String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.use(
                activeUserId(principal), id, body, idempotencyKey, requestId(request)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }

    private static long activeUserId(UserPrincipal principal) {
        if (principal == null) throw new BusinessException(
                "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        if (!principal.active()) throw new BusinessException(
                "COMMON-403-FORBIDDEN", "当前账号状态不允许访问此能力", 403, false);
        return principal.userId();
    }
}
