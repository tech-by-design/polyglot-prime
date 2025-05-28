package org.techbd.nexusingestionapi.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.nexusingestionapi.commons.Constants;
import org.techbd.nexusingestionapi.service.AwsService;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@RestController
@Slf4j
public class ApiController {

    private static final String JSON_EXTENSION = ".json";
    private static final String XML_EXTENSION = ".xml";
    private static final String METADATA_SUFFIX = "_metadata.json";
    private static final String S3_PREFIX = "s3://";
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final AwsService s3Service;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    public ApiController(AwsService s3Service, SqsClient sqsClient, ObjectMapper objectMapper) {
        this.s3Service = s3Service;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"/Bundle", "/Bundle/",
        "/Bundle/$validate", "/Bundle/$validate/",
        "/ccda/Bundle", "/ccda/Bundle/",
        "/ccda/Bundle/$validate", "/ccda/Bundle/$validate/"})
    public ResponseEntity<String> handleRequest(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        String uri = request.getRequestURI();
        boolean isCcda = uri.contains("ccda");
        String msgType = isCcda ? "ccda" : "fhir";
        String fileExtension = isCcda ? XML_EXTENSION : JSON_EXTENSION;

        RequestContext context = createRequestContext(
                headers,
                request,
                msgType,
                body.getBytes(StandardCharsets.UTF_8).length,
                extractOriginalFileName(headers, UUID.randomUUID().toString(), fileExtension)
        );

        return processJsonRequest(body, context);
    }

    @PostMapping({"/flatfile/csv/Bundle", "/flatfile/csv/Bundle/",
        "/flatfile/csv/Bundle/$validate", "/flatfile/csv/Bundle/$validate/"})
    public ResponseEntity<String> handleCSVBundle(
            @RequestParam("file") @Nonnull MultipartFile file,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        validateFile(file);

        RequestContext context = createRequestContext(
                headers, request, "csv", file.getSize(), file.getOriginalFilename());

        return processMultipartFileRequest(file, context);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Error processing request", e);
        Map<String, String> error = Map.of("error", e.getMessage());
        try {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(objectMapper.writeValueAsString(error));
        } catch (Exception ex) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Internal server error\"}");
        }
    }

    private ResponseEntity<String> processJsonRequest(String body, RequestContext context) {
        try {
            // Upload data to S3
            String bucketName = Constants.BUCKET_NAME;

            // Build metadata for the S3 object (2kb limit)
            Map<String, String> s3Metadata = buildS3Metadata(context);

            // Build metadata for the _metadata.json file
            // The detailed metadata will be saved in a separate file with the suffix "_metadata.json"
            Map<String, Object> metadataJson = buildMetadataJson(context);

            // Convert metadata to JSON string with a single line, as AWS Athena does not support pretty print
            String metadataContent = objectMapper.writeValueAsString(metadataJson);

            System.out.println("Metadata Content: " + metadataContent);

            // Save to S3
            s3Service.saveToS3(bucketName, context.objectKey(), body, s3Metadata);
            s3Service.saveToS3(bucketName, context.metadataKey(), metadataContent, null);

            // Send to SQS
            String messageId = sendToSqs(context, null);

            // Create response
            return createSuccessResponse(messageId, context);

        } catch (Exception e) {
            log.error("Error processing JSON request", e);
            throw new RuntimeException("Failed to process request: " + e.getMessage(), e);
        }
    }

    private ResponseEntity<String> processMultipartFileRequest(MultipartFile file, RequestContext context) {
        try {
            String bucketName = Constants.BUCKET_NAME;

            // Build metadata
            Map<String, Object> metadataJson = buildMetadataJson(context);

            String metadataContent = objectMapper.writeValueAsString(metadataJson);

            System.out.println("Metadata Content MultiPart: " + metadataContent);

            // Save metadata to S3
            s3Service.saveToS3(bucketName, context.metadataKey(), metadataContent, null);

            // Save file to S3
            String s3Response = s3Service.saveToS3(context.headers(), file);

            // Send to SQS
            String messageId = sendToSqs(context, s3Response);

            // Create response
            return createSuccessResponse(messageId, context);

        } catch (Exception e) {
            log.error("Error processing multipart file request", e);
            throw new RuntimeException("Failed to process file: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || file.getOriginalFilename().trim().isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is missing or empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (!originalFilename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Uploaded file must have a .zip extension.");
        }
    }

    private RequestContext createRequestContext(
            Map<String, String> headers,
            HttpServletRequest request,
            String msgType,
            long fileSize,
            String originalFileName) {

        String tenantId = headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(Constants.REQ_HEADER_TENANT_ID))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(Constants.DEFAULT_TENANT_ID);

        String interactionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String timestamp = String.valueOf(now.toEpochMilli());
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);

        String datePath = uploadTime.format(DATE_PATH_FORMATTER);
        String s3PrefixPath = String.format("%s/%s/%s-%s",
                msgType, datePath, timestamp, interactionId);

        String objectKey = s3PrefixPath + JSON_EXTENSION;
        String metadataKey = s3PrefixPath + METADATA_SUFFIX;
        String fullS3Path = S3_PREFIX + Constants.BUCKET_NAME + "/" + objectKey;

        String userAgent = headers.getOrDefault(Constants.REQ_HEADER_USER_AGENT, Constants.DEFAULT_USER_AGENT);

        String fullRequestUrl = request.getRequestURL().toString();
        String queryParams = request.getQueryString();
        String protocol = request.getProtocol();
        String localAddress = request.getLocalAddr();
        String remoteAddress = request.getRemoteAddr();

        return new RequestContext(
                headers,
                request.getRequestURI(),
                tenantId,
                interactionId,
                msgType,
                uploadTime,
                timestamp,
                originalFileName,
                fileSize,
                objectKey,
                metadataKey,
                fullS3Path,
                userAgent,
                fullRequestUrl,
                queryParams,
                protocol,
                localAddress,
                remoteAddress);
    }

    private String extractOriginalFileName(Map<String, String> headers, String interactionId, String extension) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(Constants.REQ_HEADER_CONTENT_DISPOSITION))
                .map(Map.Entry::getValue)
                .map(v -> {
                    int start = v.indexOf("filename=");
                    if (start >= 0) {
                        String name = v.substring(start + 9).replaceAll("\"", "").trim();
                        return name.isEmpty() ? interactionId + extension : name;
                    }
                    return interactionId + extension;
                })
                .findFirst()
                .orElse(interactionId + extension);
    }

    /**
     * This function generates the metadata for the S3 object, as there is a 2kb
     * size limit. The detailed metadata will be saved in a separate file with
     * the suffix "_metadata.json".
     */
    public Map<String, String> buildS3Metadata(RequestContext context) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("interactionId", context.interactionId());
        metadata.put("tenantId", context.tenantId());
        metadata.put("fileName", context.fileName());
        metadata.put("FileSize", String.valueOf(context.fileSize()));
        metadata.put("s3ObjectPath", context.fullS3Path());
        metadata.put("UploadTime", context.uploadTime().toString());
        metadata.put("UploadedBy", context.userAgent());
        return metadata;
    }

    /**
     * This function generates the _metadata.json content for the S3 object.
     */
    public Map<String, Object> buildMetadataJson(RequestContext context) {
        Map<String, Object> jsonMetadata = new HashMap<>();

        jsonMetadata.put("tenantId", context.tenantId());
        jsonMetadata.put("interactionId", context.interactionId());
        jsonMetadata.put("msgType", context.msgType());
        jsonMetadata.put("uploadDate", String.format("%d-%02d-%02d",
                context.uploadTime().getYear(), context.uploadTime().getMonthValue(), context.uploadTime().getDayOfMonth()));
        jsonMetadata.put("timestamp", context.timestamp());
        jsonMetadata.put("fileName", context.fileName());
        jsonMetadata.put("fileSize", String.valueOf(context.fileSize()));
        jsonMetadata.put("sourceSystem", "Mirth Connect");
        jsonMetadata.put("s3ObjectPath", context.fullS3Path());
        jsonMetadata.put("requestUrl", context.requestUrl());
        jsonMetadata.put("fullRequestUrl", context.fullRequestUrl());
        jsonMetadata.put("queryParams", context.queryParams());
        jsonMetadata.put("protocol", context.protocol());
        jsonMetadata.put("localAddress", context.localAddress());
        jsonMetadata.put("remoteAddress", context.remoteAddress());

        // Convert headers to list of single-entry maps
        List<Map<String, String>> headerList = context.headers().entrySet().stream()
                .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                .toList();

        jsonMetadata.put("headers", headerList);

        //// TODO: Uncomment this section when needed this JSON format.
        //// Wrap in parent object
        // Map<String, Object> wrapper = new HashMap<>();
        // wrapper.put("key", context.objectKey());
        // wrapper.put("json_metadata", jsonMetadata);

        // return wrapper;
        return jsonMetadata;
    }

    /**
     * This function generates the SQS message content.
     */
    private Map<String, Object> buildSqsMessage(RequestContext context) {
        Map<String, Object> message = new HashMap<>();
        message.put("tenantId", context.tenantId());
        message.put("interactionId", context.interactionId());
        message.put("requestUrl", context.requestUrl());
        message.put("msgType", context.msgType());
        message.put("timestamp", context.timestamp());
        message.put("fileName", context.fileName());
        message.put("fileSize", String.valueOf(context.fileSize()));
        message.put("s3ObjectPath", context.fullS3Path());
        return message;
    }

    private ResponseEntity<String> createSuccessResponse(String messageId, RequestContext context) {
        try {
            Map<String, String> response = Map.of(
                    "messageId", messageId,
                    "interactionId", context.interactionId(),
                    "fullS3Path", context.fullS3Path(),
                    "timestamp", context.timestamp());

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("Error creating success response", e);
            throw new RuntimeException("Failed to create response: " + e.getMessage(), e);
        }
    }

    private String sendToSqs(RequestContext context, String s3Response) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("tenantId", context.tenantId());
        message.put("interactionId", context.interactionId());
        message.put("requestUrl", context.requestUrl());
        message.put("msgType", context.msgType());
        message.put("timestamp", context.timestamp());
        message.put("fileName", context.fileName());
        message.put("fileSize", String.valueOf(context.fileSize()));
        message.put("s3ObjectId", context.objectKey());
        message.put("s3ObjectPath", context.fullS3Path());

        if (s3Response != null) {
            message.put("s3Response", s3Response);
        }

        String messageJson = objectMapper.writeValueAsString(message);

        return sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(Constants.FIFO_Q_URL)
                .messageBody(messageJson)
                .messageGroupId(context.tenantId())
                .build())
                .messageId();
    }

    record RequestContext(
            Map<String, String> headers,
            String requestUrl,
            String tenantId,
            String interactionId,
            String msgType,
            ZonedDateTime uploadTime,
            String timestamp,
            String fileName,
            long fileSize,
            String objectKey,
            String metadataKey,
            String fullS3Path,
            String userAgent,
            String fullRequestUrl,
            String queryParams,
            String protocol,
            String localAddress,
            String remoteAddress) {

    }

}
