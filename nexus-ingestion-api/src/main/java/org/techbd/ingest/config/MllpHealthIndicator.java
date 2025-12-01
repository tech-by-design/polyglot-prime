package org.techbd.ingest.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Health indicator that checks the availability of configured MLLP ports on
 * localhost.
 */
@Component
public class MllpHealthIndicator implements HealthIndicator {

    private final PortConfig portConfig;

    public MllpHealthIndicator(PortConfig portConfig) {
        this.portConfig = portConfig;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        if (portConfig == null || !portConfig.isLoaded()) {
            details.put("MLLP Ports", Map.of());
            return Health.up().withDetails(details).build();
        }

        List<Integer> ports = portConfig.getMllpPorts();
        Map<String, String> mllpStatus = new HashMap<>();

        for (Integer port : ports) {
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