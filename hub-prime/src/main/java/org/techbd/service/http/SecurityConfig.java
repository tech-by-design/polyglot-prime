package org.techbd.service.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.techbd.service.http.filter.GitHubUserAuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.github")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow authentication for security
        // and turn off CSRF to allow POST methods
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login/**", "/oauth2/**", "/", "/Bundle/**", "/metadata",
                        "/docs/api/interactive/swagger-ui/**", "/docs/api/interactive/**", "/docs/api/openapi/**")
                .permitAll()
                .anyRequest().authenticated())
                .oauth2Login(oauth2Login -> oauth2Login
                        .defaultSuccessUrl("/home", true))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterAfter(new GitHubUserAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);
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

}
