package org.techbd.service.http;
 
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
/**
 * Service for managing FusionAuth user authorization.
 * This service handles user authorization and role management for FusionAuth OAuth2 users.
 */
@Service
public class FusionAuthUsersService {


    private static final Logger LOG = LoggerFactory.getLogger(FusionAuthUsersService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
         private final DSLContext dsl;
         public FusionAuthUsersService(DSLContext dsl) {
            this.dsl = dsl;
         }

        //  private final String fusionAuthApiToken = System.getenv("ORG_TECHBD_SERVICE_HTTP_FUSIONAUTH_API_AUTHN_TOKEN");
         //private final String fusionAuthBaseUrl = System.getenv("ORG_TECHBD_SERVICE_HTTP_FUSIONAUTH_BASE_URL");
        //  private final String tenantID = System.getenv("ORG_TECHBD_SERVICE_HTTP_FUSIONAUTH_TENANT_ID");
         // FusionAuth API Token (hardcoded)
         private final String fusionAuthApiToken = "mf-ZafPAJM5IiXzWvSttXBlJLh65uMbFujvNPMoL1y_NC_EDSR43HXqU";
        // FusionAuth Tenant ID (hardcoded)
         private final String tenantID = "cf444f01-bdca-4d96-8a5d-6c4f0d081023";
         private final String fusionAuthBaseUrl = "https://technology-by-design-dev.fusionauth.io";

         private final WebClient fusionAuthApiClient = WebClient.builder()
            .defaultHeader("Authorization", fusionAuthApiToken)
            .defaultHeader("X-FusionAuth-TenantId", tenantID)
            .defaultHeader("Accept", "application/json")
            .build();
    // Records for FusionAuth user authorization structure
    public record AuthorizedUser(
            String fusionAuthId,
            String email,
            String name,
            List<String> roles,
            List<String> groups
            ) {
    }
   
 public DefaultOAuth2User handleFusionAuthLogin(HttpServletRequest request, OAuth2AuthenticationToken oAuth2Token , DefaultOAuth2User oAuth2User , AuthorizedUser user) {
    
      Map<String, Object> enrichedAttributes = new HashMap<>(oAuth2User.getAttributes());
               enrichedAttributes.put("name", user.name());
               enrichedAttributes.put("email", user.email());
               enrichedAttributes.put("login", user.fusionAuthId());
               enrichedAttributes.put("groupNames", user.groups()); 
               enrichedAttributes.put("role", user.roles());              
               enrichedAttributes.put("avatar_url", createAvatarUrl(oAuth2User));

             
               DefaultOAuth2User enrichedOAuth2User = new DefaultOAuth2User(
               oAuth2User.getAuthorities(),
               enrichedAttributes,"email");
               LOG.info("enrichedOAuth2User attributes: {}", enrichedOAuth2User.getAttributes());

                OAuth2AuthenticationToken newAuth = new OAuth2AuthenticationToken(
               enrichedOAuth2User,
               enrichedOAuth2User.getAuthorities(),
               oAuth2Token.getAuthorizedClientRegistrationId()
        );
               SecurityContextHolder.getContext().setAuthentication(newAuth);
            LOG.info("FusionAuth user authenticated and set in SecurityContext: {}", user.email());

       return enrichedOAuth2User;
    } 
    
    public AuthorizedUser extractFusionAuthUser(DefaultOAuth2User oAuth2User) {
    String email = Optional.ofNullable(oAuth2User.getAttribute("email"))
            .map(Object::toString)
            .orElseThrow();

    String name = Optional.ofNullable(oAuth2User.getAttribute("given_name"))
            .map(Object::toString)
            .orElse("Unknown");

    String userId = Optional.ofNullable(oAuth2User.getAttribute("sub"))
            .map(Object::toString)
            .orElseThrow();
    
    @SuppressWarnings("unchecked")
    List<String> roles = Optional.ofNullable((List<String>) oAuth2User.getAttribute("roles"))
            .orElse(List.of());
     @SuppressWarnings("unchecked")
    List<String> groupIds = Optional.ofNullable((List<String>) oAuth2User.getAttribute("groups"))
            .orElse(List.of());

    List<String> groupNames = groupIds.stream()
            .map(this::fetchGroupNameById)
            .filter(Objects::nonNull) // filter out nulls
            .toList();

    LOG.info("oAuth2User attributes: {}", oAuth2User.getAttributes());
    return new AuthorizedUser(userId, email, name, roles, groupNames);
   }
 

public static String createAvatarUrl(DefaultOAuth2User oAuth2User) {
    String pictureUrl = Optional.ofNullable(oAuth2User.getAttribute("picture"))
            .map(Object::toString)
            .orElse("");

    if (pictureUrl.isEmpty() || "null".equalsIgnoreCase(pictureUrl)) {
        String name = Optional.ofNullable(oAuth2User.getAttribute("given_name"))
                .map(Object::toString)
                .orElse("Unknown");
        return "https://ui-avatars.com/api/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
    } else {
        return pictureUrl;
    }
}


     public void convertToJson(DefaultOAuth2User oAuth2User) {
      try {
        String jsonAttributes = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(oAuth2User.getAttributes());
        LOG.info("oAuth2User attributes (JSON):\n{}", jsonAttributes);
          setRoleBasedOnFusionToken(jsonAttributes);

    } catch (Exception e) {
        LOG.error("Failed to serialize attributes to JSON", e);
    }
  }

  private String fetchGroupNameById(String groupId) {
        try {
            String url = fusionAuthBaseUrl + "/api/group/" + groupId;
            LOG.info("Fetching FusionAuth group: {}", url);

            FusionAuthGroupResponse response = fusionAuthApiClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(FusionAuthGroupResponse.class)
                    .block();

            if (response != null && response.group != null) {
                return response.group.name;
            }
        } catch (Exception e) {
            LOG.error("Error fetching FusionAuth group name for groupId: {}", groupId, e);
        }
        return null;
    }
    public static class FusionAuthGroupResponse {
        public Group group;
    }

    public static class Group {
        public String id;
        public String name;
    }

      public String setRoleBasedOnFusionToken(String tokenJson) {
    try {
        // Convert the raw JSON string to jOOQ's JSONB type
        JSONB jsonbToken = JSONB.valueOf(tokenJson);

        // Call the PostgreSQL function
        String role = dsl.select(
                DSL.field("techbd_udi_ingress.set_role_based_on_fusion_token({0})", String.class, jsonbToken)
        ).fetchOneInto(String.class);

        LOG.info("Postgres role set via FusionAuth token: {}", role);
        return role;
    } catch (Exception e) {
        LOG.error("Error calling set_role_based_on_fusion_token with token: {}", tokenJson, e);
        throw e;
    }
}
   
}