package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.r15.R15Contracts.CommandResultResource;
import cc.orbexa.hhy.access.r15.R15Contracts.NotificationPage;
import cc.orbexa.hhy.access.r15.R15Contracts.NotificationReadRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.NotificationResource;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportMessageRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportTicketPage;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportTicketResource;
import cc.orbexa.hhy.access.r15.R15Service;
import cc.orbexa.hhy.access.user.UserPrincipal;
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
@RequestMapping("/api/v1")
public class R15Controller {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R15Service service;
    private final Clock clock;

    public R15Controller(R15Service service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/notifications")
    public ApiResponse<NotificationPage> notifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.notifications(principal.userId(), page, pageSize, status, keyword, sort));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<NotificationResource> readNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody NotificationReadRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.readNotification(principal.userId(), id, body, key, requestId(request)));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<CommandResultResource> readAllNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.readAllNotifications(principal.userId(), key, requestId(request)));
    }

    @GetMapping("/announcements")
    public ApiResponse<NotificationPage> announcements(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.announcements(page, pageSize, status, keyword, sort));
    }

    @GetMapping("/announcements/{id}")
    public ApiResponse<NotificationResource> announcement(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id, HttpServletRequest request) {
        return success(request, service.announcement(id));
    }

    @GetMapping("/help/articles")
    public ApiResponse<SupportTicketPage> helpArticles(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.helpArticles(page, pageSize, status, keyword, sort));
    }

    @GetMapping("/help/articles/{id}")
    public ApiResponse<SupportTicketResource> helpArticle(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id, HttpServletRequest request) {
        return success(request, service.helpArticle(id));
    }

    @GetMapping("/support/tickets")
    public ApiResponse<SupportTicketPage> supportTickets(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.supportTickets(principal.userId(), page, pageSize, status, keyword, sort));
    }

    @GetMapping("/support/tickets/{id}")
    public ApiResponse<SupportTicketResource> supportTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            HttpServletRequest request) {
        return success(request, service.supportTicket(principal.userId(), id));
    }

    @PostMapping("/support/tickets/{id}/messages")
    public ApiResponse<SupportTicketResource> addSupportMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody SupportMessageRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.addUserMessage(principal.userId(), id, body, key, requestId(request)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
