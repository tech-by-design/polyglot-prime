package org.techbd.ingest.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
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
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import org.techbd.ingest.commons.Constants;

@Component
public class InteractionsFilter extends OncePerRequestFilter {

    private final TemplateLogger LOG;

    private final PortConfig portConfig;
    private final S3Client s3Client;
    private final Cache<String, X509Certificate[]> caCache;  // Use Caffeine or Guava for caching

    // constructor now accepts PortConfig
    public InteractionsFilter(AppLogger appLogger, PortConfig portConfig, S3Client s3Client) {
        this.LOG = appLogger.getLogger(InteractionsFilter.class);
        this.portConfig = portConfig;
        this.s3Client = s3Client;
        this.caCache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(60)).build();
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest origRequest, final HttpServletResponse origResponse,
            final FilterChain chain) throws IOException, ServletException {
        if (!origRequest.getRequestURI().equals("/")
                && !origRequest.getRequestURI().startsWith("/actuator/health")) {
            LOG.info("InteractionsFilter: start - method={} uri={}", origRequest.getMethod(),
                    origRequest.getRequestURI());

            // DEBUG: list all headers received (temporary - remove this later)
            try {
                Enumeration<String> headerNames = origRequest.getHeaderNames();
                if (headerNames != null) {
                    while (headerNames.hasMoreElements()) {
                        String name = headerNames.nextElement();
                        List<String> values = new ArrayList<>();
                        Enumeration<String> vals = origRequest.getHeaders(name);
                        while (vals.hasMoreElements()) {
                            values.add(vals.nextElement());
                        }
                        LOG.info("InteractionsFilter: Request Header - {} = {}", name, String.join(", ", values));
                    }
                }
            } catch (Exception e) {
                LOG.warn("InteractionsFilter: failed to enumerate request headers", e);
            }

            if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {

                String interactionId = UUID.randomUUID().toString();
                origRequest.setAttribute("interactionId", interactionId);
                LOG.info("Incoming Request - interactionId={}", interactionId);

                // 1) determine request port (prefer X-Forwarded-Port header)
                int requestPort = resolveRequestPort(origRequest);
                LOG.info("InteractionsFilter: resolved request port={}", requestPort);

                // 2) find port config entry that matches the request port
                PortConfig.PortEntry portEntry = null;
                if (portConfig != null && portConfig.isLoaded()) {
                    for (PortConfig.PortEntry e : portConfig.getPortConfigurationList()) {
                        if (e != null && e.port == requestPort) {
                            portEntry = e;
                            break;
                        }
                    }
                }

                if (portEntry == null) {
                    String msg = String.format("No port configuration entry found for port %d - rejecting request",
                            requestPort);
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
                LOG.info("InteractionsFilter: found PortConfig entry for port {} -> {}", requestPort, portEntry);

                // 3) If this port config requires mtls via mtls field, client must supply
                // header
                String mtlsName = portEntry.mtls;
                String mtlsBucket = System.getenv(Constants.MTLS_BUCKET_NAME);
                LOG.info("InteractionsFilter: portEntry.mtls={}, MTLS_BUCKET={}", mtlsName, mtlsBucket);

                String clientCertHeader = origRequest.getHeader(Constants.REQ_HEADER_MTLS_CLIENT_CERT);
                // Accept header value encoded as URL-encoded or base64 (recommended) or raw PEM (less reliable).
                String clientCertPem = null;
                if (clientCertHeader != null) {
                    String v = clientCertHeader.trim();
                    
                    // First identify the encoding type and decode accordingly
                    // Check URL-encoded first as it's more specific
                    if (isUrlEncoded(v)) {
                        // URL-encoded detected - decode it
                        try {
                            clientCertPem = java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8);
                            LOG.info("InteractionsFilter: detected and decoded client cert header as URL-encoded, decoded length={}", clientCertPem.length());
                        } catch (Exception urlDecodeException) {
                            LOG.error("InteractionsFilter: URL decoding failed, treating as raw header", urlDecodeException);
                            clientCertPem = v;
                        }
                    } else if (isBase64Encoded(v)) {
                        // Base64 detected - decode it
                        try {
                            byte[] decoded = java.util.Base64.getDecoder().decode(v);
                            clientCertPem = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                            LOG.info("InteractionsFilter: detected and decoded client cert header as base64, bytes={}", decoded.length);
                        } catch (IllegalArgumentException iae) {
                            LOG.error("InteractionsFilter: Base64 decoding failed, treating as raw header", iae);
                            clientCertPem = v;
                        }
                    } else {
                        // Neither URL-encoded nor Base64 - treat as raw PEM
                        clientCertPem = v;
                        LOG.info("InteractionsFilter: client cert header appears to be raw PEM, length={}", clientCertPem.length());
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

            LOG.info("InteractionsFilter: feature disabled or excluded URI - continuing chain");
        }
        chain.doFilter(origRequest, origResponse);
    }

    // helper to resolve X-Forwarded-Port (case-insensitive) or fallback to server port
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

    /**
     * Identifies if a string is URL-encoded by checking for URL-encoded characters
     * and attempting to decode it to see if the result differs from the original.
     */
    private boolean isUrlEncoded(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // Common URL-encoded characters that indicate URL encoding
        if (value.contains("%20") || value.contains("%2B") || value.contains("%2F") 
            || value.contains("%3D") || value.contains("%0A") || value.contains("%0D")) {
            return true;
        }
        
        // Try to decode and see if it changes the string
        try {
            String decoded = java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
            return !decoded.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Identifies if a string is Base64-encoded by checking its characteristics.
     * This method is more conservative to avoid false positives with URL-encoded content.
     */
    private boolean isBase64Encoded(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // If it contains URL-encoded characters, it's not pure Base64
        if (value.contains("%")) {
            return false;
        }
        
        // If it looks like PEM format, it's not pure Base64
        if (value.contains("-----BEGIN") || value.contains("-----END")) {
            return false;
        }
        
        // Remove whitespace for validation
        String trimmed = value.replaceAll("\\s", "");
        
        // Check if length is multiple of 4 (Base64 requirement)
        if (trimmed.length() % 4 != 0) {
            return false;
        }
        
        // Check if it contains only valid Base64 characters
        if (!trimmed.matches("^[A-Za-z0-9+/]*={0,2}$")) {
            return false;
        }
        
        // Must be reasonably long to be a meaningful certificate
        if (trimmed.length() < 100) {
            return false;
        }
        
        // Try to decode to verify it's valid Base64
        try {
            java.util.Base64.getDecoder().decode(trimmed);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
