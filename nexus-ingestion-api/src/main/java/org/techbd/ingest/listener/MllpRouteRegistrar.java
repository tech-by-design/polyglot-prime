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
        if (portConfig == null || !portConfig.isLoaded()) {
            log.warn("PortConfig not available or not loaded — no routes will be created.");
            return List.of();
        }

        List<PortConfig.PortEntry> entries = portConfig.getPortConfigurationList();
        if (entries == null || entries.isEmpty()) {
            log.warn("PortConfig has no entries — no routes will be created.");
            return List.of();
        }

        List<RouteBuilder> routes = new ArrayList<>();

        // Register TCP route for every entry with protocol == TCP.
        // TcpRoute will look up the destination port at runtime and decide MLLP vs plain TCP response.
        List<PortConfig.PortEntry> tcpEntries = entries.stream()
                .filter(e -> e != null)
                .filter(e -> {
                    String proto = e.protocol == null ? "" : e.protocol.trim();
                    return "tcp".equalsIgnoreCase(proto);
                })
                .collect(Collectors.toList());

        for (PortConfig.PortEntry entry : tcpEntries) {
            int port = entry.port;
            try {
                TcpRoute route = tcpFactory.create(port);
                String beanName = "tcpRoute_" + port;
                beanFactory.registerSingleton(beanName, route);
                log.info("Registered TCP route bean '{}' for port {} (responseType='{}')", beanName, port, entry.responseType);
                routes.add(route);
            } catch (Exception ex) {
                log.error("Failed to create/register TCP route for port {} : {}", port, ex.getMessage(), ex);
                throw new IllegalStateException("Failed to initialize TCP route on port " + port, ex);
            }
        }

        // register single dispatcher route (listens on dispatcherPort)
        try {
            TcpDispatcherRoute dispatcher = new TcpDispatcherRoute(dispatcherPort, messageProcessorService, appConfig, appLogger, portConfig);
            String beanName = "tcpDispatcher_" + dispatcherPort;
            beanFactory.registerSingleton(beanName, dispatcher);
            routes.add(dispatcher);
            log.info("Registered TCP dispatcher route bean '{}' listening on port {}", beanName, dispatcherPort);
        } catch (Exception ex) {
            log.error("Failed to create/register TCP dispatcher on port {} : {}", dispatcherPort, ex.getMessage(), ex);
            // continue — we already registered per-port routes where possible
        }

        if (routes.isEmpty()) {
            log.warn("No TCP routes were registered from PortConfig.");
        }

        return routes;
    }
}