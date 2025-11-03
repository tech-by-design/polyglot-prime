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
import java.util.ArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.config.AppConfig;

public class MllpRouteRegistrarTest {

    private MllpRouteFactory mllpFactory;
    private TcpRouteFactory tcpFactory;
    private ConfigurableBeanFactory beanFactory;
    private PortConfig portConfig;

    @BeforeEach
    void setUp() {
        MessageProcessorService mockRouter = mock(MessageProcessorService.class);
        AppConfig config = mock(AppConfig.class);
        AppLogger appLogger = mock(AppLogger.class);
        portConfig = mock(PortConfig.class);

        // create factories with mocks
        mllpFactory = new MllpRouteFactory(mockRouter, config, appLogger, portConfig);
        tcpFactory = new TcpRouteFactory(mockRouter, config, appLogger, portConfig);

        beanFactory = mock(ConfigurableBeanFactory.class);
    }

    @Test
    public void testRegisterMllpRoutes() {
        // portConfig loaded and returns three mllp ports entries
        when(portConfig.isLoaded()).thenReturn(true);
        List<PortConfig.PortEntry> entries = new ArrayList<>();
        PortConfig.PortEntry e1 = new PortConfig.PortEntry(); e1.port = 2575; e1.protocol = "TCP"; e1.responseType = "mllp";
        PortConfig.PortEntry e2 = new PortConfig.PortEntry(); e2.port = 2576; e2.protocol = "TCP"; e2.responseType = "mllp";
        PortConfig.PortEntry e3 = new PortConfig.PortEntry(); e3.port = 2577; e3.protocol = "TCP"; e3.responseType = "mllp";
        entries.add(e1); entries.add(e2); entries.add(e3);
        when(portConfig.getPortConfigurationList()).thenReturn(entries);

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(mllpFactory, tcpFactory, beanFactory, portConfig);

        List<RouteBuilder> routes = registrar.registerMllpRoutes();
        assertEquals(3, routes.size());

        verify(beanFactory, times(3)).registerSingleton(anyString(), any(RouteBuilder.class));
    }

    @Test
    public void testNoRoutesWhenPortConfigNotLoaded() {
        // PortConfig not loaded -> no routes
        when(portConfig.isLoaded()).thenReturn(false);

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(mllpFactory, tcpFactory, beanFactory, portConfig);

        List<RouteBuilder> result = registrar.registerMllpRoutes();

        assertTrue(result.isEmpty(), "Expected no routes to be registered when port config is not loaded");
        verifyNoInteractions(beanFactory);
    }

    @Test
    public void testNoRoutesWhenPortConfigLoadedButEmpty() {
        // PortConfig loaded but returns no entries -> no routes
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(List.of());

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(mllpFactory, tcpFactory, beanFactory, portConfig);

        List<RouteBuilder> result = registrar.registerMllpRoutes();

        assertTrue(result.isEmpty(), "Expected no routes to be registered when port config is loaded but empty");
        verifyNoInteractions(beanFactory);
    }

    @Test
    public void testRegisterMixedTcpAndMllpRoutes() {
        when(portConfig.isLoaded()).thenReturn(true);
        List<PortConfig.PortEntry> entries = new ArrayList<>();
        PortConfig.PortEntry e1 = new PortConfig.PortEntry(); e1.port = 2575; e1.protocol = "TCP"; e1.responseType = "mllp";
        PortConfig.PortEntry e2 = new PortConfig.PortEntry(); e2.port = 16010; e2.protocol = "TCP"; e2.responseType = ""; // plain TCP
        entries.add(e1); entries.add(e2);
        when(portConfig.getPortConfigurationList()).thenReturn(entries);

        MllpRouteRegistrar registrar = new MllpRouteRegistrar(mllpFactory, tcpFactory, beanFactory, portConfig);

        List<RouteBuilder> routes = registrar.registerMllpRoutes();

        // both ports should be registered (one MLLP, one TCP)
        assertEquals(2, routes.size());
        verify(beanFactory, times(2)).registerSingleton(anyString(), any(RouteBuilder.class));
    }
}
