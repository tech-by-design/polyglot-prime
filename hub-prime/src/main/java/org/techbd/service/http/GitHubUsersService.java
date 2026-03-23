package org.techbd.service.http;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@ConfigurationProperties(prefix = "org.techbd.service.http.github")
@Service
public class GitHubUsersService {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubUsersService.class);

  // @JsonIgnoreProperties(ignoreUnknown = true)
  // public record AuthorizedUser(String gitHubId, String name, String tenantId,
  // List<String> roles) {
  // }
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AuthorizedUser(String name, String emailPrimary, String profilePicUrl, String gitHubId,
      String tenantId, Map<String, Resource> resources) {
    public boolean isUserHasActuatorAccess() {
      return Optional.ofNullable(resources().get("actuator"))
          .map(actuator -> actuator.roles)
          .orElseGet(Collections::emptyList)
          .stream()
          .anyMatch(role -> role.contains("ADMIN"));
    }
  }

  public record Resource(List<String> roles) {
  }

  // this record must physically match the structure of
  // sensitive/oauth2-github-authz.yml
  public record AuthorizedUsers(List<AuthorizedUser> users) {
    public Optional<AuthorizedUser> isAuthorizedUser(final String gitHubUserID) {
      return users.stream().filter(u -> u.gitHubId().equals(gitHubUserID)).findFirst();
    }
  }

  private String gitHubApiAuthnToken = System.getenv("ORG_TECHBD_SERVICE_HTTP_GITHUB_API_AUTHN_TOKEN");
  private String authzUsersYamlUrl = System.getenv("ORG_TECHBD_SERVICE_HTTP_GITHUB_AUTHZ_USERS_YAML_URL");

  private final WebClient gitHubApiClient = WebClient.builder()
      .defaultHeader("Authorization", "token " + gitHubApiAuthnToken)
      .build();

  /**
   * Check GitHub to see if the provided userName is an authorized user. This
   * method will always go back to GitHub and load all users before returning
   * result. User is responsible for caching to reduce performance hits.
   * 
   * @param gitHubLoginID the GitHub userName to check
   * @return non-empty AuthenticatedUser if found authorized or empty if not
   *         authorized
   */
  public Optional<AuthorizedUser> isAuthorizedUser(final String gitHubLoginID) {
    final var result = getAuthorizedUsers().isAuthorizedUser(gitHubLoginID);
    LOG.info("isAuthorizedUser %s in %s: %s".formatted(gitHubLoginID, authzUsersYamlUrl, result));
    return result;
  }

  /**
   * Get list of all authorized GitHub users. This method will always go back to
   * GitHub and load all users before returning result. User is responsible for
   * caching to reduce performance hits.
   * 
   * @return all authorized users defined in GitHub file authzUsersYamlUrl
   */
  protected AuthorizedUsers getAuthorizedUsers() {
    LOG.info("Downloading users from %s".formatted(authzUsersYamlUrl));
    // Make a single request to the URL and get the YAML content directly
    final var responseBody = gitHubApiClient.get()
        .uri(authzUsersYamlUrl)
        .retrieve()
        .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
          LOG.error("Unexpected response code from GitHub API: {} ({})", clientResponse.statusCode(),
              authzUsersYamlUrl);
          return Mono
              .error(new IOException("Unexpected response code from GitHub API: " + clientResponse.statusCode()));
        })
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(10))
        .retryWhen(
            Retry.backoff(3, Duration.ofSeconds(2))
                .filter(throwable -> throwable instanceof IOException || throwable instanceof WebClientRequestException)
                .doBeforeRetry(retrySignal -> LOG.warn("GitHub API retry attempt {} due to {}",
                    retrySignal.totalRetries() + 1, retrySignal.failure().toString())) // Log retry attempt
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()))
        .block();

    if (responseBody == null) {
      LOG.error("GitHub API response body is null for " + authzUsersYamlUrl);
      return null;
    }

    // Parse the YAML content into an AuthorizedUsers object
    final var mapper = new ObjectMapper(new YAMLFactory());
    AuthorizedUsers users;
    try {
      users = mapper.readValue(responseBody, AuthorizedUsers.class);
      users.users.stream().forEach(item -> LOG.info("Item: {}", item));
      return users;
    } catch (Exception e) {
      LOG.error("Error transforming %s to AuthorizedUsers.class".formatted(authzUsersYamlUrl), e);
    }

    return new AuthorizedUsers(List.of());
  }
    
  /**
   * Creates a dummy authorized user for sandbox environments.
   * This allows testing without requiring users to be in the authorized users
   * list.
   * 
   * @param gitHubId The GitHub user ID
   * @param name     The user's display name
   * @param tenantId The tenant ID for the user
   * @return An AuthorizedUser instance with basic USER role
   */
  public AuthorizedUser createDummyAuthorizedUser(String gitHubId, String name, String tenantId) {
    // Create resources map with sudomain1.techbd.org having USER role
    Map<String, Resource> resources = Map.of(
        "sudomain1.techbd.org", new Resource(List.of("USER")));

    // Note: Not adding actuator resource, so sandbox users won't have actuator
    // access

    return new AuthorizedUser(
        name, // name
        gitHubId + "@github.local", // emailPrimary (dummy email)
        null, // profilePicUrl (can be null for sandbox)
        gitHubId, // gitHubId
        tenantId, // tenantId
        resources // resources map
    );
  }

}
