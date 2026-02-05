package org.techbd.ingest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Unit tests for InteractionsFilter focusing on URL encoding/decoding functionality.
 * Tests verify that client certificate headers are properly URL-decoded.
 */
@DisplayName("InteractionsFilter URL Encoding Tests")
class InteractionsFilterTest {

    @Mock
    private AppLogger appLogger;
    
    @Mock
    private TemplateLogger templateLogger;
    
    @Mock
    private PortConfig portConfig;
    
    @Mock
    private S3Client s3Client;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;

    @Mock
    private SoapFaultUtil soapFaultUtil;

    @Mock
    private PortResolverService portResolverService;

    private InteractionsFilter interactionsFilter;
    private MockedStatic<FeatureEnum> featureEnumMock;

    // Sample certificate content for testing
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
        
        // Mock FeatureEnum.isEnabled() to return false by default
        featureEnumMock = mockStatic(FeatureEnum.class);
        featureEnumMock.when(() -> FeatureEnum.isEnabled(any(FeatureEnum.class))).thenReturn(false);
        
        interactionsFilter = new InteractionsFilter(appLogger, portConfig, portResolverService, s3Client, soapFaultUtil);
    }

    @AfterEach
    void tearDown() {
        if (featureEnumMock != null) {
            featureEnumMock.close();
        }
    }

    @Nested
    @DisplayName("URL Decoding Tests")
    class UrlDecodingTests {

        @Test
        @DisplayName("Should decode properly URL-encoded certificate")
        void shouldDecodeUrlEncodedCertificate() throws Exception {
            // Given: A URL-encoded certificate header
            String encodedCert = URLEncoder.encode(SAMPLE_CERT_PEM, StandardCharsets.UTF_8);
            setupMockRequest("/test", encodedCert);
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Verify the certificate was processed (filter chain continues)
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle certificate with URL-encoded special characters")
        void shouldHandleCertWithUrlEncodedSpecialChars() throws Exception {
            // Given: Certificate with special characters that get URL-encoded
            String certWithSpecialChars = SAMPLE_CERT_PEM + "\nSpecial chars: +=/&?";
            String encodedCert = URLEncoder.encode(certWithSpecialChars, StandardCharsets.UTF_8);
            setupMockRequest("/test", encodedCert);
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should continue processing
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle malformed URL encoding gracefully")
        void shouldHandleMalformedUrlEncodingGracefully() throws Exception {
            // Given: Malformed URL-encoded content (incomplete percent encoding)
            String malformedEncoded = SAMPLE_CERT_PEM + "%ZZ%invalid";
            setupMockRequest("/test", malformedEncoded);
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should continue processing (treating as raw content)
            verify(filterChain).doFilter(request, response);
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
            // Given: Different URL-encoded content patterns
            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8);
            setupMockRequest("/test", encodedContent);
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should process all patterns successfully
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle empty certificate header")
        void shouldHandleEmptyCertificateHeader() throws Exception {
            // Given: Empty certificate header
            setupMockRequest("/test", "");
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should continue processing
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle null certificate header")
        void shouldHandleNullCertificateHeader() throws Exception {
            // Given: No certificate header provided
            setupMockRequest("/test", null);
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should continue processing
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should process complete mTLS flow with URL-encoded certificate")
        void shouldProcessCompleteMtlsFlowWithUrlEncodedCert() throws Exception {
            // Given: Complete setup with port config and URL-encoded certificate
            setupPortConfigMock(8443, "test-mtls");
            String encodedCert = URLEncoder.encode(SAMPLE_CERT_PEM, StandardCharsets.UTF_8);
            setupMockRequestWithPort("/api/test", encodedCert, 8443);
            
            // Mock environment variable
            System.setProperty("MTLS_BUCKET_NAME", "test-bucket");
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should have decoded the certificate and logged it
            // Verify the specific log message about URL decoding (with 2 arguments: message pattern and length)
            verify(templateLogger).info(
                "InteractionsFilter: decoded client cert header as URL-encoded, decoded length={}",
                443
            );
        }

        @Test
        @DisplayName("Should handle request without mTLS requirements")
        void shouldHandleRequestWithoutMtlsRequirements() throws Exception {
            // Given: Port config without mTLS requirements
            setupPortConfigMock(8080, null);
            setupMockRequestWithPort("/api/test", null, 8080);
            
            // When: Filter processes the request
            interactionsFilter.doFilterInternal(request, response, filterChain);
            
            // Then: Should proceed without mTLS processing
            verify(filterChain).doFilter(request, response);
            verify(templateLogger).info(
                "InteractionsFilter: mtls NOT configured for port {} - proceeding without mTLS",
                8080
            );
        }
    }

    // Helper methods for setting up mocks

    private void setupMockRequest(String uri, String certHeader) throws IOException, ServletException {
        setupMockRequestWithPort(uri, certHeader, 8443);
    }

    private void setupMockRequestWithPort(String uri, String certHeader, int port) throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServerPort()).thenReturn(port);
        when(request.getHeaderNames()).thenReturn(java.util.Collections.enumeration(
            java.util.Arrays.asList("X-Amzn-Mtls-Clientcert")));
        when(request.getHeaders("X-Amzn-Mtls-Clientcert")).thenReturn(
            java.util.Collections.enumeration(java.util.Arrays.asList("test-header")));
        when(request.getHeader("X-Amzn-Mtls-Clientcert")).thenReturn(certHeader);
        
        // Mock other headers that might be checked
        when(request.getHeader("X-Forwarded-Port")).thenReturn(String.valueOf(port));
        
        // Mock port resolver to return a PortEntry
        PortConfig.PortEntry portEntry = new PortConfig.PortEntry();
        portEntry.port = port;
        portEntry.mtls = null; // No mTLS by default
        when(portResolverService.resolve(any())).thenReturn(java.util.Optional.of(portEntry));
    }

    private void setupPortConfigMock(int port, String mtlsName) {
        PortConfig.PortEntry portEntry = new PortConfig.PortEntry();
        portEntry.port = port;
        portEntry.mtls = mtlsName;
        
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.Arrays.asList(portEntry));
        when(portResolverService.resolve(any())).thenReturn(java.util.Optional.of(portEntry));
    }

    @Test
    @DisplayName("Should skip processing for health check requests")
    void shouldSkipProcessingForHealthCheckRequests() throws Exception {
        // Given: Health check request
        when(request.getRequestURI()).thenReturn("/actuator/health/liveness");
        
        // When: Filter processes the request
        interactionsFilter.doFilterInternal(request, response, filterChain);
        
        // Then: Should skip all processing and continue chain
        verify(filterChain).doFilter(request, response);
        verify(templateLogger, never()).info(contains("InteractionsFilter: start"));
    }

    @Test
    @DisplayName("Should skip processing for root path requests")
    void shouldSkipProcessingForRootPathRequests() throws Exception {
        // Given: Root path request
        when(request.getRequestURI()).thenReturn("/");
        
        // When: Filter processes the request
        interactionsFilter.doFilterInternal(request, response, filterChain);
        
        // Then: Should skip all processing and continue chain
        verify(filterChain).doFilter(request, response);
        verify(templateLogger, never()).info(contains("InteractionsFilter: start"));
    }
}