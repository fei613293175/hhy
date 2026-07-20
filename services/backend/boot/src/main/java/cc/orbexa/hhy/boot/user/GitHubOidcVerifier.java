package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.CiAutomationProperties;
import cc.orbexa.hhy.access.user.CiAutomationService.VerifiedWorkflow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hhy.ci-automation", name = "enabled", havingValue = "true")
public class GitHubOidcVerifier {
    private static final String ISSUER = "https://token.actions.githubusercontent.com";
    private final CiAutomationProperties properties;
    private final JwtDecoder decoder;

    public GitHubOidcVerifier(CiAutomationProperties properties) {
        this.properties = properties;
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withIssuerLocation(ISSUER).build();
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.oidcAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), audience));
        this.decoder = nimbus;
    }

    public VerifiedWorkflow verify(String bearerToken, String commit, String runId) {
        Jwt jwt = decoder.decode(bearerToken);
        String repository = jwt.getClaimAsString("repository");
        String workflow = jwt.getClaimAsString("workflow_ref");
        String tokenCommit = jwt.getClaimAsString("sha");
        String tokenRunId = jwt.getClaimAsString("run_id");
        String ref = jwt.getClaimAsString("ref");
        if (!properties.githubRepository().equals(repository)
                || workflow == null || !workflow.contains(properties.githubWorkflow())
                || !commit.equals(tokenCommit) || !runId.equals(tokenRunId)
                || ref == null || !ref.startsWith("refs/heads/task/")) {
            throw new org.springframework.security.oauth2.jwt.JwtValidationException(
                    "GitHub workflow identity does not match CI automation policy", java.util.List.of());
        }
        return new VerifiedWorkflow(repository, workflow, commit, runId);
    }
}
