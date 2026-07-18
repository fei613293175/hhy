package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.CertificateResource;
import cc.orbexa.hhy.access.admin.R03ProviderCertificateCommandService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1/provider-certificates")
public class R03ProviderCertificateCommandController {
    private final R03ProviderCertificateCommandService commands;
    private final Clock clock;

    public R03ProviderCertificateCommandController(
            R03ProviderCertificateCommandService commands, Clock clock) {
        this.commands = commands;
        this.clock = clock;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('provider.certificate.write')")
    public ApiResponse<CertificateResource> upload(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody UploadBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.upload(principal,
                new R03ProviderCertificateCommandService.UploadRequest(
                        body.provider(), body.certificateType(), body.alias(),
                        body.encryptedContentBase64(), body.passwordSecretRef(), body.expiresAt()), key));
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAuthority('provider.certificate.rotate')")
    public ApiResponse<CertificateResource> rotate(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = "^cert_[1-9][0-9]{0,18}$") String id,
            @Valid @RequestBody RotateBody body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, commands.rotate(principal, id,
                new R03ProviderCertificateCommandService.RotateRequest(
                        body.newCertificateId(), body.approvalId(), body.reason(),
                        body.expectedVersion()), key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object value = request.getAttribute("requestId");
        return ApiResponse.success(value == null ? "missing" : value.toString(),
                data, Instant.now(clock));
    }

    public record UploadBody(
            @NotBlank @Size(max = 2000) String provider,
            @NotBlank @Size(max = 2000) String certificateType,
            @NotBlank @Size(max = 2000) String alias,
            @NotBlank @Size(max = 2000) String encryptedContentBase64,
            @Size(max = 2000) String passwordSecretRef,
            Instant expiresAt) { }

    public record RotateBody(
            @NotBlank @Pattern(regexp = "^cert_[1-9][0-9]{0,18}$") String newCertificateId,
            @NotBlank @Size(max = 64) String approvalId,
            @NotBlank @Size(max = 2000) String reason,
            @PositiveOrZero long expectedVersion) { }
}
