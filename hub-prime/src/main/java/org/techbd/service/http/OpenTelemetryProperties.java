package org.techbd.service.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "otel.traces")
@Getter
@Setter
public class OpenTelemetryProperties {
    private String exporter;
    private OtlpProperties otlp;
    private boolean enabled;
    @Getter
    @Setter
    public static class OtlpProperties {
        private String endpoint;
        private Map<String, String> headers;
    }
}