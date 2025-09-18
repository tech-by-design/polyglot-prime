package org.techbd.ingest.listener;

import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;

@Component
public class MllpRouteFactory {

    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private AppLogger logger;

    public MllpRouteFactory(MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.logger = appLogger;;
    }

    public MllpRoute create(int port) {
        return new MllpRoute(port, messageProcessorService,appConfig, logger);
    }
}

