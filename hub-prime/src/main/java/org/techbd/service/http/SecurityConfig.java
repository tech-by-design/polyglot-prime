package org.techbd.service.http;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class.getName());

    @Autowired
    private GitHubUserAuthorizationFilter authzFilter;

    @Value("${TECHBD_HUB_PRIME_FHIR_API_BASE_URL:#{null}}")
    private String apiUrl;

    @Value("${TECHBD_HUB_PRIME_FHIR_UI_BASE_URL:#{null}}")
    private String uiUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow authentication for security
        // and turn off CSRF to allow POST methods
        http
                .authorizeHttpRequests(
                        authorize -> authorize
                                .requestMatchers("/login/**", "/oauth2/**",
                                        "/",
                                        "/Bundle", "/Bundle/**",
                                        "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/**",
                                        "/Hl7/v2", "/Hl7/v2/",
                                        "/metadata",
                                        "/api/expect/**",
                                        "/docs/api/interactive/swagger-ui/**", "/support/**", "/docs/api/interactive/**",
                                        "/docs/api/openapi/**",
                                        "/error", "/error/**")
                                .permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(
                        oauth2Login -> oauth2Login
                                .successHandler(gitHubLoginSuccessHandler())
                                .defaultSuccessUrl("/home")
                                .loginPage("/login")
                )
                .logout(
                        logout -> logout
                                .deleteCookies("JSESSIONID")
                                .logoutSuccessUrl("/")
                                .invalidateHttpSession(true)
                                .permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        sessionManagement -> sessionManagement
                                .invalidSessionUrl("/?timeout=true")
                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .addFilterAfter(authzFilter, UsernamePasswordAuthenticationFilter.class);
        // allow us to show our own content in IFRAMEs (e.g. Swagger, etc.)
        http.headers(headers -> {
            headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
            headers.httpStrictTransportSecurity(
                    hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000)
            ); // Enable HSTS for 1 year
        });
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
