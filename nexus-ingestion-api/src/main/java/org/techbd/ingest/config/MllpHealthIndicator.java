package org.techbd.ingest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator that checks the availability of configured MLLP ports on
 * localhost.
 * <p>
 * Attempts a socket connection to each port defined in {@code HL7_MLLP_PORTS}.
 * Reports the status as UP if the port is reachable, otherwise DOWN.
 * </p>
 */
@Component
public class MllpHealthIndicator implements HealthIndicator {

    @Value("${HL7_MLLP_PORTS:2575}")
    private String mllpPorts;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        // Test MLLP ports
        String[] ports = mllpPorts.split(",");
        Map<String, String> mllpStatus = new HashMap<>();

        for (String portStr : ports) {
            int port = Integer.parseInt(portStr.trim());
            try (Socket socket = new Socket("localhost", port)) {
                mllpStatus.put("Port " + port, "UP");
            } catch (Exception e) {
                mllpStatus.put("Port " + port, "DOWN");
            }
        }

        details.put("MLLP Ports", mllpStatus);
        return Health.up().withDetails(details).build();
    }
}