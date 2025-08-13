package org.techbd.ingest.listener;

import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MllpRouteFactory {

    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    public MllpRouteFactory(MessageProcessorService messageProcessorService, AppConfig appConfig) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
    }

    public MllpRoute create(int port) {
        return new MllpRoute(port, messageProcessorService,appConfig);
    }
}

