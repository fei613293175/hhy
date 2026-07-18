package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.*;
import cc.orbexa.hhy.access.user.UserAuthService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import cc.orbexa.hhy.access.user.UserPrincipal;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthController {
    private final UserAuthService service;
    private final Clock clock;

    public UserAuthController(UserAuthService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/security-challenges")
    public ApiResponse<ChallengeResource> createChallenge(
            @Valid @RequestBody SecurityChallengeRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.createChallenge(body, key));
    }

    @PostMapping("/password/login")
    public ApiResponse<UserSessionResource> passwordLogin(
            @Valid @RequestBody PasswordLoginRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.passwordLogin(body, key, clientIp(request)));
    }

    @PostMapping("/sms/send")
    public ApiResponse<CommandResultResource> sendSms(
            @Valid @RequestBody SmsSendRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.sendSms(body, key, clientIp(request)));
    }

    @PostMapping("/sms/login")
    public ApiResponse<UserSessionResource> smsLogin(
            @Valid @RequestBody SmsLoginRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.smsLogin(body, key, clientIp(request)));
    }

    @PostMapping("/invite-codes/validate")
    public ApiResponse<CommandResultResource> validateInvite(
            @Valid @RequestBody InviteCodeValidateRequest body,
            HttpServletRequest request) {
        return success(request, service.validateInvite(body));
    }

    @GetMapping("/registration-config")
    public ApiResponse<RegistrationConfigResource> registrationConfig(HttpServletRequest request) {
        return success(request, service.registrationConfig());
    }

    @PostMapping("/register")
    public ApiResponse<UserSessionResource> register(
            @Valid @RequestBody RegisterRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.register(body, key, clientIp(request)));
    }

    @PostMapping("/password/reset")
    public ApiResponse<CommandResultResource> resetPassword(
            @Valid @RequestBody PasswordResetRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.resetPassword(body, key));
    }

    @PostMapping("/refresh")
    public ApiResponse<UserSessionResource> refresh(
            @Valid @RequestBody RefreshRequest body,
            @RequestHeader("X-Refresh-Token") @NotBlank @Size(max = 2000) String refreshToken,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return ApiResponse.success(
                requestId(request), service.refresh(body, refreshToken, key), Instant.now(clock));
    }

    @GetMapping("/sessions")
    public ApiResponse<SessionPageResource> sessions(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1")
                    @jakarta.validation.constraints.Min(1) int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20")
                    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize,
            HttpServletRequest request) {
        return success(request, service.sessions(principal, page, pageSize));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<CommandResultResource> revokeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @NotBlank @Size(max = 64) String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.revokeSession(principal, id, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String clientIp(HttpServletRequest request) {
        String value = request.getRemoteAddr();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
