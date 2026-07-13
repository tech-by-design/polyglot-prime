package org.techbd.fhir.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.techbd.corelib.service.tenant.TenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Tech by Design Hub Tenant Endpoints", description = "Tech by Design Hub Tenant Endpoints")
public class TenantController {

    private static final Logger LOG = LoggerFactory.getLogger(TenantController.class);

    private final TenantService tenantService;

    public TenantController(final TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping(value = "/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List active tenants", description = "Fetches the list of currently active tenants.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful response"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<List<Map<String, Object>>> getActiveTenants() {

        final String interactionId = UUID.randomUUID().toString();

        LOG.info("TENANT-CONTROLLER Received request to list active tenants | interactionId={}", interactionId);

        try {
            final List<Map<String, Object>> tenants = tenantService.getActiveTenants(interactionId);

            LOG.info("TENANT-CONTROLLER Returning {} active tenants | interactionId={}",
                    tenants.size(), interactionId);

            return ResponseEntity.ok(tenants);
        } catch (Exception e) {
            LOG.error("TENANT-CONTROLLER Error listing active tenants | interactionId={} : {}",
                    interactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}