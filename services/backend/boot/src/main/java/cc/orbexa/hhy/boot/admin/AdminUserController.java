package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminUserContracts.UserPageResource;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserResource;
import cc.orbexa.hhy.access.admin.AdminUserService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1/users")
public class AdminUserController {
    private final AdminUserService service;
    private final Clock clock;

    public AdminUserController(AdminUserService service, Clock clock) {
        this.service = service;
        this.clock = clock;
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

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object value = request.getAttribute("requestId");
        String requestId = value == null ? "missing" : value.toString();
        return ApiResponse.success(requestId, data, Instant.now(clock));
    }
}
