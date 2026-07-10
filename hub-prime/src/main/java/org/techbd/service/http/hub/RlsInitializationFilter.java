package org.techbd.service.http.hub;
import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.service.http.FusionAuthUserAuthorizationFilter;
import org.techbd.service.http.FusionAuthUsersService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RlsInitializationFilter extends OncePerRequestFilter {

    private final FusionAuthUsersService fusionAuthUsersService;
    private static final Logger LOG = LoggerFactory.getLogger(FusionAuthUserAuthorizationFilter.class);

    public RlsInitializationFilter(FusionAuthUsersService fusionAuthUsersService) {
        this.fusionAuthUsersService = fusionAuthUsersService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof OAuth2AuthenticationToken token
                    && token.getPrincipal() instanceof DefaultOAuth2User user) {
                fusionAuthUsersService.setRoleFromCurrentUser(user);
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            LOG.error("Error in RLS initialization filter", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error in RLS initialization filter");
        }
        // finally {
            // try {
            //     fusionAuthUsersService.resetDatabaseSession();
            // } catch (Exception e) {
            //     LOG.error("Failed to reset PostgreSQL session", e);
            // }
        // }
    }
}