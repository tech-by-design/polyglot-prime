package org.techbd.ingest.service;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.model.RequestContext;

class MetadataBuilderServiceTest {

    private final MetadataBuilderService metadataBuilderService = new MetadataBuilderService();

    private RequestContext createContext(Map<String, String> headers,String s3Response) {
        RequestContext context = new RequestContext(
                headers,
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.parse("2025-07-17T10:15:30+05:30"),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "http://localhost/upload?test=value",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                null,
                "192.168.1.2",
                "8080",
                null,
                null,
                null,
                MessageSourceType.HTTP_INGEST,
                "TEST",
                "TEST"
        );
        context.setS3Response(s3Response);
        return context;
    }

    @Test
    void testBuildS3Metadata_shouldReturnExpectedMetadata() {
        RequestContext context = createContext(Map.of("User-Agent", "JUnit"), null);

        Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);

        assertThat(metadata)
                .containsEntry("interactionId", "interaction123")
                .containsEntry("tenantId", "tenant1")
                .containsEntry("fileName", "file.txt")
                .containsEntry("FileSize", "123")
                .containsEntry("s3DataObjectPath", "s3://bucket/file.txt")
                .containsEntry("UploadedBy", "JUnit-Agent");

        assertThat(metadata).containsKey("UploadTime");
    }

    @Test
    void testBuildMetadataJson_shouldContainKeyAndJsonMetadata() {
        RequestContext context = createContext(Map.of("User-Agent", "JUnit", "X-Header", "Test"), null);

        Map<String, Object> wrapper = metadataBuilderService.buildMetadataJson(context);

        assertThat(wrapper)
                .containsKey("key")
                .containsKey("json_metadata");

        assertThat(wrapper.get("key")).isEqualTo("objectKey");

        Map<String, Object> json = (Map<String, Object>) wrapper.get("json_metadata");

        assertThat(json)
                .containsEntry("tenantId", "tenant1")
                .containsEntry("interactionId", "interaction123")
                .containsEntry("fileName", "file.txt")
                .containsEntry("fileSize", "123")
                .containsEntry("sourceSystem", "JUnit-Agent")
                .containsEntry("s3DataObjectPath", "s3://bucket/file.txt")
                .containsEntry("requestUrl", "/upload")
                .containsEntry("fullRequestUrl", "http://localhost/upload")
                .containsEntry("protocol", "HTTP/1.1")
                .containsEntry("localAddress", "127.0.0.1")
                .containsEntry("remoteAddress", "192.168.1.1");

        assertThat(json).containsKey("uploadDate");
        assertThat(json).containsKey("timestamp");

        List<Map<String, String>> headers = (List<Map<String, String>>) json.get("headers");
        assertThat(headers)
                .anyMatch(h -> h.containsKey("User-Agent") && h.containsValue("JUnit"))
                .anyMatch(h -> h.containsKey("X-Header") && h.containsValue("Test"));
    }

    @Test
    void testBuildSqsMessage_withoutS3Response_shouldNotIncludeS3Response() {
        RequestContext context = createContext(Map.of("User-Agent", "JUnit"), null);

        Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);

        assertThat(message)
                .containsEntry("tenantId", "tenant1")
                .containsEntry("interactionId", "interaction123")
                .containsEntry("fileName", "file.txt")
                .containsEntry("fileSize", 123L)
                .containsEntry("s3ObjectId", "objectKey")
                .containsEntry("s3DataObjectPath", "s3://bucket/file.txt")
                .containsEntry("requestUrl", "/upload")
                .containsEntry("timestamp", "1716899999999");

        assertThat(message).doesNotContainKey("s3Response");
    }

    @Test
    void testBuildSqsMessage_withS3Response_shouldIncludeS3Response() {
        RequestContext context = createContext(Map.of("User-Agent", "JUnit"), "S3 upload success");

        Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);

        assertThat(message)
                .containsEntry("s3Response", "S3 upload success")
                .containsEntry("tenantId", "tenant1")
                .containsEntry("fileName", "file.txt")
                .containsEntry("fileSize", 123L);
    }
}
