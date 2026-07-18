package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.AdminUserCommandService;
import cc.orbexa.hhy.access.admin.AdminUserContracts.RestrictionRequest;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserControlRequest;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserPageResource;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserResource;
import cc.orbexa.hhy.access.admin.AdminUserService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1/users")
public class AdminUserController {
    private final AdminUserService service;
    private final AdminUserCommandService commands;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public AdminUserController(
            AdminUserService service,
            AdminUserCommandService commands,
            Clock clock,
            AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.commands = commands;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.read')")
    public ApiResponse<UserPageResource> listUsers(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.listUsers(page, pageSize, status, keyword, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.read')")
    public ApiResponse<UserResource> getUser(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            HttpServletRequest request) {
        return success(request, service.getUser(id));
    }

    @PostMapping("/{id}/restrictions")
    @PreAuthorize("hasAuthority('user.restrict')")
    public ApiResponse<Object> restrict(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody RestrictionRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.restrict(
                principal, id, body, key, requestId(request), clientIpResolver.resolve(request)).payload());
    }

    @DeleteMapping("/{id}/restrictions/{type}")
    @PreAuthorize("hasAuthority('user.restrict')")
    public ApiResponse<Object> removeRestriction(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") String type,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.removeRestriction(
                principal, id, type, key, requestId(request), clientIpResolver.resolve(request)).payload());
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('user.freeze')")
    public ApiResponse<Object> freeze(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody UserControlRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.freeze(
                principal, id, body, key, requestId(request), clientIpResolver.resolve(request)).payload());
    }

    @PostMapping("/{id}/unfreeze")
    @PreAuthorize("hasAuthority('user.freeze')")
    public ApiResponse<Object> unfreeze(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody UserControlRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.unfreeze(
                principal, id, body, key, requestId(request), clientIpResolver.resolve(request)).payload());
    }

    @PostMapping("/{id}/force-logout")
    @PreAuthorize("hasAuthority('user.security')")
    public ApiResponse<Object> forceLogout(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody UserControlRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.forceLogout(
                principal, id, body, key, requestId(request), clientIpResolver.resolve(request)).payload());
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
