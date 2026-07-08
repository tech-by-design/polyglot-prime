package org.techbd.service.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import java.util.Map;
import java.util.Set;

@Component
public class RolePermissionInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RolePermissionInterceptor.class);

    @Value("${AUTH_PROVIDER:github}")
    private String authProvider;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String uri = request.getRequestURI();
        HttpSession session = request.getSession(false);

        if (session == null) {
            LOG.warn("No session found, blocking access to {}", uri);
            response.sendRedirect("/login");
            return false;
        }
        Boolean isSuperRole = (Boolean) session.getAttribute(Constant.SUPER_ROLE);

        if (uri.startsWith("/settings")
                && Boolean.TRUE.equals(isSuperRole)
                && "github".equalsIgnoreCase(authProvider)) {

            LOG.warn("Access denied");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        if (Boolean.TRUE.equals(isSuperRole)) {
            return true;
        }

        // if ("tenant_admin".equalsIgnoreCase(role)) {
        // return true; // tenant admin can access everything
        // }
        String role = (String) session.getAttribute(Constant.USER_ROLE);

        Map<String, Set<String>> rolePermissions = (Map<String, Set<String>>) session
                .getAttribute(Constant.ROLE_PERMISSIONS);
        RouteMapping rm = RouteRegistry.getRouteMapping(uri);
        if (rm != null) {
            String label = rm.label();

            if ("Settings".equals(label) || "Profile".equals(label)) {
                return true;
            }

            // Roles page is restricted to Super Admin only
            if ("Roles".equals(label)) {
                LOG.warn("Access denied for role {} to Roles page", role);
                response.sendRedirect("/settings/profile");
                return false;
            }
        }
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            LOG.warn("Role {} has no permissions, blocking access to {}", role, uri);
            response.sendRedirect("/logout");
            return false;
        }

        if (rm == null) {
            LOG.debug("No RouteMapping found for {}, allowing by default", uri);
            return true; // allow non-annotated routes
        }

        String label = rm.label(); // get the tab/menu name

        // Check if label exists as a key in rolePermissions
        if (rolePermissions.containsKey(label)) {
            LOG.debug("Access granted for role {} to menu/tab '{}'", role, label);
            return true;
        }

        // Check if label exists in any of the values (submenus) in rolePermissions
        boolean allowedInValues = rolePermissions.values().stream()
                .anyMatch(subMenus -> subMenus.contains(label));

        if (allowedInValues) {
            LOG.debug("Access granted for role {} to submenu '{}'", role, label);
            return true;
        }

        LOG.warn("Access denied for role {} to tab '{}'", role, label);
        response.sendRedirect("/home");
        return false;
    }
}
