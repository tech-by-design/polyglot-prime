package org.techbd.ingest.listener;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

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
        List<Integer> ports = Arrays.stream(portsRaw.split(","))
                .map(String::trim)
                .flatMap((String portSpec) -> {
                    if (portSpec.contains("-")) {
                        String[] range = portSpec.split("-");
                        if (range.length != 2) {
                            throw new IllegalArgumentException("Invalid port range: " + portSpec);
                        }
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start > end) {
                            throw new IllegalArgumentException("Invalid port range (start > end): " + portSpec);
                        }
                        return IntStream.rangeClosed(start, end).mapToObj(Integer::valueOf);
                    } else {
                        return Stream.of(Integer.parseInt(portSpec));
                    }
                })
                .distinct()
                .sorted()
                .toList();

        return ports.stream()
                .map(port -> {
                    try {
                        MllpRoute route = routeFactory.create(port); // may throw
                        String beanName = "mllpRoute_" + port;
                        beanFactory.registerSingleton(beanName, route);
                        log.info("Registered MllpRoute bean '{}' for port {}", beanName, port);
                        return (RouteBuilder) route;
                    } catch (Exception e) {
                        log.error("Failed to create or register MLLP route on port {}: {}", port, e.getMessage(), e);
                        throw new IllegalStateException("Failed to initialize MLLP route on port " + port, e);
                    }
                })
                .toList();
    }
}