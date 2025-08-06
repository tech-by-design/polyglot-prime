package org.techbd.ingest.config;


import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@Configuration
@EnableWs
public class WebServiceConfig  {

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        var servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "pix")
    public DefaultWsdl11Definition pixWsdl(XsdSchema hl7Schema) {
        var wsdlDefinition = new DefaultWsdl11Definition();
        wsdlDefinition.setPortTypeName("PIXPort");
        wsdlDefinition.setLocationUri("/ws");
        wsdlDefinition.setTargetNamespace("urn:hl7-org:v3");
        wsdlDefinition.setSchema(hl7Schema);
        return wsdlDefinition;
    }

    @Bean
    public XsdSchema hl7Schema() {
        return new SimpleXsdSchema(new ClassPathResource("ITI/schema/HL7V3/NE2008/multicacheschemas/PRPA_IN201301UV02.xsd"));
    }
}