package org.techbd.controller.http.hub.prime.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@Tag(name = "Tech by Design Hub Tenant Endpoints", description = "Tech by Design Hub Tenant Endpoints")
public class TenantController {

    private static final Logger LOG = LoggerFactory.getLogger(TenantController.class);

    @PostMapping("/tenant/select")
    @ResponseBody
    public ResponseEntity<Void> selectTenant(@RequestBody Map<String, String> body, HttpSession session) {
        String tenant = body.get("tenantName");

        if (tenant != null && !tenant.isBlank()) {
            session.setAttribute("activeTenant", tenant);
            LOG.info("TENANT-CONTROLLER Selected tenant {} for session {}", tenant, session.getId());
        }
        return ResponseEntity.ok().build();
    }
}