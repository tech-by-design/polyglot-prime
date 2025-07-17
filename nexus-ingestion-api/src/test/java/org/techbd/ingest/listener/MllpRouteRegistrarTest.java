package org.techbd.ingest.listenerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.techbd.ingest.listener.MllpRouteFactory;
import org.techbd.ingest.listener.MllpRouteRegistrar;
import org.techbd.ingest.service.router.IngestionRouter;

public class MllpRouteRegistrarTest {

private MllpRouteFactory factory;
private ConfigurableBeanFactory beanFactory;

@BeforeEach
void setUp() {
    IngestionRouter mockRouter = mock(IngestionRouter.class);
    factory = new MllpRouteFactory(mockRouter);
    beanFactory = mock(ConfigurableBeanFactory.class);
}

@Test
public void testRegisterMllpRoutes() {
    MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory);

    ReflectionTestUtils.setField(registrar, "portsRaw", "2575,2576,2577");

    List<RouteBuilder> routes = registrar.registerMllpRoutes();
    assertEquals(3, routes.size());

    verify(beanFactory, times(3)).registerSingleton(anyString(), any(RouteBuilder.class));
  }

  @Test
public void testNoRoutesWhenPortsRawIsNull() {
    MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory);
    ReflectionTestUtils.setField(registrar, "portsRaw", null);

    List<RouteBuilder> result = registrar.registerMllpRoutes();

    assertTrue(result.isEmpty(), "Expected no routes to be registered when portsRaw is null");
    verifyNoInteractions(beanFactory);
}

@Test
public void testNoRoutesWhenPortsRawIsBlank() {
    MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory);
    ReflectionTestUtils.setField(registrar, "portsRaw", "   "); // blank string

    List<RouteBuilder> result = registrar.registerMllpRoutes();

    assertTrue(result.isEmpty(), "Expected no routes to be registered when portsRaw is blank");
    verifyNoInteractions(beanFactory);
  }
}
