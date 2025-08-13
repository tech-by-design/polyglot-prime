package org.techbd.ingest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

class DataIngestionControllerTest {

    @Mock
    private MessageProcessorService messageProcessorService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private HttpServletRequest servletRequest;

    private ObjectMapper objectMapper;

    @InjectMocks
    private DataIngestionController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        controller = new DataIngestionController(messageProcessorService, objectMapper,appConfig);
        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/ingest"));
        when(servletRequest.getQueryString()).thenReturn("param=value");
        when(servletRequest.getProtocol()).thenReturn("HTTP/1.1");
        when(servletRequest.getLocalAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getRemoteAddr()).thenReturn("192.168.0.1");
        AppConfig.Aws awsMock = mock(AppConfig.Aws.class);
        when(awsMock.getRegion()).thenReturn("us-east-1");
        AppConfig.Aws.S3 s3Mock = mock(AppConfig.Aws.S3.class);
        when(appConfig.getAws()).thenReturn(awsMock);
        when(awsMock.getS3()).thenReturn(s3Mock);
        when(s3Mock.getBucket()).thenReturn("test-bucket"); 
    }

    static Stream<String> fileNames() {
        return Stream.of(
                "test.csv", "test.json", "test.hl7", "test.xml", "test.zip");
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void testIngestEndpointWithDifferentFiles(String fileName) throws Exception {
        InputStream inputStream = getClass().getResourceAsStream("/org/techbd/ingest/examples/" + fileName);
        assert inputStream != null : "Test file not found: " + fileName;
        byte[] fileBytes = inputStream.readAllBytes();
        MockMultipartFile mockFile = new MockMultipartFile("file", fileName, null, fileBytes);
        Map<String, String> mockHeaders = Map.of("X-Tenant-Id", "test-tenant");
        Map<String, String> mockResponse = Map.of("status", "SUCCESS", "fileName", fileName);
        when(messageProcessorService.processMessage( any(RequestContext.class),eq(mockFile)))
                .thenReturn(mockResponse);
        ResponseEntity<String> response = controller.ingest(mockFile, mockHeaders, servletRequest);
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS", fileName);
        verify(messageProcessorService).processMessage(any(RequestContext.class), eq(mockFile));
    }
}