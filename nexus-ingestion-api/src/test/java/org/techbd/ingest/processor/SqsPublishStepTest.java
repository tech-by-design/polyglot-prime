package org.techbd.ingest.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mock.web.MockMultipartFile;
import org.techbd.ingest.commons.SourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageGroupService;
import org.techbd.ingest.service.MetadataBuilderService;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import java.time.ZonedDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SqsPublishStepTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MetadataBuilderService metadataBuilderService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private AppConfig.Aws aws;

    @Mock
    private AppConfig.Aws.Sqs sqs;

    @Mock
    private MessageGroupService messageGroupService;

    @InjectMocks
    private SqsPublishStep sqsPublishStep;

    private RequestContext context;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        when(appConfig.getAws()).thenReturn(aws);
        when(aws.getSqs()).thenReturn(sqs);
        when(sqs.getFifoQueueUrl()).thenReturn("http://dummy-fifo-url");

        when(messageGroupService.createMessageGroupId(any(),any())).thenReturn("group-id-123");

        context = new RequestContext(
                Map.of("User-Agent", "JUnit-Test"),
                "/test",
                "tenant123",
                "interaction123",
                ZonedDateTime.now(),
                "uploadTime",
                "test.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://test-bucket/objectKey",
                "JUnit-Test",
                "http://localhost/test",
                "q=1",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "192.168.1.1",
                "192.168.1.2",
                "8080",null,null,SourceType.INGEST.name());

        sqsPublishStep = new SqsPublishStep(sqsClient, objectMapper, metadataBuilderService, appConfig,
                messageGroupService);
    }

    @Test
    void testProcessWithMultipartFile() throws Exception {
        Map<String, Object> mockMessage = Map.of("key", "value");
        when(metadataBuilderService.buildSqsMessage(context)).thenReturn(mockMessage);
        when(objectMapper.writeValueAsString(mockMessage)).thenReturn("{\"key\":\"value\"}");
        when(messageGroupService.createMessageGroupId(any(),any())).thenReturn("msg-group-123");
        SendMessageResponse mockResponse = SendMessageResponse.builder()
                .messageId("msg-123")
                .build();

        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test-content".getBytes());
        sqsPublishStep.process(context, file);
        assertEquals("msg-123", context.getMessageId());
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testProcessWithStringContent() throws Exception {
        String content = "test-content";
        Map<String, Object> mockMessage = new java.util.HashMap<>();
        mockMessage.put("key", "value");
        when(metadataBuilderService.buildSqsMessage(any())).thenReturn(mockMessage);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\", \"content\":\"test-content\"}");
        when(appConfig.getAws()).thenReturn(aws);
        when(aws.getSqs()).thenReturn(sqs);
        when(sqs.getFifoQueueUrl()).thenReturn("http://dummy-queue-url");
        when(messageGroupService.createMessageGroupId(any(),any())).thenReturn("group-123");
        SendMessageResponse mockResponse = SendMessageResponse.builder()
                .messageId("sqs-msg-id-456")
                .build();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);
        sqsPublishStep.process(context, content,null);
        assertEquals("sqs-msg-id-456", context.getMessageId());
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }



}