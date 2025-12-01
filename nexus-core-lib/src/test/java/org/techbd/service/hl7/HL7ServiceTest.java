
package org.techbd.service.hl7;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHl7Request;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

class HL7ServiceTest {

    private CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    private DSLContext dslContext;
    private Configuration jooqConfig;
    private HL7Service hl7Service;
    private AppLogger appLogger;
    private TemplateLogger templateLogger;
    private CoreAppConfig coreAppConfig;

    @BeforeEach
    void setup() {
        coreUdiPrimeJpaConfig = mock(CoreUdiPrimeJpaConfig.class);
        dslContext = mock(DSLContext.class);
        jooqConfig = mock(Configuration.class);
        appLogger = mock(AppLogger.class);
        templateLogger = mock(TemplateLogger.class);
        coreAppConfig = mock(CoreAppConfig.class);
        when(appLogger.getLogger(HL7Service.class)).thenReturn(templateLogger);
        when(coreUdiPrimeJpaConfig.dsl()).thenReturn(dslContext);
        when(dslContext.configuration()).thenReturn(jooqConfig);
        when(coreAppConfig.getVersion()).thenReturn("1.0.0-test");
        hl7Service = new HL7Service(coreUdiPrimeJpaConfig, appLogger, coreAppConfig);
    }

    @Test
    void testSaveOriginalHl7Payload_success() {
        try (MockedConstruction<RegisterInteractionHl7Request> mockConstruction =
                     mockConstruction(RegisterInteractionHl7Request.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

        boolean result = hl7Service.saveOriginalHl7Payload("int123", "tenantA",
            "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"), "file.hl7", "unit-test-agent", "127.0.0.1");

            assertTrue(result);
        }
    }

    @Test
    void testSaveValidation_success() {
        try (MockedConstruction<RegisterInteractionHl7Request> mockConstruction =
                     mockConstruction(RegisterInteractionHl7Request.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

        boolean result = hl7Service.saveValidation(true, "int123", "tenantA",
            "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"), "file.hl7", "unit-test-agent", "127.0.0.1");

            assertTrue(result);
        }
    }

    @Test
    void testSaveFhirConversionResult_success() {
        try (MockedConstruction<RegisterInteractionHl7Request> mockConstruction =
                     mockConstruction(RegisterInteractionHl7Request.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

        boolean result = hl7Service.saveFhirConversionResult(true, "int123", "tenantA",
            "/test-uri", Map.of("fhir", "bundle"), "file.hl7", "unit-test-agent", "127.0.0.1");

            assertTrue(result);
        }
    }

    @Test
    void testSaveOriginalHl7Payload_exception() {
        CoreUdiPrimeJpaConfig faultyConfig = mock(CoreUdiPrimeJpaConfig.class);
        when(faultyConfig.dsl()).thenThrow(new RuntimeException("Mock error"));

        HL7Service errorService = new HL7Service(faultyConfig, appLogger, coreAppConfig);

    boolean result = errorService.saveOriginalHl7Payload("int123", "tenantA", "/uri", "{}", Map.of(), "file.hl7", "unit-test-agent", "127.0.0.1");

        assertFalse(result);
    }
}
