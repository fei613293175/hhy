package cc.orbexa.hhy.access.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Binds a mobile Bearer token to the current user and session database rows. */
@Component
public final class UserBearerAuthenticationFilter extends OncePerRequestFilter {
    private final UserTokenService tokens;
    private final UserAuthStore store;
    private final Clock clock;

    public UserBearerAuthenticationFilter(UserTokenService tokens, UserAuthStore store, Clock clock) {
        this.tokens = tokens;
        this.store = store;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserTokenService.AccessClaims claims = tokens.parseAccess(authorization.substring(7));
                UserPrincipal principal = store.authenticate(claims, Instant.now(clock)).orElse(null);
                if (principal != null) {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                }
            } catch (UserTokenService.InvalidAccessTokenException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
