package org.techbd.ingest.controller;

import org.techbd.ingest.config.PortConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.SoapForwarderService;
import org.techbd.ingest.service.portconfig.PortResolverService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapFaultUtil;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class DataIngestionControllerTest {

    @Mock
    private MessageProcessorService messageProcessorService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private PortConfig portConfig;

    @Mock private PortResolverService portResolverService;
    @Mock private SoapForwarderService forwarder;
    @Mock private SoapFaultUtil soapFaultUtil;

    private ObjectMapper objectMapper;
    @Mock
    private static AppLogger appLogger;

    @Mock
    private static TemplateLogger templateLogger;

    private DataIngestionController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        when(appLogger.getLogger(DataIngestionController.class)).thenReturn(templateLogger);
        when(appConfig.getVersion()).thenReturn("1.0.0");
        controller = new DataIngestionController(
        messageProcessorService,
        objectMapper,
        appConfig,
        appLogger,
        portResolverService,   
        forwarder,             
        soapFaultUtil           
);
        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/ingest"));
        when(servletRequest.getQueryString()).thenReturn("param=value");
        when(servletRequest.getProtocol()).thenReturn("HTTP/1.1");
        when(servletRequest.getLocalAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getRemoteAddr()).thenReturn("192.168.0.1");
        AppConfig.Aws awsMock = mock(AppConfig.Aws.class);
        when(awsMock.getRegion()).thenReturn("us-east-1");
        AppConfig.Aws.S3 s3Mock = mock(AppConfig.Aws.S3.class);
        AppConfig.Aws.S3.BucketConfig defaultConfigMock = mock(AppConfig.Aws.S3.BucketConfig.class);
        when(s3Mock.getDefaultConfig()).thenReturn(defaultConfigMock);
        when(defaultConfigMock.getBucket()).thenReturn("test-bucket");
        when(defaultConfigMock.getMetadataBucket()).thenReturn("test-metadata-bucket");
        when(appConfig.getAws()).thenReturn(awsMock);
        when(awsMock.getS3()).thenReturn(s3Mock);
        when(s3Mock.getDefaultConfig().getBucket()).thenReturn("test-bucket");
        when(s3Mock.getDefaultConfig().getMetadataBucket()).thenReturn("test-metadata-bucket");
        // Default port config: loaded, but empty list (no /hold routing)
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(Collections.emptyList());
    }

    @Test
    void testGetDataBucketName_noRequestAttributes_returnsDefault() {
        // Ensure no request attributes are set
        RequestContextHolder.resetRequestAttributes();
        when(portConfig.isLoaded()).thenReturn(false);
        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("test-bucket");
    }



    @Test
    void testGetDataBucketName_withNoDataDir_returnsDefault() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn("7070");
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn(null);

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = 7070;
        entry.dataDir = null;
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.List.of(entry));

        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("test-bucket");
    }

    @Test
    void testGetDataBucketName_withInvalidPortHeader_returnsDefault() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn("notanumber");
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn(null);
        when(portConfig.isLoaded()).thenReturn(true);

        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("test-bucket");
    }

    @Test
    void testGetMetadataBucketName_noRequestAttributes_returnsDefault() {
        // Ensure no request attributes are set
        RequestContextHolder.resetRequestAttributes();
        when(portConfig.isLoaded()).thenReturn(false);
        String bucket = controller.getMetadataBucketName();
        assertThat(bucket).isEqualTo("test-metadata-bucket");
    }


    @Test
    void testGetMetadataBucketName_withNoMetadataDir_returnsDefault() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn("7070");
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn(null);

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = 7070;
        entry.metadataDir = null;
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.List.of(entry));

        String bucket = controller.getMetadataBucketName();
        assertThat(bucket).isEqualTo("test-metadata-bucket");
    }

    @Test
    void testGetMetadataBucketName_withInvalidPortHeader_returnsDefault() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn("notanumber");
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn(null);
        when(portConfig.isLoaded()).thenReturn(true);

        String bucket = controller.getMetadataBucketName();
        assertThat(bucket).isEqualTo("test-metadata-bucket");
    }

    @SuppressWarnings("deprecation")
    @Test
    void testIngest_withRawBody_shouldReturnOk() throws Exception {

        String rawData = "test";

        Map<String, String> mockHeaders = Map.of(
                "X-Tenant-Id", "test-tenant"
        );

        when(servletRequest.getContentType()).thenReturn("application/xml");
        when(servletRequest.getAttribute(Constants.INTERACTION_ID)).thenReturn("test-id");
        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost"));

        Map<String, String> result = Map.of("status", "ok");

        when(messageProcessorService.processMessage(any(RequestContext.class), anyString()))
                .thenReturn(result);

        ResponseEntity<String> response = controller.ingest(
                null,
                "json",
                null,
                rawData,
                mockHeaders,
                servletRequest,
                mock(HttpServletResponse.class)
        );

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("status");

        verify(messageProcessorService)
                .processMessage(any(RequestContext.class), eq(rawData));
    }
}
