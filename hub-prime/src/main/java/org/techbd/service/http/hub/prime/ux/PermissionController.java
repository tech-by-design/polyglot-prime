package org.techbd.service.http.hub.prime.ux;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.service.http.PermissionService;
import org.techbd.service.http.PermissionService.Role;

import com.fasterxml.jackson.databind.ObjectMapper;


@Controller
@RequestMapping("/api")
@ResponseBody
public class PermissionController {
      private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/permissions/{roleId}")
    public ResponseEntity<String> getRolePermissions(@PathVariable int roleId) {
        String response = permissionService.getRoleMenusWithPermissions(roleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/getRoles")
    public ResponseEntity<List<Role>> getRoles() {
        List<Role> response = permissionService.getRoles();
                return ResponseEntity.ok(response);

    }
    @PostMapping("/permissions/{roleId}")
    public ResponseEntity<String> saveRolePermissions(
        @PathVariable int roleId,
        @RequestBody List<Map<String, Object>> permissions, 
        Principal principal
) {
    try {
        // Filter only the permissions that are checked
        List<Map<String, Object>> checkedPermissions = permissions.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("has_permission")))
                .toList();

        // Prepare JSONB for DB function
        ObjectMapper mapper = new ObjectMapper();
        String dataJson = mapper.writeValueAsString(checkedPermissions);

        // Call service
        String response = permissionService.saveRolePermissions(roleId, dataJson,
                LocalDateTime.now(), principal != null ? principal.getName() : "system");

        return ResponseEntity.ok(response);
    } catch (Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"status\":\"error\",\"message\":\"Failed to save permissions\"}");
    }
}
    
}
