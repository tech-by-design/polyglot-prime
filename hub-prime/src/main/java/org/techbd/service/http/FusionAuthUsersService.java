package org.techbd.service.http;
 
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
/**
 * Service for managing FusionAuth user authorization.
 * This service handles user authorization and role management for FusionAuth OAuth2 users.
 */
@Service
public class FusionAuthUsersService {

    @Value("${ORG_TECHBD_SERVICE_HTTP_FUSIONAUTH_BASE_URL}")
    private String fusionAuthBaseUrl;

    @Value("${FUSIONAUTH_API_KEY}")
     private String fusionAuthApiKey;


    private static final Logger LOG = LoggerFactory.getLogger(FusionAuthUsersService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
         private final DSLContext dsl;
    private final DSLContext secondaryDsl;

    public FusionAuthUsersService(
            @Qualifier("primaryDslContext") DSLContext dsl,
            @Autowired(required = false)
            @Qualifier("secondaryDslContext") DSLContext secondaryDsl) {

        this.dsl = dsl;
        this.secondaryDsl = secondaryDsl;
    }

    // Records for FusionAuth user authorization structure
        public record AuthorizedUser(
            String fusionAuthId,
            String email,
            String name,
            List<String> roles,
            List<String> tenantIds,
            List<String> tenantNames,
            Boolean isSuperRole
            ) {
        }
   
 public DefaultOAuth2User handleFusionAuthLogin(HttpServletRequest request, OAuth2AuthenticationToken oAuth2Token , DefaultOAuth2User oAuth2User , AuthorizedUser user) {
    
      Map<String, Object> enrichedAttributes = new HashMap<>(oAuth2User.getAttributes());
               enrichedAttributes.put("name", user.name());
               enrichedAttributes.put("email", user.email());
               enrichedAttributes.put("login", user.fusionAuthId());
               enrichedAttributes.put("tenantIds", user.tenantIds());
               enrichedAttributes.put("tenantNames", user.tenantNames()); 
               enrichedAttributes.put("role", user.roles());              
               enrichedAttributes.put("avatar_url", createAvatarUrl(oAuth2User));
               enrichedAttributes.put("authProvider", "fusionauth");

             
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
    
    @SuppressWarnings({ "unchecked", "null" })
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

    boolean isSuperRole = Optional.ofNullable(oAuth2User.getAttribute("isSuperRole"))
            .map(Object::toString)
            .map(Boolean::parseBoolean)
            .orElse(false);
    
    List<String> roles = Optional.ofNullable((List<String>) oAuth2User.getAttribute("roles"))
            .orElse(List.of());
    List<String> tenantIds =
        Optional.ofNullable((List<String>) oAuth2User.getAttribute("tenantIds"))
                .orElse(List.of());

    List<String> tenantNames =
        Optional.ofNullable((List<String>) oAuth2User.getAttribute("tenantNames"))
                .orElse(List.of());

    LOG.info("oAuth2User attributes: {}", oAuth2User.getAttributes());
                return new AuthorizedUser(userId, email, name, roles, tenantIds, tenantNames, isSuperRole);
   } 
 

@SuppressWarnings("null")
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
        // setRole(dsl, jsonAttributes);
        // //   Set role on reader (if enabled)
        //     if (secondaryDsl != null) {
        //         setRole(secondaryDsl, jsonAttributes);
        //     }

        // setRoleBasedOnFusionToken(jsonAttributes);

    } catch (Exception e) {
        LOG.error("Failed to serialize attributes to JSON", e);
    }
  }

//   private String fetchGroupNameById(String groupId) {
//         try {
//             String url = fusionAuthBaseUrl + "/api/group/" + groupId;
//             LOG.info("Fetching FusionAuth group: {}", url);

//            FusionAuthGroupResponse response = fusionAuthApiClient
//         .get()
//         .uri(url)
//         .header("Authorization", fusionAuthApiKey)
//         .retrieve()
//         .bodyToMono(FusionAuthGroupResponse.class)
//         .block();
//             if (response != null && response.group != null) {
//                 return response.group.name;
//             }
//         } catch (Exception e) {
//             LOG.error("Error fetching FusionAuth group name for groupId: {}", groupId, e);
//         }
//         return null;
//     }
    // public static class FusionAuthGroupResponse {
    //     public Group group;
    // }

    // public static class Group {
    //     public String id;
    //     public String name;
    // }

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

 @SuppressWarnings("null")
public Map<String, Set<String>> getRolePermissions(String roleName) {

            resetDatabaseSession();
           String sql = "techbd_udi_ingress.idp_get_login_role_permissions(?)::text";
          String response = dsl.select(
                  DSL.field(sql, String.class, roleName)
         ).fetchOneInto(String.class);

        LOG.info("Permissions fetched for role {}: {}", roleName, response);

        if (response == null || response.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            Map<String, List<String>> parsed = objectMapper.readValue(response,
                    new TypeReference<Map<String, List<String>>>() {});

            return parsed.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> new HashSet<>(e.getValue())
                    ));

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse role permissions JSON from DB", e);
        }
    }
    public void setRoleFromCurrentUser(DefaultOAuth2User user) {
    try {
        String tokenJson = objectMapper.writeValueAsString(user.getAttributes());

        setRole(dsl, tokenJson);

            // Set role on reader (if enabled)
            if (secondaryDsl != null) {
                setRole(secondaryDsl, tokenJson);
            }

    } catch (Exception e) {
        LOG.error("Failed to set PostgreSQL role from current user", e);
        throw new RuntimeException(e);
    }
}

  private void setRole(DSLContext dsl, String tokenJson) {
        dsl.fetch(
            "SELECT techbd_udi_ingress.set_role_based_on_fusion_token(?::jsonb)",
            tokenJson
        );
    }

  public void resetDatabaseSession() {

        reset(dsl);
        if (secondaryDsl != null) {
            reset(secondaryDsl);
        }
    }
    
    private void reset(DSLContext dsl) {
        dsl.execute("RESET ROLE");
        dsl.execute("RESET jwt.claims.user_roles");
        dsl.execute("RESET jwt.claims.admin_roles");
        dsl.execute("RESET jwt.claims.tenants");
    }
   
}