package org.techbd.fhir.config;

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

                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/Bundle/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/metadata").permitAll()
                        .anyRequest().denyAll());

        return http.build();
    }
}