
package org.techbd.service.ccda;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.techbd.config.CoreAppConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCcdaRequest;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

class CCDAServiceTest {

    private DSLContext dslContext;
    private Configuration jooqConfig;
    private CCDAService ccdaService;
    private AppLogger appLogger;
    private TemplateLogger templateLogger;
    private CoreAppConfig coreAppConfig;

    @BeforeEach
    void setup() {
        dslContext = mock(DSLContext.class);
        jooqConfig = mock(Configuration.class);
        appLogger = mock(AppLogger.class);
        templateLogger = mock(TemplateLogger.class);
        coreAppConfig = mock(CoreAppConfig.class);
        when(appLogger.getLogger(CCDAService.class)).thenReturn(templateLogger);
        when(dslContext.configuration()).thenReturn(jooqConfig);
        ccdaService = new CCDAService(dslContext, appLogger, coreAppConfig);
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
            "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"), "metadata", "testUser", "127.0.0.1", "testSystem");

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
            "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"), "metadata", "testUser", "127.0.0.1", "testSystem");

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
            "/test-uri", Map.of("fhir", "bundle"), "metadata", "testUser", "127.0.0.1", "testSystem");

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
            "/test-uri", "{\"payload\":\"data\"}", Map.of("status", "ok"), "metadata", "testUser", "127.0.0.1", "testSystem");

            assertTrue(result);
        }
    }
}
