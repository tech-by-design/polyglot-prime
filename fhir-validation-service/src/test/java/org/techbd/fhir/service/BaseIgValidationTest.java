package org.techbd.fhir.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.techbd.fhir.config.AppConfig;
import org.techbd.fhir.config.AppConfig.FhirV4Config;
import org.techbd.fhir.service.engine.OrchestrationEngine;
import org.techbd.fhir.service.validation.PostPopulateSupport;
import org.techbd.fhir.service.validation.PrePopulateSupport;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.fhir.util.FHIRUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
public abstract class BaseIgValidationTest {

    protected static OrchestrationEngine engine;

    protected static Tracer tracer;

    protected static AppConfig appConfig;

    protected static SpanBuilder spanBuilder;

    protected static Span span;
    
    private static AppLogger appLogger;
    
    private static TemplateLogger templateLogger;

    protected static OrchestrationEngine.HapiValidationEngine spyHapiEngine;

    @BeforeAll
    static void initSharedEngine() throws Exception {
        tracer = mock(Tracer.class);
        appConfig = mock(AppConfig.class);
        spanBuilder = mock(SpanBuilder.class);
        span = mock(Span.class);
        appLogger = mock(AppLogger.class);
        templateLogger = mock(TemplateLogger.class);
        when(appLogger.getLogger(OrchestrationEngine.class)).thenReturn(templateLogger);
        when(appLogger.getLogger(PrePopulateSupport.class)).thenReturn(templateLogger);
        when(appLogger.getLogger(PostPopulateSupport.class)).thenReturn(templateLogger);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(appConfig.getIgPackages()).thenReturn(getIgPackages());
       // when(appConfig.getIgVersion()).thenReturn("1.3.0");

        engine = new OrchestrationEngine(appConfig,appLogger);
        Field profileMapField = FHIRUtil.class.getDeclaredField("PROFILE_MAP");
        profileMapField.setAccessible(true);
        profileMapField.set(null, getProfileMap());
    }
    private static Map<String, FhirV4Config> getIgPackages() {
        final Map<String, FhirV4Config> igPackages = new HashMap<>();
        FhirV4Config fhirV4Config = new FhirV4Config();

        // Base packages for external dependencies
        Map<String, String> basePackages = new HashMap<>();
        basePackages.put("us-core", "ig-packages/fhir-v4/us-core/stu-7.0.0");
        basePackages.put("sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0");
        basePackages.put("uv-sdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");

        // Shinny Packages
        Map<String, Map<String, String>> shinnyPackages = new HashMap<>();

        // Shinny version 1.6.3
        Map<String, String> shinny = new HashMap<>();
        shinny.put("profile-base-url", "http://shinny.org/us/ny/hrsn");
        shinny.put("package-path", "ig-packages/shin-ny-ig/shinny/v1.6.3");
        shinny.put("ig-version", "1.6.3");
        shinnyPackages.put("shinny", shinny);

        // Test Shinny version 1.7.2
        Map<String, String> testshinny = new HashMap<>();
        testshinny.put("profile-base-url", "http://test.shinny.org/us/ny/hrsn");
        testshinny.put("package-path", "ig-packages/shin-ny-ig/test-shinny/v1.7.2");
        testshinny.put("ig-version", "1.7.2");
        shinnyPackages.put("test-shinny", testshinny);

        fhirV4Config.setBasePackages(basePackages);
        fhirV4Config.setShinnyPackages(shinnyPackages);
        igPackages.put("fhir-v4", fhirV4Config);

        return igPackages;
    }

    private static Map<String, String> getProfileMap() {
        Map<String, String> profileMap = new HashMap<>();
        profileMap.put("bundle", "/StructureDefinition/SHINNYBundleProfile");
        profileMap.put("patient", "/StructureDefinition/shinny-patient");
        profileMap.put("consent", "/StructureDefinition/shinny-Consent");
        profileMap.put("encounter", "/StructureDefinition/shinny-encounter");
        profileMap.put("organization", "/StructureDefinition/shin-ny-organization");
        profileMap.put("observation", "/StructureDefinition/shinny-observation-screening-response");
        profileMap.put("questionnaire", "/StructureDefinition/shinny-questionnaire");
        profileMap.put("practitioner", "/StructureDefinition/shin-ny-practitioner");
        profileMap.put("questionnaireResponse", "/StructureDefinition/shinny-questionnaire");
        profileMap.put("observationSexualOrientation",
                "/StructureDefinition/shinny-observation-sexual-orientation");
        return profileMap;
    }
}
