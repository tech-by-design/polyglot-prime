package org.techbd.ingest.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3UploadStepTest {

    @Mock
    private MetadataBuilderService metadataBuilderService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private S3Client s3Client;

    @Mock
    private AppConfig appConfig;

    @Mock
    private AppConfig.Aws aws;

    @Mock
    private AppConfig.Aws.S3 s3;

    @InjectMocks
    private S3UploadStep s3UploadStep;

    private RequestContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(appConfig.getAws()).thenReturn(aws);
        when(aws.getS3()).thenReturn(s3);
        when(s3.getBucket()).thenReturn("test-bucket");

        context = new RequestContext(
                Map.of("User-Agent", "JUnit-Test"),
                "/test",
                "tenant123",
                "interaction123",
                ZonedDateTime.now(),
                "uploadTime",
                "testFile.txt",
                100L,
                "objectKey",
                "metadataKey",
                "s3://test-bucket/objectKey",
                "JUnit-Test",
                "http://localhost/test",
                "q=1",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "192.168.1.1",
                "192.168.1.2",
                "8080"
        );

        s3UploadStep = new S3UploadStep(metadataBuilderService, objectMapper, appConfig, s3Client);
    }

    @Test
    void testProcessWithMultipartFile() throws Exception {
        Map<String, String> metadata = Map.of("metaKey", "metaValue");
        Map<String, Object> metadataJson = Map.of("metaJsonKey", "metaJsonValue");
        when(metadataBuilderService.buildS3Metadata(any())).thenReturn(metadata);
        when(metadataBuilderService.buildMetadataJson(any())).thenReturn(metadataJson);
        when(objectMapper.writeValueAsString(metadataJson)).thenReturn("{\"metaJsonKey\":\"metaJsonValue\"}");
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        PutObjectResponse response = PutObjectResponse.builder().eTag("123etag").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);
        s3UploadStep.process(context, file);
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertEquals("Uploaded to S3: objectKey (ETag: 123etag)", context.getS3Response());
    }

    @Test
    void testProcessWithStringContent() throws Exception {
        String content = "{\"key\":\"value\"}";
        Map<String, String> metadata = Map.of("metaKey", "metaValue");
        Map<String, Object> metadataJson = Map.of("jsonKey", "jsonValue");
        when(metadataBuilderService.buildS3Metadata(any())).thenReturn(metadata);
        when(metadataBuilderService.buildMetadataJson(any())).thenReturn(metadataJson);
        when(objectMapper.writeValueAsString(metadataJson)).thenReturn("{\"jsonKey\":\"jsonValue\"}");
        s3UploadStep.process(context, content);
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}