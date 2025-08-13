package org.techbd.ingest.listener;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.MessageProcessorService;

public class MllpRouteFactoryTest {
   
    @Test
    public void testFactoryCreatesMllpRoute() {
        MessageProcessorService messageProcessorService = mock(MessageProcessorService.class);
        AppConfig appConfig = mock(AppConfig.class);
        MllpRouteFactory factory = new MllpRouteFactory(messageProcessorService, appConfig);

        MllpRoute route = factory.create(2575);

        assertNotNull(route);
    }

}
