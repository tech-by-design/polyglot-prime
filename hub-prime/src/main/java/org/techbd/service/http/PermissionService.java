package org.techbd.service.http;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.service.http.hub.prime.route.RoutesTree.HtmlAnchor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;



@Service
public class PermissionService {

     private static final Logger LOG = LoggerFactory.getLogger(FusionAuthUsersService.class);
     private final DSLContext dsl;

     public PermissionService(DSLContext dsl) {
            this.dsl = dsl;
         }

     public record Role(Integer role_id, String role_code, String role_name) {}

     public String getRoleMenusWithPermissions(int roleId) {

      try {
            String response = dsl.select(
                    DSL.field("auth.get_role_menus_with_permissions({0})", String.class, roleId)
            ).fetchOneInto(String.class);

            LOG.info("Permissions fetched for roleId {}: {}", roleId, response);
            return response;
        } catch (Exception ex) {
            LOG.error("Error while fetching role menus with permissions for roleId {}: {}", roleId, ex.getMessage(), ex);
            return "{\"status\":\"error\",\"message\":\"Unable to fetch role permissions\"}";
        }
    }

     public List<Role> getRoles() {
        try {
            List<Role> roles = dsl
                    .selectFrom(DSL.table("auth.vw_roles"))
                    .fetchInto(Role.class); // jOOQ maps directly to record

            LOG.info("Roles fetched: {}", roles);
            return roles;
        } catch (Exception ex) {
            LOG.error("Error while fetching roles: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

      public String saveRolePermissions(int roleId, String dataJson, LocalDateTime createdAt, String createdBy) {
      try {
        String sql = "SELECT auth.insert_role_menus_with_permissions(?, ?::jsonb, ?, ?)";
        return dsl.fetchValue(sql, roleId, dataJson, Timestamp.valueOf(createdAt), createdBy)
                  .toString();
       } catch (Exception ex) {
          LOG.error("Error saving permissions for roleId {}: {}", roleId, ex.getMessage(), ex);
          return "{\"status\":\"error\",\"message\":\"Unable to save permissions\"}";
      }
   } 

    @SuppressWarnings("unchecked")
    public List<HtmlAnchor> filterLinksByRole(List<HtmlAnchor> allLinks, HttpServletRequest request) {
        if (allLinks == null || allLinks.isEmpty()) {
            return List.of();
        }
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                LOG.warn("No session found. Returning empty links.");
                return List.of();
            }

            String role = (String) session.getAttribute("userRole");
            if ("tenant_admin".equalsIgnoreCase(role)) {
                return allLinks;
            }
            
            Map<String, Set<String>> allowedMenus = 
                (Map<String, Set<String>>) session.getAttribute("rolePermissions");

            if (allowedMenus == null || allowedMenus.isEmpty()) {
                LOG.warn("Role {} has no permissions configured. Returning empty links.", role);
                return List.of();
            }
            Set<String> allowedTopMenus = allowedMenus.keySet();
            return allLinks.stream()
                    .filter(link -> allowedTopMenus.contains(link.text()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.error("Error while filtering links by role", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public boolean isAllowedForRole(String linkText, HttpServletRequest request) {
        if (linkText == null || linkText.isBlank()) {
            return false;
        }
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                LOG.warn("No session found. Denying access to {}", linkText);
                return false;
            }

            String role = (String) session.getAttribute("userRole");
            if ("tenant_admin".equalsIgnoreCase(role)) {
                return true;
            }

            Map<String, Set<String>> allowedMenus = 
                (Map<String, Set<String>>) session.getAttribute("rolePermissions");

            if (allowedMenus == null || allowedMenus.isEmpty()) {
                LOG.warn("Role {} has no permissions configured. Denying access to {}", role, linkText);
                return false;
            }
            return allowedMenus.values().stream()
                    .anyMatch(subMenus -> subMenus.contains(linkText));

        } catch (Exception e) {
            LOG.error("Error while checking access for link {}", linkText, e);
            return false;
        }
    }   
}
