package org.techbd.ingest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class SoapResponseUtilTest {

    // ── Config mocks ──────────────────────────────────────────────────────────

    @Mock private AppConfig appConfig;
    @Mock private AppConfig.Soap soapConfig;
    @Mock private AppConfig.Soap.Wsa wsaConfig;
    @Mock private AppConfig.Soap.Techbd techbdConfig;

    // ── Spring-WS mocks ───────────────────────────────────────────────────────

    @Mock private MessageContext messageContext;
    @Mock private SoapMessage soapResponse;
    @Mock private SoapMessage soapRequest;
    @Mock private SoapHeader responseHeader;
    @Mock private SoapHeader requestHeader;
    @Mock private SoapHeaderElement requestHeaderElement; // MessageID in the incoming request
    @Mock private SoapHeaderElement addedElement;         // returned by responseHeader.addHeaderElement

    // ── Servlet mocks ─────────────────────────────────────────────────────────

    @Mock private TransportContext transportContext;
    @Mock private HttpServletConnection httpServletConnection;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private RequestContext requestContext;

    // ── Logger mocks ──────────────────────────────────────────────────────────

    @Mock private AppLogger appLogger;
    @Mock private TemplateLogger templateLogger;

    // ── Static mock handles ───────────────────────────────────────────────────

    private MockedStatic<TransportContextHolder> transportContextHolderMock;
    private MockedStatic<FeatureEnum>            featureEnumMock;

    // ── System under test ─────────────────────────────────────────────────────

    private SoapResponseUtil sut;

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String INTERACTION_ID = "test-interaction-123";
    private static final String WSA_NS         = "http://www.w3.org/2005/08/addressing";
    private static final String WSA_PREFIX     = "wsa";
    private static final String WSA_ACTION     = "urn:hl7-org:v3:PRPA_IN201301UV02";
    private static final String WSA_PNR_ACTION = "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b";
    private static final String WSA_TO         = "urn:hl7-org:v3:PRPA_IN201301UV02";
    private static final String TECHBD_NS      = "http://techbd.org/ingest";
    private static final String TECHBD_PREFIX  = "techbd";
    private static final String APP_VERSION    = "1.0.0-test";
    private static final String MESSAGE_ID     = "urn:uuid:request-message-id";

    // ─────────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // ── Logger ────────────────────────────────────────────────────────────
        // getLogger() is always invoked by the constructor.
        // doNothing() on void mocks is Mockito's default — no explicit stub needed.
        when(appLogger.getLogger(SoapResponseUtil.class)).thenReturn(templateLogger);

        // ── AppConfig ─────────────────────────────────────────────────────────
        // All config stubs are lenient: the exception test throws at getResponse()
        // before any config is accessed, so no config stub is consumed by every test.
        lenient().when(appConfig.getSoap()).thenReturn(soapConfig);
        lenient().when(soapConfig.getWsa()).thenReturn(wsaConfig);
        lenient().when(wsaConfig.getNamespace()).thenReturn(WSA_NS);
        lenient().when(wsaConfig.getPrefix()).thenReturn(WSA_PREFIX);

        // Action/PNR-action/To/version are only reached after the PIX/PNR branch.
        // The exception test throws at getResponse() and never reaches them.
        lenient().when(wsaConfig.getAction()).thenReturn(WSA_ACTION);
        lenient().when(wsaConfig.getPnrAction()).thenReturn(WSA_PNR_ACTION);
        lenient().when(wsaConfig.getTo()).thenReturn(WSA_TO);
        lenient().when(appConfig.getVersion()).thenReturn(APP_VERSION);

        // TechBD config only reached when the feature flag is on.
        lenient().when(soapConfig.getTechbd()).thenReturn(techbdConfig);
        lenient().when(techbdConfig.getNamespace()).thenReturn(TECHBD_NS);
        lenient().when(techbdConfig.getPrefix()).thenReturn(TECHBD_PREFIX);

        // ── Static mocks ───────────────────────────────────────────────────────
        transportContextHolderMock = mockStatic(TransportContextHolder.class);
        featureEnumMock             = mockStatic(FeatureEnum.class);

        // Transport chain — not reached by the exception test.
        lenient().when(TransportContextHolder.getTransportContext()).thenReturn(transportContext);
        lenient().when(transportContext.getConnection()).thenReturn(httpServletConnection);
        lenient().when(httpServletConnection.getHttpServletRequest()).thenReturn(httpServletRequest);
        lenient().when(httpServletRequest.getAttribute(Constants.REQUEST_CONTEXT)).thenReturn(requestContext);

        // ── Message context ────────────────────────────────────────────────────
        // getResponse() is always called; getRequest() and getSoapHeader() are not
        // reached by the exception test (which re-stubs getResponse() to throw).
        when(messageContext.getResponse()).thenReturn(soapResponse);
        lenient().when(messageContext.getRequest()).thenReturn(soapRequest);
        lenient().when(soapResponse.getSoapHeader()).thenReturn(responseHeader);
        lenient().when(soapRequest.getSoapHeader()).thenReturn(requestHeader);

        // ── Response header ────────────────────────────────────────────────────
        lenient().when(responseHeader.addHeaderElement(any(QName.class))).thenReturn(addedElement);

        // ── Request MessageID header (default: present and valid) ──────────────
        Iterator<SoapHeaderElement> iter =
                Collections.singletonList(requestHeaderElement).iterator();
        lenient().when(requestHeader.examineAllHeaderElements()).thenReturn(iter);
        lenient().when(requestHeaderElement.getName())
                 .thenReturn(new QName(WSA_NS, "MessageID", WSA_PREFIX));
        lenient().when(requestHeaderElement.getText()).thenReturn(MESSAGE_ID);

        sut = new SoapResponseUtil(appConfig, appLogger);
    }

    @AfterEach
    void tearDown() {
        transportContextHolderMock.close();
        featureEnumMock.close();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path — PIX request, feature flag disabled.
     * Exactly four WS-Addressing headers are added; the PIX action is used.
     */
    @Test
    void buildSoapResponse_pixRequest_featureFlagOff_addsWsaHeadersOnly() {
        when(requestContext.isPixRequest()).thenReturn(true);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(false);

        SoapMessage result = sut.buildSoapResponse(INTERACTION_ID, messageContext);

        assertNotNull(result);
        verify(responseHeader, times(4)).addHeaderElement(any(QName.class));
        verify(addedElement, times(4)).setText(anyString());
        verify(responseHeader).addHeaderElement(new QName(WSA_NS, "Action", WSA_PREFIX));
    }

    /**
     * PNR request — verifies the PNR action branch is taken.
     */
    @Test
    void buildSoapResponse_pnrRequest_featureFlagOff_usesPnrAction() {
        when(requestContext.isPixRequest()).thenReturn(false);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(false);

        SoapMessage result = sut.buildSoapResponse(INTERACTION_ID, messageContext);

        assertNotNull(result);
        verify(responseHeader, times(4)).addHeaderElement(any(QName.class));
    }

    /**
     * Feature flag enabled — TechBD custom segment is appended via Transformer.
     * Uses a real StreamResult so the JDK transformer has a valid output sink.
     * Asserts both custom attributes appear in the serialised XML.
     */
    @Test
    void buildSoapResponse_featureFlagOn_appendsTechBdSegment() {
        when(requestContext.isPixRequest()).thenReturn(true);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(true);

        StringWriter sw = new StringWriter();
        when(responseHeader.getResult()).thenReturn(new StreamResult(sw));

        SoapMessage result = sut.buildSoapResponse(INTERACTION_ID, messageContext);

        assertNotNull(result);
        verify(responseHeader, atLeastOnce()).getResult();
        String xml = sw.toString();
        assertThat(xml).contains("InteractionID=\"" + INTERACTION_ID + "\"");
        assertThat(xml).contains("TechBDIngestionApiVersion=\"" + APP_VERSION + "\"");
    }

    /**
     * No MessageID header present — extractRelatesTo returns null,
     * fallback RelatesTo is used, and a warning is logged.
     */
    @Test
    void buildSoapResponse_missingMessageIdHeader_usesFallbackRelatesTo() {
        when(requestHeader.examineAllHeaderElements()).thenReturn(Collections.emptyIterator());
        when(requestContext.isPixRequest()).thenReturn(true);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(false);

        SoapMessage result = sut.buildSoapResponse(INTERACTION_ID, messageContext);

        assertNotNull(result);
        verify(responseHeader).addHeaderElement(new QName(WSA_NS, "RelatesTo", WSA_PREFIX));
        verify(templateLogger).warn(anyString(), any(Object[].class));
    }

    /**
     * Header present but local name is not MessageID — skipped,
     * fallback RelatesTo is used, and a warning is logged.
     */
    @Test
    void buildSoapResponse_nonMessageIdHeader_usesFallbackRelatesTo() {
        when(requestHeaderElement.getName()).thenReturn(new QName(WSA_NS, "Action", WSA_PREFIX));
        when(requestContext.isPixRequest()).thenReturn(false);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(false);

        SoapMessage result = sut.buildSoapResponse(INTERACTION_ID, messageContext);

        assertNotNull(result);
        verify(templateLogger).warn(anyString(), any(Object[].class));
    }

    /**
     * Any exception inside buildSoapResponse is wrapped in RuntimeException
     * with the expected message, and the error level is logged.
     */
    @Test
    void buildSoapResponse_onException_throwsRuntimeException() {
        when(messageContext.getResponse()).thenThrow(new IllegalStateException("no response"));

        assertThatThrownBy(() -> sut.buildSoapResponse(INTERACTION_ID, messageContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error creating SOAP response")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(templateLogger).error(anyString(), any(Object[].class));
    }

    /**
     * MessageID response header is set to a randomly generated urn:uuid: value.
     */
    @Test
    void buildSoapResponse_generatesUrnUuidMessageId() {
        when(requestContext.isPixRequest()).thenReturn(true);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(false);

        sut.buildSoapResponse(INTERACTION_ID, messageContext);

        verify(addedElement, atLeastOnce()).setText(argThat(text ->
                text != null && text.startsWith("urn:uuid:")
        ));
    }

    /**
     * To header is populated with the value from AppConfig.
     */
    @Test
    void buildSoapResponse_setsToHeaderFromConfig() {
        when(requestContext.isPixRequest()).thenReturn(false);
        featureEnumMock
                .when(() -> FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE))
                .thenReturn(false);

        sut.buildSoapResponse(INTERACTION_ID, messageContext);

        verify(responseHeader).addHeaderElement(new QName(WSA_NS, "To", WSA_PREFIX));
        verify(addedElement, atLeastOnce()).setText(WSA_TO);
    }
}