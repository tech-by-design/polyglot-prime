package org.techbd.service.http;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
// import org.techbd.service.http.hub.RlsInitializationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@Profile("!localopen")
public class SecurityConfig {
   
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);
    
    private final RolePermissionInterceptor rolePermissionInterceptor;

    @Autowired(required = false)
    private FusionAuthUserAuthorizationFilter fusionAuthAuthorizationFilter;

    @Autowired(required = false)
    private GitHubUserAuthorizationFilter gitHubUserAuthorizationFilter;
//     @Autowired
// private RlsInitializationFilter rlsInitializationFilter;

    public SecurityConfig(RolePermissionInterceptor rolePermissionInterceptor) {
        this.rolePermissionInterceptor = rolePermissionInterceptor;
    }

    @Value("${TECHBD_HUB_PRIME_FHIR_API_BASE_URL:#{null}}")
    private String apiUrl;

    @Value("${TECHBD_HUB_PRIME_FHIR_UI_BASE_URL:#{null}}")
    private String uiUrl;
    
    @Value("${ORG_TECHBD_SERVICE_HTTP_FUSIONAUTH_BASE_URL}")
    private String fusionAuthBaseUrl;

    @Value("${SPRING_SECURITY_OAUTH2_FUSIONAUTH_CLIENT_ID}")
    private String clientId;

    @Value("${SPRING_SECURITY_OAUTH2_LOGOUT_REDIRECT_URI}")
    private String logoutRedirectUrl;
   
    @Value("${AUTH_PROVIDER:github}")
    private String authProvider;
    

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
                                .requestMatchers("/access-denied")
                                .permitAll()
                                .requestMatchers("/fusionauth/webhook").permitAll()
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
                .sessionManagement(sessionManagement -> {
            // Dynamically adjust session timeout behavior based on the authentication provider
                        if ("github".equalsIgnoreCase(authProvider)) {
                            sessionManagement
                                    .invalidSessionUrl(Constant.SESSION_TIMEOUT_URL)
                                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
                        }  else if ("fusionauth".equalsIgnoreCase(authProvider)) {
                                final String invalidSessionUrl =
                                        Constant.LOGIN_PAGE_URL + "?sessionExpired=true";
                                LOG.info(
                                        "Configuring session management for FusionAuth. " +
                                        "Invalid session URL: {}",
                                        invalidSessionUrl
                                );
                                sessionManagement
                                        .invalidSessionUrl(invalidSessionUrl)
                                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
                        }
                    });
                  if (fusionAuthAuthorizationFilter != null) {
                        http.addFilterAfter(fusionAuthAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);
                    }

                    if (gitHubUserAuthorizationFilter != null) {
                        http.addFilterAfter(gitHubUserAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);
                    }   
                    
            //         http.addFilterAfter(
            //                rlsInitializationFilter,
            // FusionAuthUserAuthorizationFilter.class);
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
                HttpSession session = request.getSession(false);
                LOG.warn(
                        "LOGOUT HANDLER INVOKED. " +
                        "URI={}, method={}, sessionExists={}, sessionId={}, " +
                        "authentication={}, authenticated={}",
                        request.getRequestURI(),
                        request.getMethod(),
                        session != null,
                        session != null ? session.getId() : "NO_SESSION",
                        authentication != null
                                ? authentication.getName()
                                : "NO_AUTHENTICATION",
                        authentication != null
                                && authentication.isAuthenticated()
                );
                if (authentication != null) {
                    LOG.info( "Clearing SecurityContext for authenticated user: {}",
                         authentication.getName()
                    );
                    new SecurityContextLogoutHandler()
                            .logout(request, response, authentication);
                } else {
                    LOG.info("Logout handler invoked without authenticated user.");
                }
                if (logoutRedirectUrl == null
                        || logoutRedirectUrl.isBlank()) {
                    LOG.warn(
                            "FusionAuth logout redirect URL is missing. " +
                            "Redirecting to application login page."
                    );
                    response.sendRedirect(Constant.LOGIN_PAGE_URL);
                    return;
                }
                String logoutUrl = fusionAuthLogoutUUrl();
                LOG.info( "Redirecting user to FusionAuth logout endpoint.");
                response.sendRedirect(logoutUrl);
            };
        }

        private String fusionAuthLogoutUUrl() {
            return fusionAuthBaseUrl + "/oauth2/logout"
                    + "?client_id=" + clientId
                    + "&post_logout_redirect_uri=" + logoutRedirectUrl;
        }

    /**
     * Register RolePermissionInterceptor for all MVC requests.
     */
    @Bean
    public WebMvcConfigurer mvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(rolePermissionInterceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(Constant.INTERCEPTOR_EXCLUDED_URLS)
                        .excludePathPatterns(Constant.STATELESS_API_URLS);
            }
        };
    }
}