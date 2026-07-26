package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.CiAutomationService;
import cc.orbexa.hhy.access.user.CiAutomationService.BootstrapCode;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserSessionResource;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal-ci/v1/android")
@ConditionalOnProperty(prefix = "hhy.ci-automation", name = "enabled", havingValue = "true")
public class CiAutomationController {
    private final GitHubOidcVerifier verifier;
    private final CiAutomationService service;
    private final Clock clock;

    public CiAutomationController(GitHubOidcVerifier verifier, CiAutomationService service, Clock clock) {
        this.verifier = verifier;
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/bootstrap")
    public ApiResponse<BootstrapCode> bootstrap(
            @RequestHeader("Authorization") @NotBlank @Size(max = 10000) String authorization,
            @Valid @RequestBody BootstrapRequest body,
            HttpServletRequest request) {
        if (!authorization.startsWith("Bearer ")) throw new IllegalArgumentException("Bearer token required");
        BootstrapCode code = service.issue(verifier.verify(
                authorization.substring("Bearer ".length()), body.commit(), body.runId()), body.release());
        return success(request, code);
    }

    @PostMapping("/session")
    public ApiResponse<UserSessionResource> session(
            @Valid @RequestBody SessionRequest body,
            HttpServletRequest request) {
        return success(request, service.redeem(body.code(), body.commit(), body.runId(),
                body.device(), clientIp(request)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(requestId == null ? "missing" : requestId.toString(), data, Instant.now(clock));
    }

    private static String clientIp(HttpServletRequest request) {
        String value = request.getRemoteAddr();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public record BootstrapRequest(
            @NotBlank @Pattern(regexp = "^[0-9a-f]{40}$") String commit,
            @NotBlank @Pattern(regexp = "^[0-9]+$") @Size(max = 64) String runId,
            @NotBlank @Pattern(regexp = "^R(?:0[1-9]|[12][0-9]|3[0-2])$") String release) { }

    public record SessionRequest(
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{40}$") String commit,
            @NotBlank @Pattern(regexp = "^[0-9]+$") @Size(max = 64) String runId,
            Map<String, Object> device) { }
}
