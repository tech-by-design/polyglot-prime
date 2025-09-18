package org.techbd.ingest.controller;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.AbstractMessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
@RestController
@Slf4j
public class DataHoldController extends AbstractMessageSourceProvider {
    private static TemplateLogger LOG;
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    public DataHoldController(MessageProcessorService messageProcessorService, ObjectMapper objectMapper,
            AppConfig appConfig, AppLogger appLogger) {
        super(appConfig, appLogger);        
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        LOG = appLogger.getLogger(DataHoldController.class);
        LOG.info("DataHoldController initialized");
    }

    /**
     * Endpoint to handle /hold requests.
     *
     * This endpoint can accept either:
     * - a file upload (multipart/form-data)
     * - raw data in the body (JSON, XML, plain text, HL7, etc.)
     *
     * If raw body data is provided, a filename is generated
     * based on the Content-Type header.
     *
     * @param file    The optional file to be ingested (when multipart/form-data).
     * @param body    The optional raw payload (when Content-Type is JSON/XML/Text).
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    @PostMapping(value = "/hold", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE,
            "multipart/related", // MTOM support
            "application/xop+xml", // XOP support
            MediaType.ALL_VALUE
    })
    public ResponseEntity<String> hold(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws Exception {
        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        LOG.info("DataHoldController:: Received ingest request. interactionId={}", interactionId);

        Map<String, String> responseMap;

        if (file != null && !file.isEmpty()) {
            LOG.info("DataHoldController:: File received: {} ({} bytes). interactionId={}",
                    file.getOriginalFilename(), file.getSize(), interactionId);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, file.getSize(), file.getOriginalFilename());
            responseMap = messageProcessorService.processMessage(context, file);

        } else if (body != null && !body.isBlank()) {
            String contentType = request.getContentType();
            String extension = HttpUtil.resolveExtension(contentType);
            String generatedFileName = "payload-" + UUID.randomUUID() + extension;
            LOG.info("DataHoldController:: Raw body received (Content-Type={}): {}... interactionId={}",
                    contentType, body.substring(0, Math.min(200, body.length())), interactionId);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, body.length(), generatedFileName);
            responseMap = messageProcessorService.processMessage(context, body);

        } else {
            LOG.warn("DataHoldController:: Neither file nor body provided. interactionId={}", interactionId);
            throw new IllegalArgumentException("Request must contain either a file or body data");
        }
        LOG.info("DataHoldController:: Ingestion processed successfully. interactionId={}", interactionId);
        String responseJson = objectMapper.writeValueAsString(responseMap);
        LOG.info("DataHoldController:: Returning response for interactionId={}", interactionId);
        return ResponseEntity.ok(responseJson);
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.HTTP_HOLD;
    }

    @Override
    public String getDataBucketName() {
        return appConfig.getAws().getS3().getHoldConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        return appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
    }

    @Override
    public String getDataKey(String interactionId, Map<String, String> headers, String originalFileName) {
        // Build S3 key:
        // hold/{destination_port}/{YYYY}/{MM}/{DD}/{timestamp_filename}.{extension}
        Instant currentTime = Instant.now();
        String timestamp = String.valueOf(currentTime.toEpochMilli());
        ZonedDateTime now = currentTime.atZone(ZoneOffset.UTC);
        String yyyy = String.format("%04d", now.getYear());
        String mm = String.format("%02d", now.getMonthValue());
        String dd = String.format("%02d", now.getDayOfMonth());
        String extension = "";
        int dotIdx = originalFileName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < originalFileName.length() - 1) {
            extension = originalFileName.substring(dotIdx + 1);
        }
        String timestampFileName = timestamp + "_" + originalFileName;
        return String.format("hold/%s/%s/%s/%s/%s%s%s",
                getDestinationPort(headers), yyyy, mm, dd, timestampFileName,
                extension.isEmpty() ? "" : ".", extension);
    }

    @Override
    public String getMetaDataKey(String interactionId, Map<String, String> headers, String originalFileName) {
        return getDataKey(interactionId, headers, originalFileName) + "_metadata.json";
    }

    @Override
    public String getAcknowledgementKey(String interactionId, Map<String, String> headers, String originalFileName) {
        return getDataKey(interactionId, headers, originalFileName) + ".ack.json";
    }
}
