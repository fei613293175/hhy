package cc.orbexa.hhy.access.user;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hhy.ci-automation")
public record CiAutomationProperties(
        boolean enabled,
        String userPhone,
        String githubRepository,
        String githubWorkflow,
        String oidcAudience,
        Duration bootstrapTtl,
        Duration sessionTtl) { }
