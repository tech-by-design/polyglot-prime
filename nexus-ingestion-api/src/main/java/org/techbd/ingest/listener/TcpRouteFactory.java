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

    @Autowired
    public TcpRouteFactory(MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.appLogger = appLogger;
        this.portConfig = portConfig;
    }

    public TcpRoute create(int port) {
        return new TcpRoute(port, messageProcessorService, appConfig, appLogger, portConfig);
    }
}