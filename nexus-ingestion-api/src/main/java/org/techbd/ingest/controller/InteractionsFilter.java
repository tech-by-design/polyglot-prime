package org.techbd.ingest.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.config.MtlsConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;

import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class InteractionsFilter extends OncePerRequestFilter {

    private final TemplateLogger LOG;

    private final MtlsConfig mtlsConfig;
    private final S3Client s3Client;
    private final Cache<String, X509Certificate[]> caCache;  // Use Caffeine or Guava for caching

    public InteractionsFilter(AppLogger appLogger, MtlsConfig mtlsConfig, S3Client s3Client) {
        this.LOG = appLogger.getLogger(InteractionsFilter.class);
        this.mtlsConfig = mtlsConfig;
        this.s3Client = s3Client;
        this.caCache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(60)).build();
    }

    protected void doFilterInternal(final HttpServletRequest origRequest, final HttpServletResponse origResponse,
            final FilterChain chain) throws IOException, ServletException {

        if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)
                && !origRequest.getRequestURI().equals("/")
                && !origRequest.getRequestURI().startsWith("/actuator/health")) {

            int port = origRequest.getServerPort();
            if (!mtlsConfig.getEnabledPorts().contains(port) || origRequest.getHeader("X-Amzn-Mtls-Clientcert") == null) {
                chain.doFilter(origRequest, origResponse);
                return;
            }

            String mtlsValue = getMtlsValueFromConfig(origRequest);
            String trustStoreName = mtlsValue + "-trust-store";
            String s3Uri = mtlsConfig.getTrustStoreMappings().get(trustStoreName);
            if (s3Uri == null) {
                origResponse.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid mTLS config");
                return;
            }

            try {
                // parse client PEM chain
                String pemChain = origRequest.getHeader("X-Amzn-Mtls-Clientcert");
                X509Certificate[] clientChain = parsePemChain(pemChain);

                // fetch or load CA bundle from cache / S3
                X509Certificate[] caCerts = caCache.get(s3Uri, () -> {
                    try {
                        return fetchCaBundleFromS3(s3Uri);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });

                // verify the client chain against CA certs
                verifyCertificateChain(clientChain, caCerts);

            } catch (ExecutionException ee) {
                throw new IOException("Failed to load CA bundle from cache/S3", ee);
            } catch (IOException ioe) {
                origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "mTLS processing error: " + ioe.getMessage());
                return;
            } catch (CertificateException ce) {
                origResponse.sendError(HttpStatus.UNAUTHORIZED.value(), "mTLS verification failed: " + ce.getMessage());
                return;
            } catch (RuntimeException re) {
                // unwrap the cause if it's an IOException or CertificateException
                Throwable cause = re.getCause();
                if (cause instanceof IOException) {
                    origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "mTLS processing error: " + cause.getMessage());
                    return;
                } else if (cause instanceof CertificateException) {
                    origResponse.sendError(HttpStatus.UNAUTHORIZED.value(), "mTLS verification failed: " + cause.getMessage());
                    return;
                } else {
                    origResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "mTLS processing error");
                    return;
                }
            }

            String interactionId = UUID.randomUUID().toString();
            origRequest.setAttribute("interactionId", interactionId);
            LOG.info("Incoming Request - interactionId={}", interactionId);

            var headerNames = origRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                var headerName = headerNames.nextElement();
                var headerValue = origRequest.getHeader(headerName);
                LOG.info("{} - Header: {} = {} for interaction id: {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS,
                        headerName, headerValue, interactionId);
            }

            chain.doFilter(origRequest, origResponse);
            return;
        }

        chain.doFilter(origRequest, origResponse);
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

    private X509Certificate[] fetchCaBundleFromS3(String s3Uri) throws IOException, CertificateException {
        URI uri = URI.create(s3Uri);
        String bucket = uri.getHost();
        String key = uri.getPath().substring(1);
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest)) {
            return parsePemChain(new BufferedReader(new InputStreamReader(s3Stream)).lines().collect(Collectors.joining("\n")));
        }
    }

    /**
     * Verify certificate chain against provided CA certs.
     * Wraps underlying checked exceptions into CertificateException to simplify callers.
     */
    private void verifyCertificateChain(X509Certificate[] clientChain, X509Certificate[] caCerts)
            throws CertificateException {
        try {
            CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(clientChain));
            TrustAnchor trustAnchor = new TrustAnchor(caCerts[0], null);
            PKIXParameters params = new PKIXParameters(Set.of(trustAnchor));
            params.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, params);
        } catch (CertPathValidatorException | java.security.NoSuchAlgorithmException | java.security.InvalidAlgorithmParameterException e) {
            throw new CertificateException("Certificate chain validation failed", e);
        }
    }

    private String getMtlsValueFromConfig(HttpServletRequest req) {
        String val = req.getParameter("mtls");
        if (val != null && !val.isBlank()) return val;
        String h = req.getHeader("X-Mtls-Profile");
        return h != null ? h : "default";
    }
}