package org.techbd.service.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.techbd.util.AWSUtil;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;

@Configuration
public class OpenTelemetryConfig {

    private final OpenTelemetryProperties properties;
    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryProperties.class.getName());

    public OpenTelemetryConfig(OpenTelemetryProperties properties) {
        this.properties = properties;
    }

    /**
     * Configures and provides an instance of {@link OpenTelemetrySdk}.
     * <p>
     * This method manually creates beans for the OpenTelemetry SDK components,
     * including the
     * {@link OtlpHttpSpanExporter}, {@link SdkTracerProvider}, and
     * {@link Resource}.
     * 
     * TODO: Move these configurations to an application-level configuration bean
     * for better modularity and maintainability.
     * Manual bean creation here is unnecessary and can be replaced with
     * Spring-managed beans.
     *
     * @return an initialized {@link OpenTelemetrySdk} instance
     */
    @SuppressWarnings("deprecation")
    @Bean
    public OpenTelemetrySdk openTelemetrySdk() {
        var headers = properties.getOtlp().getHeaders();
        var token = headers != null ? headers.get("Authorization") : null;
        LOG.info("getEndpoint() - {})", properties.getOtlp().getEndpoint());
        LOG.info("getAuthorizationTokenSecretName() - {})", properties.getOtlp().getAuthorizationTokenSecretName());
        if (token == null && properties.getOtlp().getAuthorizationTokenSecretName() != null) {
            token = AWSUtil.getValue(properties.getOtlp().getAuthorizationTokenSecretName());
            LOG.info("Authorization AWS token {}.", token);
        }

        if (token == null) {
            LOG.error("Authorization token is missing. Please ensure it is set in headers or AWS Secrets.");
        } else {
            LOG.info("Authorization token retrieved: {}", token);
        }

        OtlpHttpSpanExporter spanExporter = null;
        if (token != null) {
            spanExporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(properties.getOtlp().getEndpoint())
                    .addHeader("Authorization", token)
                    .build();
            LOG.info("Traces exported successfully.");
        } else {
            LOG.error("Failed to create span exporter without Authorization token.");
            spanExporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(properties.getOtlp().getEndpoint())
                    .build();
        }

        Resource resource = Resource.getDefault()
                .merge(Resource.create(
                        Attributes.of(ResourceAttributes.SERVICE_NAME, "techbd-hub-prime")));
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("techbd-hub-prime");
    }
}
