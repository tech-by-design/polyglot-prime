package org.techbd.ingest.listenerTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.listener.MllpRoute;
import org.techbd.ingest.listener.MllpRouteFactory;
import org.techbd.ingest.service.router.IngestionRouter;

public class MllpRouteFactoryTest {
   
    @Test
    public void testFactoryCreatesMllpRoute() {
        IngestionRouter mockRouter = mock(IngestionRouter.class);
        MllpRouteFactory factory = new MllpRouteFactory(mockRouter);

        MllpRoute route = factory.create(2575);

        assertNotNull(route);
    }

}
