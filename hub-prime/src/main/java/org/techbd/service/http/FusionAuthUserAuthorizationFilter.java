package org.techbd.service.http;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class FusionAuthUserAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FusionAuthUserAuthorizationFilter.class);

    private static final String AUTH_USER_SESSION_ATTR_NAME = "authenticatedUser";
    private static final String SUPPORT_EMAIL = "help@techbd.org";
    private static final String SUPPORT_EMAIL_DISPLAY = "Tech by Design Support <" + SUPPORT_EMAIL + ">";

    private final FusionAuthUsersService fusionAuthUsersService;

    public FusionAuthUserAuthorizationFilter(final FusionAuthUsersService fusionAuthUsersService) {
        this.fusionAuthUsersService = fusionAuthUsersService;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthenticatedUser(OAuth2User principal, FusionAuthUsersService.AuthorizedUser faUser)
            implements java.io.Serializable {
    }

    public static Optional<AuthenticatedUser> getAuthenticatedUser(@NonNull HttpServletRequest request) {
        return Optional.ofNullable(request.getSession(false))
                .map(session -> (AuthenticatedUser) session.getAttribute(AUTH_USER_SESSION_ATTR_NAME));
    }

    public static void setAuthenticatedUser(@NonNull HttpServletRequest request,
                                            @NonNull AuthenticatedUser authUser) {
        request.getSession(true).setAttribute(AUTH_USER_SESSION_ATTR_NAME, authUser);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (getAuthenticatedUser(request).isEmpty()) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof OAuth2AuthenticationToken oAuth2Token
                    && authentication.isAuthenticated()) {

                if ("fusionauth".equalsIgnoreCase(oAuth2Token.getAuthorizedClientRegistrationId())) {
                    DefaultOAuth2User oAuth2User = (DefaultOAuth2User) oAuth2Token.getPrincipal();
                    try {
                        FusionAuthUsersService.AuthorizedUser faUser =
                                fusionAuthUsersService.extractFusionAuthUser(oAuth2User);

                        DefaultOAuth2User enrichedOAuth2User =
                                fusionAuthUsersService.handleFusionAuthLogin(request, oAuth2Token, oAuth2User, faUser);

                        setAuthenticatedUser(request, new AuthenticatedUser(enrichedOAuth2User, faUser));
                        fusionAuthUsersService.convertToJson(enrichedOAuth2User);

                        LOG.info("FusionAuth user authenticated: {} roles={}", faUser.email(), faUser.roles());
                    } catch (Exception e) {
                        LOG.error("Error processing FusionAuth user login", e);
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal auth error");
                        return;
                    }
                }
            }
        }

        // Actuator access check
        if (request.getRequestURI().startsWith("/actuator")) {
            Optional<AuthenticatedUser> userOptional = getAuthenticatedUser(request);
            if (userOptional.isPresent()) {
                FusionAuthUsersService.AuthorizedUser faUser = userOptional.get().faUser();
                if (!faUser.roles().contains("ADMIN")) { // simple role check
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("text/plain");
                    response.getWriter().write("User " + faUser.fusionAuthId()
                            + " does not have roles to access actuator. Please contact "
                            + SUPPORT_EMAIL_DISPLAY);
                    response.flushBuffer();
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
