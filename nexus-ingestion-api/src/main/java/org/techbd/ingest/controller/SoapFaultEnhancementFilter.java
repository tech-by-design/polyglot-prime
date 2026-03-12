package org.techbd.ingest.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Filter that intercepts SOAP fault responses and injects error trace ID.
 * This runs at the servlet level, AFTER Spring WS has generated the fault,
 * allowing us to enhance even framework-level faults like MustUnderstand.
 * 
 * Also captures the request body to ensure it can be processed through
 * MessageProcessorService even for early-stage faults.
 * 
 * Order: LOWEST_PRECEDENCE (runs LAST in the filter chain, after all other filters)
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SoapFaultEnhancementFilter extends OncePerRequestFilter {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;

    public SoapFaultEnhancementFilter(AppLogger appLogger, MessageProcessorService messageProcessorService) {
        this.logger = appLogger.getLogger(SoapFaultEnhancementFilter.class);
        this.messageProcessorService = messageProcessorService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Only process /ws endpoints
        if (!request.getRequestURI().startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request to capture body
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        java.util.Map<String, String> capturedHeaders = extractHeaders(request);
        cachedRequest.setAttribute("CAPTURED_HEADERS", capturedHeaders);
        // Wrap response to capture output
        ResponseWrapper responseWrapper = new ResponseWrapper(response);
        
        try {
            // Continue filter chain with wrapped request and response
            filterChain.doFilter(cachedRequest, responseWrapper);
            
            // Check if response contains SOAP fault
            byte[] responseBytes = responseWrapper.getCapturedBytes();
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            
            // Check if it's a SOAP fault (contains <Fault> or <SOAP-ENV:Fault>)
            if (responseBody.contains("<Fault>") || responseBody.contains(":Fault>")) {
                logger.info("SoapFaultEnhancementFilter:: Detected SOAP fault, injecting error trace ID");
                
                // Get captured request body
                String requestBody = cachedRequest.getCachedBody();
                
                // Enhance the fault with error trace ID
                String enhancedResponse = enhanceSoapFault(responseBody, cachedRequest);
                
                // Write enhanced response
                byte[] enhancedBytes = enhancedResponse.getBytes(StandardCharsets.UTF_8);
                response.setContentLength(enhancedBytes.length);
                response.getOutputStream().write(enhancedBytes);
                
                // Process the fault message through messageProcessorService
                processEnhancedFault(cachedRequest, requestBody, enhancedResponse);
            } else {
                // Not a fault, write original response
                response.getOutputStream().write(responseBytes);
            }
            
        } catch (Exception e) {
            logger.error("SoapFaultEnhancementFilter:: Error processing response: {}", e.getMessage(), e);
            // Write original response on error
            byte[] responseBytes = responseWrapper.getCapturedBytes();
            response.getOutputStream().write(responseBytes);
        }
    }

    /**
     * Enhance SOAP fault with error trace ID
     * Only adds if not already present to avoid duplicates
     */
    private String enhanceSoapFault(String faultXml, HttpServletRequest request) {
        try {
            // Generate error trace ID
            String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            String interactionId = extractInteractionId(request);
            
            // Parse XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(faultXml.getBytes(StandardCharsets.UTF_8)));
            
            // Find the Fault element
            NodeList faultNodes = doc.getElementsByTagNameNS("*", "Fault");
            if (faultNodes.getLength() == 0) {
                faultNodes = doc.getElementsByTagName("Fault");
            }
            
            if (faultNodes.getLength() > 0) {
                Element faultElement = (Element) faultNodes.item(0);
                String faultCode = extractFaultCode(faultElement);
                String faultString = extractFaultString(faultElement);
                
                logger.error("SoapFaultEnhancementFilter:: Enhancing SOAP fault. interactionId={}, errorTraceId={}, faultCode={}, faultString={}", 
                        interactionId, errorTraceId, faultCode, faultString);
                
                // Set ingestion failed flag
                setIngestionFailedFlag(request, interactionId);
                
                // Log to CloudWatch
                LogUtil.logDetailedError(
                    400,
                    "SOAP Fault [" + faultCode + "]: " + faultString, 
                    interactionId, 
                    errorTraceId, 
                    new Exception("SOAP Fault: " + faultString)
                );
                
                // Find or create detail/Detail element
                Element detailElement = findOrCreateDetailElement(doc, faultElement);
                
                if (detailElement != null) {
                    // Check if InteractionId and ErrorTraceId already exist
                    boolean hasInteractionId = hasDetailElement(detailElement, "InteractionId");
                    boolean hasErrorTraceId = hasDetailElement(detailElement, "ErrorTraceId");
                    
                    if (hasInteractionId && hasErrorTraceId) {
                        logger.debug("SoapFaultEnhancementFilter:: Error trace details already present, skipping injection. interactionId={}", interactionId);
                        return faultXml; // Return original unchanged
                    }
                    
                    // Add InteractionId if not present
                    if (!hasInteractionId) {
                        Element interactionIdElement = doc.createElementNS("http://techbd.org/errorinfo", "err:InteractionId");
                        interactionIdElement.setTextContent(interactionId);
                        detailElement.appendChild(interactionIdElement);
                        logger.debug("SoapFaultEnhancementFilter:: Added InteractionId to SOAP fault. interactionId={}", interactionId);
                    }
                    
                    // Add ErrorTraceId if not present
                    if (!hasErrorTraceId) {
                        Element errorTraceIdElement = doc.createElementNS("http://techbd.org/errorinfo", "err:ErrorTraceId");
                        errorTraceIdElement.setTextContent(errorTraceId);
                        detailElement.appendChild(errorTraceIdElement);
                        logger.debug("SoapFaultEnhancementFilter:: Added ErrorTraceId to SOAP fault. errorTraceId={}", errorTraceId);
                    }
                    
                    logger.info("SoapFaultEnhancementFilter:: Successfully injected error trace ID. interactionId={}, errorTraceId={}", 
                            interactionId, errorTraceId);
                }
            }
            
            // Convert back to string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "no");
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
            
            return outputStream.toString(StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("SoapFaultEnhancementFilter:: Failed to enhance SOAP fault: {}", e.getMessage(), e);
            return faultXml; // Return original on error
        }
    }

    /**
     * Check if a detail element with the given local name exists
     */
    private boolean hasDetailElement(Element detailElement, String localName) {
        try {
            NodeList children = detailElement.getElementsByTagNameNS("*", localName);
            if (children.getLength() > 0) {
                return true;
            }
            
            // Also check without namespace
            children = detailElement.getElementsByTagName(localName);
            return children.getLength() > 0;
            
        } catch (Exception e) {
            logger.debug("SoapFaultEnhancementFilter:: Error checking for detail element {}: {}", localName, e.getMessage());
        }
        return false;
    }

    /**
     * Process the enhanced fault through messageProcessorService
     * Uses the cached request body captured by the request wrapper
     * Creates RequestContext if not available (for early-stage faults)
     */
    private void processEnhancedFault(HttpServletRequest request, String requestBody, String enhancedFault) {
        try {
            // Get or create RequestContext
            RequestContext context = (RequestContext) request.getAttribute(Constants.REQUEST_CONTEXT);
            
            if (context == null) {
                // Create minimal RequestContext for early-stage faults (like MustUnderstand)
                String interactionId = extractInteractionId(request);
                int requestPort = request.getServerPort();
                String sourceId = extractSourceId(request);
                String msgType = extractMsgType(request);
                
                context = new RequestContext(interactionId, requestPort, sourceId, msgType);
                
                logger.info("SoapFaultEnhancementFilter:: Created new RequestContext for early-stage fault. interactionId={}, port={}, sourceId={}, msgType={}", 
                        interactionId, requestPort, sourceId, msgType);
                
                // Store it in request attributes for potential use by other components
                request.setAttribute(Constants.REQUEST_CONTEXT, context);
            }
            
            // Populate headers if not set (required by S3UploadStep)
            if (context.getHeaders() == null) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> headers = (java.util.Map<String, String>) request
                        .getAttribute("CAPTURED_HEADERS");

                if (headers == null || headers.isEmpty()) {
                    logger.warn("SoapFaultEnhancementFilter:: No pre-captured headers found, extracting again");
                    headers = extractHeaders(request);
                }
                context.setHeaders(headers);
                final var destinationPort = HttpUtil.extractDestinationPort(headers);
                context.setDestinationPort(destinationPort);
                logger.info("SoapFaultEnhancementFilter:: Populated {} headers destinationPort :{} for interactionId={}", 
                        headers.size(), destinationPort, context.getInteractionId());
            }
            
            // Populate uploadTime if not set
            if (context.getUploadTime() == null) {
                context.setUploadTime(java.time.ZonedDateTime.now());
                logger.info("SoapFaultEnhancementFilter:: Set uploadTime to current time for interactionId={}", 
                        context.getInteractionId());
            }
            
            // Determine MessageSourceType based on SOAP message content
            org.techbd.ingest.commons.MessageSourceType messageSourceType = determineMessageSourceType(requestBody);
            context.setMessageSourceType(messageSourceType);
            
            if (requestBody != null && !requestBody.isEmpty()) {
                logger.info("SoapFaultEnhancementFilter:: Processing enhanced SOAP fault through messageProcessorService. interactionId={}, messageSourceType={}, uploadTime={}", 
                        context.getInteractionId(), messageSourceType, context.getUploadTime());
                
                // Ensure ingestion failed flag is set
                context.setIngestionFailed(true);
                
                // Process the message: captured request + enhanced fault response
                messageProcessorService.processMessage(context, requestBody, enhancedFault);
                
                logger.info("SoapFaultEnhancementFilter:: Successfully processed SOAP fault message. interactionId={}, requestBodyLength={}, messageSourceType={}", 
                        context.getInteractionId(), requestBody.length(), messageSourceType);
            } else {
                String interactionId = extractInteractionId(request);
                logger.warn("SoapFaultEnhancementFilter:: Request body is empty - skipping messageProcessorService. interactionId={}", 
                        interactionId);
                logger.info("SoapFaultEnhancementFilter:: Enhanced fault with error trace ID will still be returned to client");
            }
        } catch (Exception e) {
            String interactionId = extractInteractionId(request);
            logger.error("SoapFaultEnhancementFilter:: Failed to process enhanced fault through messageProcessorService. interactionId={}, error={}", 
                    interactionId, e.getMessage(), e);
        }
    }

    /**
     * Extract all headers from HttpServletRequest into a Map
     */
    private java.util.Map<String, String> extractHeaders(HttpServletRequest request) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        
        try {
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = request.getHeader(headerName);
                    if (headerValue != null) {
                        headers.put(headerName, headerValue);
                    }
                }
            }
            
            logger.info("SoapFaultEnhancementFilter:: Extracted {} headers from request", headers.size());
            
        } catch (Exception e) {
            logger.warn("SoapFaultEnhancementFilter:: Failed to extract headers: {}", e.getMessage());
        }
        
        return headers;
    }

    /**
     * Determine MessageSourceType based on SOAP message content
     * 
     * Detection logic:
     * - If contains "ProvideAndRegisterDocumentSetRequest" -> SOAP_PNR
     * - If contains "PRPA_IN201301UV02" or "PRPA_IN201309UV02" -> SOAP_PIX
     * - Otherwise -> SOAP_PIX (default for /ws endpoint)
     */
    private org.techbd.ingest.commons.MessageSourceType determineMessageSourceType(String soapMessage) {
        if (soapMessage == null || soapMessage.isEmpty()) {
            logger.debug("SoapFaultEnhancementFilter:: Empty SOAP message, defaulting to SOAP_PIX");
            return org.techbd.ingest.commons.MessageSourceType.SOAP_PIX;
        }
        
        try {
            // Check for PNR (Provide and Register Document Set)
            if (soapMessage.contains("ProvideAndRegisterDocumentSetRequest")) {
                logger.debug("SoapFaultEnhancementFilter:: Detected ProvideAndRegisterDocumentSetRequest -> SOAP_PNR");
                return org.techbd.ingest.commons.MessageSourceType.SOAP_PNR;
            }
            
            // Check for PIX Add (PRPA_IN201301UV02)
            if (soapMessage.contains("PRPA_IN201301UV02")) {
                logger.debug("SoapFaultEnhancementFilter:: Detected PRPA_IN201301UV02 -> SOAP_PIX");
                return org.techbd.ingest.commons.MessageSourceType.SOAP_PIX;
            }
            
            // Check for PIX Query (PRPA_IN201309UV02)
            if (soapMessage.contains("PRPA_IN201309UV02")) {
                logger.debug("SoapFaultEnhancementFilter:: Detected PRPA_IN201309UV02 -> SOAP_PIX");
                return org.techbd.ingest.commons.MessageSourceType.SOAP_PIX;
            }
            
            // Check for PIX Update Notification (PRPA_IN201302UV02)
            if (soapMessage.contains("PRPA_IN201302UV02")) {
                logger.debug("SoapFaultEnhancementFilter:: Detected PRPA_IN201302UV02 -> SOAP_PIX");
                return org.techbd.ingest.commons.MessageSourceType.SOAP_PIX;
            }
            
            // Default to SOAP_PIX for /ws endpoint
            logger.debug("SoapFaultEnhancementFilter:: No specific message type detected, defaulting to SOAP_PIX");
            return org.techbd.ingest.commons.MessageSourceType.SOAP_PIX;
            
        } catch (Exception e) {
            logger.warn("SoapFaultEnhancementFilter:: Error determining message source type: {}, defaulting to SOAP_PIX", e.getMessage());
            return org.techbd.ingest.commons.MessageSourceType.SOAP_PIX;
        }
    }

    /**
     * Extract sourceId from request URI
     * Pattern: /ingest/{sourceId}/{msgType} or /{sourceId}/{msgType}
     */
    private String extractSourceId(HttpServletRequest request) {
        try {
            String uri = request.getRequestURI();
            // Pattern: /ingest/sourceId/msgType or /sourceId/msgType
            String[] parts = uri.split("/");
            
            // Handle /ingest/sourceId/msgType pattern
            if (parts.length >= 3 && "ingest".equals(parts[1])) {
                return parts[2];
            }
            
            // Handle /sourceId/msgType pattern
            if (parts.length >= 2) {
                return parts[1];
            }
        } catch (Exception e) {
            logger.debug("SoapFaultEnhancementFilter:: Failed to extract sourceId from URI: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Extract msgType from request URI
     * Pattern: /ingest/{sourceId}/{msgType} or /{sourceId}/{msgType}
     */
    private String extractMsgType(HttpServletRequest request) {
        try {
            String uri = request.getRequestURI();
            // Pattern: /ingest/sourceId/msgType or /sourceId/msgType
            String[] parts = uri.split("/");
            
            // Handle /ingest/sourceId/msgType pattern
            if (parts.length >= 4 && "ingest".equals(parts[1])) {
                return parts[3];
            }
            
            // Handle /sourceId/msgType pattern
            if (parts.length >= 3) {
                return parts[2];
            }
        } catch (Exception e) {
            logger.debug("SoapFaultEnhancementFilter:: Failed to extract msgType from URI: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Find or create detail/Detail element in SOAP fault
     */
    private Element findOrCreateDetailElement(Document doc, Element faultElement) {
        // Try to find existing detail element (SOAP 1.1)
        NodeList detailNodes = faultElement.getElementsByTagName("detail");
        if (detailNodes.getLength() > 0) {
            return (Element) detailNodes.item(0);
        }
        
        // Try to find Detail element (SOAP 1.2)
        detailNodes = faultElement.getElementsByTagNameNS("*", "Detail");
        if (detailNodes.getLength() > 0) {
            return (Element) detailNodes.item(0);
        }
        
        // Create new detail element
        // Determine SOAP version from namespace
        String soapNamespace = faultElement.getNamespaceURI();
        Element detailElement;
        
        if (soapNamespace != null && soapNamespace.contains("2003/05/soap-envelope")) {
            // SOAP 1.2
            detailElement = doc.createElementNS(soapNamespace, "Detail");
        } else {
            // SOAP 1.1
            detailElement = doc.createElement("detail");
        }
        
        faultElement.appendChild(detailElement);
        return detailElement;
    }

    /**
     * Extract fault code from fault element
     */
    private String extractFaultCode(Element faultElement) {
        // SOAP 1.1: <faultcode>
        NodeList faultCodeNodes = faultElement.getElementsByTagName("faultcode");
        if (faultCodeNodes.getLength() > 0) {
            return faultCodeNodes.item(0).getTextContent();
        }
        
        // SOAP 1.2: <Code><Value>
        NodeList codeNodes = faultElement.getElementsByTagNameNS("*", "Code");
        if (codeNodes.getLength() > 0) {
            NodeList valueNodes = ((Element) codeNodes.item(0)).getElementsByTagNameNS("*", "Value");
            if (valueNodes.getLength() > 0) {
                return valueNodes.item(0).getTextContent();
            }
        }
        
        return "UNKNOWN";
    }

    /**
     * Extract fault string from fault element
     */
    private String extractFaultString(Element faultElement) {
        // SOAP 1.1: <faultstring>
        NodeList faultStringNodes = faultElement.getElementsByTagName("faultstring");
        if (faultStringNodes.getLength() > 0) {
            return faultStringNodes.item(0).getTextContent();
        }
        
        // SOAP 1.2: <Reason><Text>
        NodeList reasonNodes = faultElement.getElementsByTagNameNS("*", "Reason");
        if (reasonNodes.getLength() > 0) {
            NodeList textNodes = ((Element) reasonNodes.item(0)).getElementsByTagNameNS("*", "Text");
            if (textNodes.getLength() > 0) {
                return textNodes.item(0).getTextContent();
            }
        }
        
        return "Unknown fault";
    }

    /**
     * Extract interaction ID from request
     */
    private String extractInteractionId(HttpServletRequest request) {
        // Try header
        String interactionId = request.getHeader(Constants.HEADER_INTERACTION_ID);
        if (interactionId != null && !interactionId.isBlank()) {
            return interactionId;
        }
        
        // Try attribute
        Object attrInteractionId = request.getAttribute(Constants.INTERACTION_ID);
        if (attrInteractionId != null) {
            return attrInteractionId.toString();
        }
        
        return "unknown";
    }

    /**
     * Set ingestion failed flag
     */
    private void setIngestionFailedFlag(HttpServletRequest request, String interactionId) {
        try {
            RequestContext context = (RequestContext) request.getAttribute(Constants.REQUEST_CONTEXT);
            if (context != null) {
                context.setIngestionFailed(true);
                logger.info("SoapFaultEnhancementFilter:: Set ingestionFailed=true for interactionId={}", interactionId);
            }
        } catch (Exception e) {
            logger.warn("SoapFaultEnhancementFilter:: Failed to set ingestionFailed flag: {}", e.getMessage());
        }
    }

    /**
     * Request wrapper that caches the request body for multiple reads
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            InputStream requestInputStream = request.getInputStream();
            this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        public String getCachedBody() {
            return new String(this.cachedBody, StandardCharsets.UTF_8);
        }
    }

    /**
     * ServletInputStream implementation that reads from cached byte array
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final InputStream cachedBodyInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            try {
                return cachedBodyInputStream.available() == 0;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return cachedBodyInputStream.read();
        }
    }

    /**
     * Response wrapper to capture response bytes
     */
    private static class ResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream capture;
        private ServletOutputStream output;
        private PrintWriter writer;

        public ResponseWrapper(HttpServletResponse response) {
            super(response);
            capture = new ByteArrayOutputStream(response.getBufferSize());
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called on this response.");
            }

            if (output == null) {
                output = new ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        capture.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                    }
                };
            }

            return output;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (output != null) {
                throw new IllegalStateException("getOutputStream() has already been called on this response.");
            }

            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(capture, getCharacterEncoding()));
            }

            return writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            super.flushBuffer();
            if (writer != null) {
                writer.flush();
            } else if (output != null) {
                output.flush();
            }
        }

        public byte[] getCapturedBytes() {
            if (writer != null) {
                writer.flush();
            }
            return capture.toByteArray();
        }
    }
}