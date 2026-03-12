package org.techbd.ingest.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.config.annotation.WsConfigurationSupport;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapMessageFactory;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.interceptors.WsaHeaderInterceptor;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPMessage;

@Configuration
public class WebServiceConfig extends WsConfigurationSupport {

    private final SoapResponseUtil soapResponseUtil;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final AppLogger LOG;

    public WebServiceConfig(SoapResponseUtil soapResponseUtil, MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger) {
        this.soapResponseUtil = soapResponseUtil;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.LOG = appLogger;
    }

    @Override
    protected void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(new WsaHeaderInterceptor(soapResponseUtil, messageProcessorService, appConfig,LOG));
    }
   
    /**
     * WebServiceTemplate now uses a separate, simpler message factory
     * that can handle responses without requiring HTTP context.
     * Uses DYNAMIC protocol to auto-detect SOAP 1.1 vs 1.2 in responses.
     */
    @Bean
    public WebServiceTemplate webServiceTemplate() {
        SmartSoapMessageFactory messageFactory = new SmartSoapMessageFactory();
        messageFactory.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMessageFactory(messageFactory);
        return template;
    }

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        var servlet = new CustomMessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("org.techbd.iti.schema");
        return marshaller;
    }

    @Bean(name = "pix")
    public DefaultWsdl11Definition pixWsdl(@Qualifier("hl7Schema") XsdSchema hl7Schema) {
        var wsdlDefinition = new DefaultWsdl11Definition();
        wsdlDefinition.setPortTypeName("PIXPort");
        wsdlDefinition.setLocationUri("/ws");
        wsdlDefinition.setTargetNamespace("urn:hl7-org:v3");
        wsdlDefinition.setSchema(hl7Schema);
        return wsdlDefinition;
    }

    @Bean(name = "pnr")
    public DefaultWsdl11Definition pnrWsdl(@Qualifier("pnrSchema") XsdSchema pnrSchema) {
        var wsdlDefinition = new DefaultWsdl11Definition();
        wsdlDefinition.setPortTypeName("PNRPort");
        wsdlDefinition.setLocationUri("/ws");
        wsdlDefinition.setTargetNamespace("urn:ihe:iti:xds-b:2007");
        wsdlDefinition.setSchema(pnrSchema);
        return wsdlDefinition;
    }

    @Bean
    public XsdSchema hl7Schema() {
        return new SimpleXsdSchema(
                new ClassPathResource("ITI/schema/HL7V3/NE2008/multicacheschemas/PRPA_IN201301UV02.xsd"));
    }
    @Bean
    public XsdSchema pnrSchema() {
        return new SimpleXsdSchema(
            new ClassPathResource("ITI/schema/IHE/XDS.b_DocumentRepository.xsd")
        );
    }
  
    @Bean
    public SoapMessageFactory messageFactory() {
        return new SmartSoapMessageFactory();
    }

    static class SmartSoapMessageFactory extends SaajSoapMessageFactory {


        @Override
        public SaajSoapMessage createWebServiceMessage(InputStream inputStream) throws IOException {
            try {
                // Read the stream into bytes so we can inspect it
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                inputStream.transferTo(baos);
                byte[] bytes = baos.toByteArray();

                // Check for empty request body
                if (bytes == null || bytes.length == 0) {
                    throw new IOException("Empty request body - no SOAP message provided");
                }

                // Try to get HTTP context - it may or may not be available
                var transportContext = TransportContextHolder.getTransportContext();
                String httpContentType = null;
                
                // SERVER SCENARIO: HTTP context available (receiving requests at /ws)
                if (transportContext != null) {
                    try {
                        var connection = (org.springframework.ws.transport.http.HttpServletConnection) transportContext.getConnection();
                        httpContentType = connection.getHttpServletRequest().getContentType();
                    } catch (Exception e) {
                        // CLIENT SCENARIO: HTTP context not available or different type
                        // This happens when WebServiceTemplate is parsing responses
                        httpContentType = null;
                    }
                }

                // Handle MTOM multipart content (detected from HTTP header or content)
                if (httpContentType != null && httpContentType.contains("multipart/related")) {
                    MimeHeaders mimeHeaders = new MimeHeaders();
                    mimeHeaders.addHeader("Content-Type", httpContentType);
                    MessageFactory msgFactory = MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL);
                    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                    SOAPMessage soapMessage = msgFactory.createMessage(mimeHeaders, bis);
                    return new SaajSoapMessage(soapMessage);
                }
                
                // Regular SOAP handling - use DYNAMIC protocol to auto-detect version
                // This works whether HTTP context is available or not
                MimeHeaders headers = extractHeaders(bytes, httpContentType);
                MessageFactory msgFactory = MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL);
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                SOAPMessage soapMessage = msgFactory.createMessage(headers, bis);
                return new SaajSoapMessage(soapMessage);
                
            } catch (Exception e) {
                throw new IOException("Unable to create SOAP message: " + e.getMessage(), e);
            }
        }
 
        @Override
        public SaajSoapMessage createWebServiceMessage() {
            try {
                // Default to SOAP 1.1
                MessageFactory msgFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);

                // Check if HTTP request is available (server scenario)
                var transportContext = TransportContextHolder.getTransportContext();
                if (transportContext != null) {
                    try {
                        var connection = (org.springframework.ws.transport.http.HttpServletConnection) transportContext.getConnection();
                        String contentType = connection.getHttpServletRequest().getContentType();

                        if (contentType != null && contentType.contains("application/soap+xml")) {
                            // SOAP 1.2 request
                            msgFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
                        }
                    } catch (Exception e) {
                         // Expected in client mode - just use default
                    }
                }

                SOAPMessage soapMessage = msgFactory.createMessage();
                return new SaajSoapMessage(soapMessage);

            } catch (Exception e) {
                throw new RuntimeException("Failed to create SOAP message", e);
            }
        }

        /**
         * Try to extract Content-Type from the SOAP envelope for protocol detection.
         * If not found, fallback to SOAP 1.1 (text/xml).
         */
        private MimeHeaders extractHeaders(byte[] bodyBytes, String httpContentType) {
            MimeHeaders headers = new MimeHeaders();
            
            // If we have HTTP Content-Type, use it
            if (httpContentType != null && !httpContentType.isEmpty()) {
                headers.addHeader("Content-Type", httpContentType);
                return headers;
            }
            
            // Otherwise, detect from message content (CLIENT scenario)
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            if (body.contains("http://www.w3.org/2003/05/soap-envelope")) {
                headers.addHeader("Content-Type", "application/soap+xml; charset=utf-8");
            } else {
                headers.addHeader("Content-Type", "text/xml; charset=utf-8");
            }
            return headers;
        }
    }
     static class CustomMessageDispatcherServlet extends MessageDispatcherServlet {
        @Override
        protected void doService(HttpServletRequest request, HttpServletResponse response)
                throws Exception {
            super.doService(request, response);

            // Override content type AFTER processing
            String ackContentType = (String) request.getAttribute(Constants.ACK_CONTENT_TYPE);
            if (ackContentType != null && !ackContentType.isBlank()) {
                response.setContentType(ackContentType);
                response.setHeader("Content-Type", ackContentType);
            }
        }
    }
}