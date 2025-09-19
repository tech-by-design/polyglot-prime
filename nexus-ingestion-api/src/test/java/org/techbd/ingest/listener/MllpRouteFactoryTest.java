package org.techbd.ingest.listener;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;

public class MllpRouteFactoryTest {
   
    @Test
    public void testFactoryCreatesMllpRoute() {
        MessageProcessorService messageProcessorService = mock(MessageProcessorService.class);
        AppConfig appConfig = mock(AppConfig.class);
        AppLogger appLogger = mock(AppLogger.class);
        MllpRouteFactory factory = new MllpRouteFactory(messageProcessorService, appConfig, appLogger);
        MllpRoute route = factory.create(2575);
        assertNotNull(route);
    }

}
