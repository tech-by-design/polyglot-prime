package org.techbd.service.http;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.service.http.FusionAuthUsersService.AuthorizedUser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GitHubUserAuthorizationFilter extends OncePerRequestFilter {


    private static final Logger LOG = LoggerFactory.getLogger(InteractionsFilter.class.getName());

    private static final String AUTH_USER_SESSION_ATTR_NAME = "authenticatedUser";
    private static final String supportEmail = "help@techbd.org";
    private static final String supportEmailDisplayName = "Tech by Design Support <" + supportEmail + ">";

      private final GitHubUsersService gitHubUsers;
      private FusionAuthUsersService fusionAuthUsersService;

    public GitHubUserAuthorizationFilter(final GitHubUsersService gitHubUsers ,FusionAuthUsersService fusionAuthUsers) {
        this.gitHubUsers = gitHubUsers;
        this.fusionAuthUsersService = fusionAuthUsers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthenticatedUser(OAuth2User principal, GitHubUsersService.AuthorizedUser ghUser)
            implements Serializable {

    }

    public static final Optional<AuthenticatedUser> getAuthenticatedUser(
            final @NonNull HttpServletRequest request) {
        return Optional.ofNullable(request.getSession(false))
                .map(session -> (AuthenticatedUser) session.getAttribute(AUTH_USER_SESSION_ATTR_NAME));
    }

    protected static final void setAuthenticatedUser(final @NonNull HttpServletRequest request,
            final @NonNull AuthenticatedUser authUser) {
        request.getSession(true).setAttribute(AUTH_USER_SESSION_ATTR_NAME, authUser);
    }

   

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        final var sessionUser = getAuthenticatedUser(request);
        if (sessionUser.isEmpty()) {
            final var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal().toString())&&
                authentication instanceof OAuth2AuthenticationToken oAuth2Token) {
                   String registrationId = oAuth2Token.getAuthorizedClientRegistrationId();
                 DefaultOAuth2User oAuth2User = (DefaultOAuth2User) oAuth2Token.getPrincipal();
                 try{     
                    if ("github".equalsIgnoreCase(registrationId)) { 
                final var gitHubPrincipal = (DefaultOAuth2User) authentication.getPrincipal();
                final var gitHubLoginId = Optional.ofNullable(gitHubPrincipal.getAttribute("login")).orElseThrow();
                final var gitHubAuthnUser = gitHubUsers.isAuthorizedUser(gitHubLoginId.toString());
                if (!gitHubAuthnUser.isPresent()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("text/html");
                    response.getWriter().write("GitHub ID " + gitHubLoginId
                            + " does not have permission to access this resource. Please fill out the <a href=\"https://techbd.org/submit-form/access-request\" target=\"_blank\"> Access Request Form </a> to request access. Once submitted, Tech by Design Support will review your request. For further assistance, contact Tech by Design Support at <<a href=\"mailto:"
                            + supportEmailDisplayName + "\">" + supportEmail + "</a>>.");
                    return;
                }
                setAuthenticatedUser(request, new AuthenticatedUser(gitHubPrincipal, gitHubAuthnUser.orElseThrow()));
            }
             else if ("fusionauth".equalsIgnoreCase(registrationId)) {
                               
               AuthorizedUser user = fusionAuthUsersService.extractFusionAuthUser(oAuth2User);
               DefaultOAuth2User enrichedOAuth2User = fusionAuthUsersService.handleFusionAuthLogin(request, oAuth2Token , oAuth2User,user);
             
               var adaptedUser = adaptFusionUserToGitHubFormat(user);
               setAuthenticatedUser(request, new AuthenticatedUser(enrichedOAuth2User, adaptedUser));
               fusionAuthUsersService.convertToJson(enrichedOAuth2User);
               LOG.info("FusionAuth user authenticated: {}", user.email());
            }

            } catch (Exception e) {
                LOG.error("Error processing user authorization for login provider: {}", registrationId, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal auth error");
                return;
            }
            }
        }

        if (request.getRequestURI().startsWith("/actuator")) {
            Optional<AuthenticatedUser> userOptional = getAuthenticatedUser(request);
            if (userOptional.isPresent()) {
                if (!userOptional.get().ghUser.isUserHasActuatorAccess()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("text/plain"); // Ensure content type is set to text/plain
                    response.getWriter().write("GitHub ID " + userOptional.get().ghUser.gitHubId()
                            + " does not have roles to access actuator. Please send an email to "
                            + supportEmailDisplayName
                            + " from your manager, to add roles to access actuator from this GitHub ID.");
                    response.flushBuffer();
                    return; // Stop further processing of the request
                }
            }
        }

        filterChain.doFilter(request, response);
    }

     // In GitHubUserAuthorizationFilter.java (or a new util class)
    private static GitHubUsersService.AuthorizedUser adaptFusionUserToGitHubFormat(FusionAuthUsersService.AuthorizedUser faUser) {
    return new GitHubUsersService.AuthorizedUser(
        faUser.name(),              // name
        faUser.email(),             // emailPrimary
        null,                       // profilePicUrl (FusionAuth doesn't provide this)
        faUser.fusionAuthId(),      // gitHubId (reusing for compatibility)
        "fusionauth",               // tenantId or any dummy value
        Map.of( // basic actuator role support
            "actuator", new GitHubUsersService.Resource(faUser.roles())
        )
    );
  }
}
