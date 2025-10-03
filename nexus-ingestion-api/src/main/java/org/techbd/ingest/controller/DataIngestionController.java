package org.techbd.ingest.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

/**
 * Controller for handling data ingestion requests.
 * This controller processes file uploads and string content ingestion.
 */
@RestController
public class DataIngestionController extends AbstractMessageSourceProvider {
    private final TemplateLogger LOG;
    
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    public DataIngestionController(MessageProcessorService messageProcessorService, ObjectMapper objectMapper, AppConfig appConfig, AppLogger appLogger) {
        super(appConfig, appLogger);
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        LOG = appLogger.getLogger(DataIngestionController.class);
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
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Endpoint to handle ingestion requests.
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
    @PostMapping(value = "/ingest", consumes = { 
        MediaType.MULTIPART_FORM_DATA_VALUE, 
        "multipart/related", 
        "application/xop+xml", 
        MediaType.TEXT_XML_VALUE,
        MediaType.APPLICATION_XML_VALUE,
        MediaType.ALL_VALUE 
    })
    public ResponseEntity<String> ingest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws Exception {
        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        LOG.info("DataIngestionController:: Received ingest request. interactionId={}", interactionId);

        Map<String, String> responseMap;

    if (file != null && !file.isEmpty()) {
        LOG.info("DataIngestionController:: File received: {} ({} bytes). interactionId={}",
            file.getOriginalFilename(), file.getSize(), interactionId);
        RequestContext context = createRequestContext(interactionId,
            headers, request, file.getSize(), file.getOriginalFilename());
        responseMap = messageProcessorService.processMessage(context, file);

    } else if (body != null && !body.isBlank()) {
        String contentType = request.getContentType();
        String extension = HttpUtil.resolveExtension(contentType);
        String generatedFileName = "payload-" + UUID.randomUUID() + extension;
        LOG.info("DataIngestionController:: Raw body received (Content-Type={}): {}... interactionId={}",
            contentType, body.substring(0, Math.min(200, body.length())), interactionId);
        RequestContext context = createRequestContext(interactionId,
            headers, request, body.length(), generatedFileName);
        responseMap = messageProcessorService.processMessage(context, body);

    } else {
        LOG.warn("DataIngestionController:: Neither file nor body provided. interactionId={}", interactionId);
        throw new IllegalArgumentException("Request must contain either a file or body data");
    }
        LOG.info("DataIngestionController:: Ingestion processed successfully. interactionId={}", interactionId);
        String responseJson = objectMapper.writeValueAsString(responseMap);
        LOG.info("DataIngestionController:: Returning response for interactionId={}", interactionId);
        return ResponseEntity.ok(responseJson);
    }
    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.HTTP_INGEST;
    }

    @Override
    public String getDataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
    }
}
