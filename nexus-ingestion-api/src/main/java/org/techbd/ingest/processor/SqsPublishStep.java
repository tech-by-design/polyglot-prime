package org.techbd.ingest.processor;


import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@Order(2)
public class SqsPublishStep implements MessageProcessingStep {
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MetadataBuilderService metadataBuilderService;

    public SqsPublishStep(SqsClient sqsClient, ObjectMapper objectMapper, MetadataBuilderService metadataBuilderService) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.metadataBuilderService = metadataBuilderService;
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        try {
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(Constants.FIFO_Q_URL)
                    .messageBody(messageJson)
                    .messageGroupId(context.tenantId())
                    .build())
                    .messageId();
            context.setMessageId(messageId);
        } catch (Exception e) {
            throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }
    public void process(RequestContext context, String content) {
        try {
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            message.put("content", content);
            String messageJson = objectMapper.writeValueAsString(message);
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(Constants.FIFO_Q_URL)
                    .messageBody(messageJson)
                    .messageGroupId(context.tenantId())
                    .build())
                    .messageId();
            context.setMessageId(messageId);
        } catch (Exception e) {
            throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }
}
