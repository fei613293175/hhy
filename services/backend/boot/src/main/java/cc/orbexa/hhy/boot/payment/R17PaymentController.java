package cc.orbexa.hhy.boot.payment;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CreatePaymentRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentPage;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderCallbackContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderNotification;
import cc.orbexa.hhy.commerce.R17PaymentContracts.UserCommandContext;
import cc.orbexa.hhy.commerce.R17PaymentService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class R17PaymentController {
    private static final String ORDER_NO = "^[A-Za-z0-9_-]{1,128}$";
    private final R17PaymentService service;
    private final R17PaymentSignatureVerifier signatures;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public R17PaymentController(
            R17PaymentService service,
            R17PaymentSignatureVerifier signatures,
            ObjectMapper objectMapper,
            Clock clock) {
        this.service = service;
        this.signatures = signatures;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @GetMapping("/api/v1/orders/{orderNo}/cashier")
    public ApiResponse<PaymentResource> paymentGetOrdersByOrdernoCashier(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ORDER_NO) String orderNo,
            HttpServletRequest request) {
        return success(request, service.cashier(activeUserId(principal), orderNo));
    }

    @PostMapping("/api/v1/orders/{orderNo}/payments")
    public ApiResponse<PaymentResource> paymentPostOrdersByOrdernoPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ORDER_NO) String orderNo,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @RequestBody CreatePaymentRequest body,
            HttpServletRequest request) {
        UserCommandContext context = new UserCommandContext(
                activeUserId(principal), "paymentPostOrdersByOrdernoPayments", requestId(request), key);
        return success(request, service.createPayment(context, orderNo, body));
    }

    @GetMapping("/api/v1/orders/{orderNo}/payment-status")
    public ApiResponse<PaymentPage> paymentGetOrdersByOrdernoPaymentStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ORDER_NO) String orderNo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10) int waitSeconds,
            HttpServletRequest request) {
        return success(request, service.paymentStatus(
                activeUserId(principal), orderNo, page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/public-api/v1/payment/{gateway}/notify")
    public ApiResponse<PaymentResource> paymentPostPaymentByGatewayNotify(
            @PathVariable @Size(min = 1, max = 128) String gateway,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @RequestHeader("X-Provider-Timestamp") String timestamp,
            @RequestHeader("X-Provider-Nonce") String nonce,
            @RequestHeader("X-Provider-Signature") String signature,
            @RequestBody String rawBody,
            HttpServletRequest request) {
        ProviderCallbackContext context = signatures.verify(
                gateway, timestamp, nonce, signature, key, rawBody);
        ProviderNotification notification;
        try {
            notification = objectMapper.readValue(rawBody, ProviderNotification.class);
        } catch (Exception failure) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "支付回调内容格式不正确", 400, false);
        }
        return success(request, service.providerNotification(context, notification));
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
