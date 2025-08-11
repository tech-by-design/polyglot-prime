package org.techbd.ingest.listener;

import org.springframework.stereotype.Component;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MllpRouteFactory {

    private final MessageProcessorService messageProcessorService;

    public MllpRouteFactory(MessageProcessorService messageProcessorService) {
        this.messageProcessorService = messageProcessorService;
    }

    public MllpRoute create(int port) {
        return new MllpRoute(port, messageProcessorService);
    }
}

