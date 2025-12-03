package org.techbd.ingest.controller;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
import org.techbd.ingest.service.SoapForwarderService;
import org.techbd.ingest.service.portconfig.PortResolverService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller for handling data ingestion requests.
 * Supports both multipart file uploads and raw body content.
 */
@RestController
@RequestMapping("/ingest")
public class DataIngestionController extends AbstractMessageSourceProvider {

    private final SoapForwarderService forwarder;
    private final TemplateLogger LOG;
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;

    public DataIngestionController(
            MessageProcessorService messageProcessorService,
            ObjectMapper objectMapper,
            AppConfig appConfig,
            AppLogger appLogger,
            PortResolverService portResolverService,
            SoapForwarderService forwarder) {
        super(appConfig, appLogger);
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.forwarder = forwarder;
        this.LOG = appLogger.getLogger(DataIngestionController.class);
        LOG.info("DataIngestionController initialized");
    }

    /**
     * Health check endpoint.
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
     * Universal ingest endpoint supporting:
     * - /ingest (no params)
     * - /ingest/ (no params with trailing slash)
     * - /ingest/{sourceId}/{msgType} (with params)
     * 
     * Handles both multipart file uploads and raw body content.
     */
    @PostMapping(
            value = {"", "/", "/{sourceId}/{msgType}"},
            consumes = {
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    "multipart/related",
                    "application/xop+xml",
                    MediaType.TEXT_XML_VALUE,
                    MediaType.APPLICATION_XML_VALUE,
                    MediaType.ALL_VALUE
            }
    )
    public ResponseEntity<String> ingest(
            @PathVariable(name = "sourceId", required = false) String sourceId,
            @PathVariable(name = "msgType", required = false) String msgType,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        LOG.info("Received ingest request. interactionId={} sourceId={} msgType={}",
                interactionId, sourceId, msgType);

        // Validate that request contains data
        if ((file == null || file.isEmpty()) && (body == null || body.isBlank())) {
            LOG.warn("Empty request received. interactionId={}", interactionId);
            throw new IllegalArgumentException("Request must contain either a file or body");
        }

        // Check for SOAP forwarding first (before processing)
        if (body != null && !body.isBlank() && isSoapRequest(msgType)) {
            LOG.info("SOAP forwarding to /ws endpoint sourceId={} msgType={} interactionId={}",
                    sourceId, msgType, interactionId);
            return forwarder.forward(request, body, sourceId, msgType, interactionId);
        }

        Map<String, String> result = Optional.ofNullable(file)
                .filter(f -> !f.isEmpty())
                .map(f -> processMultipartFile(sourceId, msgType, headers, request, interactionId, f))
                .orElseGet(() -> processRawBody(sourceId, msgType, headers, request, response, interactionId, body));

        String json = objectMapper.writeValueAsString(result);
        LOG.info("Returning response for interactionId={}", interactionId);
        return ResponseEntity.ok(json);
    }

    /**
     * Processes multipart file uploads.
     */
    private Map<String, String> processMultipartFile(
            String sourceId,
            String msgType,
            Map<String, String> headers,
            HttpServletRequest request,
            String interactionId,
            MultipartFile file) {
        
        try {
            LOG.info("File received: {} ({} bytes). interactionId={}",
                    file.getOriginalFilename(), file.getSize(), interactionId);

            RequestContext context = createRequestContext(
                    interactionId, headers, request, file.getSize(), file.getOriginalFilename());

            context.setSourceId(sourceId);
            context.setMsgType(msgType);

            return messageProcessorService.processMessage(context, file);
        } catch (Exception e) {
            LOG.error("Error processing multipart file. interactionId={}", interactionId, e);
            throw new RuntimeException("Failed to process file: " + e.getMessage(), e);
        }
    }

    /**
     * Processes raw request body content.
     */
    private Map<String, String> processRawBody(
            String sourceId,
            String msgType,
            Map<String, String> headers,
            HttpServletRequest request,
            HttpServletResponse response,
            String interactionId,
            String body) {
        
        try {
            String contentType = request.getContentType();
            String extension = HttpUtil.resolveExtension(contentType);
            String generatedFileName = "payload-" + UUID.randomUUID() + extension;

            LOG.info("Raw body received (Content-Type={}): {}... interactionId={}",
                    contentType, body.substring(0, Math.min(200, body.length())), interactionId);

            RequestContext context = createRequestContext(
                    interactionId, headers, request, body.length(), generatedFileName);

            context.setSourceId(sourceId);
            context.setMsgType(msgType);

            return messageProcessorService.processMessage(context, body);
        } catch (Exception e) {
            LOG.error("Error processing raw body. interactionId={}", interactionId, e);
            throw new RuntimeException("Failed to process body: " + e.getMessage(), e);
        }
    }

    /**
     * Determines if the request should be forwarded to SOAP endpoint.
     */
    private boolean isSoapRequest(String msgType) {
        if (msgType == null) {
            return false;
        }
        String normalized = msgType.trim().toLowerCase();
        return normalized.equals("pix") 
                || normalized.equals("pnr") 
                || normalized.equals("ws");
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.HTTP_INGEST;
    }
}