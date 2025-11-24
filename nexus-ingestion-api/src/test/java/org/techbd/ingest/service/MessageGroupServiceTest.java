package org.techbd.ingest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        when(appLogger.getLogger(MllpMessageGroupStrategy.class)).thenReturn(templateLogger);
        // Register strategies in order of precedence
        List<MessageGroupStrategy> strategies = List.of(
                new MllpMessageGroupStrategy(appLogger),
                new TenantMessageGroupStrategy(),
                new IpPortMessageGroupStrategy()
        );

        messageGroupService = new MessageGroupService(strategies, appLogger);
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

    // Helper to build parameter maps more readably
    private static Map<String, String> params(String... kvPairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put(kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("MLLP → Composite ID built from QE, DeliveryType, Facility, and MessageCode")
    void testMllpCompositeGroupIdWithAllFields() {
        Map<String, String> additional = params(
                Constants.QE, "healthelink",
                Constants.DELIVERY_TYPE, "DEL",
                Constants.FACILITY, "FAC",
                Constants.MESSAGE_CODE, "MSG"
        );

        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, additional);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("healthelink_FAC_MSG_DEL", groupId);
    }

    @Test
    @DisplayName("MLLP → QE only builds simple groupId")
    void testMllpWithQEOnly_usesQEAsGroupId() {
        Map<String, String> additional = params(Constants.QE, "healthelink");
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, additional);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("healthelink", groupId);
    }

    @Test
    @DisplayName("MLLP → Partial fields build only available components")
    void testMllpWithPartialAdditionalDetails_usesAvailableFields() {
        Map<String, String> additional = params(Constants.DELIVERY_TYPE, "DEL", Constants.FACILITY, "");
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, additional);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("DEL", groupId);
    }

    @Test
    @DisplayName("MLLP → No additional details uses port as fallback")
    void testMllpWithNoAdditionalDetails_usesPort() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "9090", MessageSourceType.MLLP, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("9090", groupId);
    }

    @Test
    @DisplayName("MLLP → No details and no port returns default group ID")
    void testMllpWithNoAdditionalAndNoPort_returnsDefault() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", null, MessageSourceType.MLLP, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    @DisplayName("Tenant Strategy → Non-MLLP uses tenant ID as group ID")
    void testTenantStrategy_usedWhenNotMllpAndTenantPresent() {
        var context = buildContext("tenantX", "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("tenantX", groupId);
    }

    @Test
    @DisplayName("Tenant Strategy → Default tenant falls back to IP/Port")
    void testTenantStrategy_withDefaultTenantId_fallbacksToIpPort() {
        var context = buildContext(Constants.DEFAULT_TENANT_ID, "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    @DisplayName("IP/Port Fallback → Builds composite from available fields")
    void testIpPortFallback_buildsCompositeFromAvailableFields() {
        var context = buildContext(null, "192.168.1.1", "192.168.1.2", "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    @DisplayName("IP/Port Fallback → Partial fields still build composite")
    void testIpPortFallback_withMissingFields_buildsPartialComposite() {
        var context = buildContext(null, "192.168.1.1", null, "8080", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_8080", groupId);
    }

    @Test
    @DisplayName("IP/Port Fallback → All blank returns default tenant ID")
    void testIpPortFallback_withAllBlank_returnsUnknownTenantId() {
        var context = buildContext(null, "   ", "   ", "   ", MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("unknown-tenantId", groupId);
    }

    @Test
    @DisplayName("IP/Port Fallback → All null returns default tenant ID")
    void testIpPortFallback_withAllNull_returnsUnknownTenantId() {
        var context = buildContext(null, null, null, null, MessageSourceType.HTTP_INGEST, null);
        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("unknown-tenantId", groupId);
    }
}
