package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class IdentitySandboxIsolationTest {
    @Test
    void sandboxCanOnlyStartInStagingAndNeverProduction() {
        IdentitySandboxProperties properties = properties(true);
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        IdentitySandboxStartupGuard.verify(properties, staging);

        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("production");
        assertThrows(IllegalStateException.class,
                () -> IdentitySandboxStartupGuard.verify(properties, production));

        MockEnvironment mixed = new MockEnvironment();
        mixed.setActiveProfiles("staging", "prod");
        assertThrows(IllegalStateException.class,
                () -> IdentitySandboxStartupGuard.verify(properties, mixed));
    }

    @Test
    void createsOnlyAllowedHttpsHandoffAndKeepsPendingUntilPublicCompletion() {
        R05IdentitySandboxClient client = new R05IdentitySandboxClient(properties(true));
        var command = new IdentityProviderClient.ProviderLivenessCommand(
                IdentitySandboxProperties.PROVIDER, 9, 3,
                "ff738a6c-71a1-4647-ab50-5d6f3cb5f544",
                URI.create("https://h5.orbexa.cc/identity/callback"), "key-1234567890123456");

        var token = client.createLiveness(command);
        assertTrue(token.providerOrderNo().startsWith("stg-"));
        assertEquals("api.orbexa.cc", token.livenessUrl().getHost());
        assertTrue(token.livenessUrl().getRawQuery().contains("returnUrl="));
        assertEquals(IdentityProviderClient.LivenessDecision.PENDING,
                client.queryLiveness(new IdentityProviderClient.ProviderLivenessQuery(
                        IdentitySandboxProperties.PROVIDER, token.providerOrderNo())).decision());

        var unsafe = new IdentityProviderClient.ProviderLivenessCommand(
                IdentitySandboxProperties.PROVIDER, 9, 3, command.state(),
                URI.create("https://evil.example/identity/callback"), command.idempotencyKey());
        assertThrows(IllegalArgumentException.class, () -> client.createLiveness(unsafe));
    }

    @Test
    void routerNeverSendsSandboxProviderToProductionAdapter() {
        IdentityProviderClient production = mock(IdentityProviderClient.class);
        IdentityProviderClient sandbox = mock(IdentityProviderClient.class);
        R05IdentityProviderRouter router = new R05IdentityProviderRouter(production, sandbox);
        var query = new IdentityProviderClient.ProviderLivenessQuery(
                IdentitySandboxProperties.PROVIDER, "stg-order");
        var expected = new IdentityProviderClient.ProviderLivenessOutcome(
                IdentityProviderClient.LivenessDecision.PENDING, "stg-order", null, new byte[0]);
        when(sandbox.queryLiveness(query)).thenReturn(expected);

        assertSame(expected, router.queryLiveness(query));
        verify(sandbox).queryLiveness(query);
    }

    @Test
    void completionIsServerSideTerminalAndTheStateCannotBeReplayed() {
        String state = "ff738a6c-71a1-4647-ab50-5d6f3cb5f544";
        Instant now = Instant.parse("2026-07-20T05:40:00Z");
        var session = new IdentityService.Session(
                9, 3, state, "LIVENESS_PENDING", IdentitySandboxProperties.PROVIDER,
                URI.create("https://api.orbexa.cc/public-api/v1/identity/sandbox/liveness"),
                null, now.plus(Duration.ofMinutes(10)), 2, 1);
        var context = new IdentityProviderResultCoordinator.ProcessingContext(
                session, "stg-order", "测试用户", "110101199001010015");
        class FakeStore implements IdentitySandboxService.Store {
            Optional<IdentityProviderResultCoordinator.ProcessingContext> current = Optional.of(context);
            IdentityProviderResultCoordinator.ProviderCompletion completion;

            @Override
            public Optional<IdentityProviderResultCoordinator.ProcessingContext> sandboxContextByState(
                    String candidate) {
                return state.equals(candidate) ? current : Optional.empty();
            }

            @Override
            public IdentityService.Session complete(
                    IdentityProviderResultCoordinator.ProcessingContext processing,
                    IdentityProviderResultCoordinator.ProviderCompletion result, Instant completedAt) {
                completion = result;
                current = Optional.empty();
                return new IdentityService.Session(
                        session.id(), session.userId(), state, result.status(), session.provider(),
                        session.livenessUrl(), result.failureCode(), session.expiresAt(), 3, 1);
            }
        }
        FakeStore store = new FakeStore();
        IdentitySandboxService service = new IdentitySandboxService(
                store, properties(true), Clock.fixed(now, ZoneOffset.UTC));

        var completed = service.complete(
                state, "https://h5.orbexa.cc/identity/callback", "PASS");

        assertEquals("VERIFIED", completed.status());
        assertEquals("VERIFIED", store.completion.status());
        assertThrows(RuntimeException.class, () -> service.complete(
                state, "https://h5.orbexa.cc/identity/callback", "PASS"));
    }

    private static IdentitySandboxProperties properties(boolean enabled) {
        return new IdentitySandboxProperties(enabled,
                URI.create("https://api.orbexa.cc/public-api/v1/identity/sandbox"),
                "h5.orbexa.cc", 5, Duration.ofMinutes(10));
    }
}
