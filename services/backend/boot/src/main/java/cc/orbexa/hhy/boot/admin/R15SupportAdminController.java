package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.r15.R15Contracts.ConversationPage;
import cc.orbexa.hhy.access.r15.R15Contracts.ConversationResource;
import cc.orbexa.hhy.access.r15.R15Contracts.DecideRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportAssignRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportCloseRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportMessageRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportTicketPage;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportTicketResource;
import cc.orbexa.hhy.access.r15.R15Service;
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
public class R15SupportAdminController {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R15Service service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R15SupportAdminController(R15Service service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/support/tickets")
    @PreAuthorize("hasAuthority('support.read')")
    public ApiResponse<SupportTicketPage> supportTickets(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.supportTickets(null, page, pageSize, status, keyword, sort));
    }

    @GetMapping("/support/tickets/{id}")
    @PreAuthorize("hasAuthority('support.read')")
    public ApiResponse<SupportTicketResource> supportTicket(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            HttpServletRequest request) {
        return success(request, service.supportTicket(null, id));
    }

    @PostMapping("/support/tickets/{id}/assign")
    @PreAuthorize("hasAuthority('support.assign')")
    public ApiResponse<SupportTicketResource> assign(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody SupportAssignRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.assignTicket(principal.adminId(), id, body, key,
                requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/support/tickets/{id}/messages")
    @PreAuthorize("hasAuthority('support.reply')")
    public ApiResponse<SupportTicketResource> reply(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody SupportMessageRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.replyTicket(principal.adminId(), id, body, key,
                requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/support/tickets/{id}/close")
    @PreAuthorize("hasAuthority('support.close')")
    public ApiResponse<SupportTicketResource> close(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody SupportCloseRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.closeTicket(principal.adminId(), id, body, key,
                requestId(request), clientIpResolver.resolve(request)));
    }

    @GetMapping("/chat-reports")
    @PreAuthorize("hasAuthority('chat.report.read')")
    public ApiResponse<ConversationPage> chatReports(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.chatReports(page, pageSize, status, keyword, sort));
    }

    @PostMapping("/chat-reports/{id}/decide")
    @PreAuthorize("hasAuthority('chat.report.decide')")
    public ApiResponse<ConversationResource> decideChatReport(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody DecideRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.decideChatReport(principal.adminId(), id, body, key,
                requestId(request), clientIpResolver.resolve(request)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
