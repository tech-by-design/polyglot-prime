package org.techbd.service.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import java.util.Map;
import java.util.Set;

@Component
public class RolePermissionInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RolePermissionInterceptor.class);

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

        String role = (String) session.getAttribute("userRole");
        if ("tenant_admin".equalsIgnoreCase(role)) {
            return true; // tenant admin can access everything
        }

        Map<String, Set<String>> rolePermissions =
                (Map<String, Set<String>>) session.getAttribute("rolePermissions");

        if (rolePermissions == null || rolePermissions.isEmpty()) {
            LOG.warn("Role {} has no permissions, blocking access to {}", role, uri);
            response.sendRedirect("/login");
            return false;
        }

        RouteMapping rm = RouteRegistry.getRouteMapping(uri);
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
        response.sendRedirect("/login");
        return false;
    }
}
