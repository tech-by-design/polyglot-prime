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
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.SoapForwarderService;
import org.techbd.ingest.service.portconfig.PortResolverService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.SoapFaultUtil;
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
    private final SoapFaultUtil soapFaultUtil;

    public DataIngestionController(
            MessageProcessorService messageProcessorService,
            ObjectMapper objectMapper,
            AppConfig appConfig,
            AppLogger appLogger,
            PortResolverService portResolverService,
            SoapForwarderService forwarder,
            SoapFaultUtil soapFaultUtil) {
        super(appConfig, appLogger);
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.forwarder = forwarder;
        this.soapFaultUtil = soapFaultUtil;
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

        // Check if this is a SOAP request
        boolean isSoapReq = isSoapRequest(msgType);

        // Validate that request contains data
        if ((file == null || file.isEmpty()) && (body == null || body.isBlank())) {
            LOG.warn("Empty request received. interactionId={} isSoapRequest={}", interactionId, isSoapReq);
            
            // Generate SOAP fault for SOAP requests
            if (isSoapReq) {
                return handleEmptySoapRequest(interactionId, request);
            }
            
            // For non-SOAP requests, throw exception as before
            throw new IllegalArgumentException("Request must contain either a file or body");
        }

        // Check for SOAP forwarding (if body is present)
        if (body != null && !body.isBlank() && isSoapReq) {
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
     * Handles empty SOAP requests by generating a proper SOAP fault with error trace ID
     */
    private ResponseEntity<String> handleEmptySoapRequest(String interactionId, HttpServletRequest request) {
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        
        LOG.error("DataIngestionController:: Empty SOAP request body. interactionId={}, errorTraceId={}", 
                interactionId, errorTraceId);
        
        // Log detailed error to CloudWatch
        LogUtil.logDetailedError(
            400, 
            "Empty SOAP request body - no SOAP message provided", 
            interactionId, 
            errorTraceId, 
            new IllegalArgumentException("Empty SOAP request body")
        );
        
        // Determine SOAP version and generate fault
        String contentType = request.getContentType();
        boolean isSoap12 = soapFaultUtil.isSoap12(contentType);
        
        String soapFault = isSoap12 
            ? soapFaultUtil.createClientSoap12Fault("Empty request body - no SOAP message provided", interactionId, errorTraceId)
            : soapFaultUtil.createClientSoap11Fault("Empty request body - no SOAP message provided", interactionId, errorTraceId);
        
        LOG.info("DataIngestionController:: Returning SOAP fault. interactionId={}, errorTraceId={}", 
                interactionId, errorTraceId);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.valueOf(isSoap12 ? "application/soap+xml; charset=utf-8" : "text/xml; charset=utf-8"))
            .body(soapFault);
    }

    /**
     * Processes multipart file uploads.
     * Catches generic exceptions, sets ingestionFailed flag, and ensures payload storage.
     */
    private Map<String, String> processMultipartFile(
            String sourceId,
            String msgType,
            Map<String, String> headers,
            HttpServletRequest request,
            String interactionId,
            MultipartFile file) {
        
        RequestContext context = null;
        try {
            LOG.info("File received: {} ({} bytes). interactionId={}",
                    file.getOriginalFilename(), file.getSize(), interactionId);

            context = createRequestContext(
                    interactionId, headers, request, file.getSize(), file.getOriginalFilename());

            context.setSourceId(sourceId);
            context.setMsgType(msgType);

            return messageProcessorService.processMessage(context, file);
        } catch (Exception e) {
            LOG.error("Error processing multipart file. interactionId={}", interactionId, e);
            
            // Set ingestion failed flag
            if (context != null) {
                context.setIngestionFailed(true);
                
                // Attempt to store the original payload even on failure
                try {
                    LOG.info("Attempting to store original payload despite failure. interactionId={}", interactionId);
                    messageProcessorService.processMessage(context, file);
                } catch (Exception storageException) {
                    LOG.error("Failed to store original payload after exception. interactionId={}", 
                            interactionId, storageException);
                }
            }
            
            throw new RuntimeException("Failed to process file: " + e.getMessage(), e);
        }
    }

    /**
     * Processes raw request body content.
     * Catches generic exceptions, sets ingestionFailed flag, and ensures payload storage.
     */
    private Map<String, String> processRawBody(
            String sourceId,
            String msgType,
            Map<String, String> headers,
            HttpServletRequest request,
            HttpServletResponse response,
            String interactionId,
            String body) {
        
        RequestContext context = null;
        try {
            String contentType = request.getContentType();
            String extension = HttpUtil.resolveExtension(contentType);
            String generatedFileName = "payload-" + UUID.randomUUID() + extension;

            LOG.info("Raw body received (Content-Type={}): interactionId={}",
                    contentType, interactionId);

            context = createRequestContext(
                    interactionId, headers, request, body.length(), generatedFileName);

            context.setSourceId(sourceId);
            context.setMsgType(msgType);

            return messageProcessorService.processMessage(context, body);
        } catch (Exception e) {
            LOG.error("Error processing raw body. interactionId={}", interactionId, e);
            
            // Set ingestion failed flag
            if (context != null) {
                context.setIngestionFailed(true);
                
                // Attempt to store the original payload even on failure
                try {
                    LOG.info("Attempting to store original payload despite failure. interactionId={}", interactionId);
                    messageProcessorService.processMessage(context, body);
                } catch (Exception storageException) {
                    LOG.error("Failed to store original payload after exception. interactionId={}", 
                            interactionId, storageException);
                }
            }
            
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