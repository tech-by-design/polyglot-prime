package org.techbd.ingest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for defining HTTP security rules.
 * <p>
 * This configuration sets up a {@link SecurityFilterChain} bean to specify
 * access rules for various endpoints and disables CSRF for stateless clients.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures security settings for the application.
     * <p>
     * This method sets up the security filter chain, disabling CSRF protection
     * and defining access rules for various endpoints.
     * </p>
     *
     * @param http the HttpSecurity object to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // CSRF off for non-browser clients
                .authorizeHttpRequests(auth -> auth
                        // Publicly permitted endpoints

                        .requestMatchers(HttpMethod.GET, "/actuator/health/mllp").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/ingest").permitAll()
                        .requestMatchers("/ws").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/features/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/features/*/enable").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/features/*/disable").permitAll()

                        // Deny access to all other endpoints
                        .requestMatchers("/actuator/health").denyAll()
                        // .requestMatchers("/togglz-console/**").denyAll()
                        .anyRequest().denyAll());

        return http.build();
    }
}