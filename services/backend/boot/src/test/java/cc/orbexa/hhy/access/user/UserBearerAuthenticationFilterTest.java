package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class UserBearerAuthenticationFilterTest {
    private static final Instant NOW = Instant.parse("2026-07-18T05:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bindsSignedAccessTokenToCurrentDatabaseSession() throws Exception {
        UserAuthStore store = mock(UserAuthStore.class);
        UserTokenService tokens = new UserTokenService(new ObjectMapper(), properties(), clock);
        UserPrincipal expected = new UserPrincipal(17L, 23L, 4L, "access-jti-17");
        when(store.authenticate(any(), any())).thenReturn(Optional.of(expected));
        UserBearerAuthenticationFilter filter = new UserBearerAuthenticationFilter(tokens, store, clock);
        String bearer = tokens.issueAccess(17L, 23L, 4L, "access-jti-17", NOW.plusSeconds(300));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/sessions");
        request.addHeader("Authorization", "Bearer " + bearer);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(expected, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void deniesTokenWhenItsPersistedSessionIsNoLongerActive() throws Exception {
        UserAuthStore store = mock(UserAuthStore.class);
        UserTokenService tokens = new UserTokenService(new ObjectMapper(), properties(), clock);
        when(store.authenticate(any(), any())).thenReturn(Optional.empty());
        UserBearerAuthenticationFilter filter = new UserBearerAuthenticationFilter(tokens, store, clock);
        String bearer = tokens.issueAccess(17L, 23L, 4L, "access-jti-17", NOW.plusSeconds(300));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/sessions");
        request.addHeader("Authorization", "Bearer " + bearer);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotParseMobileTokensOnAdminRoutes() throws Exception {
        UserAuthStore store = mock(UserAuthStore.class);
        UserBearerAuthenticationFilter filter = new UserBearerAuthenticationFilter(
                new UserTokenService(new ObjectMapper(), properties(), clock), store, clock);

        filter.doFilter(new MockHttpServletRequest("GET", "/admin-api/v1/me/security"),
                new MockHttpServletResponse(), new MockFilterChain());

        verifyNoInteractions(store);
    }

    private static UserAuthProperties properties() {
        return new UserAuthProperties(
                "test-only-user-jwt-secret-at-least-32-characters",
                "test-only-user-token-hmac-secret-at-least-32-characters",
                "test-only-user-snapshot-root-secret-at-least-32-characters",
                "test/user", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24));
    }
}
