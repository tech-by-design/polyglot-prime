package org.techbd.ingest.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.config.AppConfig;

public class MllpRouteRegistrarTest {

    private MllpRouteFactory factory;
    private ConfigurableBeanFactory beanFactory;
    private PortConfig portConfig;

    @BeforeEach
    void setUp() {
        MessageProcessorService mockRouter = mock(MessageProcessorService.class);
        AppConfig config = mock(AppConfig.class);
        AppLogger appLogger = mock(AppLogger.class);
        factory = new MllpRouteFactory(mockRouter, config, appLogger);
        beanFactory = mock(ConfigurableBeanFactory.class);

        portConfig = mock(PortConfig.class);
    }

    @Test
    public void testRegisterMllpRoutes() {
        // portConfig loaded and returns three mllp ports
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getMllpPorts()).thenReturn(List.of(2575, 2576, 2577));

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory, portConfig);

        List<RouteBuilder> routes = registrar.registerMllpRoutes();
        assertEquals(3, routes.size());

        verify(beanFactory, times(3)).registerSingleton(anyString(), any(RouteBuilder.class));
    }

    @Test
    public void testNoRoutesWhenPortConfigNotLoaded() {
        // PortConfig not loaded -> no routes
        when(portConfig.isLoaded()).thenReturn(false);

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory, portConfig);

        List<RouteBuilder> result = registrar.registerMllpRoutes();

        assertTrue(result.isEmpty(), "Expected no routes to be registered when port config is not loaded");
        verifyNoInteractions(beanFactory);
    }

    @Test
    public void testNoRoutesWhenPortConfigLoadedButEmpty() {
        // PortConfig loaded but returns no mllp ports -> no routes
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getMllpPorts()).thenReturn(List.of());

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory, portConfig);

        List<RouteBuilder> result = registrar.registerMllpRoutes();

        assertTrue(result.isEmpty(), "Expected no routes to be registered when port config is loaded but empty");
        verifyNoInteractions(beanFactory);
    }

    @Test
    public void testRegisterMllpRoutesWithPortRange() {
        // portConfig loaded and returns a range of mllp ports
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getMllpPorts()).thenReturn(List.of(2575, 2576, 2577));

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory, portConfig);

        List<RouteBuilder> routes = registrar.registerMllpRoutes();

        assertEquals(3, routes.size(), "Expected 3 routes to be registered from range 2575-2577");
        verify(beanFactory, times(3)).registerSingleton(anyString(), any(RouteBuilder.class));
    }

    @Test
    public void testRegisterMllpRoutesWithMixedPortsAndRange() {
        // portConfig loaded and returns mixed mllp ports and a range
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getMllpPorts()).thenReturn(List.of(2575, 2578, 2579, 2580, 2582));

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(factory, beanFactory, portConfig);

        List<RouteBuilder> routes = registrar.registerMllpRoutes();

        assertEquals(5, routes.size(), "Expected 5 routes to be registered from ports and range");
        verify(beanFactory, times(5)).registerSingleton(anyString(), any(RouteBuilder.class));
    }
}
