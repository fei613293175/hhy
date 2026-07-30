package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.DomainConfigService;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.LayerStatus;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.DomainResource;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1/domains/{code}")
public class R03DomainCommandController {
    private final DomainConfigService domains;
    private final Clock clock;

    public R03DomainCommandController(DomainConfigService domains, Clock clock) {
        this.domains = domains;
        this.clock = clock;
    }

    @PutMapping
    @PreAuthorize("hasAuthority('domain.write')")
    public ApiResponse<DomainResource> update(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$") String code,
            @Valid @RequestBody UpdateBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, resource(domains.update(
                code, body.hostname(), body.certificateMode(), body.expectedVersion(),
                key, principal.adminId())));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('domain.verify')")
    public ApiResponse<DomainResource> verify(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$") String code,
            @Valid @RequestBody VerifyBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, resource(domains.verify(
                code, Boolean.TRUE.equals(body.force()), key, principal.adminId())));
    }

    private static DomainResource resource(DomainConfigService.DomainAggregate value) {
        var verification = value.lastVerification();
        return new DomainResource(
                value.config().code(), value.config().environment().name(),
                value.config().hostname(),
                verification == null ? "PENDING" : layer(verification.dns().status()),
                verification == null ? "PENDING" : layer(verification.tls().status()),
                verification == null ? "PENDING" : verification.certificateStatus(),
                verification == null ? value.verifiedAt() : verification.attemptedAt(),
                value.version());
    }

    private static String layer(LayerStatus value) {
        return value == LayerStatus.NOT_RUN ? "PENDING" : value.name();
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(requestId == null ? "missing" : requestId.toString(),
                data, Instant.now(clock));
    }

    public record UpdateBody(
            @NotBlank @Size(max = 2000) String hostname,
            @Size(max = 2000) String certificateMode,
            @PositiveOrZero long expectedVersion) { }

    public record VerifyBody(Boolean force) { }
}
