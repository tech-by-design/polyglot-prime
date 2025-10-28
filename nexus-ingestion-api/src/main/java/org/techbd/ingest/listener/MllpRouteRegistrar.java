package org.techbd.ingest.listener;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import org.techbd.ingest.config.PortConfig;

@Configuration
@Slf4j
public class MllpRouteRegistrar {

    private final MllpRouteFactory routeFactory;
    private final ConfigurableBeanFactory beanFactory;
    private final PortConfig portConfig;

    public MllpRouteRegistrar(MllpRouteFactory routeFactory, ConfigurableBeanFactory beanFactory, PortConfig portConfig) {
        this.routeFactory = routeFactory;
        this.beanFactory = beanFactory;
        this.portConfig = portConfig;
    }

    @Bean
    public List<RouteBuilder> registerMllpRoutes() {
        if (portConfig == null || !portConfig.isLoaded()) {
            log.warn("PortConfig not available or not loaded — no MLLP routes will be created.");
            return List.of();
        }

        List<Integer> ports = portConfig.getMllpPorts();
        if (ports == null || ports.isEmpty()) {
            log.warn("No ports in PortConfig configured with responseType='mllp' and protocol='TCP' — no MLLP routes will be created.");
            return List.of();
        }

        return ports.stream()
                .map(port -> {
                    try {
                        MllpRoute route = routeFactory.create(port);
                        String beanName = "mllpRoute_" + port;
                        beanFactory.registerSingleton(beanName, route);
                        log.info("Registered MllpRoute bean '{}' for port {}", beanName, port);
                        return (RouteBuilder) route;
                    } catch (Exception e) {
                        log.error("Failed to create or register MLLP route on port {}: {}", port, e.getMessage(), e);
                        throw new IllegalStateException("Failed to initialize MLLP route on port " + port, e);
                    }
                })
                .collect(Collectors.toList());
    }
}