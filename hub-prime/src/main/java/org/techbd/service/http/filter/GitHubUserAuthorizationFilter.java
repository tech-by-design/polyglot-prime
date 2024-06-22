package org.techbd.service.http.filter;

import java.io.IOException;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GitHubUserAuthorizationFilter extends OncePerRequestFilter {
  private static final String supportEmail = "NYeC QCS Support <qcs-help@qualifiedentity.org>";
  private static final Logger LOGGER = LoggerFactory.getLogger(GitHubUserAuthorizationFilter.class);

  static List<GitHubUserService.AuthenticatedUser> newListUsers;

  @Override
  protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    try {
      newListUsers = new GitHubUserService().getUserList().users();
      for (GitHubUserService.AuthenticatedUser user : newListUsers) {
        LOGGER.info(
            "[GITHUB-AUTH] List of Users available in sensitive repo: \tgithub-id: {}\tname: {}\ttenantId: {}\troles: {}",
            user.gitHubId(), user.name(), user.tenantId(), user.roles());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      DefaultOAuth2User authUser = (DefaultOAuth2User) authentication.getPrincipal();
      String githubId = authUser.getAttribute("login");
      LOGGER.info("[GITHUB-AUTH] Attempted User: \t github-id: {}\t name: {}",
          githubId, authUser.getAttribute("name"));
      final var user = newListUsers.stream()
          .filter(u -> u.gitHubId().equals(githubId) && u.roles().contains("USER"))
          .findFirst();
      if (!user.isPresent()) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("You do not have permission to access this resource. Please send an email to "
            + supportEmail + " with your GitHub ID to request access.");
        return;
      }

    }
    filterChain.doFilter(request, response);
  }
}
