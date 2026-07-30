package cc.orbexa.hhy.access.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Explicitly configured testing capability that can never activate in production profiles. */
@Component
class TestRegistrationInvitePolicy {
    private final Environment environment;
    private final String code;
    private final long inviterId;

    TestRegistrationInvitePolicy(
            Environment environment,
            @Value("${hhy.test-registration.universal-invite-code:}") String code,
            @Value("${hhy.test-registration.universal-inviter-id:0}") long inviterId) {
        this.environment = environment;
        this.code = code == null ? "" : code.trim();
        this.inviterId = inviterId;
    }

    Optional<Long> inviterIdFor(String candidate) {
        if (!nonProductionTestProfile() || code.isBlank() || inviterId <= 0 || candidate == null) {
            return Optional.empty();
        }
        boolean matches = MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                candidate.trim().getBytes(StandardCharsets.UTF_8));
        return matches ? Optional.of(inviterId) : Optional.empty();
    }

    private boolean nonProductionTestProfile() {
        boolean allowed = environment.acceptsProfiles(Profiles.of("dev", "test", "staging"));
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        return allowed && !production;
    }
}
