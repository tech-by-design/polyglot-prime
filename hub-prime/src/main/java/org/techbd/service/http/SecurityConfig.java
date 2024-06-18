package org.techbd.service.http;

import org.springframework.context.annotation.Bean;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.techbd.service.http.filter.GitHubUserAuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.github")
@Profile("!localopen")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow authentication for security
        // and turn off CSRF to allow POST methods
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login/**", "/oauth2/**", "/", "/Bundle", "/Bundle/**", "/metadata",
                        "/docs/api/interactive/swagger-ui/**", "/docs/api/interactive/**", "/docs/api/openapi/**")
                .permitAll()
                .anyRequest().authenticated())
                .oauth2Login(oauth2Login -> oauth2Login
                        .defaultSuccessUrl("/home", true))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterAfter(new GitHubUserAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ExceptionHandlingFilter(), GitHubUserAuthorizationFilter.class);
        // allow us to show our own content in IFRAMEs (e.g. Swagger, etc.)
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        // System.out.println(http.);
        return http.build();
    }

    @Bean
    public CorsFilter corsFilter() {
        // primarily setup for Swagger UI and OpenAPI integration
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*"); // Customize as needed
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    public class ExceptionHandlingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
                @NotNull FilterChain filterChain)
                throws ServletException, IOException {

            if (request.getRequestURI().startsWith("/Bundle")) {

                // Check if X-TechBD-Tenant-ID header is missing
                if (request.getHeader("X-TechBD-Tenant-ID") == null) {
                    handleError(response, HttpServletResponse.SC_NOT_FOUND, "X-TechBD-Tenant-ID header is missing");
                    return;
                }

                // Check if Content-Type is application/json
                if (!"application/json".equals(request.getContentType())) {
                    handleError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                            "Content-Type must be application/json");
                    return;
                }

            }
            filterChain.doFilter(request, response);
        }

        private void handleError(HttpServletResponse response, int statusCode, String message) throws IOException {
            response.setStatus(statusCode);
            response.getWriter().write(message);
        }
    }

}
