package org.techbd.ingest.exceptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ws.soap.SoapFault;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;

import javax.xml.namespace.QName;

public class CustomSoapFaultResolverTest {
    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    @Mock
    private SoapFault soapFault;

    @Mock
    private SoapFaultDetail faultDetail;

    @Mock
    private TransportContext transportContext;

    @Mock
    private HttpServletConnection connection;

    @Mock
    private HttpServletRequest request;

    private CustomSoapFaultResolver resolver;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        when(appLogger.getLogger(CustomSoapFaultResolver.class))
                .thenReturn(templateLogger);

        resolver = new CustomSoapFaultResolver(appLogger);

        TransportContextHolder.setTransportContext(transportContext);
        when(transportContext.getConnection()).thenReturn(connection);
        when(connection.getHttpServletRequest()).thenReturn(request);

        when(soapFault.getFaultDetail()).thenReturn(faultDetail);
    }

    @Test
    void shouldCustomizeFault_successfully() {

        SoapFaultDetailElement elementMock = mock(SoapFaultDetailElement.class);

        when(soapFault.getFaultStringOrReason()).thenReturn("Some error");
        when(soapFault.getFaultCode()).thenReturn(new QName("500"));

        when(request.getHeader(Constants.HEADER_INTERACTION_ID))
                .thenReturn("INT-123");

        when(soapFault.getFaultDetail()).thenReturn(faultDetail);

        when(faultDetail.addFaultDetailElement(any()))
                .thenReturn(elementMock);

        resolver.customizeFault(null, new RuntimeException("boom"), soapFault);

        verify(templateLogger).error(
                contains("SOAP Fault occurred"),
                eq("INT-123"),
                any(),
                any(),
                any());

        verify(templateLogger).info(
                contains("Added error trace details"),
                any(),
                any(),
                any());
    }

    @Test
    void shouldHandleMustUnderstandFault() {

        when(soapFault.getFaultStringOrReason())
                .thenReturn("MustUnderstand header missing");

        when(soapFault.getFaultCode()).thenReturn(new QName("500"));
        when(request.getHeader(Constants.HEADER_INTERACTION_ID))
                .thenReturn("INT-456");

        resolver.customizeFault(null, new RuntimeException("error"), soapFault);

        verify(templateLogger).warn(
                contains("MustUnderstand fault detected"),
                eq("INT-456"),
                any());
    }

    @Test
    void shouldReturnUnknown_whenNoInteractionId() {

        when(request.getHeader(Constants.HEADER_INTERACTION_ID))
                .thenReturn(null);
        when(request.getAttribute(Constants.INTERACTION_ID))
                .thenReturn(null);

        when(soapFault.getFaultStringOrReason()).thenReturn("Error");
        when(soapFault.getFaultCode()).thenReturn(new QName("500"));

        resolver.customizeFault(null, new RuntimeException("boom"), soapFault);

        verify(templateLogger).error(
                contains("SOAP Fault occurred"),
                eq("unknown"),
                any(),
                any(),
                any());
    }

    @Test
    void shouldSetIngestionFailedFlag_whenContextExists() {

        RequestContext context = mock(RequestContext.class);

        when(request.getAttribute(Constants.REQUEST_CONTEXT))
                .thenReturn(context);

        when(soapFault.getFaultStringOrReason()).thenReturn("Error");
        when(soapFault.getFaultCode()).thenReturn(new QName("500"));

        resolver.customizeFault(null, new RuntimeException("boom"), soapFault);

        verify(context).setIngestionFailed(true);
    }

    @Test
    void shouldLogDebug_whenContextIsNull() {

        when(request.getAttribute(Constants.REQUEST_CONTEXT))
                .thenReturn(null);

        when(soapFault.getFaultStringOrReason()).thenReturn("Error");
        when(soapFault.getFaultCode()).thenReturn(new QName("500"));

        resolver.customizeFault(null, new RuntimeException("boom"), soapFault);

        verify(templateLogger).debug(
                contains("RequestContext not yet available"),
                any());
    }

    @Test
    void shouldHandleException_inSetIngestionFailedFlag() {

        when(transportContext.getConnection()).thenThrow(new RuntimeException("fail"));

        when(soapFault.getFaultStringOrReason()).thenReturn("Error");
        when(soapFault.getFaultCode()).thenReturn(new QName("500"));

        resolver.customizeFault(null, new RuntimeException("boom"), soapFault);

        verify(templateLogger).warn(
                contains("Failed to set ingestionFailed flag"),
                any());
    }

    @Test
    void shouldHandleException_inCustomizeFault() {

        when(soapFault.getFaultStringOrReason()).thenThrow(new RuntimeException("fail"));

        resolver.customizeFault(null, new RuntimeException("boom"), soapFault);

        verify(templateLogger).error(
                contains("Failed to customize SOAP fault"),
                any(),
                any(),
                any(),
                any());
    }
}
