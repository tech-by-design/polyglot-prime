package org.techbd.service.fhir;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreAppConfig.FhirV4Config;
import org.techbd.config.CoreAppConfig.ShinnyPackageConfig;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.fhir.validation.PostPopulateSupport;
import org.techbd.service.fhir.validation.PrePopulateSupport;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
public abstract class BaseIgValidationTest {

    protected static OrchestrationEngine engine;

    protected static Tracer tracer;

    protected static CoreAppConfig appConfig;

    protected static SpanBuilder spanBuilder;

    protected static Span span;
    
    private static AppLogger appLogger;
    
    private static TemplateLogger templateLogger;

    protected static OrchestrationEngine.HapiValidationEngine spyHapiEngine;

    @BeforeAll
    static void initSharedEngine() throws Exception {
        tracer = mock(Tracer.class);
        appConfig = mock(CoreAppConfig.class);
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
        Field profileMapField = CoreFHIRUtil.class.getDeclaredField("PROFILE_MAP");
        profileMapField.setAccessible(true);
        profileMapField.set(null, getProfileMap());
    }
    private static Map<String, FhirV4Config> getIgPackages() {
        final Map<String, FhirV4Config> igPackages = new HashMap<>();
        FhirV4Config fhirV4Config = new FhirV4Config();
    
        // Default base packages - used by SHIN-NY IG 1.9.4
        Map<String, String> basePackages = new HashMap<>();
        basePackages.put("us-core", "ig-packages/fhir-v4/us-core/stu-7.0.0");
        basePackages.put("sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0");
        basePackages.put("uv-sdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");
    
        // SHIN-NY Packages
        Map<String, ShinnyPackageConfig> shinnyPackages = new HashMap<>();
    
        // SHIN-NY version 1.9.4
        ShinnyPackageConfig shinny = new ShinnyPackageConfig();
        shinny.setProfileBaseUrl("http://shinny.org/us/ny/hrsn");
        shinny.setPackagePath("ig-packages/shin-ny-ig/shinny/v1.9.4");
        shinny.setIgVersion("1.9.4");
        shinnyPackages.put("shinny", shinny);
    
        // Test SHIN-NY version 2.0.0
        ShinnyPackageConfig testshinny = new ShinnyPackageConfig();
        testshinny.setProfileBaseUrl("http://test.shinny.org/us/ny/hrsn");
        testshinny.setPackagePath("ig-packages/shin-ny-ig/test-shinny/v2.0.0");
        testshinny.setIgVersion("2.0.0");
    
        // Base packages specific to SHIN-NY IG 2.0.0
        Map<String, String> testBasePackages = new HashMap<>();
        testBasePackages.put("us-core", "ig-packages/fhir-v4/us-core/stu-7.0.0-updated");
        testBasePackages.put("sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.3.0");
        testBasePackages.put("uv-sdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");
    
        testshinny.setBasePackages(testBasePackages);
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
