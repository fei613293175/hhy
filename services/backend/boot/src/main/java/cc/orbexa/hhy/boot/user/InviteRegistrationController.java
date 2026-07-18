package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.InviteRegistrationConfigPageResource;
import cc.orbexa.hhy.access.user.UserAuthService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/public-api/v1/invite")
public class InviteRegistrationController {
    private final UserAuthService service;
    private final Clock clock;

    public InviteRegistrationController(UserAuthService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/{code}/registration-config")
    public ApiResponse<InviteRegistrationConfigPageResource> registrationConfig(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String code,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String sort,
            HttpServletRequest request) {
        if (cursor != null && page != 1) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "游标与页码不能同时使用", 400, false);
        }
        return ApiResponse.success(
                requestId(request), service.inviteRegistrationConfig(code, page, pageSize),
                Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
