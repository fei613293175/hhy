package cc.orbexa.hhy.boot.security;

import cc.orbexa.hhy.access.admin.AdminBearerAuthenticationFilter;
import cc.orbexa.hhy.access.user.UserBearerAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods=false)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SecurityErrorResponseWriter errorWriter,
            AdminBearerAuthenticationFilter adminBearerAuthenticationFilter,
            UserBearerAuthenticationFilter userBearerAuthenticationFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/public-api/**",
                                "/ws",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/app/version-check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/registration-config").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/internal-ci/v1/android/bootstrap",
                                "/internal-ci/v1/android/session",
                                "/api/v1/auth/security-challenges",
                                "/api/v1/auth/password/login",
                                "/api/v1/auth/sms/send",
                                "/api/v1/auth/sms/login",
                                "/api/v1/auth/invite-codes/validate",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/admin-api/v1/auth/login",
                                "/admin-api/v1/auth/mfa/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me")
                                .hasAnyRole("USER", "RESTRICTED_USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets")
                                .hasAnyRole("USER", "RESTRICTED_USER")
                        .requestMatchers("/api/v1/**").hasRole("USER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.writeUnauthenticated(request, response))
                .accessDeniedHandler((request, response, exception) ->
                                errorWriter.writeForbidden(request, response)))
                .addFilterBefore(adminBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userBearerAuthenticationFilter, AdminBearerAuthenticationFilter.class)
                .build();
    }
}
