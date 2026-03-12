package org.techbd.ingest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,  "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET,  "/").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingest/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/hold").permitAll()
                .requestMatchers("/ws").permitAll()
                // XDS.b Repository endpoint
                .requestMatchers(HttpMethod.POST, "/xds/XDSbRepositoryWS").permitAll()
                // Feature toggle endpoints
                .requestMatchers(HttpMethod.GET,  "/api/features/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/features/**").permitAll()
                // Hard-deny actuator root
                .requestMatchers("/actuator/health").denyAll()
                // Return 404 for everything else instead of 403
                .anyRequest().denyAll()
            )
            // Replace the default 403 with 404 for unmatched / denied routes
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpStatus.NOT_FOUND.value(), "Not Found"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpStatus.NOT_FOUND.value(), "Not Found"))
            );

        return http.build();
    }
}