package org.techbd.ingest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

class DataHoldControllerTest {

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

    private DataHoldController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        when(appLogger.getLogger(DataHoldController.class)).thenReturn(templateLogger);
        when(appConfig.getVersion()).thenReturn("1.0.0");
        controller = new DataHoldController(messageProcessorService, objectMapper, appConfig, appLogger, portConfig);
        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/hold"));

        AppConfig.Aws awsMock = mock(AppConfig.Aws.class);
        when(awsMock.getRegion()).thenReturn("us-east-1");
        AppConfig.Aws.S3 s3Mock = mock(AppConfig.Aws.S3.class);
        AppConfig.Aws.S3.BucketConfig holdConfigMock = mock(AppConfig.Aws.S3.BucketConfig.class);
        when(s3Mock.getHoldConfig()).thenReturn(holdConfigMock);
        when(holdConfigMock.getBucket()).thenReturn("hold-bucket");
        when(holdConfigMock.getMetadataBucket()).thenReturn("hold-metadata-bucket");
        when(awsMock.getS3()).thenReturn(s3Mock);
        when(appConfig.getAws()).thenReturn(awsMock);

        // Default port config: loaded, but empty list
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(Collections.emptyList());
    }

    @Test
    void testGetDataBucketName_noRequestAttributes_returnsDefault() {
        RequestContextHolder.resetRequestAttributes();
        when(portConfig.isLoaded()).thenReturn(false);
        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("hold-bucket");
    }

    @Test
    void testGetDataBucketName_withDataDir_appendsDataDir() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader("x-server-port")).thenReturn("6060");
        when(servletRequest.getHeader("X-Server-Port")).thenReturn(null);

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = 6060;
        entry.dataDir = "/hold/per/port/dir/";
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.List.of(entry));

        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("hold-bucket/hold/per/port/dir");
    }

    @Test
    void testGetDataBucketName_withNoDataDir_returnsDefault() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader("x-server-port")).thenReturn("5050");
        when(servletRequest.getHeader("X-Server-Port")).thenReturn(null);

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = 5050;
        entry.dataDir = null;
        when(portConfig.isLoaded()).thenReturn(true);
        when(portConfig.getPortConfigurationList()).thenReturn(java.util.List.of(entry));

        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("hold-bucket");
    }

    @Test
    void testGetDataBucketName_withInvalidPortHeader_returnsDefault() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(servletRequest.getHeader("x-server-port")).thenReturn("nope");
        when(servletRequest.getHeader("X-Server-Port")).thenReturn(null);
        when(portConfig.isLoaded()).thenReturn(true);

        String bucket = controller.getDataBucketName();
        assertThat(bucket).isEqualTo("hold-bucket");
    }
}
