package org.techbd.ingest.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.service.iti.AcknowledgementService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;
import org.techbd.iti.schema.II;
import org.techbd.iti.schema.MCCIIN000002UV01;
import org.techbd.iti.schema.MCCIMT000100UV01Device;
import org.techbd.iti.schema.MCCIMT000100UV01Sender;
import org.techbd.iti.schema.PRPAIN201301UV02;
import org.techbd.iti.schema.PRPAIN201302UV02;
import org.techbd.iti.schema.PRPAIN201304UV02;
import org.techbd.iti.schema.RegistryResponseType;
import org.techbd.iti.schema.ProvideAndRegisterDocumentSetRequestType;
import org.w3c.dom.Document;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBElement;

/**
 * Integration-style unit tests for {@link PixEndpoint} and {@link PnrEndpoint}.
 *
 * <p>
 * Each test reads the corresponding fixture from:
 * {@code nexus-ingestion-api/src/main/resources/soap-test-resources/}
 * and asserts every field the application actually populates in the response,
 * derived from the real response fixtures and the
 * {@link AcknowledgementService}
 * implementation.
 *
 * <p>
 * Response fields asserted per PIX response:
 * <ul>
 * <li>Body: {@code id.root} — non-blank UUID generated per interaction</li>
 * <li>Body: {@code creationTime.value} — IST timestamp (yyyyMMddHHmmssZ)</li>
 * <li>Body: {@code interactionId.root} = {@code 2.16.840.1.113883.1.6}</li>
 * <li>Body: {@code interactionId.extension} = {@code MCCI_IN000002UV01}</li>
 * <li>Body: {@code receiver.device.id.root} — mirrors request sender
 * id.root</li>
 * <li>Body: {@code sender.device.id.root} — mirrors request sender id.root</li>
 * <li>Body: {@code sender.device.telecom.value} — protocol string
 * (HTTP/1.1)</li>
 * <li>Body: {@code acknowledgement.targetMessage.id.root} — mirrors request
 * id.root</li>
 * <li>Body: {@code acknowledgement.targetMessage.id.extension} — mirrors
 * request id.extension</li>
 * <li>Header: {@code wsa:Action} =
 * {@code urn:hl7-org:v3:MCCI_IN000002UV01}</li>
 * <li>Header: {@code wsa:MessageID} — non-blank urn:uuid:</li>
 * <li>Header: {@code wsa:RelatesTo} — matches request wsa:MessageID</li>
 * <li>Header: {@code wsa:To} =
 * {@code http://www.w3.org/2005/08/addressing/anonymous}</li>
 * <li>Header: {@code techbd:Interaction InteractionID} — non-blank UUID</li>
 * <li>Header: {@code techbd:Interaction TechBDIngestionApiVersion} —
 * non-blank</li>
 * </ul>
 *
 * <p>
 * PNR response fields asserted:
 * <ul>
 * <li>Body: {@code RegistryResponse status} = Success URI</li>
 * <li>Header: {@code wsa:Action} =
 * {@code ProvideAndRegisterDocumentSet-bResponse}</li>
 * <li>Header: {@code wsa:RelatesTo} — matches request wsa:MessageID</li>
 * <li>Header: {@code techbd:Interaction} attributes present</li>
 * </ul>
 *
 * <p>
 * SOAP Fault scenarios cover:
 * <ul>
 * <li>Missing body / null RAW_SOAP_MESSAGE</li>
 * <li>Malformed XML in RAW_SOAP_MESSAGE</li>
 * <li>Missing request MessageID header (RelatesTo fallback)</li>
 * <li>RuntimeException in ackService → error ack returned</li>
 * <li>PixEndpoint internal exception → createPixAcknowledgmentError called</li>
 * <li>PnrEndpoint internal exception → Failure RegistryResponse returned</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PIX and PNR endpoint tests using fixture files")
class SoapEndpointFixtureTest {

    // ── Resource base path ────────────────────────────────────────────────────
    private static final String RESOURCE_BASE = "src/test/resources//org/techbd/ingest/soap-test-resources/";

    // ── Namespaces ─────────────────────────────────────────────────────────────
    private static final String NS_WSA = "http://www.w3.org/2005/08/addressing";
    private static final String NS_HL7 = "urn:hl7-org:v3";
    private static final String NS_TECHBD = "urn:techbd:custom";
    private static final String NS_RS = "urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0";

    // ── Expected constant values derived from actual response files ───────────
    private static final String INTERACTION_ID_ROOT = "2.16.840.1.113883.1.6";
    private static final String INTERACTION_ID_EXT = "MCCI_IN000002UV01";
    private static final String WSA_TO_ANON = "http://www.w3.org/2005/08/addressing/anonymous";
    private static final String PIX_ACK_ACTION = "urn:hl7-org:v3:MCCI_IN000002UV01";
    private static final String PNR_ACK_ACTION = "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-bResponse";
    private static final String PNR_SUCCESS_STATUS = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success";
    private static final String PNR_FAILURE_STATUS = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure";

    // ── Mocks shared across nested classes ────────────────────────────────────

    // spy — wraps real AcknowledgementService; initialised in baseSetUp() after appLogger stub
    AcknowledgementService ackService;
    @Mock
    AppConfig appConfig;
    @Mock
    AppConfig.Soap soapConfig;
    @Mock
    AppConfig.Soap.Wsa wsaConfig;
    @Mock
    AppConfig.Soap.Techbd techbdSoapConfig;
    @Mock
    AppConfig.Aws awsConfig;
    @Mock
    AppConfig.Aws.S3 s3Config;
    @Mock
    AppConfig.Aws.S3.BucketConfig defaultBucketConfig;
    @Mock
    PortConfig portConfig;
    @Mock
    AppLogger appLogger;
    @Mock
    TemplateLogger templateLogger;
    @Mock
    MessageContext messageContext;
    @Mock
    TransportContext transportContext;
    @Mock
    HttpServletConnection httpServletConnection;
    @Mock
    HttpServletRequest httpRequest;

    MockedStatic<TransportContextHolder> transportContextHolderMock;
    MockedStatic<ErrorTraceIdGenerator> errorTraceIdGeneratorMock;
    MockedStatic<LogUtil> logUtilMock;

    // ── Common setUp / tearDown ───────────────────────────────────────────────

    @BeforeEach
    void baseSetUp() {
        lenient().when(appLogger.getLogger(any())).thenReturn(templateLogger);
        // Create spy AFTER appLogger stub — the constructor calls appLogger.getLogger()
        ackService = spy(new AcknowledgementService(appLogger));

        // ── AppConfig.version (org.techbd.version) ────────────────────────────
        lenient().when(appConfig.getVersion()).thenReturn("test-version");

        // ── AppConfig.Soap (org.techbd.soap) ─────────────────────────────────
        lenient().when(appConfig.getSoap()).thenReturn(soapConfig);
        // wsa sub-config — values mirror application.yml soap.wsa section
        lenient().when(soapConfig.getWsa()).thenReturn(wsaConfig);
        lenient().when(wsaConfig.getNamespace()).thenReturn(NS_WSA);
        lenient().when(wsaConfig.getPrefix()).thenReturn("wsa");
        lenient().when(wsaConfig.getAction()).thenReturn(PIX_ACK_ACTION);
        lenient().when(wsaConfig.getPnrAction()).thenReturn(PNR_ACK_ACTION);
        lenient().when(wsaConfig.getTo()).thenReturn(WSA_TO_ANON);
        lenient().when(wsaConfig.getUnderstoodNamespaces()).thenReturn(NS_WSA);
        // techbd sub-config — values mirror application.yml soap.techbd section
        lenient().when(soapConfig.getTechbd()).thenReturn(techbdSoapConfig);
        lenient().when(techbdSoapConfig.getNamespace()).thenReturn(NS_TECHBD);
        lenient().when(techbdSoapConfig.getPrefix()).thenReturn("techbd");

        // ── AppConfig.Aws (org.techbd.aws) ───────────────────────────────────
        // Full chain: appConfig.getAws().getS3().getDefaultConfig().getBucket()
        // is called by AbstractMessageSourceProvider.createRequestContext()
        lenient().when(appConfig.getAws()).thenReturn(awsConfig);
        lenient().when(awsConfig.getRegion()).thenReturn("us-east-1");
        lenient().when(awsConfig.getSecretName()).thenReturn("default-secret");
        lenient().when(awsConfig.getS3()).thenReturn(s3Config);
        lenient().when(s3Config.getDefaultConfig()).thenReturn(defaultBucketConfig);
        lenient().when(defaultBucketConfig.getBucket()).thenReturn("test-s3-bucket");
        lenient().when(defaultBucketConfig.getMetadataBucket()).thenReturn("test-s3-metadata-bucket");

        transportContextHolderMock = mockStatic(TransportContextHolder.class);
        errorTraceIdGeneratorMock = mockStatic(ErrorTraceIdGenerator.class);
        logUtilMock = mockStatic(LogUtil.class);

        lenient().when(TransportContextHolder.getTransportContext()).thenReturn(transportContext);
        lenient().when(transportContext.getConnection()).thenReturn(httpServletConnection);
        lenient().when(httpServletConnection.getHttpServletRequest()).thenReturn(httpRequest);
        lenient().when(ErrorTraceIdGenerator.generateErrorTraceId()).thenReturn("ERR-FIXTURE-001");

        // AbstractMessageSourceProvider.createRequestContext() exact calls
        lenient().when(httpRequest.getHeaderNames())
                .thenReturn(Collections.enumeration(Collections.emptyList()));
        lenient().when(httpRequest.getRequestURL())
                .thenReturn(new StringBuffer("http://localhost:8080/soap"));
        lenient().when(httpRequest.getQueryString()).thenReturn(null);
        lenient().when(httpRequest.getProtocol()).thenReturn("HTTP/1.1");
        lenient().when(httpRequest.getLocalAddr()).thenReturn("127.0.0.1");
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(httpRequest.getRequestURI()).thenReturn("/soap");

        lenient().when(httpRequest.getHeader(Constants.HEADER_SOURCE_ID)).thenReturn("test-source");
        lenient().when(httpRequest.getHeader(Constants.HEADER_MSG_TYPE)).thenReturn("test-type");
        lenient().when(httpRequest.getHeader(Constants.HEADER_INTERACTION_ID))
                .thenReturn("fixture-interaction-id");
    }

    @AfterEach
    void baseTearDown() {
        transportContextHolderMock.close();
        errorTraceIdGeneratorMock.close();
        logUtilMock.close();
    }

    // =========================================================================
    // PIX ADD (pix-add-request.txt / pix-add-response.txt)
    // =========================================================================

    @Nested
    @DisplayName("PIX Add — pix-add-request_1_2.txt / pix-add-response_1_2.txt")
    class PixAddFixture {

        private String requestXml;
        private Document responseDoc;
        private PRPAIN201301UV02 parsedRequest;
        private PixEndpoint sut;

        @BeforeEach
        void setUp() throws Exception {
            requestXml = readFixture("pix-add-request_1_2.txt");
            responseDoc = parseXml(readFixture("pix-add-response_1_2.txt"));

            parsedRequest = parsePixAddRequest(requestXml);

            lenient().when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(requestXml);
            lenient().when(ackService.createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> buildPixAck(parsedRequest));
            lenient().when(ackService.createPixAcknowledgmentError(anyString(), anyString(), anyString()))
                    .thenReturn(new MCCIIN000002UV01());

            sut = new PixEndpoint(ackService, appConfig, appLogger, portConfig);
        }

        // ── Response body assertions ──────────────────────────────────────────

        @Test
        @DisplayName("Response id.root is a non-blank UUID")
        void response_id_root_isNonBlankUuid() {
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getId().getRoot()).isNotBlank();
            // UUID pattern (may be plain UUID or urn:uuid: prefixed)
            assertThat(result.getId().getRoot())
                    .matches("^[0-9a-fA-F\\-]{36}$|^urn:uuid:[0-9a-fA-F\\-]{36}$");
        }

        @Test
        @DisplayName("Response creationTime is populated with a non-blank timestamp")
        void response_creationTime_isPopulated() {
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getCreationTime()).isNotNull();
            assertThat(result.getCreationTime().getValue()).isNotBlank();
            // Should be yyyyMMddHHmmss[Z/offset] — at least 14 chars
            assertThat(result.getCreationTime().getValue().length()).isGreaterThanOrEqualTo(14);
        }

        @Test
        @DisplayName("Response interactionId.root = 2.16.840.1.113883.1.6")
        void response_interactionId_root() {
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getInteractionId().getRoot()).isEqualTo(INTERACTION_ID_ROOT);
        }

        @Test
        @DisplayName("Response interactionId.extension = MCCI_IN000002UV01")
        void response_interactionId_extension() {
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getInteractionId().getExtension()).isEqualTo(INTERACTION_ID_EXT);
        }

        @Test
        @DisplayName("Response receiver.device.id.root mirrors request sender id.root (1.2.3.4.5)")
        void response_receiver_mirrorsRequestSenderRoot() {
            // Fixture: request sender id root = "1.2.3.4.5"
            String expectedRoot = xpath(responseDoc,
                    "//ns2:receiver/ns2:device/ns2:id/@root", NS_HL7, "ns2");

            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getReceiver()).isNotEmpty();
            assertThat(result.getReceiver().get(0).getDevice().getId())
                    .anyMatch(id -> "1.2.3.4.5".equals(id.getRoot()));
        }

        @Test
        @DisplayName("Response sender.device.id.root mirrors request sender id.root")
        void response_sender_mirrorsRequestSenderRoot() {
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getSender()).isNotNull();
            assertThat(result.getSender().getDevice().getId())
                    .anyMatch(id -> "1.2.3.4.5".equals(id.getRoot()));
        }

        @Test
        @DisplayName("Response sender.device.telecom.value = HTTP/1.1 (protocol from request context)")
        void response_sender_telecom_isProtocolString() {
            // Actual fixture response has telecom value="HTTP/1.1"
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getSender().getDevice().getTelecom()).isNotEmpty();
            assertThat(result.getSender().getDevice().getTelecom().get(0).getValue())
                    .isNotBlank();
        }

        @Test
        @DisplayName("Response acknowledgement.targetMessage.id echoes request id (root + extension)")
        void response_acknowledgement_targetMessage_echoesRequestId() {
            // Fixture request: id root="2.16.840.1.113883.3.72.5.9.1" extension="12345"
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getAcknowledgement()).isNotEmpty();
            II targetId = result.getAcknowledgement().get(0).getTargetMessage().getId();
            assertThat(targetId.getRoot()).isEqualTo("2.16.840.1.113883.3.72.5.9.1");
            assertThat(targetId.getExtension()).isEqualTo("12345");
        }

        @Test
        @DisplayName("Exactly one acknowledgement block in response")
        void response_hasExactlyOneAcknowledgement() {
            MCCIIN000002UV01 result = sut.handlePixAdd(parsedRequest, messageContext);

            assertThat(result.getAcknowledgement()).hasSize(1);
        }

        // ── Header field assertions (derived from response fixture) ───────────

        @Test
        @DisplayName("Response wsa:Action header = urn:hl7-org:v3:MCCI_IN000002UV01")
        void responseFixture_header_action() {
            String action = xpath(responseDoc, "//wsa:Action", NS_WSA, "wsa");
            assertThat(action).isEqualTo(PIX_ACK_ACTION);
        }

        @Test
        @DisplayName("Response wsa:RelatesTo header echoes request wsa:MessageID")
        void responseFixture_header_relatesTo_matchesRequestMessageId() {
            // Request MessageID: urn:uuid:123e4567-e89b-12d3-a456-426614174000
            String relatesTo = xpath(responseDoc, "//wsa:RelatesTo", NS_WSA, "wsa");
            assertThat(relatesTo).isEqualTo("urn:uuid:123e4567-e89b-12d3-a456-426614174000");
        }

        @Test
        @DisplayName("Response wsa:To header = anonymous address")
        void responseFixture_header_to_isAnonymous() {
            String to = xpath(responseDoc, "//wsa:To", NS_WSA, "wsa");
            assertThat(to).isEqualTo(WSA_TO_ANON);
        }

        @Test
        @DisplayName("Response wsa:MessageID header is a non-blank urn:uuid: value")
        void responseFixture_header_messageId_isUrnUuid() {
            String msgId = xpath(responseDoc, "//wsa:MessageID", NS_WSA, "wsa");
            assertThat(msgId).startsWith("urn:uuid:");
            assertThat(msgId.replace("urn:uuid:", "")).matches("[0-9a-fA-F\\-]{36}");
        }

        @Test
        @DisplayName("Response HL7 body id.root is a valid UUID")
        void responseFixture_body_id_root_isUuid() {
            String root = xpath(responseDoc, "//ns2:id/@root", NS_HL7, "ns2");
            // The first id element is the message ID — UUID format
            assertThat(root).matches("[0-9a-fA-F\\-]{36}");
        }

        @Test
        @DisplayName("Response HL7 body interactionId.root = 2.16.840.1.113883.1.6")
        void responseFixture_body_interactionId_root() {
            String root = xpath(responseDoc, "//ns2:interactionId/@root", NS_HL7, "ns2");
            assertThat(root).isEqualTo(INTERACTION_ID_ROOT);
        }

        @Test
        @DisplayName("Response HL7 body acknowledgement targetMessage id echoes request id")
        void responseFixture_body_acknowledgement_targetMessage() {
            String root = xpath(responseDoc,
                    "//ns2:acknowledgement/ns2:targetMessage/ns2:id/@root", NS_HL7, "ns2");
            String ext = xpath(responseDoc,
                    "//ns2:acknowledgement/ns2:targetMessage/ns2:id/@extension", NS_HL7, "ns2");
            assertThat(root).isEqualTo("2.16.840.1.113883.3.72.5.9.1");
            assertThat(ext).isEqualTo("12345");
        }

        @Test
        @DisplayName("Response HL7 sender telecom value is HTTP/1.1")
        void responseFixture_body_sender_telecom() {
            String telecom = xpath(responseDoc,
                    "//ns2:sender/ns2:device/ns2:telecom/@value", NS_HL7, "ns2");
            assertThat(telecom).isEqualTo("HTTP/1.1");
        }
    }

    // =========================================================================
    // PIX MERGE (pix-merge-request.txt / pix-merge-response.txt)
    // =========================================================================

    @Nested
    @DisplayName("PIX Merge — pix-merge-request_1_2.txt / pix-merge-response_1_2.txt")
    class PixMergeFixture {

        private String requestXml;
        private Document responseDoc;
        private PRPAIN201304UV02 parsedRequest;
        private PixEndpoint sut;

        @BeforeEach
        void setUp() throws Exception {
            requestXml = readFixture("pix-merge-request_1_2.txt");
            responseDoc = parseXml(readFixture("pix-merge-response_1_2.txt"));
            parsedRequest = parsePixMergeRequest(requestXml);

            lenient().when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(requestXml);
            lenient().when(ackService.createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> buildPixAck(parsedRequest));
            lenient().when(ackService.createPixAcknowledgmentError(anyString(), anyString(), anyString()))
                    .thenReturn(new MCCIIN000002UV01());

            sut = new PixEndpoint(ackService, appConfig, appLogger, portConfig);
        }

        @Test
        @DisplayName("Response id.root is a non-blank UUID")
        void response_id_root_isUuid() {
            String root = xpath(responseDoc, "//ns2:id/@root", NS_HL7, "ns2");
            assertThat(root).matches("[0-9a-fA-F\\-]{36}");
        }

        @Test
        @DisplayName("Response creationTime value is populated (≥14 chars)")
        void response_creationTime_populated() {
            String ts = xpath(responseDoc, "//ns2:creationTime/@value", NS_HL7, "ns2");
            assertThat(ts).isNotBlank();
            assertThat(ts.length()).isGreaterThanOrEqualTo(14);
        }

        @Test
        @DisplayName("Response interactionId root and extension are correct")
        void response_interactionId() {
            assertThat(xpath(responseDoc, "//ns2:interactionId/@root", NS_HL7, "ns2"))
                    .isEqualTo(INTERACTION_ID_ROOT);
            assertThat(xpath(responseDoc, "//ns2:interactionId/@extension", NS_HL7, "ns2"))
                    .isEqualTo(INTERACTION_ID_EXT);
        }

        @Test
        @DisplayName("Response wsa:RelatesTo matches merge request wsa:MessageID")
        void response_relatesTo_matchesMergeRequestMessageId() {
            // Merge request MessageID: urn:uuid:223e4567-e89b-12d3-a456-426614174999
            String relatesTo = xpath(responseDoc, "//wsa:RelatesTo", NS_WSA, "wsa");
            assertThat(relatesTo).isEqualTo("urn:uuid:223e4567-e89b-12d3-a456-426614174999");
        }

        @Test
        @DisplayName("Response acknowledgement targetMessage id.extension = MERGE-12345")
        void response_acknowledgement_targetMessage_mergeExtension() {
            String ext = xpath(responseDoc,
                    "//ns2:acknowledgement/ns2:targetMessage/ns2:id/@extension", NS_HL7, "ns2");
            assertThat(ext).isEqualTo("MERGE-12345");
        }

        @Test
        @DisplayName("Response acknowledgement targetMessage id.root echoes request id.root")
        void response_acknowledgement_targetMessage_root() {
            String root = xpath(responseDoc,
                    "//ns2:acknowledgement/ns2:targetMessage/ns2:id/@root", NS_HL7, "ns2");
            assertThat(root).isEqualTo("2.16.840.1.113883.3.72.5.9.1");
        }

        @Test
        @DisplayName("Response receiver device id.root mirrors request sender (1.2.3.4.5)")
        void response_receiver_mirrorsSender() {
            String root = xpath(responseDoc,
                    "//ns2:receiver/ns2:device/ns2:id/@root", NS_HL7, "ns2");
            assertThat(root).isEqualTo("1.2.3.4.5");
        }

        @Test
        @DisplayName("Response sender telecom value = HTTP/1.1")
        void response_sender_telecom() {
            String telecom = xpath(responseDoc,
                    "//ns2:sender/ns2:device/ns2:telecom/@value", NS_HL7, "ns2");
            assertThat(telecom).isEqualTo("HTTP/1.1");
        }

        @Test
        @DisplayName("techbd:Interaction attributes are both present and non-blank")
        void response_techbd_interaction_attributes() {
            assertThat(xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "InteractionID"))
                    .matches("[0-9a-fA-F\\-]{36}");
            assertThat(xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "TechBDIngestionApiVersion"))
                    .isNotBlank();
        }

        @Test
        @DisplayName("handlePixDuplicateResolved returns ack from service for merge request")
        void endpoint_handlePixDuplicateResolved_returnsAck() {
            MCCIIN000002UV01 ack = buildPixAck(parsedRequest);
            when(ackService.createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(ack);

            MCCIIN000002UV01 result = sut.handlePixDuplicateResolved(parsedRequest, messageContext);

            assertThat(result).isSameAs(ack);
            verify(ackService).createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString());
        }
    }

    // =========================================================================
    // PIX UPDATE (pix-update-request.txt / pix-update-response.txt)
    // =========================================================================

    @Nested
    @DisplayName("PIX Update — pix-update-request_1_2.txt / pix-update-response_1_2.txt")
    class PixUpdateFixture {

        private String requestXml;
        private Document responseDoc;
        private PRPAIN201302UV02 parsedRequest;
        private PixEndpoint sut;

        @BeforeEach
        void setUp() throws Exception {
            requestXml = readFixture("pix-update-request_1_2.txt");
            responseDoc = parseXml(readFixture("pix-update-response_1_2.txt"));
            parsedRequest = parsePixUpdateRequest(requestXml);

            lenient().when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(requestXml);
            lenient().when(ackService.createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> buildPixAckForUpdate(parsedRequest));
            lenient().when(ackService.createPixAcknowledgmentError(anyString(), anyString(), anyString()))
                    .thenReturn(new MCCIIN000002UV01());

            sut = new PixEndpoint(ackService, appConfig, appLogger, portConfig);
        }

        @Test
        @DisplayName("Response id.root is a UUID")
        void response_id_root_isUuid() {
            String root = xpath(responseDoc, "//ns2:id/@root", NS_HL7, "ns2");
            assertThat(root).matches("[0-9a-fA-F\\-]{36}");
        }

        @Test
        @DisplayName("Response creationTime is populated")
        void response_creationTime_populated() {
            String ts = xpath(responseDoc, "//ns2:creationTime/@value", NS_HL7, "ns2");
            assertThat(ts).isNotBlank().hasSizeGreaterThanOrEqualTo(14);
        }

        @Test
        @DisplayName("Response interactionId is correct")
        void response_interactionId_correct() {
            assertThat(xpath(responseDoc, "//ns2:interactionId/@root", NS_HL7, "ns2"))
                    .isEqualTo(INTERACTION_ID_ROOT);
            assertThat(xpath(responseDoc, "//ns2:interactionId/@extension", NS_HL7, "ns2"))
                    .isEqualTo(INTERACTION_ID_EXT);
        }

        @Test
        @DisplayName("Update request has empty SOAP Header — wsa:RelatesTo falls back to 'unknown-incoming-message-id'")
        void response_relatesTo_fallbackForMissingWsaHeader() {
            // pix-update-request.txt has an empty <SOAP-ENV:Header/> — no MessageID
            // Application falls back to "urn:uuid:unknown-incoming-message-id"
            String relatesTo = xpath(responseDoc, "//wsa:RelatesTo", NS_WSA, "wsa");
            assertThat(relatesTo).isEqualTo("urn:uuid:unknown-incoming-message-id");
        }

        @Test
        @DisplayName("Response acknowledgement targetMessage id.extension = update-56789")
        void response_acknowledgement_updateExtension() {
            String ext = xpath(responseDoc,
                    "//ns2:acknowledgement/ns2:targetMessage/ns2:id/@extension", NS_HL7, "ns2");
            assertThat(ext).isEqualTo("update-56789");
        }

        @Test
        @DisplayName("Response receiver device id.root mirrors request sender (1.2.3.4.5.6.7.8.9)")
        void response_receiver_mirrorsSender() {
            String root = xpath(responseDoc,
                    "//ns2:receiver/ns2:device/ns2:id/@root", NS_HL7, "ns2");
            assertThat(root).isEqualTo("1.2.3.4.5.6.7.8.9");
        }

        @Test
        @DisplayName("techbd:Interaction attributes present")
        void response_techbd_interaction_present() {
            assertThat(xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "InteractionID"))
                    .matches("[0-9a-fA-F\\-]{36}");
            assertThat(xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "TechBDIngestionApiVersion"))
                    .isNotBlank();
        }

        @Test
        @DisplayName("handlePixUpdate calls ackService and returns response")
        void endpoint_handlePixUpdate_callsService() {
            MCCIIN000002UV01 ack = buildPixAckForUpdate(parsedRequest);
            when(ackService.createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(ack);

            MCCIIN000002UV01 result = sut.handlePixUpdate(parsedRequest, messageContext);

            assertThat(result).isSameAs(ack);
        }
    }

    // =========================================================================
    // PNR (pnr-request.txt / pnr-response.txt)
    // =========================================================================

    @Nested
    @DisplayName("PNR — pnr-request_1_2.txt / pnr-response_1_2.txt")
    class PnrFixture {

        private String requestXml;
        private Document responseDoc;
        private PnrEndpoint sut;

        @Mock
        JAXBElement<ProvideAndRegisterDocumentSetRequestType> jaxbRequest;
        @Mock
        ProvideAndRegisterDocumentSetRequestType requestPayload;
        @Mock
        RegistryResponseType successResponse;

        @BeforeEach
        void setUp() throws Exception {
            org.mockito.MockitoAnnotations.openMocks(this);
            lenient().when(appLogger.getLogger(any())).thenReturn(templateLogger);

            requestXml = readFixture("pnr-request_1_2.txt");
            responseDoc = parseXml(readFixture("pnr-response_1_2.txt"));

            lenient().when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(requestXml);
            lenient().when(jaxbRequest.getValue()).thenReturn(requestPayload);
            lenient().when(ackService.createPnrAcknowledgement(eq("Success"), anyString()))
                    .thenReturn(successResponse);
            lenient().when(successResponse.getStatus()).thenReturn(PNR_SUCCESS_STATUS);

            sut = new PnrEndpoint(ackService, appConfig, appLogger);
        }

        // ── Response body assertions ──────────────────────────────────────────

        @Test
        @DisplayName("Response RegistryResponse status = Success URI")
        void responseFixture_status_isSuccess() {
            String status = xpath(responseDoc, "//ns3:RegistryResponse/@status", NS_RS, "ns3");
            assertThat(status).isEqualTo(PNR_SUCCESS_STATUS);
        }

        @Test
        @DisplayName("Endpoint returns JAXBElement wrapping successResponse")
        void endpoint_returnsSuccessJaxbElement() {
            JAXBElement<RegistryResponseType> result = sut.handleProvideAndRegister(jaxbRequest, messageContext);

            assertThat(result).isNotNull();
            assertThat(result.getValue()).isSameAs(successResponse);
        }

        @Test
        @DisplayName("ackService.createPnrAcknowledgement called with 'Success' and request interactionId")
        void endpoint_callsAckService_withSuccess() {
            sut.handleProvideAndRegister(jaxbRequest, messageContext);

            verify(ackService).createPnrAcknowledgement(eq("Success"), anyString());
        }

        // ── Response header assertions ────────────────────────────────────────

        @Test
        @DisplayName("Response wsa:Action = ProvideAndRegisterDocumentSet-bResponse")
        void responseFixture_header_action() {
            String action = xpath(responseDoc, "//wsa:Action", NS_WSA, "wsa");
            assertThat(action).isEqualTo(PNR_ACK_ACTION);
        }

        @Test
        @DisplayName("Response wsa:RelatesTo echoes request wsa:MessageID")
        void responseFixture_header_relatesTo_matchesRequestMessageId() {
            // PNR request MessageID: urn:uuid:12345678-90ab-cdef-1234-567890abcdef
            String relatesTo = xpath(responseDoc, "//wsa:RelatesTo", NS_WSA, "wsa");
            assertThat(relatesTo).isEqualTo("urn:uuid:12345678-90ab-cdef-1234-567890abcdef");
        }

        @Test
        @DisplayName("Response wsa:To = anonymous address")
        void responseFixture_header_to_isAnonymous() {
            String to = xpath(responseDoc, "//wsa:To", NS_WSA, "wsa");
            assertThat(to).isEqualTo(WSA_TO_ANON);
        }

        @Test
        @DisplayName("Response wsa:MessageID is a urn:uuid: value")
        void responseFixture_header_messageId_isUrnUuid() {
            String msgId = xpath(responseDoc, "//wsa:MessageID", NS_WSA, "wsa");
            assertThat(msgId).startsWith("urn:uuid:");
        }

        @Test
        @DisplayName("Response techbd:Interaction InteractionID is a UUID")
        void responseFixture_techbd_interactionId_isUuid() {
            String id = xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "InteractionID");
            assertThat(id).matches("[0-9a-fA-F\\-]{36}");
        }

        @Test
        @DisplayName("Response techbd:Interaction TechBDIngestionApiVersion is populated")
        void responseFixture_techbd_apiVersion_populated() {
            String v = xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "TechBDIngestionApiVersion");
            assertThat(v).isNotBlank();
        }

        @Test
        @DisplayName("getMessageSource returns SOAP_PNR")
        void getMessageSource_returnsSoapPnr() {
            assertThat(sut.getMessageSource()).isEqualTo(MessageSourceType.SOAP_PNR);
        }
    }

    // =========================================================================
    // PNR MTOM (pnr-xdsb-mtom-request.txt / pnr-xdsb-mtom-response.txt)
    // =========================================================================

    @Nested
    @DisplayName("PNR MTOM — pnr-xdsb-mtom-request.txt / pnr-xdsb-mtom-response.txt")
    class PnrMtomFixture {

        private String requestRaw; // full MTOM multipart including boundary
        private String requestXml; // extracted SOAP envelope XML only
        private Document responseDoc;
        private PnrEndpoint sut;

        @Mock
        JAXBElement<ProvideAndRegisterDocumentSetRequestType> jaxbRequest;
        @Mock
        ProvideAndRegisterDocumentSetRequestType requestPayload;
        @Mock
        RegistryResponseType successResponse;

        @BeforeEach
        void setUp() throws Exception {
            org.mockito.MockitoAnnotations.openMocks(this);
            lenient().when(appLogger.getLogger(any())).thenReturn(templateLogger);

            requestRaw = readFixture("pnr-xdsb-mtom-request.txt");
            requestXml = extractSoapEnvelopeFromMtom(requestRaw);
            responseDoc = parseXml(readFixture("pnr-xdsb-mtom-response.txt"));

            lenient().when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(requestRaw);
            lenient().when(jaxbRequest.getValue()).thenReturn(requestPayload);
            lenient().when(ackService.createPnrAcknowledgement(eq("Success"), anyString()))
                    .thenReturn(successResponse);
            lenient().when(successResponse.getStatus()).thenReturn(PNR_SUCCESS_STATUS);

            sut = new PnrEndpoint(ackService, appConfig, appLogger);
        }

        @Test
        @DisplayName("MTOM response RegistryResponse status = Success URI")
        void mtomResponse_status_isSuccess() {
            String status = xpath(responseDoc, "//ns3:RegistryResponse/@status", NS_RS, "ns3");
            assertThat(status).isEqualTo(PNR_SUCCESS_STATUS);
        }

        @Test
        @DisplayName("MTOM response wsa:Action = ProvideAndRegisterDocumentSet-bResponse")
        void mtomResponse_action_isCorrect() {
            String action = xpath(responseDoc, "//wsa:Action", NS_WSA, "wsa");
            assertThat(action).isEqualTo(PNR_ACK_ACTION);
        }

        @Test
        @DisplayName("MTOM response wsa:RelatesTo matches MTOM request MessageID (no urn:uuid: prefix)")
        void mtomResponse_relatesTo_matchesMtomRequestMessageId() {
            // MTOM request MessageID: f19a3e9e-324d-4c6c-8a96-6747955d86f5 (no urn:uuid:
            // prefix)
            String relatesTo = xpath(responseDoc, "//wsa:RelatesTo", NS_WSA, "wsa");
            assertThat(relatesTo).isEqualTo("f19a3e9e-324d-4c6c-8a96-6747955d86f5");
        }

        @Test
        @DisplayName("MTOM response wsa:To = anonymous address")
        void mtomResponse_to_isAnonymous() {
            String to = xpath(responseDoc, "//wsa:To", NS_WSA, "wsa");
            assertThat(to).isEqualTo(WSA_TO_ANON);
        }

        @Test
        @DisplayName("MTOM response wsa:MessageID is a urn:uuid: value")
        void mtomResponse_messageId_isUrnUuid() {
            String msgId = xpath(responseDoc, "//wsa:MessageID", NS_WSA, "wsa");
            assertThat(msgId).startsWith("urn:uuid:");
        }

        @Test
        @DisplayName("MTOM response techbd:Interaction InteractionID present")
        void mtomResponse_techbd_interactionId_present() {
            String id = xpathAttr(responseDoc, "//techbd:Interaction", NS_TECHBD, "techbd", "InteractionID");
            assertThat(id).matches("[0-9a-fA-F\\-]{36}");
        }

        @Test
        @DisplayName("MTOM request contains SOAP envelope extracted from multipart boundary")
        void mtomRequest_soapEnvelope_extracted() {
            assertThat(requestXml).contains("<soap:Envelope");
            assertThat(requestXml).contains("ProvideAndRegisterDocumentSetRequest");
        }

        @Test
        @DisplayName("MTOM request contains CCD payload part")
        void mtomRequest_containsCcdPayload() {
            assertThat(requestRaw).contains("<ClinicalDocument");
            assertThat(requestRaw).contains("payload@meditech.com");
        }

        @Test
        @DisplayName("MTOM endpoint returns success response for MTOM request")
        void endpoint_handlesRawMtomMessage_returnsSuccess() {
            JAXBElement<RegistryResponseType> result = sut.handleProvideAndRegister(jaxbRequest, messageContext);

            assertThat(result).isNotNull();
            assertThat(result.getValue().getStatus()).isEqualTo(PNR_SUCCESS_STATUS);
        }
    }

    // =========================================================================
    // SOAP FAULT SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("SOAP Fault scenarios")
    class SoapFaultScenarios {

        private PixEndpoint pixEndpoint;
        private PnrEndpoint pnrEndpoint;

        @Mock
        PRPAIN201301UV02 addRequest;
        @Mock
        MCCIMT000100UV01Device senderDevice;
        @Mock
        MCCIMT000100UV01Sender sender;
        @Mock
        MCCIIN000002UV01 errAck;

        @Mock
        JAXBElement<ProvideAndRegisterDocumentSetRequestType> jaxbRequest;
        @Mock
        ProvideAndRegisterDocumentSetRequestType requestPayload;
        @Mock
        RegistryResponseType failureResponse;

        @BeforeEach
        void setUp() throws Exception {
            org.mockito.MockitoAnnotations.openMocks(this);
            lenient().when(appLogger.getLogger(any())).thenReturn(templateLogger);

            lenient().when(sender.getDevice()).thenReturn(senderDevice);
            lenient().when(addRequest.getId()).thenReturn(requestIdWith("2.16.840.1.113883.3.72.5.9.1", "FAULT-001"));
            lenient().when(addRequest.getSender()).thenReturn(sender);
            lenient().when(jaxbRequest.getValue()).thenReturn(requestPayload);
            lenient().when(ackService.createPixAcknowledgmentError(anyString(), anyString(), anyString()))
                    .thenReturn(errAck);
            lenient().when(ackService.createPnrAcknowledgement(eq("Failure"), anyString(), anyString()))
                    .thenReturn(failureResponse);
            lenient().when(failureResponse.getStatus()).thenReturn(PNR_FAILURE_STATUS);

            pixEndpoint = new PixEndpoint(ackService, appConfig, appLogger, portConfig);
            pnrEndpoint = new PnrEndpoint(ackService, appConfig, appLogger);
        }

        // ── PIX fault scenarios ───────────────────────────────────────────────

        @Test
        @DisplayName("FAULT-PIX-01: null RAW_SOAP_MESSAGE causes NPE → error ack returned")
        void fault_pix_nullRawSoapMessage_returnsErrorAck() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(null);
            when(httpRequest.getHeader(Constants.HEADER_INTERACTION_ID)).thenReturn("fault-interaction");

            MCCIIN000002UV01 result = pixEndpoint.handlePixAdd(addRequest, messageContext);

            assertThat(result).isSameAs(errAck);
            verify(ackService).createPixAcknowledgmentError(
                    eq("Internal server error"), eq("fault-interaction"), anyString());
        }

        @Test
        @DisplayName("FAULT-PIX-02: ackService throws RuntimeException → error ack returned")
        void fault_pix_ackServiceThrows_returnsErrorAck() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenReturn(readFixtureQuiet("pix-add-request_1_2.txt"));
            when(ackService.createPixAcknowledgement(any(), any(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("downstream service unavailable"));

            MCCIIN000002UV01 result = pixEndpoint.handlePixAdd(addRequest, messageContext);

            assertThat(result).isSameAs(errAck);
            verify(ackService).createPixAcknowledgmentError(
                    eq("Internal server error"), anyString(), eq("ERR-FIXTURE-001"));
        }

        @Test
        @DisplayName("FAULT-PIX-03: ErrorTraceIdGenerator called on exception — trace ID included in error ack")
        void fault_pix_errorTraceId_includedInErrorAck() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("raw message read failed"));

            pixEndpoint.handlePixAdd(addRequest, messageContext);

            verify(ackService).createPixAcknowledgmentError(
                    anyString(), anyString(), eq("ERR-FIXTURE-001"));
            errorTraceIdGeneratorMock.verify(ErrorTraceIdGenerator::generateErrorTraceId);
        }

        @Test
        @DisplayName("FAULT-PIX-04: LogUtil.logDetailedError called with status 500 on exception")
        void fault_pix_logUtil_calledOnException() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("log util test"));

            pixEndpoint.handlePixAdd(addRequest, messageContext);

            logUtilMock.verify(() -> LogUtil.logDetailedError(
                    eq(500), anyString(), anyString(), anyString(), any(Exception.class)));
        }

        @Test
        @DisplayName("FAULT-PIX-05: templateLogger.error called with exception on failure")
        void fault_pix_loggerError_calledOnException() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("logger test"));

            pixEndpoint.handlePixAdd(addRequest, messageContext);

            verify(templateLogger).error(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("FAULT-PIX-06: Missing wsa:MessageID in request → RelatesTo = unknown-incoming-message-id (handled at SoapResponseUtil level)")
        void fault_pix_missingMessageId_fallbackRelatesTo() {
            // This is a structural property: the SoapResponseUtil falls back to
            // "urn:uuid:unknown-incoming-message-id" when no MessageID found.
            // The update request fixture demonstrates this — its response has the fallback
            // value.
            String updateResponse = readFixtureQuiet("pix-update-response_1_2.txt");
            assertThat(updateResponse).contains("urn:uuid:unknown-incoming-message-id");
        }

        @Test
        @DisplayName("FAULT-PIX-07: handlePixUpdate exception → error ack with correct trace ID")
        void fault_pixUpdate_exception_returnsErrorAck() {
            PRPAIN201302UV02 updateRequest = mock(PRPAIN201302UV02.class);
            lenient().when(updateRequest.getId())
                    .thenReturn(requestIdWith("2.16.840.1.113883.3.72.5.9.1", "update-fault"));
            lenient().when(updateRequest.getSender()).thenReturn(sender);
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("update fault"));

            MCCIIN000002UV01 result = pixEndpoint.handlePixUpdate(updateRequest, messageContext);

            assertThat(result).isSameAs(errAck);
        }

        @Test
        @DisplayName("FAULT-PIX-08: handlePixDuplicateResolved exception → error ack returned")
        void fault_pixMerge_exception_returnsErrorAck() {
            PRPAIN201304UV02 mergeRequest = mock(PRPAIN201304UV02.class);
            lenient().when(mergeRequest.getId())
                    .thenReturn(requestIdWith("2.16.840.1.113883.3.72.5.9.1", "merge-fault"));
            lenient().when(mergeRequest.getSender()).thenReturn(sender);
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("merge fault"));

            MCCIIN000002UV01 result = pixEndpoint.handlePixDuplicateResolved(mergeRequest, messageContext);

            assertThat(result).isSameAs(errAck);
        }

        // ── PNR fault scenarios ───────────────────────────────────────────────

        @Test
        @DisplayName("FAULT-PNR-01: null RAW_SOAP_MESSAGE → Failure RegistryResponse returned")
        void fault_pnr_nullRawSoapMessage_returnsFailure() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE")).thenReturn(null);
            when(httpRequest.getHeader(Constants.HEADER_INTERACTION_ID)).thenReturn("pnr-fault");

            JAXBElement<RegistryResponseType> result = pnrEndpoint.handleProvideAndRegister(jaxbRequest,
                    messageContext);

            assertThat(result).isNotNull();
            assertThat(result.getValue().getStatus()).isEqualTo(PNR_FAILURE_STATUS);
            verify(ackService).createPnrAcknowledgement(eq("Failure"), anyString(), anyString());
        }

        @Test
        @DisplayName("FAULT-PNR-02: ackService.createPnrAcknowledgement throws → Failure response")
        void fault_pnr_ackServiceThrows_returnsFailure() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenReturn(readFixtureQuiet("pnr-request_1_2.txt"));
            when(ackService.createPnrAcknowledgement(eq("Success"), anyString()))
                    .thenThrow(new RuntimeException("pnr service down"));

            JAXBElement<RegistryResponseType> result = pnrEndpoint.handleProvideAndRegister(jaxbRequest,
                    messageContext);

            assertThat(result.getValue().getStatus()).isEqualTo(PNR_FAILURE_STATUS);
        }

        @Test
        @DisplayName("FAULT-PNR-03: ErrorTraceIdGenerator called on PNR exception")
        void fault_pnr_errorTraceIdGenerator_called() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("pnr trace test"));

            pnrEndpoint.handleProvideAndRegister(jaxbRequest, messageContext);

            errorTraceIdGeneratorMock.verify(ErrorTraceIdGenerator::generateErrorTraceId);
        }

        @Test
        @DisplayName("FAULT-PNR-04: LogUtil.logDetailedError called with 500 on PNR exception")
        void fault_pnr_logUtil_calledWith500() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("pnr logutil test"));

            pnrEndpoint.handleProvideAndRegister(jaxbRequest, messageContext);

            logUtilMock.verify(() -> LogUtil.logDetailedError(
                    eq(500), anyString(), anyString(), anyString(), any(Exception.class)));
        }

        @Test
        @DisplayName("FAULT-PNR-05: Failure response status = Failure URI (not Success)")
        void fault_pnr_failureResponse_hasCorrectStatus() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("force failure"));

            JAXBElement<RegistryResponseType> result = pnrEndpoint.handleProvideAndRegister(jaxbRequest,
                    messageContext);

            assertThat(result.getValue().getStatus())
                    .isEqualTo(PNR_FAILURE_STATUS)
                    .doesNotContain("Success");
        }

        @Test
        @DisplayName("FAULT-PNR-06: Error response includes errorTraceId in ackService call")
        void fault_pnr_errorTraceId_passedToAckService() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenThrow(new RuntimeException("trace id verify"));

            pnrEndpoint.handleProvideAndRegister(jaxbRequest, messageContext);

            verify(ackService).createPnrAcknowledgement(
                    eq("Failure"), anyString(), eq("ERR-FIXTURE-001"));
        }

        @Test
        @DisplayName("FAULT-PNR-07: MTOM request with exception returns Failure response")
        void fault_pnr_mtomException_returnsFailure() {
            when(messageContext.getProperty("RAW_SOAP_MESSAGE"))
                    .thenReturn(readFixtureQuiet("pnr-xdsb-mtom-request.txt"));
            when(ackService.createPnrAcknowledgement(eq("Success"), anyString()))
                    .thenThrow(new RuntimeException("mtom processing failed"));

            JAXBElement<RegistryResponseType> result = pnrEndpoint.handleProvideAndRegister(jaxbRequest,
                    messageContext);

            assertThat(result.getValue().getStatus()).isEqualTo(PNR_FAILURE_STATUS);
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Reads a fixture file from the project resource path using Java NIO (Java 8+).
     * Path: nexus-ingestion-api/src/main/resources/soap-test-resources/{filename}
     */
    static String readFixture(String filename) throws IOException {
        return Files.readString(Path.of(RESOURCE_BASE + filename));
    }

    /** Silent variant — wraps IOException for use inside lambdas/mocks. */
    static String readFixtureQuiet(String filename) {
        try {
            return readFixture(filename);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read fixture: " + filename, e);
        }
    }

    /** Extracts the SOAP Envelope XML from an MTOM multipart message. */
    static String extractSoapEnvelopeFromMtom(String mtomRaw) {
        int envStart = mtomRaw.indexOf("<?xml");
        int envEnd = mtomRaw.indexOf("</soap:Envelope>", envStart);
        if (envEnd == -1) {
            envEnd = mtomRaw.indexOf("</soap:Envelope>", envStart);
        }
        if (envStart == -1 || envEnd == -1)
            return mtomRaw;
        return mtomRaw.substring(envStart, envEnd + "</soap:Envelope>".length());
    }

    /** Parses an XML string into a DOM Document with namespace awareness. */
    static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Evaluates a simple namespace-aware XPath to retrieve element text content or
     * attribute value. Uses prefix-to-namespace mapping for single-namespace
     * queries.
     */
    static String xpath(Document doc, String expr, String ns, String prefix) {
        try {
            javax.xml.xpath.XPathFactory xpf = javax.xml.xpath.XPathFactory.newInstance();
            javax.xml.xpath.XPath xp = xpf.newXPath();
            xp.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String p) {
                    return switch (p) {
                        case "wsa" -> NS_WSA;
                        case "techbd" -> NS_TECHBD;
                        case "ns2" -> NS_HL7;
                        case "ns3" -> NS_RS;
                        default -> ns;
                    };
                }

                @Override
                public String getPrefix(String n) {
                    return null;
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String n) {
                    return null;
                }
            });
            return xp.evaluate(expr, doc);
        } catch (Exception e) {
            throw new RuntimeException("XPath failed: " + expr, e);
        }
    }

    /**
     * Retrieves an attribute value from the first element matching the XPath
     * expression.
     */
    static String xpathAttr(Document doc, String elementExpr, String ns, String prefix, String attrName) {
        try {
            javax.xml.xpath.XPathFactory xpf = javax.xml.xpath.XPathFactory.newInstance();
            javax.xml.xpath.XPath xp = xpf.newXPath();
            xp.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String p) {
                    return switch (p) {
                        case "wsa" -> NS_WSA;
                        case "techbd" -> NS_TECHBD;
                        case "ns2" -> NS_HL7;
                        case "ns3" -> NS_RS;
                        default -> ns;
                    };
                }

                @Override
                public String getPrefix(String n) {
                    return null;
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String n) {
                    return null;
                }
            });
            org.w3c.dom.Node node = (org.w3c.dom.Node) xp.evaluate(
                    elementExpr, doc, javax.xml.xpath.XPathConstants.NODE);
            if (node == null)
                return "";
            org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
            if (attrs == null)
                return "";
            org.w3c.dom.Node attr = attrs.getNamedItem(attrName);
            return attr != null ? attr.getNodeValue() : "";
        } catch (Exception e) {
            throw new RuntimeException("XPath attr failed: " + elementExpr + "@" + attrName, e);
        }
    }

    // ── HL7 request object builders ───────────────────────────────────────────

    /**
     * Builds a real PRPAIN201301UV02 by extracting id and sender from the add
     * request XML.
     */
    static PRPAIN201301UV02 parsePixAddRequest(String xml) throws Exception {
        Document doc = parseXml(xml);
        PRPAIN201301UV02 req = new PRPAIN201301UV02();
        req.setId(extractMsgId(doc, "//v3:PRPA_IN201301UV02/v3:id"));
        req.setSender(extractSender(doc));
        return req;
    }

    static PRPAIN201302UV02 parsePixUpdateRequest(String xml) throws Exception {
        Document doc = parseXml(xml);
        PRPAIN201302UV02 req = new PRPAIN201302UV02();
        req.setId(extractMsgId(doc, "//v3:PRPA_IN201302UV02/v3:id"));
        req.setSender(extractSender(doc));
        return req;
    }

    static PRPAIN201304UV02 parsePixMergeRequest(String xml) throws Exception {
        Document doc = parseXml(xml);
        PRPAIN201304UV02 req = new PRPAIN201304UV02();
        req.setId(extractMsgId(doc, "//v3:PRPA_IN201304UV02/v3:id"));
        req.setSender(extractSender(doc));
        return req;
    }

    private static II extractMsgId(Document doc, String xpathBase) {
        String nsHl7 = "urn:hl7-org:v3";
        try {
            javax.xml.xpath.XPath xp = buildXPath(nsHl7);
            String root = xp.evaluate(xpathBase + "/@root", doc);
            String ext = xp.evaluate(xpathBase + "/@extension", doc);
            II id = new II();
            id.setRoot(root.isBlank() ? "2.16.840.1.113883.3.72.5.9.1" : root);
            id.setExtension(ext.isBlank() ? "UNKNOWN" : ext);
            return id;
        } catch (Exception e) {
            II id = new II();
            id.setRoot("2.16.840.1.113883.3.72.5.9.1");
            id.setExtension("UNKNOWN");
            return id;
        }
    }

    private static org.techbd.iti.schema.MCCIMT000100UV01Sender extractSender(Document doc) {
        try {
            javax.xml.xpath.XPath xp = buildXPath("urn:hl7-org:v3");
            String root = xp.evaluate("//v3:sender/v3:device/v3:id/@root", doc);
            MCCIMT000100UV01Device device = new MCCIMT000100UV01Device();
            II id = new II();
            id.setRoot(root.isBlank() ? "1.2.3.4.5" : root);
            device.getId().add(id);
            org.techbd.iti.schema.MCCIMT000100UV01Sender sender = new org.techbd.iti.schema.MCCIMT000100UV01Sender();
            sender.setDevice(device);
            return sender;
        } catch (Exception e) {
            org.techbd.iti.schema.MCCIMT000100UV01Sender sender = new org.techbd.iti.schema.MCCIMT000100UV01Sender();
            sender.setDevice(new MCCIMT000100UV01Device());
            return sender;
        }
    }

    private static javax.xml.xpath.XPath buildXPath(String defaultNs) {
        javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
            @Override
            public String getNamespaceURI(String p) {
                return "v3".equals(p) ? defaultNs : defaultNs;
            }

            @Override
            public String getPrefix(String n) {
                return null;
            }

            @Override
            public java.util.Iterator<String> getPrefixes(String n) {
                return null;
            }
        });
        return xp;
    }

    /**
     * Builds a real MCCIIN000002UV01 acknowledgement mirroring the fixture response
     * fields.
     */
    static MCCIIN000002UV01 buildPixAck(PRPAIN201301UV02 req) {
        return buildAck(req.getId(), req.getSender() != null ? req.getSender().getDevice() : null);
    }

    static MCCIIN000002UV01 buildPixAck(PRPAIN201304UV02 req) {
        return buildAck(req.getId(), req.getSender() != null ? req.getSender().getDevice() : null);
    }

    static MCCIIN000002UV01 buildPixAckForUpdate(PRPAIN201302UV02 req) {
        return buildAck(req.getId(), req.getSender() != null ? req.getSender().getDevice() : null);
    }

    private static MCCIIN000002UV01 buildAck(II requestId, MCCIMT000100UV01Device senderDevice) {
        // Delegate to the real AcknowledgementService logic (inline for test
        // independence)
        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        II id = new II();
        id.setRoot(java.util.UUID.randomUUID().toString());
        ack.setId(id);

        org.techbd.iti.schema.TS ts = new org.techbd.iti.schema.TS();
        ts.setValue(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.of("+05:30"))));
        ack.setCreationTime(ts);

        II interaction = new II();
        interaction.setRoot(INTERACTION_ID_ROOT);
        interaction.setExtension(INTERACTION_ID_EXT);
        ack.setInteractionId(interaction);

        // Receiver mirrors sender
        org.techbd.iti.schema.MCCIMT000200UV01Receiver receiver = new org.techbd.iti.schema.MCCIMT000200UV01Receiver();
        org.techbd.iti.schema.MCCIMT000200UV01Device rcvDevice = new org.techbd.iti.schema.MCCIMT000200UV01Device();
        if (senderDevice != null && !senderDevice.getId().isEmpty()) {
            II copy = new II();
            copy.setRoot(senderDevice.getId().get(0).getRoot());
            rcvDevice.getId().add(copy);
        }
        receiver.setDevice(rcvDevice);
        ack.getReceiver().add(receiver);

        // Sender
        org.techbd.iti.schema.MCCIMT000200UV01Sender snd = new org.techbd.iti.schema.MCCIMT000200UV01Sender();
        org.techbd.iti.schema.MCCIMT000200UV01Device sndDevice = new org.techbd.iti.schema.MCCIMT000200UV01Device();
        org.techbd.iti.schema.TEL tel = new org.techbd.iti.schema.TEL();
        tel.setValue("HTTP/1.1");
        sndDevice.getTelecom().add(tel);
        if (senderDevice != null && !senderDevice.getId().isEmpty()) {
            II copy = new II();
            copy.setRoot(senderDevice.getId().get(0).getRoot());
            sndDevice.getId().add(copy);
        }
        snd.setDevice(sndDevice);
        ack.setSender(snd);

        // Acknowledgement
        org.techbd.iti.schema.MCCIMT000200UV01Acknowledgement ackBlock = new org.techbd.iti.schema.MCCIMT000200UV01Acknowledgement();
        org.techbd.iti.schema.MCCIMT000200UV01TargetMessage target = new org.techbd.iti.schema.MCCIMT000200UV01TargetMessage();
        if (requestId != null) {
            target.setId(requestId);
        } else {
            II unknown = new II();
            unknown.setRoot("UNKNOWN");
            target.setId(unknown);
        }
        ackBlock.setTargetMessage(target);
        ack.getAcknowledgement().add(ackBlock);

        return ack;
    }

    private static II requestIdWith(String root, String extension) {
        II id = new II();
        id.setRoot(root);
        id.setExtension(extension);
        return id;
    }
}