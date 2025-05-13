package org.techbd.orchestrate.fhir;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;
import org.techbd.util.FHIRUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;


public abstract class BaseOrchestrationEngineTest {

    @InjectMocks
    protected static OrchestrationEngine engine;

    @Mock
    protected static Tracer tracer;

    @Mock
    protected static AppConfig appConfig;

    @Mock
    protected static SpanBuilder spanBuilder;

    @Mock
    protected static Span span;

    @BeforeAll
    static void initSharedEngine() throws Exception {
        MockitoAnnotations.openMocks(BaseOrchestrationEngineTest.class);
        when(appConfig.getIgPackages()).thenReturn(getIgPackages());
        when(appConfig.getIgVersion()).thenReturn("1.3.0");
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        engine = new OrchestrationEngine(tracer, appConfig);

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

        // Shinny version 1.2.3
        Map<String, String> shinnyV123 = new HashMap<>();
        shinnyV123.put("profile-base-url", "http://shinny.org/us/ny/hrsn");
        shinnyV123.put("package-path", "ig-packages/shin-ny-ig/shinny/v1.2.3");
        shinnyV123.put("ig-version", "1.2.3");
        shinnyPackages.put("shinny-v1-2-3", shinnyV123);

        // Test Shinny version 1.3.0
        Map<String, String> testShinnyV130 = new HashMap<>();
        testShinnyV130.put("profile-base-url", "http://test.shinny.org/us/ny/hrsn");
        testShinnyV130.put("package-path", "ig-packages/shin-ny-ig/test-shinny/v1.3.0");
        testShinnyV130.put("ig-version", "1.3.0");
        shinnyPackages.put("test-shinny-v1-3-0", testShinnyV130);

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
