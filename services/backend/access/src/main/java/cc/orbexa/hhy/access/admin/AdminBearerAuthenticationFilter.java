package cc.orbexa.hhy.access.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class AdminBearerAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> REPLAY_ONLY_POST_PATHS = Set.of(
            "/admin-api/v1/auth/logout",
            "/admin-api/v1/me/security/password/change",
            "/admin-api/v1/me/security/mfa/enroll",
            "/admin-api/v1/me/security/mfa/confirm",
            "/admin-api/v1/me/security/mfa/disable");
    private final AdminTokenService tokens;
    private final AdminSecurityStore repository;
    private final Clock clock;

    public AdminBearerAuthenticationFilter(
            AdminTokenService tokens, AdminSecurityStore repository, Clock clock) {
        this.tokens = tokens;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin-api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AdminTokenService.TokenClaims claims = tokens.parseAccess(authorization.substring(7));
                Instant now = Instant.now(clock);
                AdminPrincipal principal = repository.authenticate(claims, now).orElse(null);
                if (principal == null && replayOnlyRequest(request)) {
                    principal = repository.authenticateRevokedReplay(claims, now).orElse(null);
                }
                if (principal != null) {
                    var authorities = principal.permissionCodes().stream()
                            .map(SimpleGrantedAuthority::new).toList();
                    if (principal.replayOnly()) {
                        authorities = java.util.List.of(
                                new SimpleGrantedAuthority(AdminPrincipal.IDEMPOTENCY_REPLAY_MARKER));
                    }
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (AdminTokenService.InvalidAdminTokenException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    static boolean replayOnlyRequest(HttpServletRequest request) {
        String key = request.getHeader("X-Idempotency-Key");
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return "POST".equals(request.getMethod())
                && REPLAY_ONLY_POST_PATHS.contains(path)
                && key != null && !key.isBlank();
    }
}
