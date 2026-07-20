package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.UserSessionResource;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CiAutomationService {
    private final CiAutomationProperties properties;
    private final CiAutomationStore store;
    private final UserAuthStore users;
    private final UserAuthService authentication;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CiAutomationService(CiAutomationProperties properties, CiAutomationStore store,
                               UserAuthStore users, UserAuthService authentication,
                               Clock clock, Environment environment) {
        this.properties = properties;
        this.store = store;
        this.users = users;
        this.authentication = authentication;
        this.clock = clock;
        if (properties.enabled()) {
            boolean staging = environment.acceptsProfiles(Profiles.of("staging"));
            boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
            if (!staging || production) {
                throw new IllegalStateException("CI automation can only be enabled in an isolated staging profile");
            }
            requireConfigured(properties.userPhone(), "CI automation user phone");
            requireConfigured(properties.githubRepository(), "CI GitHub repository");
            requireConfigured(properties.githubWorkflow(), "CI GitHub workflow");
            requireConfigured(properties.oidcAudience(), "CI OIDC audience");
            requireDuration(properties.bootstrapTtl(), java.time.Duration.ofMinutes(10), "CI bootstrap TTL");
            requireDuration(properties.sessionTtl(), java.time.Duration.ofMinutes(30), "CI session TTL");
        }
    }

    @Transactional
    public BootstrapCode issue(VerifiedWorkflow identity) {
        requireEnabled();
        UserAuthStore.UserRow user = users.findUser(properties.userPhone())
                .orElseThrow(() -> new IllegalStateException("Configured CI automation user does not exist"));
        if (!"ACTIVE".equals(user.status())) {
            throw new IllegalStateException("Configured CI automation user is not eligible for a session");
        }
        byte[] material = new byte[32];
        random.nextBytes(material);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(material);
        Instant expiresAt = Instant.now(clock).plus(properties.bootstrapTtl());
        store.create(user.id(), sha256(code), identity.repository(), identity.workflow(),
                identity.commit(), identity.runId(), expiresAt);
        return new BootstrapCode(code, expiresAt, identity.commit(), identity.runId());
    }

    @Transactional
    public UserSessionResource redeem(String code, String commit, String runId,
                                      Map<String, Object> device, String clientIp) {
        requireEnabled();
        Instant now = Instant.now(clock);
        CiAutomationStore.BootstrapRow row = store.findForUpdate(sha256(code))
                .orElseThrow(() -> new IllegalArgumentException("CI bootstrap code is invalid"));
        if (!row.expiresAt().isAfter(now)
                || !MessageDigest.isEqual(row.commit().getBytes(), commit.getBytes())
                || !MessageDigest.isEqual(row.runId().getBytes(), runId.getBytes())) {
            throw new IllegalArgumentException("CI bootstrap code is invalid or expired");
        }
        if (!store.consume(row.id(), now)) {
            throw new IllegalArgumentException("CI bootstrap code was already consumed");
        }
        return authentication.automationSession(row.userId(), properties.userPhone(), device,
                clientIp, properties.sessionTtl());
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new IllegalStateException("CI automation is disabled");
    }

    private static void requireConfigured(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is required");
    }

    private static void requireDuration(java.time.Duration value, java.time.Duration maximum, String label) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(label + " must be positive and no longer than " + maximum);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record VerifiedWorkflow(String repository, String workflow, String commit, String runId) { }
    public record BootstrapCode(String code, Instant expiresAt, String commit, String runId) { }
}
