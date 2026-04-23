package org.techbd.ingest.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.w3c.dom.Element;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.w3c.dom.Document;

@ExtendWith(MockitoExtension.class)
public class SoapFaultEnhancementFilterTest {

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    @Mock
    private MessageProcessorService messageProcessorService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private SoapFaultEnhancementFilter filter;

    @BeforeEach
    void setUp() {
        when(appLogger.getLogger(any())).thenReturn(templateLogger);
        filter = new SoapFaultEnhancementFilter(appLogger, messageProcessorService);
    }

    /**
     * Case 1: Non /ws endpoint → should skip processing
     */
    @Test
    void shouldSkipNonWsRequest() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(messageProcessorService);
    }

    /**
     * Case 2: SOAP fault response → should enhance and process
     */
    @Test
    void shouldEnhanceSoapFaultAndProcess() throws Exception {

        String soapFault = "<Envelope><Body><Fault><faultcode>500</faultcode><faultstring>Error</faultstring></Fault></Body></Envelope>";

        when(request.getRequestURI()).thenReturn("/ws/test");
        when(request.getInputStream()).thenReturn(
                new jakarta.servlet.ServletInputStream() {
                    private final ByteArrayInputStream input = new ByteArrayInputStream("<req/>".getBytes());

                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    }
                });

        // Mock response output stream
        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        when(response.getOutputStream()).thenReturn(outputStream);

        // Simulate filterChain writing SOAP fault
        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.getOutputStream().write(soapFault.getBytes());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(templateLogger).info(contains("Detected SOAP fault"));
        verify(messageProcessorService, atLeastOnce())
                .processMessage(any(), anyString(), anyString());
    }

    /**
     * Case 3: Normal response → should pass through without processing
     */
    @Test
    void shouldPassThroughNonFaultResponse() throws Exception {

        String normalResponse = "<Envelope><Body>SUCCESS</Body></Envelope>";

        when(request.getRequestURI()).thenReturn("/ws/test");
        when(request.getInputStream()).thenReturn(
                new jakarta.servlet.ServletInputStream() {
                    private final ByteArrayInputStream input = new ByteArrayInputStream("<req/>".getBytes());

                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    }
                });

        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        when(response.getOutputStream()).thenReturn(outputStream);

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.getOutputStream().write(normalResponse.getBytes());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(messageProcessorService, never()).processMessage(any(), any(), any());
    }

    /**
     * Case 4: Exception handling → should not break response
     */
    @Test
    void shouldHandleExceptionGracefully() throws Exception {

        when(request.getRequestURI()).thenReturn("/ws/test");

        when(request.getInputStream()).thenReturn(
                new jakarta.servlet.ServletInputStream() {
                    private final ByteArrayInputStream input = new ByteArrayInputStream("<req/>".getBytes());

                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    }
                });

        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new RuntimeException("boom"))
                .when(filterChain).doFilter(any(), any());
        filter.doFilterInternal(request, response, filterChain);
        verify(templateLogger).error(
                contains("Error processing response"),
                any(),
                any());
    }

    @Test
    void shouldHandleExceptionInSetIngestionFailedFlag() throws Exception {

        String soapFault = "<Envelope><Body><Fault>"
                + "<faultcode>500</faultcode>"
                + "<faultstring>Error</faultstring>"
                + "</Fault></Body></Envelope>";

        when(request.getRequestURI()).thenReturn("/ws/test");

        lenient().when(request.getAttribute(anyString())).thenReturn(null);

        when(request.getInputStream()).thenReturn(
                new jakarta.servlet.ServletInputStream() {
                    private final ByteArrayInputStream input = new ByteArrayInputStream("<req/>".getBytes());

                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    }
                });

        RequestContext context = mock(RequestContext.class);

        doThrow(new RuntimeException("fail"))
                .when(context).setIngestionFailed(true);

        when(request.getAttribute(Constants.REQUEST_CONTEXT)).thenReturn(context);

        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        when(response.getOutputStream()).thenReturn(outputStream);

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.getOutputStream().write(soapFault.getBytes());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(templateLogger).warn(
                contains("Failed to set ingestionFailed flag"),
                any());
    }

    @Test
    void shouldExtractFaultString_fromSoap12() throws Exception {

        String soapFault = "<Envelope xmlns=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<Body><Fault>"
                + "<Code><Value>500</Value></Code>"
                + "<Reason><Text>SOAP 1.2 Error</Text></Reason>"
                + "</Fault></Body></Envelope>";

        when(request.getRequestURI()).thenReturn("/ws/test");

        when(request.getInputStream()).thenReturn(new jakarta.servlet.ServletInputStream() {
            private final ByteArrayInputStream input = new ByteArrayInputStream("<req/>".getBytes());

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(jakarta.servlet.ReadListener readListener) {
            }
        });

        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        when(response.getOutputStream()).thenReturn(outputStream);

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.getOutputStream().write(soapFault.getBytes());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(templateLogger).error(
                contains("Enhancing SOAP fault"),
                any(), any(), any(),
                eq("SOAP 1.2 Error"));
    }

    @Test
    void shouldReturnUnknownFault_whenNoFaultStringPresent() throws Exception {

        String soapFault = "<Envelope><Body><Fault>"
                + "<faultcode>500</faultcode>"
                + "</Fault></Body></Envelope>";

        when(request.getRequestURI()).thenReturn("/ws/test");

        when(request.getInputStream()).thenReturn(new jakarta.servlet.ServletInputStream() {
            private final ByteArrayInputStream input = new ByteArrayInputStream("<req/>".getBytes());

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(jakarta.servlet.ReadListener readListener) {
            }
        });

        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        when(response.getOutputStream()).thenReturn(outputStream);

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.getOutputStream().write(soapFault.getBytes());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(templateLogger).error(
                contains("Enhancing SOAP fault"),
                any(), any(), any(),
                eq("Unknown fault"));
    }

    @Test
    void shouldReturnTrue_whenElementExistsWithNamespace() throws Exception {

        String xml = "<detail xmlns:err=\"http://test\"><err:ErrorTraceId>123</err:ErrorTraceId></detail>";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // IMPORTANT

        Document doc = factory
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));

        Element detail = doc.getDocumentElement();

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("hasDetailElement", Element.class, String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(filter, detail, "ErrorTraceId");

        assertTrue(result);
    }

    @Test
    void shouldReturnFalse_andLog_whenExceptionOccurs() throws Exception {

        Element element = mock(Element.class);

        when(element.getElementsByTagNameNS(any(), any()))
                .thenThrow(new RuntimeException("boom"));

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("hasDetailElement", Element.class, String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(filter, element, "ErrorTraceId");

        assertFalse(result);

        verify(templateLogger).debug(
                contains("Error checking for detail element"),
                any(),
                any());
    }

    @Test
    void shouldSkipProcessing_whenRequestBodyIsEmpty() throws Exception {

        // Mock request
        when(request.getAttribute(Constants.REQUEST_CONTEXT)).thenReturn(null);
        when(request.getAttribute("CAPTURED_HEADERS")).thenReturn(null);
        when(request.getAttribute(Constants.INTERACTION_ID)).thenReturn("test-id"); // 🔥 ADD THIS

        // No headers
        when(request.getAttribute("CAPTURED_HEADERS")).thenReturn(null);

        // IMPORTANT: empty body
        String requestBody = "";
        String enhancedFault = "<fault/>";

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("processEnhancedFault",
                        HttpServletRequest.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(filter, request, requestBody, enhancedFault);

        verify(templateLogger).warn(
                contains("Request body is empty - skipping messageProcessorService"),
                any());

        verify(templateLogger).info(
                contains("Enhanced fault with error trace ID will still be returned to client"));

        verify(messageProcessorService, never())
                .processMessage(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldExtractHeadersSuccessfully() throws Exception {

        Enumeration<String> headerNames = Collections.enumeration(List.of("h1", "h2"));

        when(request.getHeaderNames()).thenReturn(headerNames);
        when(request.getHeader("h1")).thenReturn("v1");
        when(request.getHeader("h2")).thenReturn("v2");

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("extractHeaders", HttpServletRequest.class);
        method.setAccessible(true);

        Map<String, String> result = (Map<String, String>) method.invoke(filter, request);

        assertEquals(2, result.size());
        assertEquals("v1", result.get("h1"));
        assertEquals("v2", result.get("h2"));

        verify(templateLogger).info(
                contains("Extracted"),
                eq(2));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnEmptyMap_andLogWarning_whenExceptionOccurs() throws Exception {

        when(request.getHeaderNames()).thenThrow(new RuntimeException("boom"));

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("extractHeaders", HttpServletRequest.class);
        method.setAccessible(true);

        Map<String, String> result = (Map<String, String>) method.invoke(filter, request);

        assertTrue(result.isEmpty());

        verify(templateLogger).warn(
                contains("Failed to extract headers"),
                any());
    }

    @Test
    void shouldReturnSoapPnr_whenPNRDetected() throws Exception {

        String msg = "<ProvideAndRegisterDocumentSetRequest>";

        MessageSourceType result = invokeDetermine(msg);

        assertEquals(MessageSourceType.SOAP_PNR, result);
    }

    @Test
    void shouldReturnSoapPix_whenPixAddDetected() throws Exception {

        String msg = "PRPA_IN201301UV02";

        MessageSourceType result = invokeDetermine(msg);

        assertEquals(MessageSourceType.SOAP_PIX, result);
    }

    @Test
    void shouldReturnSoapPix_whenPixQueryDetected() throws Exception {

        String msg = "PRPA_IN201309UV02";

        MessageSourceType result = invokeDetermine(msg);

        assertEquals(MessageSourceType.SOAP_PIX, result);
    }

    @Test
    void shouldReturnSoapPix_whenPixUpdateDetected() throws Exception {

        String msg = "PRPA_IN201302UV02";

        MessageSourceType result = invokeDetermine(msg);

        assertEquals(MessageSourceType.SOAP_PIX, result);
    }

    @Test
    void extractInteractionId_shouldReturnHeaderValue_whenHeaderPresentAndNotBlank() throws Exception {

        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getHeader(Constants.HEADER_INTERACTION_ID))
                .thenReturn("12345");

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("extractInteractionId", HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(new SoapFaultEnhancementFilter(appLogger, messageProcessorService),
                request);

        assertEquals("12345", result);
    }

    @Test
    void shouldReturnExistingDetailElement_soap11w() throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element fault = doc.createElement("Fault");
        doc.appendChild(fault);

        Element detail = doc.createElement("detail");
        fault.appendChild(detail);

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("findOrCreateDetailElement", Document.class, Element.class);
        method.setAccessible(true);

        Element result = (Element) method.invoke(new SoapFaultEnhancementFilter(appLogger, messageProcessorService),
                doc, fault);

        assertSame(detail, result);
    }

    @Test
    void shouldReturnExistingDetailElement_soap12() throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // IMPORTANT
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        String soapNS = "http://www.w3.org/2003/05/soap-envelope";

        Element fault = doc.createElementNS(soapNS, "Fault");
        doc.appendChild(fault);

        Element detail = doc.createElementNS(soapNS, "Detail");
        fault.appendChild(detail);

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("findOrCreateDetailElement", Document.class, Element.class);
        method.setAccessible(true);

        Element result = (Element) method.invoke(new SoapFaultEnhancementFilter(appLogger, messageProcessorService),
                doc, fault);

        assertSame(detail, result);
    }

    @Test
    void extractSourceId_shouldReturnSourceId_whenIngestPattern() throws Exception {

        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/ingest/ABC123/type");

        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("extractSourceId", HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(new SoapFaultEnhancementFilter(appLogger, messageProcessorService),
                request);

        assertEquals("ABC123", result);
    }

    private MessageSourceType invokeDetermine(String input) throws Exception {
        Method method = SoapFaultEnhancementFilter.class
                .getDeclaredMethod("determineMessageSourceType", String.class);
        method.setAccessible(true);

        return (MessageSourceType) method.invoke(filter, input);
    }
}
