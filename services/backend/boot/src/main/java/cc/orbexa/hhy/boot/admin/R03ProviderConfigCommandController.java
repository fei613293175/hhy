package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.ProviderConfigResource;
import cc.orbexa.hhy.access.admin.R03ProviderConfigCommandService;
import cc.orbexa.hhy.shared.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Frozen R03 provider commands with durable first-response idempotency. */
@Validated
@RestController
@RequestMapping("/admin-api/v1/provider-configs/{provider}")
public class R03ProviderConfigCommandController {
    private final R03ProviderConfigCommandService commands;
    private final Clock clock;

    public R03ProviderConfigCommandController(
            R03ProviderConfigCommandService commands, Clock clock) {
        this.commands = commands;
        this.clock = clock;
    }

    @PostMapping("/versions")
    @PreAuthorize("hasAuthority('provider.config.write')")
    public ApiResponse<ProviderConfigResource> create(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{0,63}$") String provider,
            @Valid @RequestBody CreateBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.create(principal, provider,
                new R03ProviderConfigCommandService.CreateRequest(
                        body.environment(), body.values(), body.secretRefs(), body.remark()), key));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('provider.config.test')")
    public ApiResponse<ProviderConfigResource> test(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{0,63}$") String provider,
            @Valid @RequestBody TestBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.test(principal, provider,
                new R03ProviderConfigCommandService.TestRequest(
                        body.versionId(), body.testRecipient()), key));
    }

    @PostMapping("/activate")
    @PreAuthorize("hasAuthority('provider.config.activate')")
    public ApiResponse<ProviderConfigResource> activate(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{0,63}$") String provider,
            @Valid @RequestBody ActivateBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.activate(principal, provider,
                new R03ProviderConfigCommandService.ActivateRequest(
                        body.versionId(), body.approvalId(), body.expectedVersion()), key));
    }

    @PostMapping("/rollback")
    @PreAuthorize("hasAuthority('provider.config.activate')")
    public ApiResponse<ProviderConfigResource> rollback(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{0,63}$") String provider,
            @Valid @RequestBody RollbackBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.rollback(principal, provider,
                new R03ProviderConfigCommandService.RollbackRequest(
                        body.targetVersionId(), body.approvalId(), body.reason(),
                        body.expectedVersion()), key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }

    public record CreateBody(
            @NotBlank @Size(max = 2000) String environment,
            @NotNull JsonNode values,
            JsonNode secretRefs,
            @Size(max = 2000) String remark) { }

    public record TestBody(
            @NotBlank @Size(max = 64) String versionId,
            @Size(max = 2000) String testRecipient) { }

    public record ActivateBody(
            @NotBlank @Size(max = 64) String versionId,
            @NotBlank @Size(max = 64) String approvalId,
            @PositiveOrZero long expectedVersion) { }

    public record RollbackBody(
            @NotBlank @Size(max = 64) String targetVersionId,
            @NotBlank @Size(max = 64) String approvalId,
            @NotBlank @Size(max = 2000) String reason,
            @PositiveOrZero long expectedVersion) { }
}
