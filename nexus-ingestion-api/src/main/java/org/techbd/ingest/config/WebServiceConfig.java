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
import org.techbd.ingest.interceptors.WsaHeaderInterceptor;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;

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

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        var servlet = new MessageDispatcherServlet();
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
    /**
     * Custom message factory that dynamically supports SOAP 1.1 and SOAP 1.2 based
     * on Content-Type.
     */
    @Bean
    public SoapMessageFactory messageFactory() {
        return new SmartSoapMessageFactory();
    }

    static class SmartSoapMessageFactory extends SaajSoapMessageFactory {

       @Override
        public SaajSoapMessage createWebServiceMessage(InputStream inputStream) throws IOException {
            try {
                // Get HTTP request to check Content-Type
                var transportContext = TransportContextHolder.getTransportContext();
                String httpContentType = null;
                if (transportContext != null) {
                    var connection = (org.springframework.ws.transport.http.HttpServletConnection) transportContext.getConnection();
                    httpContentType = connection.getHttpServletRequest().getContentType();
                }
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                inputStream.transferTo(baos);
                byte[] bytes = baos.toByteArray();
                
                // Check if it's MTOM multipart content from HTTP header
                if (httpContentType != null && httpContentType.contains("multipart/related")) {
                    // Handle MTOM - create MIME headers from HTTP request
                    MimeHeaders mimeHeaders = new MimeHeaders();
                    mimeHeaders.addHeader("Content-Type", httpContentType);
                    MessageFactory msgFactory = MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL);
                    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                    SOAPMessage soapMessage = msgFactory.createMessage(mimeHeaders, bis);
                    return new SaajSoapMessage(soapMessage);
                } else {
                    // Regular SOAP handling
                    MimeHeaders headers = extractHeaders(bytes);
                    MessageFactory msgFactory =
                    MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL);
                    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                    SOAPMessage soapMessage = msgFactory.createMessage(headers, bis);
                    return new SaajSoapMessage(soapMessage);
                }
            } catch (Exception e) {
                throw new IOException("Unable to create SOAP message", e);
            }
        }
 
        @Override
        public SaajSoapMessage createWebServiceMessage() {
            try {
                // Default to SOAP 1.1
                MessageFactory msgFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);

                // Check if HTTP request is available
                var transportContext = TransportContextHolder.getTransportContext();
                if (transportContext != null) {
                    var connection = (org.springframework.ws.transport.http.HttpServletConnection) transportContext
                            .getConnection();
                    String contentType = connection.getHttpServletRequest().getContentType();

                    if (contentType != null && contentType.contains("application/soap+xml")) {
                        // SOAP 1.2 request
                        msgFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
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
        private MimeHeaders extractHeaders(byte[] bodyBytes) {
            MimeHeaders headers = new MimeHeaders();
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            if (body.contains("http://www.w3.org/2003/05/soap-envelope")) {
                headers.addHeader("Content-Type", "application/soap+xml");
            } else {
                headers.addHeader("Content-Type", "text/xml");
            }
            return headers;
        }
        private String getContentType(MimeHeaders headers) {
            String[] header = headers.getHeader("Content-Type");
            return header != null && header.length > 0 ? header[0] : null;
        }
    }

}