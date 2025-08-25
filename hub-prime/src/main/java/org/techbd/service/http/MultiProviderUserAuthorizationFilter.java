package org.techbd.service.http;
 
import java.io.IOException;
import java.util.Optional;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.service.http.FusionAuthUsersService.AuthorizedUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
 
/**
 * Authorization filter that handles users from multiple OAuth2 providers
 * including GitHub and FusionAuth.
 */
@Component
public class MultiProviderUserAuthorizationFilter extends OncePerRequestFilter {
 
    private static final Logger LOG = LoggerFactory.getLogger(MultiProviderUserAuthorizationFilter.class);
 
    @Autowired
    private GitHubUsersService gitHubUsersService;
 
    @Autowired
    private FusionAuthUsersService fusionAuthUsersService;
 
    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
       
  
            final var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal().toString())) {
               
                final var oauth2Principal = (DefaultOAuth2User) authentication.getPrincipal();
                String provider = (String) oauth2Principal.getAttribute("provider");
               
                if (provider == null) {
                    // Try to determine provider from attributes
                    if (oauth2Principal.getAttribute("login") != null && oauth2Principal.getAttribute("node_id") != null) {
                        provider = "github";
                    } else if (oauth2Principal.getAttribute("email") != null) {
                        provider = "fusionauth";
                    }
                }
 
                switch (provider != null ? provider.toLowerCase() : "") {
                    case "github":
                        handleGitHubUser(request, oauth2Principal);
                        break;
                    case "fusionauth":
                        handleFusionAuthUser(request, oauth2Principal);
                        break;
                    default:
                        LOG.warn("Unknown OAuth2 provider or provider not set in attributes");
                        break;
                }
            }
        filterChain.doFilter(request, response);
    }
 
    private void handleGitHubUser(HttpServletRequest request, DefaultOAuth2User gitHubPrincipal) {
        final var gitHubLoginId = Optional.ofNullable(gitHubPrincipal.getAttribute("login")).orElseThrow();
        final var gitHubAuthnUser = gitHubUsersService.isAuthorizedUser(gitHubLoginId.toString());
       
        if (gitHubAuthnUser.isPresent()) {
            final var sessionUser = new SessionUser(
                    gitHubAuthnUser.get().gitHubId(),
                    gitHubAuthnUser.get().name(),
                    gitHubAuthnUser.get().emailPrimary(),
                    java.util.List.of("USER"), // Default role for GitHub users
                    "github",
                  java.util.Collections.emptyList()
            );
            request.getSession().setAttribute("sessionUser", sessionUser);
            LOG.info("GitHub user authenticated and authorized: {}", gitHubLoginId);
        } else {
            LOG.warn("GitHub user not authorized: {}", gitHubLoginId);
        }
    }
 
    private void handleFusionAuthUser(HttpServletRequest request, DefaultOAuth2User fusionAuthPrincipal) {
       
     AuthorizedUser fUser = fusionAuthUsersService.extractFusionAuthUser(fusionAuthPrincipal);

     final var sessionUser = new SessionUser(
            fUser.fusionAuthId(),
            fUser.name(),
            fUser.email(),
            fUser.roles(),
            "fusionauth",
            fUser.groups() );

    request.getSession().setAttribute("sessionUser", sessionUser);
    LOG.info("FusionAuth user authenticated and authorized: {}",  fUser.email());
    }
 
    
    /**
     * Session user record that works with multiple OAuth2 providers
     */
    public record SessionUser(
            String userId,
            String name,
            String email,
            java.util.List<String> roles,
            String provider,
            java.util.List<String> groups
    ) {
    }

      public static Optional<GitHubUserAuthorizationFilter.AuthenticatedUser> getAuthenticatedUser(
            final HttpServletRequest request) {
        final var sessionUser = request.getSession().getAttribute("sessionUser");
        if (sessionUser instanceof SessionUser su) {
            // For GitHub users, we need to create a compatible AuthenticatedUser object
            if ("github".equals(su.provider())) {
                // Create a minimal OAuth2User for backward compatibility
                var attributes = new java.util.HashMap<String, Object>();
                attributes.put("login", su.userId());
                attributes.put("name", su.name());
                attributes.put("email", su.email());
 
                var oauth2User = new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                    java.util.Collections.emptyList(), attributes, "login");
 
                // Create a minimal GitHubUsersService.AuthorizedUser for backward compatibility
                var authorizedUser = new GitHubUsersService.AuthorizedUser(
                    su.name(), su.email(), "", su.userId(), "", java.util.Collections.emptyMap());
 
                return Optional.of(new GitHubUserAuthorizationFilter.AuthenticatedUser(oauth2User, authorizedUser));
            }
        }
        return Optional.empty();
    }
}