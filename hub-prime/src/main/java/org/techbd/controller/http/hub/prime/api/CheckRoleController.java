package org.techbd.controller.http.hub.prime.api;

import org.jooq.DSLContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.techbd.service.http.GitHubUserAuthorizationFilter;
import org.techbd.service.http.InteractionsFilter;

import jakarta.servlet.http.HttpServletRequest;

import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/debug")
public class CheckRoleController {

  private final DSLContext dsl;
      private static final Logger LOG = LoggerFactory.getLogger(InteractionsFilter.class.getName());

 public CheckRoleController(DSLContext dsl) {
     this.dsl = dsl;}

     @GetMapping("/db-role")
    public Map<String, Object> getCurrentDbRole(HttpServletRequest request) {
        LOG.info("Received request to check current DB role");

          Map<String, Object> response = new HashMap<>();
         String role = GitHubUserAuthorizationFilter.getAuthenticatedUser(request)
        .map(authUser -> {
            Object roleAttr = authUser.principal().getAttributes().get("role");

            if (roleAttr instanceof java.util.Collection<?> roles && !roles.isEmpty()) {
                // FusionAuth: roles is a list
                return roles.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            } else if (roleAttr != null) {
                // Single role (string)
                return roleAttr.toString();
            } else {
                // Fall back to Spring authorities
                return authUser.principal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","));
            }
        })
        .orElse("NO_ROLE");
        LOG.info("Determined role to set/check: {}", role);
        try {
            if (role != null && !role.isBlank()) {
                String setRoleSql = "SET ROLE \"" + role + "\"";
                LOG.info("Executing SQL: {}", setRoleSql);
                dsl.execute(setRoleSql);
                response.put("setRoleAttempted", role);
            } else {
                response.put("setRoleAttempted", "none (only checking current role)");
            }

            String sql = "SELECT current_user, session_user, current_role";
            LOG.debug("Executing SQL: {}", sql);
            Record record = dsl.fetchOne(sql);

            if (record != null) {
                Map<String, Object> result = record.intoMap();
                LOG.info("DB role query result: {}", result);
                response.put("dbResult", result);
            } else {
                LOG.warn("DB role query returned no result");
                response.put("error", "No result returned");
            }

        } catch (Exception e) {
            LOG.error("Error while setting/checking DB role", e);
            response.put("error", e.getMessage());
        }

        return response;
    }
    }
