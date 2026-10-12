package org.techbd.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight process health endpoint for external uptime probes. */
@RestController
public class HealthCheckController {

    @RequestMapping(value = "/healthcheck", method = { RequestMethod.GET, RequestMethod.HEAD })
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body("UP");
    }
}
