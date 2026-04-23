package org.techbd.ingest.interceptors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.feature.FeatureEnum;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import javax.xml.namespace.QName;
import java.util.Arrays;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;

@ExtendWith(MockitoExtension.class)
public class WsaHeaderInterceptorTest {
    @Mock
    private SoapResponseUtil soapResponseUtil;

    @Mock
    private MessageProcessorService messageProcessorService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    @Mock
    private MessageContext messageContext;

    @Mock
    private SoapMessage soapMessage;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private WsaHeaderInterceptor interceptor;

    @BeforeEach
    void setup() {
        when(appLogger.getLogger(any())).thenReturn(templateLogger);
        interceptor = new WsaHeaderInterceptor(
                soapResponseUtil,
                messageProcessorService,
                appConfig,
                appLogger);
    }

    @Test
    void shouldHandleRequest_forWsEndpoint() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getRequest()).thenReturn(soapMessage);

        doAnswer(invocation -> {
            ByteArrayOutputStream os = invocation.getArgument(0);
            os.write("<soap>req</soap>".getBytes());
            return null;
        }).when(soapMessage).writeTo(any());

        boolean result = interceptor.handleRequest(messageContext, new Object());

        assertTrue(result);
        verify(messageContext).setProperty(
                eq(Constants.RAW_SOAP_ATTRIBUTE),
                eq("<soap>req</soap>"));
    }

    @Test
    void shouldSkipHandleRequest_whenNotWsEndpoint() throws Exception {

        mockTransportContext("/other");

        boolean result = interceptor.handleRequest(messageContext, new Object());

        assertTrue(result);
        verify(messageContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldHandleResponse_successfully() throws Exception {

        mockTransportContext("/ws");

        when(request.getAttribute(Constants.INTERACTION_ID)).thenReturn("INT-1");

        when(soapResponseUtil.buildSoapResponse(any(), any()))
                .thenReturn(soapMessage);

        when(messageContext.getProperty(Constants.RAW_SOAP_ATTRIBUTE))
                .thenReturn("<req/>");

        when(request.getAttribute(Constants.REQUEST_CONTEXT))
                .thenReturn(mock(RequestContext.class));

        boolean result = interceptor.handleResponse(messageContext, new Object());

        assertTrue(result);

        verify(messageProcessorService).processMessage(
                any(),
                eq("<req/>"),
                any());
    }

    @Test
    void shouldHandleFault_andLogError() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getResponse()).thenReturn(soapMessage);
        when(messageContext.getProperty("interactionId")).thenReturn("INT-2");

        doAnswer(invocation -> {
            ByteArrayOutputStream os = invocation.getArgument(0);
            os.write("<fault><faultstring>Error</faultstring></fault>".getBytes());
            return null;
        }).when(soapMessage).writeTo(any());

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logMock = mockStatic(LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-1");

            boolean result = interceptor.handleFault(messageContext, new Object());

            assertTrue(result);

            verify(templateLogger).error(
                    contains("SOAP Fault encountered"),
                    eq("INT-2"),
                    eq("TRACE-1"),
                    any());
        }
    }

    @Test
    void shouldHandleAfterCompletion_success() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty("interactionId")).thenReturn("INT-3");

        interceptor.afterCompletion(messageContext, new Object(), null);

        verify(templateLogger).info(
                contains("completed"),
                eq("INT-3"),
                eq("SUCCESS"));
    }

    @Test
    void shouldHandleAfterCompletion_withException() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty("interactionId")).thenReturn("INT-4");

        Exception ex = new RuntimeException("boom");

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logMock = mockStatic(LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-2");

            interceptor.afterCompletion(messageContext, new Object(), ex);

            verify(templateLogger).error(
                    contains("completed with ERROR"),
                    eq("INT-4"),
                    eq("TRACE-2"),
                    eq("boom"),
                    eq(ex));
        }
    }

    @Test
    void shouldReturnTrue_whenFeatureEnabled() {

        SoapHeaderElement header = mock(SoapHeaderElement.class);

        when(header.getName()).thenReturn(
                new QName("ns", "Header", "p"));

        try (MockedStatic<FeatureEnum> featureMock = mockStatic(FeatureEnum.class)) {

            featureMock.when(() -> FeatureEnum.isEnabled(FeatureEnum.IGNORE_MUST_UNDERSTAND_HEADERS)).thenReturn(true);

            assertTrue(interceptor.understands(header));
        }
    }

    @Test
    void shouldExtractFaultString() throws Exception {

        Method method = WsaHeaderInterceptor.class
                .getDeclaredMethod("extractFaultString", String.class);

        method.setAccessible(true);

        String xml = "<fault><faultstring>Error123</faultstring></fault>";

        String result = (String) method.invoke(interceptor, xml);

        assertEquals("Error123", result);
    }

    @Test
    void shouldReturnFalse_whenNoDetailElements() throws Exception {

        SoapFaultDetail detail = mock(SoapFaultDetail.class);

        when(detail.getDetailEntries())
                .thenReturn(Collections.emptyIterator());

        Method method = WsaHeaderInterceptor.class
                .getDeclaredMethod("hasDetailElement", SoapFaultDetail.class, String.class);

        method.setAccessible(true);

        boolean result = (boolean) method.invoke(interceptor, detail, "ErrorTraceId");

        assertFalse(result);
    }

    @Test
    void shouldInjectErrorTraceIdIntoFault_whenNotPresent() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty(Constants.INTERACTION_ID))
                .thenReturn("INT-1");

        SoapBody soapBody = mock(SoapBody.class);
        SoapFault soapFault = mock(SoapFault.class);
        SoapFaultDetail faultDetail = mock(SoapFaultDetail.class);

        when(messageContext.getResponse()).thenReturn(soapMessage);
        when(soapMessage.getSoapBody()).thenReturn(soapBody);
        when(soapBody.getFault()).thenReturn(soapFault);
        when(soapFault.getFaultDetail()).thenReturn(null);
        when(soapFault.addFaultDetail()).thenReturn(faultDetail);

        SoapFaultDetailElement interactionElement = mock(SoapFaultDetailElement.class);
        SoapFaultDetailElement traceElement = mock(SoapFaultDetailElement.class);

        when(faultDetail.addFaultDetailElement(any()))
                .thenReturn(interactionElement)
                .thenReturn(traceElement);

        try (MockedStatic<ErrorTraceIdGenerator> mockTrace = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logMock = mockStatic(LogUtil.class)) {

            mockTrace.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-1");

            interceptor.handleFault(messageContext, new Object());

            verify(interactionElement).addText("INT-1");
            verify(traceElement).addText("TRACE-1");
        }
    }

    @Test
    void shouldSkipInjection_whenAlreadyPresent() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty(Constants.INTERACTION_ID))
                .thenReturn("INT-1");

        SoapBody soapBody = mock(SoapBody.class);
        SoapFault soapFault = mock(SoapFault.class);
        SoapFaultDetail faultDetail = mock(SoapFaultDetail.class);

        when(messageContext.getResponse()).thenReturn(soapMessage);
        when(soapMessage.getSoapBody()).thenReturn(soapBody);
        when(soapBody.getFault()).thenReturn(soapFault);
        when(soapFault.getFaultDetail()).thenReturn(faultDetail);

        Iterator<SoapFaultDetailElement> iterator = Arrays.asList(
                mockDetailElement("InteractionId"),
                mockDetailElement("ErrorTraceId")).iterator();

        when(faultDetail.getDetailEntries()).thenReturn(iterator);

        interceptor.handleFault(messageContext, new Object());

        // No new elements added
        verify(faultDetail, never()).addFaultDetailElement(any());
    }

    @Test
    void shouldExtractFaultString_fromSoap12() throws Exception {

        String xml = "<soap:Text xml:lang=\"en\">Invalid request</soap:Text>";

        String result = invokeExtractFaultString(xml);

        assertEquals("Invalid request", result);
    }

    @Test
    void shouldReturnUnknown_whenNoFaultStringPresent() throws Exception {

        String xml = "<soap>No fault here</soap>";

        String result = invokeExtractFaultString(xml);

        assertEquals("Unknown fault", result);
    }

    @Test
    void shouldReturnUnknown_whenIncompleteFaultString() throws Exception {

        String xml = "<faultstring>Error only start tag";

        String result = invokeExtractFaultString(xml);

        assertEquals("Unknown fault", result);
    }

    @Test
    void shouldReturnFailureMessage_whenExceptionOccurs() throws Exception {

        String result = invokeExtractFaultString(null);

        assertEquals("Failed to extract fault string", result);
    }

    @Test
    void shouldReturnTrue_whenNamespaceMatchesConfigured() {

        SoapHeaderElement header = mock(SoapHeaderElement.class);
        QName qName = new QName("http://test.com", "TestHeader");
        when(header.getName()).thenReturn(qName);

        AppConfig.Soap soap = mock(AppConfig.Soap.class);
        AppConfig.Soap.Wsa wsa = mock(AppConfig.Soap.Wsa.class);

        when(appConfig.getSoap()).thenReturn(soap);
        when(soap.getWsa()).thenReturn(wsa);
        when(wsa.getUnderstoodNamespaces())
                .thenReturn("http://test.com,http://other.com");

        try (MockedStatic<FeatureEnum> mockFeature = mockStatic(FeatureEnum.class)) {

            mockFeature.when(() -> FeatureEnum.isEnabled(FeatureEnum.IGNORE_MUST_UNDERSTAND_HEADERS)).thenReturn(false);

            boolean result = interceptor.understands(header);

            assertTrue(result);
        }
    }

    @Test
    void shouldSkipAfterCompletion_whenNotWsEndpoint() throws Exception {

        mockTransportContext("/not-ws"); // IMPORTANT

        interceptor.afterCompletion(messageContext, new Object(), null);

        verify(templateLogger, never()).info(any(), any(), any());
    }

    @Test
    void shouldOverrideContentType_whenAckContentTypePresent() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty(Constants.INTERACTION_ID))
                .thenReturn("INT-1");

        when(request.getAttribute(Constants.ACK_CONTENT_TYPE))
                .thenReturn("application/xml");

        HttpServletConnection connection = (HttpServletConnection) TransportContextHolder.getTransportContext()
                .getConnection();

        when(connection.getHttpServletResponse()).thenReturn(response);

        interceptor.afterCompletion(messageContext, new Object(), null);

        verify(response).setHeader("Content-Type", "application/xml");
        verify(response).setContentType("application/xml");

        verify(templateLogger).info(
                contains("Overriding response content type"),
                eq("application/xml"),
                eq("INT-1"));
    }

    @Test
    void shouldLogWarning_whenOverrideContentTypeFails() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty(Constants.INTERACTION_ID))
                .thenReturn("INT-1");

        when(request.getAttribute(Constants.ACK_CONTENT_TYPE))
                .thenReturn("application/xml");

        HttpServletConnection connection = (HttpServletConnection) TransportContextHolder.getTransportContext()
                .getConnection();

        when(connection.getHttpServletResponse()).thenReturn(response);

        doThrow(new RuntimeException("fail"))
                .when(response).setHeader(any(), any());

        interceptor.afterCompletion(messageContext, new Object(), null);

        verify(templateLogger).warn(
                contains("Failed to override response content type"),
                eq("INT-1"),
                contains("fail"));
    }

    @Test
    void shouldSkipHandleFault_whenNotWsEndpoint() throws Exception {

        mockTransportContext("/not-ws");

        boolean result = interceptor.handleFault(messageContext, new Object());

        assertTrue(result);
        verify(messageContext, never()).getResponse();
        verify(templateLogger, never()).error(any(), any(), any(), any());
    }

    @Test
    void shouldSkipHandleResponse_whenNotWsEndpoint() throws Exception {

        mockTransportContext("/not-ws");

        boolean result = interceptor.handleResponse(messageContext, new Object());

        assertTrue(result);

        verifyNoInteractions(soapResponseUtil);
        verifyNoInteractions(messageProcessorService);
        verify(messageContext, never()).getProperty(any());
    }

    @Test
    void shouldUseRawSoapFromRequest_whenAvailable() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getRequest()).thenReturn(soapMessage);

        doAnswer(invocation -> {
            ByteArrayOutputStream os = invocation.getArgument(0);
            os.write("<soap>original</soap>".getBytes());
            return null;
        }).when(soapMessage).writeTo(any());

        when(request.getAttribute(Constants.RAW_SOAP_ATTRIBUTE))
                .thenReturn("<soap>from-request</soap>");

        boolean result = interceptor.handleRequest(messageContext, new Object());

        assertTrue(result);

        verify(messageContext).setProperty(
                Constants.RAW_SOAP_ATTRIBUTE,
                "<soap>from-request</soap>");

        verify(templateLogger, never()).info(any(), any());
    }

    @Test
    void shouldReturnFalse_andLogWarning_whenExceptionOccursInIsWsEndpoint() throws Exception {

        TransportContext transportContext = mock(TransportContext.class);

        when(transportContext.getConnection())
                .thenThrow(new RuntimeException("boom"));

        TransportContextHolder.setTransportContext(transportContext);

        boolean result = interceptor.handleRequest(messageContext, new Object());

        assertTrue(result);

        verify(templateLogger).warn(
                contains("Error checking request URI"),
                eq("boom"));
    }

    @Test
    void shouldHandleException_inHandleFaultOuterCatch() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty(Constants.INTERACTION_ID))
                .thenReturn("INT-1");

        when(messageContext.getResponse()).thenReturn(soapMessage);

        doThrow(new RuntimeException("write-fail"))
                .when(soapMessage).writeTo(any());

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logMock = mockStatic(LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-2");

            boolean result = interceptor.handleFault(messageContext, new Object());

            assertTrue(result);

            verify(templateLogger).error(
                    contains("Exception while processing SOAP fault"),
                    eq("INT-1"),
                    eq("TRACE-2"),
                    contains("write-fail"),
                    any(Exception.class));

            logMock.verify(() -> LogUtil.logDetailedError(
                    eq(500),
                    contains("Exception while processing SOAP fault"),
                    eq("INT-1"),
                    eq("TRACE-2"),
                    any(Exception.class)));
        }
    }

    @Test
    void shouldGenerateErrorTraceId_whenNull_inOuterCatch() throws Exception {

        mockTransportContext("/ws");

        when(messageContext.getProperty(Constants.INTERACTION_ID))
                .thenReturn("INT-1");

        when(messageContext.getResponse())
                .thenThrow(new RuntimeException("early-fail"));

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logMock = mockStatic(LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-NULL");

            boolean result = interceptor.handleFault(messageContext, new Object());

            assertTrue(result);

            verify(templateLogger).error(
                    contains("Exception while processing SOAP fault"),
                    eq("INT-1"),
                    eq("TRACE-NULL"), // <-- generated in catch block
                    contains("early-fail"),
                    any(Exception.class));

            logMock.verify(() -> LogUtil.logDetailedError(
                    eq(500),
                    contains("Exception while processing SOAP fault"),
                    eq("INT-1"),
                    eq("TRACE-NULL"),
                    any(Exception.class)));
        }
    }

    private String invokeExtractFaultString(String xml) throws Exception {
        Method method = WsaHeaderInterceptor.class
                .getDeclaredMethod("extractFaultString", String.class);

        method.setAccessible(true);

        return (String) method.invoke(interceptor, xml);
    }

    private SoapFaultDetailElement mockDetailElement(String name) {
        SoapFaultDetailElement element = mock(SoapFaultDetailElement.class);
        QName qName = new QName("ns", name);
        when(element.getName()).thenReturn(qName);
        return element;
    }

    private void mockTransportContext(String uri) {

        HttpServletConnection connection = mock(HttpServletConnection.class);

        when(connection.getHttpServletRequest()).thenReturn(request);

        when(request.getRequestURI()).thenReturn(uri);

        TransportContext transportContext = mock(TransportContext.class);
        when(transportContext.getConnection()).thenReturn(connection);

        TransportContextHolder.setTransportContext(transportContext);
    }

}
