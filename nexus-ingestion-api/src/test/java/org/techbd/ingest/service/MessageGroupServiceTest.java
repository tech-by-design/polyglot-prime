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

    @BeforeEach
    void setUp() {
        messageGroupService = new MessageGroupService();
    }

    private RequestContext buildContext(String tenantId,
                                        String sourceIp,
                                        String destinationIp,
                                        String destinationPort,
                                        String sourceType) {
        return new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                tenantId,
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
                sourceIp, // sourceIp
                destinationIp, // destinationIp
                destinationPort,null,null,sourceType
        );

    }

    @Test
    void testCreateMessageGroupId_withValidValues() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "8080", "INGEST");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    void testCreateMessageGroupId_withNullValues_returnsDefault() {
        var context = buildContext(null, null, "192.168.1.2", "8080", "INGEST");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withEmptyValues_returnsDefault() {
        var context = buildContext(null, "   ", "", " ", "INGEST");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withOneMissingField_returnsDefault() {
        var context = buildContext(null, "192.168.1.1", null, "8080", "INGEST");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withTenantIdPresent_usesTenantId() {
        var context = buildContext("tenantX", "192.168.1.1", "192.168.1.2", "8080", "INGEST");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("tenantX", groupId);
    }

    @Test
    void testCreateMessageGroupId_withSourceTypeMLLP_usesDestinationPort() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", "MLLP");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("9090", groupId);
    }

    @Test
    void testCreateMessageGroupId_withSourceTypeMLLP_missingPort_returnsDefault() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", null, "MLLP");
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }
}
