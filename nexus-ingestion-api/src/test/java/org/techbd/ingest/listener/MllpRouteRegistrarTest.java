package org.techbd.ingest.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.env.Environment;

import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

@ExtendWith(MockitoExtension.class)
public class MllpRouteRegistrarTest {

    @Mock
    MllpRouteFactory mllpFactory;

    @Mock
    TcpRouteFactory tcpFactory;

    @Mock
    ConfigurableBeanFactory beanFactory;

    @Mock
    MessageProcessorService messageProcessorService;

    @Mock
    AppConfig appConfig;

    @Mock
    AppLogger appLogger;

    @Mock
    TemplateLogger templateLogger;

    @Mock
    Environment env;

    @Test
    public void testRegisterMllpRoutesRegistersDispatcherAndTcpRoutes() throws Exception {
        // prepare PortConfig mock with one TCP entry (reflection to create PortEntry)
        PortConfig portConfig = mock(PortConfig.class);
        when(portConfig.isLoaded()).thenReturn(true);

        Class<?> entryClass = Class.forName("org.techbd.ingest.config.PortConfig$PortEntry");
        Object entry = entryClass.getDeclaredConstructor().newInstance();
        entryClass.getField("protocol").set(entry, "tcp");
        entryClass.getField("responseType").set(entry, "mllp");
        entryClass.getField("port").set(entry, 2576);

        // satisfy generics by casting
        when(portConfig.getPortConfigurationList()).thenReturn((List) List.of(entry));

        // tcpFactory returns a mocked TcpRoute
        TcpRoute mockedTcpRoute = mock(TcpRoute.class);
        when(tcpFactory.create(2576)).thenReturn(mockedTcpRoute);

        // appLogger -> templateLogger
        when(appLogger.getLogger(any(Class.class))).thenReturn(templateLogger);

        // environment provides dispatcher port
        when(env.getProperty("TCP_DISPATCHER_PORT")).thenReturn("2575");

        // instantiate registrar with all required mocks and Environment
        MllpRouteRegistrar registrar = new MllpRouteRegistrar(
                mllpFactory,
                tcpFactory,
                beanFactory,
                portConfig,
                messageProcessorService,
                appConfig,
                appLogger,
                env
        );

        // Call registration
        var routes = registrar.registerMllpRoutes();

        // Basic assertions
        assertNotNull(routes, "routes list must not be null");
        assertTrue(routes.size() >= 1, "at least one route expected (dispatcher)");

        // verify per-port route registration happened and dispatcher registered
        verify(beanFactory).registerSingleton(eq("tcpRoute_2576"), any());
        verify(beanFactory).registerSingleton(eq("tcpDispatcher_2575"), any());
    }
}
