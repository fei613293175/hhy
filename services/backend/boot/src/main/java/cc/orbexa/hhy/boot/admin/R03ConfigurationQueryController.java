package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.CertificateResource;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.DomainResource;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.Page;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.ProviderConfigResource;
import cc.orbexa.hhy.access.admin.R03ConfigurationQueryStore;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
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

/** Frozen R03 read endpoints. Secret material and SecretRef paths never leave this boundary. */
@Validated
@RestController
@RequestMapping("/admin-api/v1")
public class R03ConfigurationQueryController {
    private final R03ConfigurationQueryStore queries;
    private final Clock clock;

    public R03ConfigurationQueryController(R03ConfigurationQueryStore queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @GetMapping("/provider-configs")
    @PreAuthorize("hasAuthority('provider.config.read')")
    public ApiResponse<Page<ProviderConfigResource>> providerConfigs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, queries.providerConfigs(page, pageSize, status, keyword, sort));
    }

    @GetMapping("/provider-configs/{provider}")
    @PreAuthorize("hasAuthority('provider.config.read')")
    public ApiResponse<ProviderConfigResource> providerConfig(
            @PathVariable @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{0,63}$") String provider,
            HttpServletRequest request) {
        ProviderConfigResource resource = queries.providerConfig(provider)
                .orElseThrow(() -> new BusinessException(
                        "COMMON-404-NOT_FOUND", "供应商配置不存在", 404, false));
        return success(request, resource);
    }

    @GetMapping("/provider-certificates")
    @PreAuthorize("hasAuthority('provider.certificate.read')")
    public ApiResponse<Page<CertificateResource>> certificates(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, queries.certificates(page, pageSize, status, keyword, sort));
    }

    @GetMapping("/domains")
    @PreAuthorize("hasAuthority('domain.read')")
    public ApiResponse<Page<DomainResource>> domains(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, queries.domains(page, pageSize, status, keyword, sort));
    }

    @GetMapping("/domains/dns-actions")
    @PreAuthorize("hasAuthority('domain.read')")
    public ApiResponse<Page<DomainResource>> dnsActions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, queries.dnsActions(page, pageSize, status, keyword, sort));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
