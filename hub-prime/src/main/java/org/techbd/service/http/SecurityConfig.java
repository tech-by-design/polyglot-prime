package org.techbd.service.http;

import java.io.IOException;

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
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
 
@Configuration
@Profile("!localopen")
public class SecurityConfig {
  
    
    @Autowired
    private FusionAuthUserAuthorizationFilter fusionAuthAuthorizationFilter;

    @Value("${TECHBD_HUB_PRIME_FHIR_API_BASE_URL:#{null}}")
    private String apiUrl;

    @Value("${TECHBD_HUB_PRIME_FHIR_UI_BASE_URL:#{null}}")
    private String uiUrl;

    @Bean
    public SecurityFilterChain statelessSecurityFilterChain(final HttpSecurity http) throws Exception {
        // Stateless configuration for bundle endpoints
        http
                .securityMatcher(Constant.STATELESS_API_URLS)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()) // Allow all requests
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless
                .csrf(AbstractHttpConfigurer::disable); // Disable CSRF for stateless APIs

        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow authentication for security
        // and turn off CSRF to allow POST methods
        http
                .authorizeHttpRequests(
                        authorize -> authorize
                                .requestMatchers(Constant.UNAUTHENTICATED_URLS)
                                .permitAll()
                                .anyRequest().authenticated())
                .oauth2Login(
                        oauth2Login -> oauth2Login
                                .successHandler(oAuth2LoginSuccessHandler())
                                .defaultSuccessUrl(Constant.HOME_PAGE_URL)
                                .loginPage(Constant.LOGIN_PAGE_URL))
               .logout(logout -> logout
                                .deleteCookies(Constant.SESSIONID_COOKIE)   // clear JSESSIONID (or your custom session cookie)
                                .invalidateHttpSession(true)                // kill server-side session
                                .clearAuthentication(true)  
                                .logoutSuccessHandler(customLogoutSuccessHandler())
                                .permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        sessionManagement -> sessionManagement
                                .invalidSessionUrl(Constant.SESSION_TIMEOUT_URL)
                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterAfter(fusionAuthAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);
        // allow us to show our own content in IFRAMEs (e.g. Swagger, etc.)
        http.headers(headers -> {
            headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
            headers.httpStrictTransportSecurity(
                    hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(Constant.HSTS_MAX_AGE)); // Enable HSTS
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
        // Expose Location header for session time out redirection at the UI side (AGGrid etc)
        config.addExposedHeader("Location");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    @Bean
    public AuthenticationSuccessHandler oAuth2LoginSuccessHandler() {
        return new OAuth2LoginSuccessHandler();
    }
 
    private static class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
 
        private final RequestCache requestCache = new HttpSessionRequestCache();

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request,
                HttpServletResponse response, Authentication authentication)
                throws IOException, jakarta.servlet.ServletException {
            final var savedRequest = requestCache.getRequest(request, response);

            if (savedRequest == null) {
                response.sendRedirect(Constant.HOME_PAGE_URL);
                return;
            }

            final var targetUrl = savedRequest.getRedirectUrl();
            response.sendRedirect(targetUrl);
        }
    }

    @Bean
    public LogoutSuccessHandler customLogoutSuccessHandler() {
    return (request, response, authentication) -> {
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        String fusionAuthLogoutUrl = "https://technology-by-design-dev.fusionauth.io/oauth2/logout"
                + "?client_id=79425b8d-50cc-40fd-af2e-55ccb26422a5"
                + "&post_logout_redirect_uri=https://hub.fo.dev.techbd.org/";
        response.sendRedirect(fusionAuthLogoutUrl);
    };
}

}
