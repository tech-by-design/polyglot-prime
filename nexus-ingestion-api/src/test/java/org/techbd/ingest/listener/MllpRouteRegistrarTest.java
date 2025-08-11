package org.techbd.ingest.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.techbd.ingest.service.MessageProcessorService;
public class MllpRouteRegistrarTest {

  private MllpRouteFactory factory;
  private ConfigurableBeanFactory beanFactory;

  @BeforeEach
  void setUp() {
    MessageProcessorService mockRouter = mock(MessageProcessorService.class);
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
    ReflectionTestUtils.setField(registrar, "portsRaw", "   "); 

    List<RouteBuilder> result = registrar.registerMllpRoutes();

    assertTrue(result.isEmpty(), "Expected no routes to be registered when portsRaw is blank");
    verifyNoInteractions(beanFactory);
  }

  @Test
  public void testRegisterMllpRoutesWithPortRange() {
    MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory);
    ReflectionTestUtils.setField(registrar, "portsRaw", "2575-2577"); 

    List<RouteBuilder> routes = registrar.registerMllpRoutes();

    assertEquals(3, routes.size(), "Expected 3 routes to be registered from range 2575-2577");
    verify(beanFactory, times(3)).registerSingleton(anyString(), any(RouteBuilder.class));
  }

  @Test
  public void testRegisterMllpRoutesWithMixedPortsAndRange() {
    MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory);
    ReflectionTestUtils.setField(registrar, "portsRaw", "2575,2578-2580,2582");

    List<RouteBuilder> routes = registrar.registerMllpRoutes();

    assertEquals(5, routes.size(), "Expected 5 routes to be registered from ports and range");
    verify(beanFactory, times(5)).registerSingleton(anyString(), any(RouteBuilder.class));
  }

 @Test
public void testFailureDuringRouteInitialization() {
    MllpRouteFactory failingFactory = mock(MllpRouteFactory.class);
    ConfigurableBeanFactory mockBeanFactory = mock(ConfigurableBeanFactory.class);

    MllpRoute route2575 = mock(MllpRoute.class);
    when(failingFactory.create(2575)).thenReturn(route2575);
    when(failingFactory.create(2576)).thenThrow(new RuntimeException("Port in use"));

    MllpRouteRegistrar registrar = new MllpRouteRegistrar(failingFactory, mockBeanFactory);
    ReflectionTestUtils.setField(registrar, "portsRaw", "2575,2576");
    IllegalStateException thrown = assertThrows(IllegalStateException.class, registrar::registerMllpRoutes);
    assertTrue(thrown.getMessage().contains("2576"));
    assertTrue(thrown.getCause().getMessage().contains("Port in use"));
    verify(mockBeanFactory).registerSingleton(anyString(), eq(route2575));
}

}
