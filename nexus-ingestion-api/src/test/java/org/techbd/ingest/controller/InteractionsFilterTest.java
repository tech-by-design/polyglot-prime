package org.techbd.ingest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.service.portconfig.PortResolverService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapFaultUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.services.s3.S3Client;

@DisplayName("InteractionsFilter URL Encoding Tests")
class InteractionsFilterTest {

    @Mock private AppLogger appLogger;
    @Mock private TemplateLogger templateLogger;
    @Mock private PortConfig portConfig;
    @Mock private S3Client s3Client;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private SoapFaultUtil soapFaultUtil;
    @Mock private PortResolverService portResolverService;

    private InteractionsFilter interactionsFilter;
    private MockedStatic<FeatureEnum> featureEnumMock;

    private static final String SAMPLE_CERT_PEM =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDXTCCAkWgAwIBAgIJAOC2W3W7o0o9MA0GCSqGSIb3DQEBBQUAMEUxCzAJBgNV\n" +
        "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
        "aWRnaXRzIFB0eSBMdGQwHhcNMTQwNzI0MTY1NzU5WhcNMTUwNzI0MTY1NzU5WjBF\n" +
        "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
        "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
        "CgKCAQEAvpnVNLxo5iI9RdkwZJCgWW2EqO6J1mP7Nw8rC5sFTFPu7wqE8KLmRHRN\n" +
        "-----END CERTIFICATE-----";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(appLogger.getLogger(any(Class.class))).thenReturn(templateLogger);
        featureEnumMock = mockStatic(FeatureEnum.class);
        featureEnumMock.when(() -> FeatureEnum.isEnabled(any(FeatureEnum.class))).thenReturn(false);
        interactionsFilter = new InteractionsFilter(
                appLogger, portConfig, portResolverService, s3Client, soapFaultUtil);
    }

    @AfterEach
    void tearDown() {
        if (featureEnumMock != null) featureEnumMock.close();
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    /**
     * Creates a ServletInputStream backed by an empty byte array.
     * CachedBodyHttpServletRequest calls getInputStream().readAllBytes() once
     * during construction — this prevents the NullPointerException.
     */
    private ServletInputStream emptyServletInputStream() {
        return servletInputStreamOf(new byte[0]);
    }

    private ServletInputStream servletInputStreamOf(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady()    { return true; }
            @Override public void setReadListener(ReadListener rl) {}
        };
    }

    // ── Mock setup helpers ────────────────────────────────────────────────────

    private void setupMockRequest(String uri, String certHeader)
            throws IOException, ServletException {
        setupMockRequestWithPort(uri, certHeader, 8443);
    }

    private void setupMockRequestWithPort(String uri, String certHeader, int port)
            throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServerPort()).thenReturn(port);
        when(request.getHeader("X-Forwarded-Port")).thenReturn(String.valueOf(port));
        when(request.getHeaderNames()).thenReturn(java.util.Collections.enumeration(
                java.util.Arrays.asList("X-Amzn-Mtls-Clientcert")));
        when(request.getHeaders("X-Amzn-Mtls-Clientcert")).thenReturn(
                java.util.Collections.enumeration(
                        java.util.Arrays.asList("test-header")));
        when(request.getHeader("X-Amzn-Mtls-Clientcert")).thenReturn(certHeader);

        // ── KEY FIX: stub getInputStream() so CachedBodyHttpServletRequest
        //    can call readAllBytes() without a NullPointerException ──────────
        when(request.getInputStream()).thenReturn(emptyServletInputStream());

        PortConfig.PortEntry portEntry = new PortConfig.PortEntry();
        portEntry.port = port;
        portEntry.mtls = null;
        when(portResolverService.resolve(any(), any()))
                .thenReturn(java.util.Optional.of(portEntry));
    }

    private void setupPortConfigMock(int port, String mtlsName) {
        PortConfig.PortEntry portEntry = new PortConfig.PortEntry();
        portEntry.port = port;
        portEntry.mtls = mtlsName;
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList())
                .thenReturn(java.util.Arrays.asList(portEntry));
        when(portResolverService.resolve(any(), any()))
                .thenReturn(java.util.Optional.of(portEntry));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("URL Decoding Tests")
    class UrlDecodingTests {

        @Test
        @DisplayName("Should decode properly URL-encoded certificate")
        void shouldDecodeUrlEncodedCertificate() throws Exception {
            String encodedCert = URLEncoder.encode(SAMPLE_CERT_PEM, StandardCharsets.UTF_8);
            setupMockRequest("/test", encodedCert);
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle certificate with URL-encoded special characters")
        void shouldHandleCertWithUrlEncodedSpecialChars() throws Exception {
            String certWithSpecialChars = SAMPLE_CERT_PEM + "\nSpecial chars: +=/&?";
            String encodedCert = URLEncoder.encode(certWithSpecialChars, StandardCharsets.UTF_8);
            setupMockRequest("/test", encodedCert);
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle malformed URL encoding gracefully")
        void shouldHandleMalformedUrlEncodingGracefully() throws Exception {
            String malformedEncoded = SAMPLE_CERT_PEM + "%ZZ%invalid";
            setupMockRequest("/test", malformedEncoded);
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Simple certificate content",
            "Certificate with spaces and + signs",
            "Certificate%20with%20encoded%20spaces",
            "Mixed%2Bcontent%20with%2Fslashes"
        })
        @DisplayName("Should handle various URL-encoded patterns")
        void shouldHandleVariousUrlEncodedPatterns(String content) throws Exception {
            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8);
            setupMockRequest("/test", encodedContent);
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle empty certificate header")
        void shouldHandleEmptyCertificateHeader() throws Exception {
            setupMockRequest("/test", "");
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle null certificate header")
        void shouldHandleNullCertificateHeader() throws Exception {
            setupMockRequest("/test", null);
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should process complete mTLS flow with URL-encoded certificate")
        void shouldProcessCompleteMtlsFlowWithUrlEncodedCert() throws Exception {
            setupPortConfigMock(8443, "test-mtls");
            String encodedCert = URLEncoder.encode(SAMPLE_CERT_PEM, StandardCharsets.UTF_8);
            setupMockRequestWithPort("/api/test", encodedCert, 8443);
            System.setProperty("MTLS_BUCKET_NAME", "test-bucket");
            interactionsFilter.doFilterInternal(request, response, filterChain);

            // Match actual log signature: message + decoded length + interactionId
            // All arguments must use matchers when ANY argument uses a matcher
            verify(templateLogger).info(
                    ArgumentMatchers.eq("InteractionsFilter: decoded client cert header as URL-encoded, decoded length={}  interactionId: {}"),
                    ArgumentMatchers.eq(443),
                    ArgumentMatchers.anyString()
            );
        }

        @Test
        @DisplayName("Should handle request without mTLS requirements")
        void shouldHandleRequestWithoutMtlsRequirements() throws Exception {
            setupPortConfigMock(8080, null);
            setupMockRequestWithPort("/api/test", null, 8080);
            interactionsFilter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(any(), any());

            // Match actual log signature: message + port + interactionId
            // All arguments must use matchers when ANY argument uses a matcher
            verify(templateLogger).info(
                    ArgumentMatchers.eq("InteractionsFilter: mtls NOT configured for port {} - proceeding without mTLS  interactionId: {}"),
                    ArgumentMatchers.eq(8080),
                    ArgumentMatchers.anyString()
            );
        }
    }

    @Test
    @DisplayName("Should skip processing for health check requests")
    void shouldSkipProcessingForHealthCheckRequests() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health/liveness");
        // ── FIX: stub getInputStream() even for health-check path ────────────
        when(request.getInputStream()).thenReturn(emptyServletInputStream());
        interactionsFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(any(), any());
        verify(templateLogger, never()).info(contains("InteractionsFilter: start"));
    }

    @Test
    @DisplayName("Should skip processing for root path requests")
    void shouldSkipProcessingForRootPathRequests() throws Exception {
        when(request.getRequestURI()).thenReturn("/");
        // ── FIX: stub getInputStream() even for root path ─────────────────────
        when(request.getInputStream()).thenReturn(emptyServletInputStream());
        interactionsFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(any(), any());
        verify(templateLogger, never()).info(contains("InteractionsFilter: start"));
    }
}