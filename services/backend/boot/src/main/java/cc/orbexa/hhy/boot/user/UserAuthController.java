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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
