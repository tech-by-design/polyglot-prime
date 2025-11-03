package org.techbd.ingest.listener;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;

public class MllpRouteFactoryTest {

    @Test
    public void testFactoryCreatesMllpRoute() {
        MessageProcessorService svc = mock(MessageProcessorService.class);
        AppConfig cfg = mock(AppConfig.class);
        AppLogger appLogger = mock(AppLogger.class);
        PortConfig portConfig = mock(PortConfig.class); // provide mock portConfig for new ctor

        MllpRouteFactory factory = new MllpRouteFactory(svc, cfg, appLogger, portConfig);
        MllpRoute route = factory.create(2575);
        assertNotNull(route);
    }
}
