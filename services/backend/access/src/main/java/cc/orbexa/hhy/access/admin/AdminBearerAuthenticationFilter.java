package cc.orbexa.hhy.access.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class AdminBearerAuthenticationFilter extends OncePerRequestFilter {
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
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AdminTokenService.TokenClaims claims = tokens.parseAccess(authorization.substring(7));
                repository.authenticate(claims, Instant.now(clock)).ifPresent(principal -> {
                    var authorities = principal.permissionCodes().stream()
                            .map(SimpleGrantedAuthority::new).toList();
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (AdminTokenService.InvalidAdminTokenException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
