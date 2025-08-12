package org.techbd.ingest.controller;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for handling data ingestion requests.
 * This controller processes file uploads and string content ingestion.
 */
@RestController
@Slf4j
public class DataIngestionController {
    private static final Logger LOG = LoggerFactory.getLogger(DataIngestionController.class.getName()); 
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;

    public DataIngestionController(MessageProcessorService messageProcessorService, ObjectMapper objectMapper) {
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        LOG.info("DataIngestionController initialized");
    }

    /**
     * Health check endpoint using HEAD method on /ingest endpoint.
     *
     * @return A response entity indicating service health status.
     */
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity<Void> healthCheck() {
        try {
            LOG.info("Health check requested via HEAD /ingest");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Endpoint to handle file ingestion requests.
     *
     * @param file    The file to be ingested.
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    @PostMapping(value = "/ingest")
    public ResponseEntity<String> ingest(
            @RequestParam("file") @Nonnull MultipartFile file,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws Exception {
        
        // Get interactionId from filter
        String interactionId = (String) request.getAttribute("interactionId");
        LOG.info("DataIngestionController:: Received ingest request. interactionId={}", interactionId);
        if (file == null || file.isEmpty()) {
            LOG.warn("Uploaded file is empty. interactionId={}", interactionId);
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        RequestContext context = createRequestContext(interactionId,
                headers, request, file.getSize(), file.getOriginalFilename());
        LOG.info("DataIngestionController:: RequestContext created for interactionId={}", interactionId);
         Map<String, String> responseMap = messageProcessorService.processMessage(context, file);
        LOG.info("DataIngestionController:: File processed successfully. interactionId={}", interactionId);
        String responseJson = objectMapper.writeValueAsString(responseMap);
        LOG.info("DataIngestionController:: Returning response for interactionId={}", interactionId);
        return ResponseEntity.ok(responseJson);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        // Try to extract interactionId from exception or context if possible
        String interactionId = "unknown";
        LOG.error("DataIngestionController:: Error processing request. interactionId={}", interactionId, e);
        Map<String, String> error = Map.of("error", e.getMessage());
        try {
            String errorJson = objectMapper.writeValueAsString(error);
            LOG.info("DataIngestionController:: Returning BAD_REQUEST for interactionId={}", interactionId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorJson);
        } catch (Exception ex) {
            LOG.error("DataIngestionController:: Error serializing error response. interactionId={}", interactionId, ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Internal server error\"}");
        }
    }
    /**
     * Function to handle string content ingestion requests.
     *
     * @param content The string content to be ingested.
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    private RequestContext createRequestContext(
            String interactionId,
            Map<String, String> headers,
            HttpServletRequest request,
            long fileSize,
            String originalFileName) {
        LOG.info("DataIngestionController:: Creating RequestContext. interactionId={}", interactionId);

        String sourceIp = null;
        var tenantId = headers.get(Constants.REQ_HEADER_TENANT_ID);
        final var xForwardedFor = headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            sourceIp = xForwardedFor.split(",")[0].trim();
        } else {
            sourceIp = headers.get(Constants.REQ_HEADER_X_REAL_IP);
        }

        // Extract destination IP and port
        final var destinationIp = headers.get(Constants.REQ_X_SERVER_IP);
        final var destinationPort = headers.get(Constants.REQ_X_SERVER_PORT);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.TENANT_ID;
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.DEFAULT_TENANT_ID;
        }
        log.info("Request Headers - tenantId: {}, xForwardedFor: {}, xRealIp: {}, sourceIp: {}, destinationIp: {}, destinationPort: {}, interactionId: {}",
        headers.get(Constants.REQ_HEADER_TENANT_ID),
        headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR),
        headers.get(Constants.REQ_HEADER_X_REAL_IP),
        sourceIp,
        destinationIp,
        destinationPort,interactionId);

        Instant now = Instant.now();
        String timestamp = String.valueOf(now.toEpochMilli());
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);

        String datePath = uploadTime.format(DATE_PATH_FORMATTER);

        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf('.') + 1); // e.g., csv
        String fileBaseName = originalFileName.substring(0, originalFileName.lastIndexOf('.')); // e.g., ttest

        String s3PrefixPath = String.format("data/%s/%s/%s", datePath, timestamp, interactionId);
        String metadataPrefixPath = String.format("metadata/%s/%s/%s", datePath, timestamp, interactionId);

        String objectKey = String.format("data/%s/%s-%s-%s.%s",
                datePath, timestamp, interactionId, fileBaseName, fileExtension);

        String metadataKey = String.format("metadata/%s/%s-%s-%s-%s-metadata.json",
                datePath, timestamp, interactionId, fileBaseName, fileExtension);

        String fullS3Path = Constants.S3_PREFIX + Constants.BUCKET_NAME + "/" + objectKey;

        String userAgent = headers.getOrDefault(Constants.REQ_HEADER_USER_AGENT, Constants.DEFAULT_USER_AGENT);

        String fullRequestUrl = request.getRequestURL().toString();
        String queryParams = request.getQueryString();
        String protocol = request.getProtocol();
        String localAddress = request.getLocalAddr();
        String remoteAddress = request.getRemoteAddr();

        LOG.info("DataIngestionController:: RequestContext built for interactionId={}: tenantId={}, objectKey={}", interactionId, tenantId, objectKey);

        return new RequestContext(
                headers,
                request.getRequestURI(),
                tenantId,
                interactionId,
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
                remoteAddress,
                sourceIp,
                destinationIp,
                destinationPort,null,null
        );
    }
}
