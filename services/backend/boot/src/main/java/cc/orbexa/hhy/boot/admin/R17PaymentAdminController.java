package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.commerce.R17PaymentContracts.AdminCommandContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.AdminPaymentQueryRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentPage;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ReconciliationRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ResolvePaymentExceptionRequest;
import cc.orbexa.hhy.commerce.R17PaymentService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
public class R17PaymentAdminController {
    private static final String ID = "^[0-9]{1,19}$";
    private final R17PaymentService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R17PaymentAdminController(
            R17PaymentService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('payment.read')")
    public ApiResponse<PaymentPage> adminPaymentsGetPayments(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminPayments(page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/payment-callbacks")
    @PreAuthorize("hasAuthority('payment.read')")
    public ApiResponse<PaymentPage> adminPaymentsGetPaymentCallbacks(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminCallbacks(page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/payments/{id}/query")
    @PreAuthorize("hasAuthority('payment.query')")
    public ApiResponse<PaymentResource> adminPaymentsPostPaymentsByIdQuery(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @RequestBody(required = false) AdminPaymentQueryRequest body,
            HttpServletRequest request) {
        return success(request, service.queryPayment(
                actor(principal, "adminPaymentsPostPaymentsByIdQuery", key, request), id, body));
    }

    @GetMapping("/payment-exceptions")
    @PreAuthorize("hasAuthority('payment.exception.read')")
    public ApiResponse<PaymentPage> adminPaymentsGetPaymentExceptions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminExceptions(page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/payment-exceptions/{id}/resolve")
    @PreAuthorize("hasAuthority('payment.exception.resolve')")
    public ApiResponse<CommandResultResource> adminPaymentsPostPaymentExceptionsByIdResolve(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @RequestBody ResolvePaymentExceptionRequest body,
            HttpServletRequest request) {
        return success(request, service.resolveException(
                actor(principal, "adminPaymentsPostPaymentExceptionsByIdResolve", key, request),
                id,
                body));
    }

    @PostMapping("/payment-reconciliation/run")
    @PreAuthorize("hasAuthority('payment.reconcile')")
    public ApiResponse<CommandResultResource> adminPaymentsPostPaymentReconciliationRun(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @RequestBody ReconciliationRequest body,
            HttpServletRequest request) {
        return success(request, service.reconcile(
                actor(principal, "adminPaymentsPostPaymentReconciliationRun", key, request), body));
    }

    private AdminCommandContext actor(
            AdminPrincipal principal, String operationId, String key, HttpServletRequest request) {
        return new AdminCommandContext(
                principal.adminId(), principal.sessionId(), principal.username(), operationId,
                requestId(request), clientIpResolver.resolve(request), key);
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
