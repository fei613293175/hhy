package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CreateRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.OrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.PatchRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.QuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.SubmitReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class R20RedPacketController {
    private static final String ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R20RedPacketService service;
    private final Clock clock;

    public R20RedPacketController(R20RedPacketService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/api/v1/red-packet-campaigns")
    public ApiResponse<CampaignPage> redPacketGetRedPacketCampaigns(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.userCampaigns(
                activeUserId(principal), page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/api/v1/me/red-packet-campaigns")
    public ApiResponse<CampaignPage> redPacketGetMeRedPacketCampaigns(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.userCampaigns(
                activeUserId(principal), page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/api/v1/red-packet-campaigns")
    public ApiResponse<CampaignResource> redPacketPostRedPacketCampaigns(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.create(activeUserId(principal), body, key, requestId(request)));
    }

    @GetMapping("/api/v1/red-packet-campaigns/{id}")
    public ApiResponse<CampaignResource> redPacketGetRedPacketCampaignsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            HttpServletRequest request) {
        return success(request, service.userCampaign(activeUserId(principal), id));
    }

    @PatchMapping("/api/v1/red-packet-campaigns/{id}")
    public ApiResponse<CampaignResource> redPacketPatchRedPacketCampaignsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody PatchRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.patch(
                activeUserId(principal), id, body, key, requestId(request)));
    }

    @PostMapping("/api/v1/red-packet-campaigns/{id}/submit-review")
    public ApiResponse<CampaignResource> redPacketPostRedPacketCampaignsByIdSubmitReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody SubmitReviewRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.submitReview(
                activeUserId(principal), id, body, key, requestId(request)));
    }

    @PostMapping("/api/v1/red-packet-campaigns/{id}/quote")
    public ApiResponse<CommandResultResource> redPacketPostRedPacketCampaignsByIdQuote(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody QuoteRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.quote(
                activeUserId(principal), id, body, key, requestId(request)));
    }

    @PostMapping("/api/v1/red-packet-campaigns/{id}/orders")
    public ApiResponse<CommandResultResource> redPacketPostRedPacketCampaignsByIdOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody OrderRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.order(
                activeUserId(principal), id, body, key, requestId(request)));
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
