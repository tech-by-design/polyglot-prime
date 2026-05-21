package org.techbd.controller;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.RestController;
import org.techbd.component.SessionRegistry;
import org.techbd.service.http.FusionAuthUserAuthorizationFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/fusionauth")
public class FusionAuthWebhookController {

    @Autowired
    private SessionRegistry sessionRegistry;
    private static final Logger LOG = LoggerFactory.getLogger(FusionAuthUserAuthorizationFilter.class);

    @SuppressWarnings("unchecked")
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {

        LOG.info("Webhook received: {}", payload);

        Map<String, Object> event = (Map<String, Object>) payload.get("event");

        if (event == null) {
            return ResponseEntity.ok().build();
        }

        String type = (String) event.get("type");

        // Only handle lock/deactivate event
        if ("user.deactivate".equals(type) || "user.delete".equals(type)) {

            Map<String, Object> user = (Map<String, Object>) event.get("user");

            if (user != null) {
                Boolean active = (Boolean) user.get("active");
                String userId = (String) user.get("id");

                if (Boolean.FALSE.equals(active)) {

                    LOG.info("User locked, invalidating sessions for userId: {}", userId);

                    Set<HttpSession> sessions = sessionRegistry.getSessions(userId);

                    for (HttpSession session : sessions) {
                        session.invalidate();
                        sessionRegistry.removeSession(userId, session);
                    }
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}