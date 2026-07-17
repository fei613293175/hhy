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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    public AdminSecurityController(AdminSecurityService service) {
        this.service = service;
    }

    @PostMapping("/auth/login")
    public AdminSessionResource login(
            @Valid @RequestBody LoginRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.login(body, key, requestId(request), ip(request), device(request));
    }

    @PostMapping("/auth/mfa/verify")
    public AdminSessionResource verifyMfa(
            @Valid @RequestBody MfaVerifyRequest body,
            @RequestHeader("X-MFA-Ticket") @NotBlank @Size(max = 2000) String ticket,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.verifyMfa(body, ticket, key, requestId(request), ip(request), device(request));
    }

    @PostMapping("/auth/logout")
    public CommandResultResource logout(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody LogoutRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.logout(principal, body.expectedVersion(), body.reason(), key, requestId(request), ip(request));
    }

    @GetMapping("/me/security")
    @PreAuthorize("hasAuthority('admin.self.read')")
    public AdminSelfSecurityResource overview(@AuthenticationPrincipal AdminPrincipal principal) {
        return service.overview(principal);
    }

    @PostMapping("/me/security/password/change")
    @PreAuthorize("hasAuthority('admin.self.security')")
    public CommandResultResource changePassword(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.changePassword(principal, body, key, requestId(request), ip(request));
    }

    @PostMapping("/me/security/mfa/enroll")
    @PreAuthorize("hasAuthority('admin.self.security')")
    public MfaEnrollmentResource enrollMfa(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.enrollMfa(principal, key, requestId(request), ip(request));
    }

    @PostMapping("/me/security/mfa/confirm")
    @PreAuthorize("hasAuthority('admin.self.security')")
    public CommandResultResource confirmMfa(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody MfaConfirmRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.confirmMfa(principal, body, key, requestId(request), ip(request));
    }

    @PostMapping("/me/security/mfa/disable")
    @PreAuthorize("hasAuthority('admin.self.security')")
    public CommandResultResource disableMfa(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody MfaDisableRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return service.disableMfa(principal, body, key, requestId(request), ip(request));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }

    private static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].strip();
        }
        return request.getRemoteAddr();
    }

    private static String device(HttpServletRequest request) {
        String value = request.getHeader("X-Device-Fingerprint");
        return value == null || value.isBlank() ? null : value.substring(0, Math.min(128, value.length()));
    }
}
