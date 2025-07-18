package org.techbd.ingest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;

class MessageGroupServiceTest {

    private MessageGroupService messageGroupService;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        messageGroupService = new MessageGroupService();
    }

    @Test
    void testCreateMessageGroupId_withValidValues() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1", // localAddress
                "192.168.1.1", // remoteAddress
                "192.168.1.1", // sourceIp
                "192.168.1.2", // destinationIp
                "8080" // destinationPort
        );

        String groupId = messageGroupService.createMessageGroupId(context);
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    void testCreateMessageGroupId_withNullValues_returnsDefault() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                null, // sourceIp
                "192.168.1.2",
                "8080");

        String groupId = messageGroupService.createMessageGroupId(context);
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withEmptyValues_returnsDefault() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "   ", // sourceIp blank
                "", // destinationIp empty
                " " // destinationPort blank
        );

        String groupId = messageGroupService.createMessageGroupId(context);
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withOneMissingField_returnsDefault() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "192.168.1.1",
                null, // destinationIp is null
                "8080");

        String groupId = messageGroupService.createMessageGroupId(context);
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }
}