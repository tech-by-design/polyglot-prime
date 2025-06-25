
package org.techbd.service.ccda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCcdaRequest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CCDAServiceTest {

    private CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    private DSLContext dslContext;
    private Configuration jooqConfig;
    private CCDAService ccdaService;

    @BeforeEach
    void setup() {
        coreUdiPrimeJpaConfig = mock(CoreUdiPrimeJpaConfig.class);
        dslContext = mock(DSLContext.class);
        jooqConfig = mock(Configuration.class);
        when(coreUdiPrimeJpaConfig.dsl()).thenReturn(dslContext);
        when(dslContext.configuration()).thenReturn(jooqConfig);

        ccdaService = new CCDAService(coreUdiPrimeJpaConfig);
    }

    @Test
    void testSaveOriginalCcdaPayload_success() {
        try (MockedConstruction<RegisterInteractionCcdaRequest> mockConstruction =
                     mockConstruction(RegisterInteractionCcdaRequest.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

            boolean result = ccdaService.saveOriginalCcdaPayload("int123", "tenantA",
                    "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"));

            assertTrue(result);
        }
    }

    @Test
    void testSaveValidation_success() {
        try (MockedConstruction<RegisterInteractionCcdaRequest> mockConstruction =
                     mockConstruction(RegisterInteractionCcdaRequest.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

            boolean result = ccdaService.saveValidation(true, "int123", "tenantA",
                    "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"));

            assertTrue(result);
        }
    }

    @Test
    void testSaveFhirConversionResult_success() {
        try (MockedConstruction<RegisterInteractionCcdaRequest> mockConstruction =
                     mockConstruction(RegisterInteractionCcdaRequest.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

            boolean result = ccdaService.saveFhirConversionResult(true, "int123", "tenantA",
                    "/test-uri", Map.of("fhir", "bundle"));

            assertTrue(result);
        }
    }

    @Test
    void testSaveCcdaValidation_success() {
        try (MockedConstruction<RegisterInteractionCcdaRequest> mockConstruction =
                     mockConstruction(RegisterInteractionCcdaRequest.class,
                             (mock, context) -> {
                                 when(mock.execute(jooqConfig)).thenReturn(1);
                                 when(mock.getReturnValue()).thenReturn(new ObjectMapper().createObjectNode());
                             })) {

            boolean result = ccdaService.saveCcdaValidation(true, "int123", "tenantA",
                    "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"));

            assertTrue(result);
        }
    }

    @Test
    void testSaveOriginalCcdaPayload_exception() {
        CoreUdiPrimeJpaConfig faultyConfig = mock(CoreUdiPrimeJpaConfig.class);
        when(faultyConfig.dsl()).thenThrow(new RuntimeException("Mock error"));

        CCDAService errorService = new CCDAService(faultyConfig);

        boolean result = errorService.saveOriginalCcdaPayload("int123", "tenantA", "/uri", "{}", Map.of());

        assertFalse(result);
    }
}
