package org.techbd.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.processor.MessageProcessingStep;

public class MessageProcessorServiceTest {

    private MessageProcessorService service;
    private MessageProcessingStep step1;
    private MessageProcessingStep step2;

    @BeforeEach
    void setUp() {
        step1 = mock(MessageProcessingStep.class);
        step2 = mock(MessageProcessingStep.class);
        service = new MessageProcessorService(List.of(step1, step2));
    }

    @Test
    void testProcessMessageWithMultipartFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.hl7");
        when(file.getSize()).thenReturn(123L);

        RequestContext context = mock(RequestContext.class);
        when(context.getInteractionId()).thenReturn("int-001");
        when(context.getMessageId()).thenReturn("msg-001");
        when(context.getFullS3DataPath()).thenReturn("s3://bucket/test.hl7");
        when(context.getTimestamp()).thenReturn("2025-07-17T12:00:00Z");
        when (context.getMessageSourceType()).thenReturn(MessageSourceType.HTTP_INGEST); 
        when(step1.isEnabledFor(context)).thenReturn(true);
        when(step2.isEnabledFor(context)).thenReturn(true);
        Map<String, String> result = service.processMessage(context, file);

        verify(step1).process(context, file);
        verify(step2).process(context, file);

        assertThat(result).containsEntry("messageId", "msg-001");
        assertThat(result).containsEntry("interactionId", "int-001");
        assertThat(result).containsEntry("fullS3Path", "s3://bucket/test.hl7");
        assertThat(result).containsEntry("timestamp", "2025-07-17T12:00:00Z");
    }
    @Test
    void testProcessMessageWithMultipartFile_ForHoldApi() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.hl7");
        when(file.getSize()).thenReturn(123L);

        RequestContext context = mock(RequestContext.class);
        when(context.getInteractionId()).thenReturn("int-001");
        when(context.getMessageId()).thenReturn("msg-001");
        when(context.getFullS3DataPath()).thenReturn("s3://bucket/test.hl7");
        when(context.getTimestamp()).thenReturn("2025-07-17T12:00:00Z");
        when (context.getMessageSourceType()).thenReturn(MessageSourceType.HTTP_INGEST); 
        when(step1.isEnabledFor(context)).thenReturn(true);
        when(step2.isEnabledFor(context)).thenReturn(false);
        Map<String, String> result = service.processMessage(context, file);

        verify(step1).process(context, file);
        verify(step2,times(0)).process(context, file);

        assertThat(result).containsEntry("messageId", "msg-001");
        assertThat(result).containsEntry("interactionId", "int-001");
        assertThat(result).containsEntry("fullS3Path", "s3://bucket/test.hl7");
        assertThat(result).containsEntry("timestamp", "2025-07-17T12:00:00Z");
    }
    @Test
    void testProcessMessageWithString() {
        String content = "MSH|^~\\&|...";
        String mllpAck = "MSA|AA|msg-002";
        RequestContext context = mock(RequestContext.class);
        when(context.getInteractionId()).thenReturn("int-002");
        when(context.getMessageId()).thenReturn("msg-002");
        when(context.getFullS3DataPath()).thenReturn("s3://bucket/test-string.hl7");
        when(context.getTimestamp()).thenReturn("2025-07-17T13:00:00Z");
        when (context.getMessageSourceType()).thenReturn(MessageSourceType.HTTP_INGEST);
        when(step1.isEnabledFor(context)).thenReturn(true);
        when(step2.isEnabledFor(context)).thenReturn(true);
        Map<String, String> result = service.processMessage(context, content, mllpAck);

        verify(step1).process(context, content, mllpAck);
        verify(step2).process(context, content, mllpAck);

        assertThat(result).containsEntry("messageId", "msg-002");
        assertThat(result).containsEntry("interactionId", "int-002");
        assertThat(result).containsEntry("fullS3Path", "s3://bucket/test-string.hl7");
        assertThat(result).containsEntry("timestamp", "2025-07-17T13:00:00Z");
    }

    @Test
    void testCreateSuccessResponseHandlesException() {
        RequestContext context = mock(RequestContext.class);
        when(context.getInteractionId()).thenReturn("int-003");
        when(context.getMessageId()).thenReturn("msg-003");
        when(context.getFullS3DataPath()).thenThrow(new RuntimeException("Simulated failure"));
        when (context.getMessageSourceType()).thenReturn(MessageSourceType.HTTP_INGEST);
        Map<String, String> result = service.processMessage(context, "raw-content", "");

        assertThat(result).containsEntry("error", "Failed to create response");
        assertThat(result).containsEntry("interactionId", "int-003");
        assertThat(result).containsEntry("exception", "RuntimeException");
        assertThat(result).containsEntry("message", "Simulated failure");
    }
}
