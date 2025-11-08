package org.techbd.ingest.util;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Utility class for accessing port configuration entries in a safe and consistent way.
 */
@Component
public class PortConfigUtil {

    private static final Logger log = LoggerFactory.getLogger(PortConfigUtil.class);

    private final PortConfig portConfig;

    @Autowired
    public PortConfigUtil(PortConfig portConfig) {
        this.portConfig = portConfig;
    }

    /**
     * Reads the PortEntry configuration for a given port.
     *
     * @param port the port number to look up
     * @return Optional containing the PortEntry if found, otherwise empty
     */
    public Optional<PortEntry> readPortEntry(int port) {
        if (port <= 0) {
            log.warn("PortConfigUtil: Invalid port number {}", port);
            return Optional.empty();
        }

        try {
            Optional<PortEntry> entry = portConfig.findEntryForPort(port);
            if (entry.isPresent()) {
                log.info("PortConfigUtil: Found PortEntry for port {} -> {}", port, entry.get());
            } else {
                log.warn("PortConfigUtil: No configuration found for port {}", port);
            }
            return entry;
        } catch (Exception e) {
            log.error("PortConfigUtil: Error reading PortEntry for port {}", port, e);
            return Optional.empty();
        }
    }
}
