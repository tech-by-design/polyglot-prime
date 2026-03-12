package org.techbd.csv.controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckController.class);

    /**
     * Health check endpoint using GET/HEAD on root.
     *
     * @return 200 OK if healthy, 503 otherwise.
     */
    @RequestMapping(value = "/", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> healthCheck() {
        try {
            LOG.info("Health check requested via /");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}

