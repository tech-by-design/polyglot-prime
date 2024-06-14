package org.techbd.service.http.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.service.http.hub.prime.Controller;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class GitHubUserAuthorizationFilter extends OncePerRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitHubUserAuthorizationFilter.class);

  static List<Controller.AuthenticatedUser> newListUsers;

  @Override
  protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    try {
      newListUsers = new GitHubUserService().getUserList().users();
      for (Controller.AuthenticatedUser user : newListUsers) {
        LOGGER.info("List of Users available in sensitive repo: \ngithub-id: {}\nname: {}\ntenantId: {}\nroles: {}",
            user.gitHubId(), user.name(), user.tenantId(), user.roles());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      DefaultOAuth2User authUser = (DefaultOAuth2User) authentication.getPrincipal();
      String githubId = authUser.getAttribute("login");
      Optional<Controller.AuthenticatedUser> user = newListUsers.stream()
          .filter(u -> u.gitHubId().equals(githubId) && u.roles().contains("USER"))
          .findFirst();
      if (!user.isPresent()) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("Access Denied");
        return;
      }

    }
    filterChain.doFilter(request, response);
  }
}
