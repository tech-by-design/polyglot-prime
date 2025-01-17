package org.techbd.service.http;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.annotation.PostConstruct;

@Configuration
public class OLTPConfiguration {
        @Value("${TECHBD_OPEN_OBSERVE_PASSWORD}")
        public String openObservePassword;

        @Value("${TECHBD_OPEN_OBSERVE_STREAM_NAME}")
        public String streamName;

        @Value("${TECHBD_OPEN_OBSERVE_URL}")
        public String openObserveUrl;

        @PostConstruct
        public void initializeOpenTelemetry() {
                // Configure OTLP Log Exporter
                OtlpHttpLogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder()
                                .setEndpoint(openObserveUrl + "/api/" + streamName + "/v1/logs")
                                .addHeader("stream-name", "TechBD-" + streamName + "-logs")
                                .addHeader("Authorization", "Basic " + openObservePassword)
                                .build();

                // Create a Log Record Processor
                BatchLogRecordProcessor logProcessor = BatchLogRecordProcessor.builder(logExporter).setScheduleDelay(Duration.ofSeconds(5)).build();

                // Define the service name as part of the Resource
                Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.builder()
                                                .put("service.name", streamName + "-TechBD-service")
                                                .build()));

                // Create LoggerProvider and OpenTelemetry SDK
                SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                                .setResource(resource) // Set the resource with service name
                                .addLogRecordProcessor(logProcessor)
                                .build();

                OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                                .setLoggerProvider(loggerProvider)
                                .build();

                // Install OpenTelemetrySdk into OpenTelemetryAppender
                OpenTelemetryAppender.install(openTelemetrySdk);
        }

}