package org.techbd.nexusingestionapi.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.techbd.nexusingestionapi.service.AwsService;

import software.amazon.awssdk.services.sqs.SqsClient;

public class DataIngestionControllerTest {

    private AwsService s3Service;
    private SqsClient sqsClient;
    private ObjectMapper objectMapper;
    private DataIngestionController controller;

    @BeforeEach
    void setup() {
        s3Service = mock(AwsService.class);
        sqsClient = mock(SqsClient.class);
        objectMapper = new ObjectMapper();
        controller = new ApiController(s3Service, sqsClient, objectMapper);
    }

    @Test
    void testBuildS3Metadata() {
        Map<String, String> headers = Map.of(
            "User-Agent", "JUnit-Test-Agent",
            "X-Tenant-Id", "testTenant"
        );

        ApiController.RequestContext context = new ApiController.RequestContext(
            headers,
            "/Bundle",
            "testTenant",
            "12345",
            //"fhir",
            ZonedDateTime.now(),
            "1716899999999",
            "testFile.json",
            1024L,
            "fhir/2025/05/28-12345.json",
            "fhir/2025/05/28-12345_metadata.json",
            "s3://test-bucket/fhir/2025/05/28-12345.json",
            "JUnit-Test-Agent",
            "http://localhost:8080/Bundle",
            "param=value",
            "HTTP/1.1",
            "127.0.0.1",
            "192.168.1.1"
        );

        Map<String, String> metadata = controller.buildS3Metadata(context);

        assertEquals("12345", metadata.get("interactionId"));
        assertEquals("testTenant", metadata.get("tenantId"));
        assertEquals("testFile.json", metadata.get("fileName"));
        assertEquals("1024", metadata.get("FileSize"));
        assertEquals("s3://test-bucket/fhir/2025/05/28-12345.json", metadata.get("s3ObjectPath"));
        assertNotNull(metadata.get("UploadTime"));
        assertEquals("JUnit-Test-Agent", metadata.get("UploadedBy"));
    }

    @Test
    void testBuildMetadataJson() {
        Map<String, String> headers = Map.of(
            "User-Agent", "JUnit-Test-Agent",
            "X-Tenant-Id", "testTenant",
            "Content-Type", "application/json"
        );

        ApiController.RequestContext context = new ApiController.RequestContext(
            headers,
            "/Bundle",
            "testTenant",
            "abcde-12345",
          //  "fhir",
            ZonedDateTime.parse("2025-05-28T12:00:00Z"),
            "1716899999999",
            "example.json",
            2048L,
            "fhir/2025/05/28-abcde-12345.json",
            "fhir/2025/05/28-abcde-12345_metadata.json",
            "s3://test-bucket/fhir/2025/05/28-abcde-12345.json",
            "JUnit-Test-Agent",
            "http://localhost:8080/Bundle",
            "q=123",
            "HTTP/1.1",
            "10.0.0.1",
            "172.16.1.1"
        );

        Map<String, Object> metadataJson = controller.buildMetadataJson(context);

        assertEquals("testTenant", metadataJson.get("tenantId"));
        assertEquals("abcde-12345", metadataJson.get("interactionId"));
     //   assertEquals("fhir", metadataJson.get("msgType"));
        assertEquals("example.json", metadataJson.get("fileName"));
        assertEquals("2048", metadataJson.get("fileSize"));
        assertEquals("s3://test-bucket/fhir/2025/05/28-abcde-12345.json", metadataJson.get("s3ObjectPath"));
        assertEquals("http://localhost:8080/Bundle", metadataJson.get("fullRequestUrl"));
        assertEquals("q=123", metadataJson.get("queryParams"));
        assertEquals("HTTP/1.1", metadataJson.get("protocol"));
        assertEquals("10.0.0.1", metadataJson.get("localAddress"));
        assertEquals("172.16.1.1", metadataJson.get("remoteAddress"));
        assertEquals("Mirth Connect", metadataJson.get("sourceSystem"));

        // Check headers list
        List<?> headerList = (List<?>) metadataJson.get("headers");
        assertNotNull(headerList);
        assertEquals(3, headerList.size());
    }
}
