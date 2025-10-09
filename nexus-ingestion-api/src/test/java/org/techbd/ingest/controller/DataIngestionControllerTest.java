package org.techbd.ingest.controller;

import org.techbd.ingest.config.PortConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

class DataIngestionControllerTest {

    @Mock
    private MessageProcessorService messageProcessorService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private PortConfig portConfig;

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
        controller = new DataIngestionController(messageProcessorService, objectMapper, appConfig, appLogger, portConfig);
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
    void testGetDataBucketName_withDataDir_appendsDataDir() {
        // Prepare request attributes with header
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn("9090");
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn(null);

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = 9090;
        entry.dataDir = "/per/port/dir/";
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.List.of(entry));

        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("test-bucket/per/port/dir");
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
    void testGetMetadataBucketName_withMetadataDir_appendsMetadataDir() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn("9090");
        when(servletRequest.getHeader(Constants.REQ_X_FORWARDED_PORT)).thenReturn(null);

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = 9090;
        entry.metadataDir = "/per/port/meta/";
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.List.of(entry));

        String bucket = controller.getMetadataBucketName();
        assertThat(bucket).isEqualTo("test-metadata-bucket/per/port/meta");
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

    static Stream<String> fileNames() {
        return Stream.of(
                "test.csv", "test.json", "test.hl7", "test.xml");
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void testIngestEndpointWithDifferentFiles(String fileName) throws Exception {
        InputStream inputStream = getClass().getResourceAsStream("/org/techbd/ingest/examples/" + fileName);
        if (inputStream == null) {
            throw new AssertionError("Test file not found: " + fileName);
        }
        byte[] fileBytes = inputStream.readAllBytes();
        MockMultipartFile mockFile = new MockMultipartFile("file", fileName, null, fileBytes);
        Map<String, String> mockHeaders = Map.of(
                "X-Tenant-Id", "test-tenant",
                "x-server-port", "8080"
        );
        Map<String, String> mockResponse = Map.of("status", "SUCCESS", "fileName", fileName);

        when(messageProcessorService.processMessage(any(RequestContext.class), eq(mockFile)))
                .thenReturn(mockResponse);

        ResponseEntity<String> response = controller.ingest(mockFile, null, mockHeaders, servletRequest);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS", fileName);
        verify(messageProcessorService).processMessage(any(RequestContext.class), eq(mockFile));
    }

    @Test
    void testIngestRawData_shouldStoreAsDat() throws Exception {
        String rawData = "some raw test data";
        Map<String, String> mockHeaders = Map.of(
                "X-Tenant-Id", "test-tenant",
                "x-server-port", "8080"
        );

        Map<String, String> mockResponse = Map.of(
                "status", "SUCCESS",
                "fileName", "generatedFile.dat",
                "messageId", "12345");

        when(messageProcessorService.processMessage(any(RequestContext.class), eq(rawData)))
                .thenReturn(mockResponse);

        ResponseEntity<String> response = controller.ingest(null, rawData, mockHeaders, servletRequest);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS");
        assertThat(response.getBody()).contains(".dat");
        assertThat(response.getBody()).contains("messageId");

        verify(messageProcessorService).processMessage(any(RequestContext.class), eq(rawData));
    }

    @Test
    void testIngestRawData_shouldStoreAsXml() throws Exception {
        String rawData = "<note><to>User</to><from>Tester</from></note>";
        Map<String, String> mockHeaders = Map.of(
                "X-Tenant-Id", "test-tenant",
                "Content-Type", "application/xml",
                "x-server-port", "8080"
        );

        Map<String, String> mockResponse = Map.of(
                "status", "SUCCESS",
                "fileName", "generatedFile.xml",
                "messageId", "67890");

        when(messageProcessorService.processMessage(any(RequestContext.class), eq(rawData)))
                .thenReturn(mockResponse);

        ResponseEntity<String> response = controller.ingest(null, rawData, mockHeaders, servletRequest);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS");
        assertThat(response.getBody()).contains(".xml");
        assertThat(response.getBody()).contains("messageId");

        verify(messageProcessorService).processMessage(any(RequestContext.class), eq(rawData));
    }

    @Test
    void testIngestRawData_shouldStoreAsJson() throws Exception {
        String rawData = "{ \"name\": \"tester\", \"type\": \"json\" }";
        Map<String, String> mockHeaders = Map.of(
                "X-Tenant-Id", "test-tenant",
                "Content-Type", "application/json",
                "x-server-port", "8080"
        );

        Map<String, String> mockResponse = Map.of(
                "status", "SUCCESS",
                "fileName", "generatedFile.json",
                "messageId", "99999");

        when(messageProcessorService.processMessage(any(RequestContext.class), eq(rawData)))
                .thenReturn(mockResponse);

        ResponseEntity<String> response = controller.ingest(null, rawData, mockHeaders, servletRequest);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS");
        assertThat(response.getBody()).contains(".json");
        assertThat(response.getBody()).contains("messageId");

        verify(messageProcessorService).processMessage(any(RequestContext.class), eq(rawData));
    }

    @Test
    void testIngest_withMissingPortHeader_shouldReturnBadRequest() throws Exception {
        String rawData = "test";
        Map<String, String> mockHeaders = Map.of("X-Tenant-Id", "test-tenant");
        ResponseEntity<String> response = controller.ingest(null, rawData, mockHeaders, servletRequest);
        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).contains("x-forwarded-port");
    }

    @Test
    void testIngest_withInvalidPortHeader_shouldReturnBadRequest() throws Exception {
        String rawData = "test";
        Map<String, String> mockHeaders = Map.of(
                "X-Tenant-Id", "test-tenant",
                Constants.REQ_X_FORWARDED_PORT, "notaport"
        );
        ResponseEntity<String> response = controller.ingest(null, rawData, mockHeaders, servletRequest);
        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).contains("x-forwarded-port");
    }
}
