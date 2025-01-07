package org.techbd.service.http;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "otel")
@Getter
@Setter
public class OpenTelemetryProperties {
    private TracesProperties traces;
    private MetricsProperties metrics;

    @Getter
    @Setter
    public static class TracesProperties {
        private String exporter;
        private OtlpProperties otlp;
    }

    @Getter
    @Setter
    public static class MetricsProperties {
        private String exporter;
        private OtlpProperties otlp;
    }

    @Getter
    @Setter
    public static class OtlpProperties {
        private String endpoint;
        private Map<String, String> headers;
        private boolean enabled;
        private String authorizationTokenSecretName;
    }
}