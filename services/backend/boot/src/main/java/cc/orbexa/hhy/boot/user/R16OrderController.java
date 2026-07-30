package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderResource;
import cc.orbexa.hhy.commerce.R16CommerceService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Frozen R16 owner-isolated mobile order read surface. */
@Validated
@RestController
public class R16OrderController {
    private static final String ORDER_NO = "^[A-Za-z0-9_-]{1,128}$";
    private final R16CommerceService service;
    private final Clock clock;

    public R16OrderController(R16CommerceService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/me/orders")
    public ApiResponse<OrderPage> orderGetMeOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.userOrders(
                activeUserId(principal), page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/api/v1/me/orders/{orderNo}")
    public ApiResponse<OrderResource> orderGetMeOrdersByOrderno(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ORDER_NO) String orderNo,
            HttpServletRequest request) {
        return success(request, service.userOrder(activeUserId(principal), orderNo));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }

    private static long activeUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
        if (!principal.active()) {
            throw new BusinessException(
                    "COMMON-403-FORBIDDEN", "当前账号状态不允许访问此能力", 403, false);
        }
        return principal.userId();
    }
}
