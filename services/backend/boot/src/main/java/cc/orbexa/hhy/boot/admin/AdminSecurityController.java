package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.AdminSecurityService;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSelfSecurityResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSessionResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.CommandResultResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LoginRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LogoutRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaConfirmRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaDisableRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaEnrollmentResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaVerifyRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.PasswordChangeRequest;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1")
public class AdminSecurityController {
    private final AdminSecurityService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public AdminSecurityController(
            AdminSecurityService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/auth/login")
    public ApiResponse<AdminSessionResource> login(
            @Valid @RequestBody LoginRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.login(
                body, key, requestId(request), clientIpResolver.resolve(request), device(request)));
    }

    @PostMapping("/auth/mfa/verify")
    public ApiResponse<AdminSessionResource> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest body,
            @RequestHeader("X-MFA-Ticket") @NotBlank @Size(max = 2000) String ticket,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.verifyMfa(
                body, ticket, key, requestId(request), clientIpResolver.resolve(request), device(request)));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<CommandResultResource> logout(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody(required = false) LogoutRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        Long expectedVersion = body == null ? null : body.expectedVersion();
        String reason = body == null ? null : body.reason();
        return success(request,
                service.logout(principal, expectedVersion, reason, key,
                        requestId(request), clientIpResolver.resolve(request)));
    }

    @GetMapping("/me/security")
    @PreAuthorize("hasAuthority('admin.self.read')")
    public ApiResponse<AdminSelfSecurityResource> overview(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request) {
        return success(request, service.overview(principal));
    }

    @PostMapping("/me/security/password/change")
    @PreAuthorize("(hasAuthority('admin.self.security') and !principal.replayOnly()) or "
            + "(hasAuthority('admin.idempotency.replay') and principal.replayOnly())")
    public ApiResponse<AdminSelfSecurityResource> changePassword(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.changePassword(
                principal, body, key, requestId(request),
                clientIpResolver.resolve(request), device(request)));
    }

    @PostMapping("/me/security/mfa/enroll")
    @PreAuthorize("(hasAuthority('admin.self.security') and !principal.replayOnly()) or "
            + "(hasAuthority('admin.idempotency.replay') and principal.replayOnly())")
    public ApiResponse<MfaEnrollmentResource> enrollMfa(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.enrollMfa(
                principal, key, requestId(request), clientIpResolver.resolve(request)));
    }

    @PostMapping("/me/security/mfa/confirm")
    @PreAuthorize("(hasAuthority('admin.self.security') and !principal.replayOnly()) or "
            + "(hasAuthority('admin.idempotency.replay') and principal.replayOnly())")
    public ApiResponse<AdminSelfSecurityResource> confirmMfa(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody MfaConfirmRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.confirmMfa(
                principal, body, key, requestId(request),
                clientIpResolver.resolve(request), device(request)));
    }

    @PostMapping("/me/security/mfa/disable")
    @PreAuthorize("(hasAuthority('admin.self.security') and !principal.replayOnly()) or "
            + "(hasAuthority('admin.idempotency.replay') and principal.replayOnly())")
    public ApiResponse<AdminSelfSecurityResource> disableMfa(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody MfaDisableRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.disableMfa(
                principal, body, key, requestId(request),
                clientIpResolver.resolve(request), device(request)));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String device(HttpServletRequest request) {
        String value = request.getHeader("X-Device-Fingerprint");
        return value == null || value.isBlank() ? null : value.substring(0, Math.min(128, value.length()));
    }
}
