package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.content.ContentContracts.BanRequest;
import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.ContentContracts.DictionaryRequest;
import cc.orbexa.hhy.content.ContentContracts.OfficialMarkRequest;
import cc.orbexa.hhy.content.ContentContracts.RecommendRequest;
import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
import cc.orbexa.hhy.content.ContentService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1")
public class AdminContentController {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final ContentService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public AdminContentController(ContentService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/contents")
    @PreAuthorize("hasAuthority('content.read')")
    public ApiResponse<ContentPage> contents(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) @Size(max = 64) String categoryCode,
            @RequestParam(required = false) @Size(max = 32) String regionCode,
            HttpServletRequest request) {
        return success(request, service.list(
                page, pageSize, cursor, status, keyword, sort, contentType, categoryCode, regionCode));
    }

    @GetMapping("/contents/{id}")
    @PreAuthorize("hasAuthority('content.read')")
    public ApiResponse<ContentResource> content(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id, HttpServletRequest request) {
        return success(request, service.detail(id));
    }

    @PostMapping("/contents/{id}/online")
    @PreAuthorize("hasAuthority('content.manage')")
    public ApiResponse<CommandResult> online(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody StatusRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.online(
                principal.adminId(), id, body, key, requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/contents/{id}/offline")
    @PreAuthorize("hasAuthority('content.manage')")
    public ApiResponse<CommandResult> offline(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody StatusRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.offline(
                principal.adminId(), id, body, key, requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/contents/{id}/ban")
    @PreAuthorize("hasAuthority('content.ban')")
    public ApiResponse<CommandResult> ban(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody BanRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.ban(
                principal.adminId(), id, body, key, requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/contents/{id}/recommend")
    @PreAuthorize("hasAuthority('content.recommend')")
    public ApiResponse<CommandResult> recommend(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody RecommendRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.recommend(
                principal.adminId(), id, body, key, requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/contents/{id}/official-mark")
    @PreAuthorize("hasAuthority('content.official')")
    public ApiResponse<CommandResult> officialMark(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody OfficialMarkRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.official(
                principal.adminId(), id, body, key, requestId(request), clientIpResolver.resolve(request)));
    }

    @GetMapping("/content-dictionaries")
    @PreAuthorize("hasAuthority('content.dict.read')")
    public ApiResponse<ContentPage> dictionaries(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.dictionaries(page, pageSize, cursor, status, keyword, sort));
    }

    @PutMapping("/content-dictionaries/{code}")
    @PreAuthorize("hasAuthority('content.dict.write')")
    public ApiResponse<CommandResult> putDictionary(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @NotBlank @Size(max = 128) String code,
            @Valid @RequestBody DictionaryRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.putDictionary(
                principal.adminId(), code, body, key, requestId(request), clientIpResolver.resolve(request)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
