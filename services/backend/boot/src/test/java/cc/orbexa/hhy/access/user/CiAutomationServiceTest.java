package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.CiAutomationService.VerifiedWorkflow;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserSessionResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CiAutomationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    void enabledAutomationFailsClosedOutsideIsolatedStaging() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("staging", "production");
        assertThrows(IllegalStateException.class, () -> service(properties(), production,
                mock(CiAutomationStore.class), mock(UserAuthStore.class), mock(UserAuthService.class)));
    }

    @Test
    void enabledAutomationRejectsBootstrapTtlAboveTenMinutes() {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        CiAutomationProperties unsafe = new CiAutomationProperties(true, "13800000006", "fei613293175/hhy",
                ".github/workflows/android-candidate-request.yml", "hhy-android-e2e",
                Duration.ofMinutes(11), Duration.ofMinutes(15));
        assertThrows(IllegalStateException.class, () -> service(unsafe, staging,
                mock(CiAutomationStore.class), mock(UserAuthStore.class), mock(UserAuthService.class)));
    }

    @Test
    void oidcIdentityCreatesOneTimeCodeAndRedeemsShortSession() throws Exception {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        CiAutomationStore store = mock(CiAutomationStore.class);
        CiAutomationFixtureStore fixtures = mock(CiAutomationFixtureStore.class);
        UserAuthStore users = mock(UserAuthStore.class);
        UserAuthService authentication = mock(UserAuthService.class);
        when(users.findUser("13800000006")).thenReturn(Optional.of(new UserAuthStore.UserRow(7L, "ACTIVE")));
        CiAutomationService service = service(properties(), staging, store, fixtures, users, authentication);

        VerifiedWorkflow identity = new VerifiedWorkflow(
                "fei613293175/hhy", ".github/workflows/android-candidate-request.yml@refs/heads/task/R06",
                "a".repeat(40), "12345");
        CiAutomationService.BootstrapCode bootstrap = service.issue(identity, "R12");
        String codeHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bootstrap.code().getBytes(StandardCharsets.UTF_8)));
        assertEquals(NOW.plus(Duration.ofMinutes(10)), bootstrap.expiresAt());
        verify(store).create(eq(7L), eq(codeHash), eq(identity.repository()), eq(identity.workflow()),
                eq(identity.commit()), eq(identity.runId()), eq(bootstrap.expiresAt()));
        verify(fixtures).prepareR12SubmitTarget(7L);
        when(store.findForUpdate(codeHash)).thenReturn(Optional.of(new CiAutomationStore.BootstrapRow(
                9L, 7L, identity.commit(), identity.runId(), bootstrap.expiresAt())));
        when(store.consume(9L, NOW)).thenReturn(true);
        UserSessionResource expected = new UserSessionResource(
                "access", "refresh", NOW.plusSeconds(900), "7", "11", null, java.util.List.of());
        when(authentication.automationSession(eq(7L), eq("13800000006"), any(), eq("127.0.0.1"),
                eq(Duration.ofMinutes(15)))).thenReturn(expected);

        assertEquals(expected, service.redeem(bootstrap.code(), identity.commit(), identity.runId(),
                Map.of("platform", "ANDROID"), "127.0.0.1"));
        verify(store).consume(9L, NOW);
    }

    @Test
    void nonR12BootstrapDoesNotPrepareR12Fixture() {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        CiAutomationStore store = mock(CiAutomationStore.class);
        CiAutomationFixtureStore fixtures = mock(CiAutomationFixtureStore.class);
        UserAuthStore users = mock(UserAuthStore.class);
        when(users.findUser("13800000006")).thenReturn(Optional.of(new UserAuthStore.UserRow(7L, "ACTIVE")));
        CiAutomationService service = service(properties(), staging, store, fixtures, users,
                mock(UserAuthService.class));

        service.issue(identity(), "R11");

        verify(fixtures, never()).prepareR12SubmitTarget(anyLong());
        verify(fixtures, never()).prepareR14ChatTarget(anyLong(), any(), any(), any());
        verify(store).create(eq(7L), any(), any(), any(), any(), any(), any());
    }

    @Test
    void r14BootstrapPreparesCommitAndRunBoundChatFixtureBeforeIssuingCode() {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        CiAutomationStore store = mock(CiAutomationStore.class);
        CiAutomationFixtureStore fixtures = mock(CiAutomationFixtureStore.class);
        UserAuthStore users = mock(UserAuthStore.class);
        when(users.findUser("13800000006"))
                .thenReturn(Optional.of(new UserAuthStore.UserRow(7L, "ACTIVE")));
        CiAutomationService service = service(properties(), staging, store, fixtures, users,
                mock(UserAuthService.class));
        VerifiedWorkflow identity = identity();

        service.issue(identity, "R14");

        verify(fixtures).prepareR14ChatTarget(
                7L, "R14", identity.commit(), identity.runId());
        verify(fixtures, never()).prepareR12SubmitTarget(anyLong());
        verify(store).create(eq(7L), any(), any(), any(), eq(identity.commit()), eq(identity.runId()), any());
    }

    @Test
    void invalidReleaseFailsBeforeUserOrFixtureMutation() {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        CiAutomationStore store = mock(CiAutomationStore.class);
        CiAutomationFixtureStore fixtures = mock(CiAutomationFixtureStore.class);
        UserAuthStore users = mock(UserAuthStore.class);
        CiAutomationService service = service(properties(), staging, store, fixtures, users,
                mock(UserAuthService.class));

        assertThrows(IllegalArgumentException.class, () -> service.issue(identity(), "R33"));

        verifyNoInteractions(users, fixtures, store);
    }

    @Test
    void fixtureFailurePreventsBootstrapCodeCreation() {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        CiAutomationStore store = mock(CiAutomationStore.class);
        CiAutomationFixtureStore fixtures = mock(CiAutomationFixtureStore.class);
        UserAuthStore users = mock(UserAuthStore.class);
        when(users.findUser("13800000006")).thenReturn(Optional.of(new UserAuthStore.UserRow(7L, "ACTIVE")));
        when(fixtures.prepareR12SubmitTarget(7L)).thenThrow(new IllegalStateException("incomplete fixture"));
        CiAutomationService service = service(properties(), staging, store, fixtures, users,
                mock(UserAuthService.class));

        assertThrows(IllegalStateException.class, () -> service.issue(identity(), "R12"));

        verifyNoInteractions(store);
    }

    private static CiAutomationService service(CiAutomationProperties properties, MockEnvironment environment,
                                               CiAutomationStore store, UserAuthStore users,
                                               UserAuthService authentication) {
        return service(properties, environment, store, mock(CiAutomationFixtureStore.class), users, authentication);
    }

    private static CiAutomationService service(CiAutomationProperties properties, MockEnvironment environment,
                                               CiAutomationStore store, CiAutomationFixtureStore fixtures,
                                               UserAuthStore users, UserAuthService authentication) {
        return new CiAutomationService(properties, store, fixtures, users, authentication,
                Clock.fixed(NOW, ZoneOffset.UTC), environment);
    }

    private static VerifiedWorkflow identity() {
        return new VerifiedWorkflow("fei613293175/hhy",
                ".github/workflows/android-candidate-request.yml@refs/heads/task/R12",
                "a".repeat(40), "12345");
    }

    private static CiAutomationProperties properties() {
        return new CiAutomationProperties(true, "13800000006", "fei613293175/hhy",
                ".github/workflows/android-candidate-request.yml", "hhy-android-e2e",
                Duration.ofMinutes(10), Duration.ofMinutes(15));
    }
}
