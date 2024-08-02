package org.techbd.service.http;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.github")
@Profile("!localopen")
public class SecurityConfig {

    @Autowired
    private GitHubUserAuthorizationFilter authzFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow authentication for security
        // and turn off CSRF to allow POST methods
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login/**", "/oauth2/**", "/", "/Bundle", "/Bundle/**", "/metadata",
                        "/api/expect/**",
                        "/docs/api/interactive/swagger-ui/**", "/support/**", "/docs/api/interactive/**",
                        "/docs/api/openapi/**",
                        "/error", "/error/**")
                .permitAll()
                .anyRequest().authenticated())
                .oauth2Login(oauth2Login -> oauth2Login
                .successHandler(gitHubLoginSuccessHandler())
                .defaultSuccessUrl("/home")
                .loginPage("/login"))
                .logout(logout -> logout
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterAfter(authzFilter, UsernamePasswordAuthenticationFilter.class);
        // allow us to show our own content in IFRAMEs (e.g. Swagger, etc.)
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
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

    @Bean
    public AuthenticationSuccessHandler gitHubLoginSuccessHandler() {
        return new GitHubLoginSuccessHandler();
    }

    private static class GitHubLoginSuccessHandler implements AuthenticationSuccessHandler {

        private final RequestCache requestCache = new HttpSessionRequestCache();

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request,
                HttpServletResponse response, Authentication authentication)
                throws IOException, jakarta.servlet.ServletException {
            final var savedRequest = requestCache.getRequest(request, response);

            if (savedRequest == null) {
                response.sendRedirect("/home");
                return;
            }

            final var targetUrl = savedRequest.getRedirectUrl();
            response.sendRedirect(targetUrl);
        }
    }

}
