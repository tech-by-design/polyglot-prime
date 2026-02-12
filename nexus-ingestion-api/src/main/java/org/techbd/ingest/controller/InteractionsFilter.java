package org.techbd.ingest.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.portconfig.PortResolverService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.SoapFaultUtil;
import org.techbd.ingest.util.TemplateLogger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Component
public class InteractionsFilter extends OncePerRequestFilter {

    private final TemplateLogger LOG;
    private final PortConfig portConfig;
    private final PortResolverService portResolverService;
    private final S3Client s3Client;
    private final Cache<String, X509Certificate[]> caCache;
    private final SoapFaultUtil soapFaultUtil;
    private static final Pattern PATH_PATTERN = Pattern.compile("^/(?:ingest/)?([^/]+)/([^/]+)(?:/.*)?$");

    public InteractionsFilter(AppLogger appLogger, PortConfig portConfig, 
                             PortResolverService portResolverService, S3Client s3Client, SoapFaultUtil soapFaultUtil) {
        this.LOG = appLogger.getLogger(InteractionsFilter.class);
        this.portConfig = portConfig;
        this.portResolverService = portResolverService;
        this.s3Client = s3Client;
        this.soapFaultUtil = soapFaultUtil;
        this.caCache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(60)).build();
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest origRequest, final HttpServletResponse origResponse,
            final FilterChain chain) throws IOException, ServletException {
        boolean isSoapEndpoint = origRequest.getRequestURI().startsWith("/ws");
        String interactionId = origRequest.getHeader(Constants.HEADER_INTERACTION_ID);;
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = (String) origRequest.getAttribute(Constants.INTERACTION_ID);
        }
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = UUID.randomUUID().toString();
        }
        try {
            if (!origRequest.getRequestURI().equals("/")
                    && !origRequest.getRequestURI().startsWith("/actuator/health")) {
                LOG.info("InteractionsFilter: start - method={} uri={}", origRequest.getMethod(),
                        origRequest.getRequestURI());

                if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
                    try {
                        Enumeration<String> headerNames = origRequest.getHeaderNames();

                        if (headerNames != null && headerNames.hasMoreElements()) {

                            String allHeaders = Collections.list(headerNames).stream()
                                    .map(name -> {
                                        List<String> values = Collections.list(origRequest.getHeaders(name));
                                        return name + " = " + String.join(", ", values);
                                    })
                                    .collect(Collectors.joining(" | "));

                            LOG.info("InteractionsFilter: interactionId={} | Request Headers - {}",
                                    interactionId,
                                    allHeaders);
                        } else {
                            LOG.info("InteractionsFilter: interactionId={} | Request Headers - none",
                                    interactionId);
                        }

                    } catch (Exception e) {
                        LOG.warn("InteractionsFilter: interactionId={} | failed to enumerate request headers",
                                interactionId, e);
                    }
                } 
        
                origRequest.setAttribute(Constants.INTERACTION_ID, interactionId);
                LOG.info("Incoming Request - interactionId={}", interactionId);

                // 1) determine request port (prefer X-Forwarded-Port header)
                int requestPort = resolveRequestPort(origRequest);
                LOG.info("InteractionsFilter: resolved request port={} interactionId: {}", requestPort, interactionId);

                // 2) Extract sourceId and msgType from URI path
                String requestUri = origRequest.getRequestURI();
                String sourceId = null;
                String msgType = null;
                
                Matcher matcher = PATH_PATTERN.matcher(requestUri);
                if (matcher.matches()) {
                    sourceId = matcher.group(1);
                    msgType = matcher.group(2);
                    LOG.info("InteractionsFilter: extracted from path - sourceId={}, msgType={} interactionId: {}", sourceId, msgType, interactionId);
                } else {
                    LOG.info("InteractionsFilter: path does not match expected pattern for sourceId/msgType extraction: {} interactionId: {}", requestUri, interactionId);
                }
                if (sourceId == null) {
                    sourceId = origRequest.getHeader(Constants.HEADER_SOURCE_ID);
                }
                if(msgType == null) {
                    msgType = origRequest.getHeader(Constants.HEADER_MSG_TYPE);
                }
               RequestContext context = new RequestContext(interactionId,requestPort,sourceId,msgType);

                // 4) Use PortResolverService to find the matching port entry
                Optional<PortConfig.PortEntry> portEntryOpt = portResolverService.resolve(context);

                if (portEntryOpt.isEmpty()) {
                    String msg = String.format("No port configuration entry found for port %d, sourceId=%s, msgType=%s - rejecting request",
                            requestPort, sourceId, msgType);
                    LOG.warn("InteractionsFilter: {}", msg);
                    origResponse.setStatus(HttpStatus.BAD_REQUEST.value());
                    origResponse.setContentType("application/json;charset=UTF-8");
                    String safe = msg.replace("\\", "\\\\").replace("\"", "\\\"");
                    try {
                        origResponse.getWriter()
                                .write(String.format("{\"error\":\"Bad Request\",\"description\":\"%s\"}", safe));
                        origResponse.getWriter().flush();
                    } catch (IOException ioe) {
                        LOG.error("InteractionsFilter: failed to write Bad Request response", ioe);
                    }
                    return;
                }

                PortConfig.PortEntry portEntry = portEntryOpt.get();
                LOG.info("InteractionsFilter: resolved PortConfig entry for port {} -> sourceId={}, msgType={}, route={}", 
                        requestPort, portEntry.sourceId, portEntry.msgType, portEntry.route);

                // 3) If this port config requires mtls via mtls field, client must supply
                // header
                String mtlsName = portEntry.mtls;
                String mtlsBucket = System.getenv(Constants.MTLS_BUCKET_NAME);
                origRequest.setAttribute(Constants.ACK_CONTENT_TYPE, portEntry.ackContentType);
                LOG.info("InteractionsFilter: portEntry.mtls={}, MTLS_BUCKET={}", mtlsName, mtlsBucket);

                String clientCertHeader = origRequest.getHeader(Constants.REQ_HEADER_MTLS_CLIENT_CERT);
                // Accept header value as URL-encoded (always expected to be URL-encoded).
                String clientCertPem = null;
                if (clientCertHeader != null) {
                    String v = clientCertHeader.trim();
                    
                    // Header value is always expected to be URL-encoded, so decode it
                    try {
                        v = v.replace("+", "%2B");
                        v = v.replace(" ", "%20");
                        clientCertPem = java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8);
                        LOG.info("InteractionsFilter: decoded client cert header as URL-encoded, decoded length={}", clientCertPem.length());
                    } catch (Exception urlDecodeException) {
                        LOG.error("InteractionsFilter: URL decoding failed, treating as raw header", urlDecodeException);
                        clientCertPem = v;
                    }
                }

                if (mtlsName != null && !mtlsName.isBlank()) {
                    if (clientCertHeader == null) {
                        String msg = String.format("Missing required header '%s' for mTLS on port %d",
                                Constants.REQ_HEADER_MTLS_CLIENT_CERT, requestPort);
                        LOG.warn("InteractionsFilter: {}", msg);
                        origResponse.setStatus(HttpStatus.BAD_REQUEST.value());
                        origResponse.setContentType("application/json;charset=UTF-8");
                        String safe = msg.replace("\\", "\\\\").replace("\"", "\\\"");
                        try {
                            origResponse.getWriter()
                                    .write(String.format("{\"error\":\"Bad Request\",\"description\":\"%s\"}", safe));
                            origResponse.getWriter().flush();
                        } catch (IOException ioe) {
                            LOG.error(
                                    "InteractionsFilter: failed to write Bad Request response for missing mTLS header",
                                    ioe);
                        }
                        return;
                    }
                } else {
                    // mtlsName null -> still allow header-based flow if header present, but not
                    // mandatory
                    LOG.info("InteractionsFilter: mtls not set in port config for port {}. clientCertHeaderPresent={}",
                            requestPort, clientCertHeader != null);
                }

                // prepare server bundle key if mtlsName + bucket available
                String serverBundleKey = (mtlsName != null && !mtlsName.isBlank() && mtlsBucket != null
                        && !mtlsBucket.isBlank())
                                ? (mtlsName + "-bundle.pem")
                                : null;

                // If mtls is not configured for this port, skip mTLS processing entirely.
                if (mtlsName == null || mtlsName.isBlank()) {
                    LOG.info("InteractionsFilter: mtls NOT configured for port {} - proceeding without mTLS",
                            requestPort);
                    chain.doFilter(origRequest, origResponse);
                    return;
                }

                try {
                    // parse client certificate chain (if header present)
                    X509Certificate[] clientChain = clientCertPem != null ? parsePemChain(clientCertPem)
                            : new X509Certificate[0];
                    LOG.info("InteractionsFilter: parsed client certificate chain length={}", clientChain.length);

                    // 4) obtain CA/server cert(s) to validate client against:
                    X509Certificate[] caCerts = null;
                    if (serverBundleKey != null) {
                        final String bucketFinal = mtlsBucket;
                        final String keyFinal = serverBundleKey;
                        final String cacheKey = "s3://" + bucketFinal + "/" + keyFinal;

                        // log cache hit/miss
                        X509Certificate[] cached = caCache.getIfPresent(cacheKey);
                        if (cached != null) {
                            LOG.info("InteractionsFilter: CA bundle cache hit for {}", cacheKey);
                        } else {
                            LOG.info("InteractionsFilter: CA bundle cache miss for {}, fetching from S3 {}/{}",
                                    cacheKey, bucketFinal, keyFinal);
                        }

                        caCerts = caCache.get(cacheKey, () -> {
                            try {
                                return fetchPemFromS3AndParse(bucketFinal, keyFinal);
                            } catch (Exception ex) {
                                LOG.error("InteractionsFilter: error fetching CA bundle from S3 {}/{}", bucketFinal,
                                        keyFinal, ex);
                                throw new RuntimeException(ex);
                            }
                        });
                        LOG.info("InteractionsFilter: loaded CA bundle from S3, cert count={}",
                                caCerts != null ? caCerts.length : 0);
                    }

                    // 5) verify client chain against CA certs (server bundle is used as trust
                    // anchors)
                    verifyCertificateChain(clientChain, caCerts);
                    LOG.info("InteractionsFilter: mTLS verification succeeded for port={} uri={}", requestPort,
                            origRequest.getRequestURI());

                } catch (ExecutionException ee) {
                    LOG.error("InteractionsFilter: failed to load CA bundle from cache/S3", ee);
                    // return 500 with a small JSON body (similar handling as IOException)
                    origResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    origResponse.setContentType("application/json;charset=UTF-8");
                    String msg = "Failed to load CA bundle from cache/S3";
                    String safe = msg.replace("\\", "\\\\").replace("\"", "\\\"");
                    try {
                        origResponse.getWriter().write(
                                String.format("{\"error\":\"Internal Server Error\",\"description\":\"%s\"}", safe));
                        origResponse.getWriter().flush();
                    } catch (IOException ioe2) {
                        LOG.error("InteractionsFilter: failed to write Internal Server Error response", ioe2);
                    }
                    return;
                } catch (IOException ioe) {
                    LOG.error("InteractionsFilter: IO error during mTLS processing", ioe);
                    origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            "mTLS processing error: " + ioe.getMessage());
                    return;
                } catch (CertificateException ce) {
                    // Log full details (stack trace and cause) and return a JSON body with a
                    // descriptive message.
                    LOG.error("InteractionsFilter: mTLS verification failed for port={}. Cause:", requestPort, ce);
                    origResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
                    origResponse.setContentType("application/json;charset=UTF-8");
                    String description = ce.getMessage() != null ? ce.getMessage() : "mTLS verification failed";
                    // escape backslashes, quotes and newlines for safe JSON embedding
                    description = description.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = String.format("{\"error\":\"mTLS verification failed\",\"description\":\"%s\"}",
                            description);
                    try {
                        origResponse.getWriter().write(json);
                        origResponse.getWriter().flush();
                    } catch (IOException ioe) {
                        LOG.error("InteractionsFilter: failed to write error response", ioe);
                    }
                    return;
                } catch (RuntimeException re) {
                    LOG.error("InteractionsFilter: unexpected runtime error during mTLS processing", re);
                    // unwrap causes to detect S3 missing key / 404
                    Throwable t = re;
                    while (t != null) {
                        if (t instanceof software.amazon.awssdk.services.s3.model.NoSuchKeyException) {
                            LOG.warn("InteractionsFilter: CA bundle not found in S3: {}", t.getMessage());
                            origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                    "mTLS CA bundle not found: " + t.getMessage());
                            return;
                        }
                        if (t instanceof software.amazon.awssdk.services.s3.model.S3Exception) {
                            software.amazon.awssdk.services.s3.model.S3Exception s3e = (software.amazon.awssdk.services.s3.model.S3Exception) t;
                            if (s3e.statusCode() == 404) {
                                LOG.warn("InteractionsFilter: S3 reported 404 for CA bundle: {}", s3e.getMessage());
                                origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                        "mTLS CA bundle not found: " + s3e.getMessage());
                                return;
                            }
                        }
                        if (t instanceof IOException) {
                            origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                    "mTLS processing error: " + t.getMessage());
                            return;
                        }
                        if (t instanceof CertificateException) {
                            origResponse.sendError(HttpStatus.UNAUTHORIZED.value(),
                                    "mTLS verification failed: " + t.getMessage());
                            return;
                        }
                        t = t.getCause();
                    }
                    // fallback generic error
                    origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "mTLS processing error");
                    return;
                }

                // success: proceed
                Enumeration<String> headerNames = origRequest.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = origRequest.getHeader(headerName);
                    LOG.info("{} - Header: {} = {} for interaction id: {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS,
                            headerName, headerValue, interactionId);
                }

                chain.doFilter(origRequest, origResponse);
                return;
            }
            chain.doFilter(origRequest, origResponse);
            
        } catch (Exception e) {
            // NEW: Handle SOAP-specific errors
            if (isSoapEndpoint && isSoapParsingError(e)) {
                // Use fallback for interactionId if not set yet
                if (interactionId == null) {
                    interactionId = "unknown";
                }
                handleSoapError(origRequest, origResponse, e, interactionId);
            } else {
                // Re-throw non-SOAP errors
                throw e;
            }
        }
    }
    /**
     * Check if the exception is related to SOAP parsing/message creation
     */
    private boolean isSoapParsingError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        return message.contains("Unable to create SOAP message") ||
               message.contains("Empty request body") ||
               message.contains("Unable to create envelope") ||
               message.contains("SAAJ0511") ||
               e instanceof IOException;
    }

    /**
     * Generate and send SOAP fault response with error trace ID
     */
    private void handleSoapError(HttpServletRequest request, HttpServletResponse response, 
                                 Exception e, String interactionId) {
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        
        try {
            // Determine error message
            String errorMessage = "Unable to process SOAP request";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Empty request body")) {
                    errorMessage = "Empty request body - no SOAP message provided";
                } else if (e.getMessage().contains("Unable to create envelope")) {
                    errorMessage = "Invalid SOAP message format";
                } else {
                    errorMessage = e.getMessage();
                }
            }
            
            LOG.error("InteractionsFilter:: SOAP parsing error. interactionId={}, errorTraceId={}, error={}", 
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                400, 
                "SOAP parsing error: " + errorMessage, 
                interactionId, 
                errorTraceId, 
                e
            );
            
            // Determine SOAP version and generate fault using utility
            String contentType = request.getContentType();
            boolean isSoap12 = soapFaultUtil.isSoap12(contentType);
            
            String soapFault = soapFaultUtil.createSoapFault(errorMessage, interactionId, errorTraceId, isSoap12);
            
            // Set response
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(isSoap12 ? "application/soap+xml; charset=utf-8" : "text/xml; charset=utf-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            
            PrintWriter writer = response.getWriter();
            writer.write(soapFault);
            writer.flush();
            
            LOG.info("InteractionsFilter:: Sent SOAP fault response. interactionId={}, errorTraceId={}", 
                    interactionId, errorTraceId);
            
        } catch (Exception ex) {
            LOG.error("InteractionsFilter:: Failed to generate SOAP fault response. interactionId={}, errorTraceId={}, error={}", 
                    interactionId, errorTraceId, ex.getMessage(), ex);
        }
    }

    /**
     * Resolves the request port by checking the X-Forwarded-Port header (case-insensitive)
     * or falling back to the server port.
     *
     * @param req the HTTP servlet request
     * @return the resolved port number
     */
    private int resolveRequestPort(HttpServletRequest req) {
        var headerNames = req.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String hn = headerNames.nextElement();
                if (hn != null && hn.equalsIgnoreCase("X-Forwarded-Port")) {
                    String val = req.getHeader(hn);
                    try {
                        return Integer.parseInt(val);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return req.getServerPort();
    }

    private X509Certificate[] fetchPemFromS3AndParse(String bucket, String key) throws IOException, CertificateException {
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest)) {
            String pem = new BufferedReader(new InputStreamReader(s3Stream)).lines().collect(Collectors.joining("\n"));
            return parsePemChain(pem);
        }
    }

    private X509Certificate[] fetchCaBundleFromS3Uri(String s3Uri) throws IOException, CertificateException {
        URI uri = URI.create(s3Uri);
        String bucket = uri.getHost();
        String key = uri.getPath().substring(1);
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest)) {
            return parsePemChain(new BufferedReader(new InputStreamReader(s3Stream)).lines().collect(Collectors.joining("\n")));
        }
    }

    private X509Certificate[] parsePemChain(String pem) throws IOException, CertificateException {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            List<X509Certificate> certs = new ArrayList<>();
            Object obj;
            while ((obj = parser.readObject()) != null) {
                if (obj instanceof X509CertificateHolder) {
                    certs.add(new JcaX509CertificateConverter().getCertificate((X509CertificateHolder) obj));
                }
            }
            return certs.toArray(new X509Certificate[0]);
        }
    }

    /**
     * Verify certificate chain against provided CA certs. Wraps underlying
     * checked exceptions into CertificateException to simplify callers.
     */
    private void verifyCertificateChain(X509Certificate[] clientChain, X509Certificate[] caCerts)
            throws CertificateException {
        if (clientChain == null || clientChain.length == 0) {
            throw new CertificateException("No valid client certificates provided");
        }
        if (caCerts == null || caCerts.length == 0) {
            throw new CertificateException("No CA certificates provided for validation");
        }
        try {
            // Log parsed client certs and CA certs for debugging (subject/issuer)
            for (int i = 0; i < clientChain.length; i++) {
                X509Certificate c = clientChain[i];
                LOG.info("InteractionsFilter: clientChain[{}] subject='{}' issuer='{}' serial={}", i, c.getSubjectX500Principal(), c.getIssuerX500Principal(), c.getSerialNumber());
            }
            for (int i = 0; i < caCerts.length; i++) {
                X509Certificate c = caCerts[i];
                LOG.info("InteractionsFilter: caCerts[{}] subject='{}' issuer='{}' serial={}", i, c.getSubjectX500Principal(), c.getIssuerX500Principal(), c.getSerialNumber());
            }

            CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(clientChain));

            // Build TrustAnchor set from all CA certs in the bundle
            java.util.Set<TrustAnchor> trustAnchors = new java.util.HashSet<>();
            for (X509Certificate ca : caCerts) {
                trustAnchors.add(new TrustAnchor(ca, null));
            }

            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, params);
        } catch (CertPathValidatorException | java.security.NoSuchAlgorithmException | java.security.InvalidAlgorithmParameterException e) {
            // Build a more detailed diagnostic message for logs / internal use
            StringBuilder details = new StringBuilder();
            details.append(e.getClass().getSimpleName());
            if (e instanceof CertPathValidatorException) {
                CertPathValidatorException cpve = (CertPathValidatorException) e;
                details.append(": reason=").append(cpve.getReason());
                try {
                    int idx = cpve.getIndex();
                    details.append(", certIndex=").append(idx);
                } catch (Throwable ignored) {
                }
            }
            if (e.getMessage() != null) {
                details.append(", message=").append(e.getMessage());
            }
            // Throw CertificateException with the detailed message as internal diagnostic.
            // Note: callers should log the exception (including cause) but return a generic message to clients.
            throw new CertificateException("Certificate chain validation failed: " + details.toString(), e);
        }
    }
}