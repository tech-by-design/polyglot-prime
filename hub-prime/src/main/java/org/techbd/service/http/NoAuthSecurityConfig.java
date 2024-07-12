package org.techbd.service.http;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.core.authority.AuthorityUtils;

@Configuration
@EnableWebSecurity
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.github")
@Profile("localopen")
public class NoAuthSecurityConfig {
    @Autowired
    public NoAuthSecurityConfig() {
        System.out.println("NoAuthSecurityConfig initialized with profile: Localopen");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow all requests without any security (for local unauthenticated data)
        // and turn off CSRF to allow POST methods
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
        // allow us to show our own content in IFRAMEs (e.g. Swagger, etc.)
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        http.anonymous();
        // SecurityContextHolder.getContext().setAuthentication(
        // new AnonymousAuthenticationToken("key", "anonymousUser",
        // AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
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
