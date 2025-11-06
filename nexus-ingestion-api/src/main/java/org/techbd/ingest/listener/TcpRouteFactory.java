package org.techbd.ingest.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;

/**
 * Factory to create plain TCP routes (non-MLLP).
 */
@Component
public class TcpRouteFactory {

    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final AppLogger appLogger;
    private final PortConfig portConfig;
    private final int dispatcherPort;

    @Autowired
    public TcpRouteFactory(MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.appLogger = appLogger;
        this.portConfig = portConfig;
        
        // Read TCP_DISPATCHER_PORT from environment variable
        String val = System.getenv("TCP_DISPATCHER_PORT");
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("Required environment variable TCP_DISPATCHER_PORT is not set. Please set TCP_DISPATCHER_PORT to the dispatcher listen port.");
        }
        try {
            this.dispatcherPort = Integer.parseInt(val.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalStateException("Invalid TCP_DISPATCHER_PORT value: '" + val + "'. Must be a valid integer port number.", nfe);
        }
    }

    public TcpRoute create() {
        return new TcpRoute(dispatcherPort, messageProcessorService, appConfig, appLogger, portConfig);
    }
}