package org.techbd.ingest.listener;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.util.AppLogger;

@Configuration
@Slf4j
public class MllpRouteRegistrar {

    private final MllpRouteFactory mllpFactory;
    private final TcpRouteFactory tcpFactory;
    private final ConfigurableBeanFactory beanFactory;
    private final PortConfig portConfig;
    private final int dispatcherPort;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final AppLogger appLogger;

    public MllpRouteRegistrar(MllpRouteFactory mllpFactory,
            TcpRouteFactory tcpFactory,
            ConfigurableBeanFactory beanFactory,
            PortConfig portConfig,
            MessageProcessorService messageProcessorService,
            AppConfig appConfig,
            AppLogger appLogger,
            Environment env) {
        this.mllpFactory = mllpFactory;
        this.tcpFactory = tcpFactory;
        this.beanFactory = beanFactory;
        this.portConfig = portConfig;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.appLogger = appLogger;

        // Require TCP_DISPATCHER_PORT to be explicitly set; do not fall back to any default.
        String val = (env != null) ? env.getProperty("TCP_DISPATCHER_PORT") : null;
        if (val == null || val.isBlank()) {
            val = System.getenv("TCP_DISPATCHER_PORT");
        }
        if (val == null || val.isBlank()) {
            // Fail fast — indicate configuration error clearly so the application returns HTTP 500 on requests.
            throw new IllegalStateException("Required environment variable TCP_DISPATCHER_PORT is not set. Please set TCP_DISPATCHER_PORT to the dispatcher listen port.");
        }
        try {
            this.dispatcherPort = Integer.parseInt(val.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalStateException("Invalid TCP_DISPATCHER_PORT value: '" + val + "'. Must be a valid integer port number.", nfe);
        }
    }

    @Bean
    public List<RouteBuilder> registerMllpRoutes() {
        List<RouteBuilder> routes = new ArrayList<>();

        // Register single dispatcher route (listens on dispatcherPort from TCP_DISPATCHER_PORT environment variable)
        try {
            TcpRoute tcpRoute = tcpFactory.create();
            String beanName = "tcpRoute_" + dispatcherPort;
            beanFactory.registerSingleton(beanName, tcpRoute);
            routes.add(tcpRoute);
            log.info("Registered TCP route bean '{}' listening on port {} (from TCP_DISPATCHER_PORT)", beanName, dispatcherPort);
        } catch (Exception ex) {
            log.error("Failed to create/register TCP route on port {} : {}", dispatcherPort, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to initialize TCP route on port " + dispatcherPort, ex);
        }

        return routes;
    }
}
