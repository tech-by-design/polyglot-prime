package org.techbd.ingest.listener;

import lombok.extern.slf4j.Slf4j;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class MllpRouteRegistrar {

    @Value("${HL7_MLLP_PORTS}")
    private String portsRaw;

    private final MllpRouteFactory routeFactory;
    private final ConfigurableBeanFactory beanFactory;

    public MllpRouteRegistrar(MllpRouteFactory routeFactory, ConfigurableBeanFactory beanFactory) {
        this.routeFactory = routeFactory;
        this.beanFactory = beanFactory;
    }

    @Bean
    public List<RouteBuilder> registerMllpRoutes() {
        if (portsRaw == null || portsRaw.isBlank()) {
            log.warn("HL7_MLLP_PORTS is empty — no MLLP routes will be created.");
            return List.of();
        }

        return Arrays.stream(portsRaw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(port -> {
                    MllpRoute route = routeFactory.create(port);
                    String beanName = "mllpRoute_" + port;
                    beanFactory.registerSingleton(beanName, route);
                    log.info("Registered MllpRoute bean '{}' for port {}", beanName, port);
                    return (RouteBuilder) route;
                })
                .toList();
    }
}