package md.utm.gms.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security baseline configuration.
 *
 * <p>Current state: permits actuator health/info endpoints publicly and
 * requires authentication for all other routes. Stateless session policy —
 * JWT filter will be inserted here in the RBAC work-package.
 *
 * <p>Next steps:
 * <ul>
 *   <li>Add JWT authentication filter before {@code UsernamePasswordAuthenticationFilter}.
 *   <li>Apply method-level {@code @PreAuthorize} with role checks (ADMIN, OPERATOR, VIEWER).
 *   <li>Enable audit logging for 401 / 403 responses.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator health / info — publicly readable (liveness probes, dashboard)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // All other endpoints require an authenticated request
                // (JWT filter to be added in the RBAC work-package)
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
