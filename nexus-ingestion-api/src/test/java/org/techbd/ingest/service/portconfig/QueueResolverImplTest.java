package org.techbd.ingest.service.portconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

class QueueResolverImplTest {

    private AppConfig appConfig;
    private AppConfig.Aws aws;
    private AppConfig.Aws.Sqs sqsConfig;

    private AppLogger appLogger;
    private TemplateLogger templateLogger;
    private SqsClient sqsClient;

    private QueueResolverImpl resolver;

    @BeforeEach
    void setUp() {
        appConfig = mock(AppConfig.class);
        aws = mock(AppConfig.Aws.class);
        sqsConfig = mock(AppConfig.Aws.Sqs.class);

        when(appConfig.getAws()).thenReturn(aws);
        when(aws.getSqs()).thenReturn(sqsConfig);
        when(sqsConfig.getFifoQueueUrl()).thenReturn("default-queue-url");

        appLogger = mock(AppLogger.class);
        templateLogger = mock(TemplateLogger.class);
        when(appLogger.getLogger(QueueResolverImpl.class)).thenReturn(templateLogger);

        sqsClient = mock(SqsClient.class);

        resolver = new QueueResolverImpl(appConfig, appLogger, sqsClient);
    }

    @Test
    @DisplayName("Should resolve queue using X-TechBd-Queue-Name header via SQS")
    void shouldResolveQueueFromHeaderViaSqs() {
        RequestContext context =
                new RequestContext("interaction-1", 8080, "SRC", "MSG");
        context.setHeaders(Map.of("X-TechBd-Queue-Name", "override-queue"));

        GetQueueUrlResponse response =
                GetQueueUrlResponse.builder().queueUrl("resolved-queue-url").build();

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(response);

        resolver.resolve(context, null, "interaction-1");

        assertThat(context.getQueueUrl()).isEqualTo("resolved-queue-url");

        verify(sqsClient).getQueueUrl(any(GetQueueUrlRequest.class));
    }

    @Test
    @DisplayName("Should fall back when header override cannot be resolved by SQS")
    void shouldFallbackWhenHeaderOverrideFails() {
        RequestContext context =
                new RequestContext("interaction-2", 8080, "SRC", "MSG");
        context.setHeaders(Map.of("x-techbd-queue-name", "bad-queue"));

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenThrow(SqsException.builder().message("not found").build());

        PortEntry entry = new PortEntry();
        entry.queue = "port-config-queue";

        resolver.resolve(context, entry, "interaction-2");

        assertThat(context.getQueueUrl()).isEqualTo("port-config-queue");
    }

    @Test
    @DisplayName("Should use queue from port configuration when no header override")
    void shouldUseQueueFromPortConfig() {
        RequestContext context =
                new RequestContext("interaction-3", 8080, "SRC", "MSG");

        PortEntry entry = new PortEntry();
        entry.queue = "port-config-queue";

        resolver.resolve(context, entry, "interaction-3");

        assertThat(context.getQueueUrl()).isEqualTo("port-config-queue");

        verifyNoInteractions(sqsClient);
    }

    @Test
    @DisplayName("Should fall back to default FIFO queue when no header or port queue")
    void shouldFallbackToDefaultQueue() {
        RequestContext context =
                new RequestContext("interaction-4", 8080, "SRC", "MSG");

        resolver.resolve(context, null, "interaction-4");

        assertThat(context.getQueueUrl()).isEqualTo("default-queue-url");

        verifyNoInteractions(sqsClient);
    }
}
