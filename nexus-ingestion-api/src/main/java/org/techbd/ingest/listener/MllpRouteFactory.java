package org.techbd.ingest.listener;

import org.springframework.stereotype.Component;
import org.techbd.ingest.service.router.IngestionRouter;

@Component
public class MllpRouteFactory {

    private final IngestionRouter ingestionRouter;

    public MllpRouteFactory(IngestionRouter ingestionRouter) {
        this.ingestionRouter = ingestionRouter;
    }

    public MllpRoute create(int port) {
        return new MllpRoute(port, ingestionRouter);
    }
}

