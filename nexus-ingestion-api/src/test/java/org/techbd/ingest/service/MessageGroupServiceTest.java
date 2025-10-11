package org.techbd.ingest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
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
    private TemplateLogger templateLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(appLogger.getLogger(MessageGroupService.class)).thenReturn(templateLogger);

        // Inject strategies in the order of precedence
        List varStrategies = List.of(
                new MllpMessageGroupStrategy(),
                new TenantMessageGroupStrategy(),
                new IpPortMessageGroupStrategy()
        );
        messageGroupService = new MessageGroupService(varStrategies, appLogger);
    }

    private RequestContext buildContext(String tenantId,
                                        String sourceIp,
                                        String destinationIp,
                                        String destinationPort,
                                        MessageSourceType sourceType,
                                        Map<String, String> additionalParams) {
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
                "127.0.0.1",
                "192.168.1.1",
                sourceIp,
                destinationIp,
                destinationPort,
                null,
                null,
                null,
                sourceType,
                "TEST",
                "TEST",
                "0.700.0"
        ) {{
            setAdditionalParameters(additionalParams != null ? additionalParams : new HashMap<>());
        }};
    }

    @Test
    void testMllpWithAdditionalDetails_buildsCompositeGroupId() {
        Map<String, String> additional = Map.of(
                "deliveryType", "DEL",
                "facility", "FAC",
                "messageCode", "MSG"
        );
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, additional);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("DEL_FAC_MSG", groupId);
    }

    @Test
    void testMllpWithNoAdditionalDetails_usesPort() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("9090", groupId);
    }

    @Test
    void testMllpWithNoAdditionalAndNoPort_returnsDefault() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", null, MessageSourceType.MLLP, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testTenantStrategy_usedWhenNotMllpAndTenantPresent() {
        var context = buildContext("tenantX", "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("tenantX", groupId);
    }
    @Test
    void testIpPortFallback_buildsCompositeFromAvailableFields() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    void testIpPortFallback_withMissingFields_buildsPartialComposite() {
        var context = buildContext(null, "192.168.1.1", null, "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_8080", groupId);
    }

    @Test
    void testIpPortFallback_withAllBlank_returnsUnknownTenantId() {
        var context = buildContext(null, "   ", "   ", "   ", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("unknown-tenantId", groupId);
    }

    @Test
    void testIpPortFallback_withAllNull_returnsUnknownTenantId() {
        var context = buildContext(null, null, null, null, MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("unknown-tenantId", groupId);
    }

    @Test
    void testMllpWithPartialAdditionalDetails_usesAvailableFields() {
        Map<String, String> additional = Map.of(
                "deliveryType", "DEL",
                "facility", ""
        );
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, additional);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("DEL", groupId);
    }

    @Test
    void testTenantStrategy_withDefaultTenantId_fallbacksToIpPort() {
        var context = buildContext(Constants.DEFAULT_TENANT_ID, "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }
}
