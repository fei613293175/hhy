package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.R08Contracts.Conversation;
import cc.orbexa.hhy.content.R08Contracts.DirectConversationRequest;
import cc.orbexa.hhy.content.R14Contracts.BlockRequest;
import cc.orbexa.hhy.content.R14Contracts.ChatMessage;
import cc.orbexa.hhy.content.R14Contracts.CommandResult;
import cc.orbexa.hhy.content.R14Contracts.ConversationPage;
import cc.orbexa.hhy.content.R14Contracts.MessagePage;
import cc.orbexa.hhy.content.R14Contracts.ReadRequest;
import cc.orbexa.hhy.content.R14Contracts.ReportRequest;
import cc.orbexa.hhy.content.R14Contracts.SendMessageRequest;
import cc.orbexa.hhy.content.R14Service;
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

/** Frozen R14 one-to-one chat HTTP surface. */
@Validated
@RestController
public class R14Controller {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R14Service service;
    private final Clock clock;

    public R14Controller(R14Service service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/conversations")
    public ApiResponse<ConversationPage> chatGetConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.conversations(
                principal.userId(), page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/api/v1/conversations/direct")
    public ApiResponse<Conversation> chatPostConversationsDirect(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DirectConversationRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.direct(principal.userId(), body, key));
    }

    @GetMapping("/api/v1/conversations/{id}/messages")
    public ApiResponse<MessagePage> chatGetConversationsByIdMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.messages(
                principal.userId(), id, page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/api/v1/conversations/{id}/messages")
    public ApiResponse<ChatMessage> chatPostConversationsByIdMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody SendMessageRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.send(principal.userId(), id, body, key));
    }

    @PostMapping("/api/v1/conversations/{id}/read")
    public ApiResponse<CommandResult> chatPostConversationsByIdRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ReadRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.read(principal.userId(), id, body, key));
    }

    @DeleteMapping("/api/v1/conversations/{id}")
    public ApiResponse<CommandResult> chatDeleteConversationsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.hide(principal.userId(), id, key));
    }

    @PostMapping("/api/v1/users/{id}/block")
    public ApiResponse<CommandResult> chatPostUsersByIdBlock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody BlockRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.block(principal.userId(), id, body, key));
    }

    @DeleteMapping("/api/v1/users/{id}/block")
    public ApiResponse<CommandResult> chatDeleteUsersByIdBlock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.unblock(principal.userId(), id, key));
    }

    @PostMapping("/api/v1/conversations/{id}/report")
    public ApiResponse<CommandResult> chatPostConversationsByIdReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ReportRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.report(principal.userId(), id, body, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
