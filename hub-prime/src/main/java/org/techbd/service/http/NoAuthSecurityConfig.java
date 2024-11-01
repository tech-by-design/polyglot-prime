package org.techbd.service.http;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@EnableWebSecurity
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.github")
@Profile("localopen")
public class NoAuthSecurityConfig {

    @Value("${TECHBD_HUB_PRIME_FHIR_API_BASE_URL:#{null}}")
    private String apiUrl;

    @Value("${TECHBD_HUB_PRIME_FHIR_UI_BASE_URL:#{null}}")
    private String uiUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        // allow all requests without any security (for local unauthenticated data)
        // and turn off CSRF to allow POST methods
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
        // allow us to show our own content in IFRAMEs (e.g. Swagger, etc.)
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        http.anonymous(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        List<String> allowedOrigins = List.of(uiUrl, apiUrl);
        config.setAllowedOrigins(allowedOrigins); // Set allowed origins
        // Configure methods and headers applicable to both UI and API requests
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        //config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config); // Apply this configuration to all endpoints

        return new CorsFilter(source);
    }

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
