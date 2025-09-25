package org.techbd.ingest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

class MessageGroupServiceTest {

    private MessageGroupService messageGroupService;
    @Mock
    private AppLogger appLogger;

    @Mock
    private static TemplateLogger templateLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);        
        when(appLogger.getLogger(MessageGroupService.class)).thenReturn(templateLogger);
        messageGroupService = new MessageGroupService(appLogger);
    }

    private RequestContext buildContext(String tenantId,
                                        String sourceIp,
                                        String destinationIp,
                                        String destinationPort,
                                        MessageSourceType sourceType) {
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
                destinationPort, null, null, null,
                sourceType, "TEST", "TEST","0.700.0"
        );
    }

    @Test
    void testCreateMessageGroupId_withSourceTypeMLLP_andPort_usesPort() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("9090", groupId);
    }

    @Test
    void testCreateMessageGroupId_withSourceTypeMLLP_missingPort_returnsDefault() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", null, MessageSourceType.MLLP);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withTenantIdPresent_nonMLLP_usesTenantId() {
        var context = buildContext("tenantX", "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("tenantX", groupId);
    }

    @Test
    void testCreateMessageGroupId_withAllFieldsPresent_buildsComposite() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    void testCreateMessageGroupId_withOneMissingField_stillBuildsComposite() {
        var context = buildContext(null, "192.168.1.1", null, "8080", MessageSourceType.HTTP_INGEST);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        // Should still return composite from available fields
        assertEquals("192.168.1.1_8080", groupId);
    }

    @Test
    void testCreateMessageGroupId_withAllBlank_returnsDefault() {
        var context = buildContext(null, "   ", "   ", "   ", MessageSourceType.HTTP_INGEST);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("unknown-tenantId", groupId);
    }

    @Test
    void testCreateMessageGroupId_withAllNull_returnsDefault() {
        var context = buildContext(null, null, null, null, MessageSourceType.HTTP_INGEST);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("unknown-tenantId", groupId);
    }
}

