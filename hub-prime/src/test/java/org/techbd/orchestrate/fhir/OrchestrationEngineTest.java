package org.techbd.orchestrate.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.orchestrate.fhir.OrchestrationEngine.HapiValidationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.OrchestrationSession;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationEngine;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;
import org.techbd.util.FHIRUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

@ExtendWith(MockitoExtension.class)
class OrchestrationEngineTest {

        @Mock
        private Tracer tracer;

        @Mock
        private FhirBundleValidator mockValidator;

        @InjectMocks
        private OrchestrationEngine engine;

        @Mock
        private OrchestrationSession session;

        private HapiValidationEngine spyHapiEngine;

        @Mock
        private Map<ValidationEngine, ValidationEngine> validationEngineCache;
        @Mock
        private SpanBuilder spanBuilder; // Mock the SpanBuilder

        @Mock
        private Span span;
        @Mock
        private List<FhirBundleValidator> fhirBundleValidators;

        private OrchestrationEngine.OrchestrationSession sessionSpy;

        private OrchestrationEngine.OrchestrationSession sessionSpy2;

        @BeforeEach
        void setUp() throws Exception {
                when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
                when(spanBuilder.startSpan()).thenReturn(span);
                spyHapiEngine = spy(new HapiValidationEngine.Builder()
                                .withFhirProfileUrl(
                                                "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .withIgPackages(getIgPackages())
                                .withTracer(tracer)
                                .build());
                Field profileMapField = FHIRUtil.class.getDeclaredField("PROFILE_MAP");
                profileMapField.setAccessible(true);
                profileMapField.set(null, getProfileMap());
        }

        @Test
        void testOrchestrateSingleSession() {
                String payload = "{ \"resourceType\": \"Bundle\", \"id\": \"AHCHRSNScreeningResponseExample\", \"meta\": { \"lastUpdated\": \"2024-02-23T00:00:00Z\", \"profile\": [\"http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile\"] } }";
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();
                sessionSpy = spy(realSession);
                engine.orchestrate(sessionSpy);
                assertThat(engine.getSessions()).hasSize(1);
                assertThat(engine.getSessions().get(0).getPayloads()).containsExactly(payload);
                assertThat(engine.getSessions().get(0).getFhirProfileUrl())
                                .isEqualTo("http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");
                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).isFalse();
                OperationOutcome operationOutcome = (OperationOutcome) FhirContext.forR4().newJsonParser()
                                .parseResource(results.get(0).getOperationOutcome());
                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues)
                                .isNotNull()
                                .hasSizeGreaterThan(1);
        }

        @Test
        void testOrchestrateMultipleSessions() {
                String payload = "{ \"resourceType\": \"Bundle\", \"id\": \"AHCHRSNScreeningResponseExample\", \"meta\": { \"lastUpdated\": \"2024-02-23T00:00:00Z\", \"profile\": [\"http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile\"] } }";
                String payload2 = "{ \"resourceType\": \"Bundle\", \"id\": \"AHCHRSNScreeningResponseExample\", \"meta\": { \"lastUpdated\": \"2024-02-23T00:00:00Z\", \"profile\": [\"http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile\"] } }";
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();
                sessionSpy = spy(realSession);

                OrchestrationEngine.OrchestrationSession realSession2 = engine.session()
                                .withPayloads(List.of(payload2))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHl7ValidationApiEngine()
                                .build();
                sessionSpy2 = spy(realSession2);
                engine.orchestrate(sessionSpy, sessionSpy2);
                assertThat(engine.getSessions()).hasSize(2);
                OrchestrationEngine.OrchestrationSession retrievedSession1 = engine.getSessions().get(0);
                assertThat(retrievedSession1.getPayloads()).containsExactly(payload);
                assertThat(retrievedSession1.getFhirProfileUrl())
                                .isEqualTo("http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");
                assertThat(retrievedSession1.getValidationResults()).hasSize(1);
                assertThat(retrievedSession1.getValidationResults().get(0).isValid()).isFalse();
                OperationOutcome operationOutcome = (OperationOutcome) FhirContext.forR4().newJsonParser()
                                .parseResource(retrievedSession1.getValidationResults().get(0).getOperationOutcome());
                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues)
                                .isNotNull()
                                .hasSizeGreaterThan(1);
                OrchestrationEngine.OrchestrationSession retrievedSession2 = engine.getSessions().get(1);
                assertThat(retrievedSession2.getPayloads()).containsExactly(payload2);
                assertThat(retrievedSession2.getFhirProfileUrl())
                                .isEqualTo("http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");
                assertThat(retrievedSession2.getValidationResults()).hasSize(1);
        }

        @Test
        void testValidationEngineCaching() {
                OrchestrationEngine.OrchestrationSession session1 = engine.session()
                                .withPayloads(List.of("payload1"))
                                .withTracer(tracer)
                                .withFhirProfileUrl("http://shinny.org/us/ny/hrsn")
                                .withFhirIGPackages(getIgPackages())
                                .addHapiValidationEngine()
                                .build();

                OrchestrationEngine.OrchestrationSession session2 = engine.session()
                                .withPayloads(List.of("payload2"))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl("http://shinny.org/us/ny/hrsn")
                                .addHapiValidationEngine()
                                .build();

                engine.orchestrate(session1, session2);
                Map<String, FhirV4Config> igPackages = getIgPackages();
                String igVersion = new String();
                Map<String, String> codeSystemMap = new HashMap<>();
                codeSystemMap.put("shinnyConsentProvisionTypesVS",
                                "http://shinny.org/us/ny/hrsn/shinnyConsentProvision");
                assertThat(engine.getSessions()).hasSize(2);
                assertThat(engine.getValidationEngine(OrchestrationEngine.ValidationEngineIdentifier.HAPI,
                                "http://shinny.org/us/ny/hrsn", igPackages, igVersion, tracer, "test"))
                                .isSameAs(engine.getValidationEngine(
                                                OrchestrationEngine.ValidationEngineIdentifier.HAPI,
                                                "http://shinny.org/us/ny/hrsn", igPackages, igVersion, tracer,
                                                "test"));
        }

        @Test
        void testIgVersion1_2_3_ValidBundle() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/AHCHRSNQuestionnaireResponseExample1.2.3.json"));
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();

                sessionSpy = spy(realSession);
                engine.orchestrate(sessionSpy);
                assertThat(engine.getSessions()).hasSize(1);
                assertThat(engine.getSessions().get(0).getPayloads()).containsExactly(payload);
                assertThat(engine.getSessions().get(0).getFhirProfileUrl())
                                .isEqualTo("http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");

                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).isTrue();
                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());

                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues).isNotNull().hasSizeGreaterThan(1);
                assertThat(issues)
                                .extracting(OperationOutcomeIssueComponent::getSeverity)
                                .doesNotContain(OperationOutcome.IssueSeverity.ERROR);
        }

        @Test
        void testIgVersion1_2_3_InvalidBundle_ReferentialIntegrityError() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/AHCHRSNQuestionnaireResponseExample1.2.3-ReferentialIntegrityError.json"));
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();

                sessionSpy = spy(realSession);
                engine.orchestrate(sessionSpy);
                assertThat(engine.getSessions()).hasSize(1);
                assertThat(engine.getSessions().get(0).getPayloads()).containsExactly(payload);
                assertThat(engine.getSessions().get(0).getFhirProfileUrl())
                                .isEqualTo("http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");

                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).isFalse();

                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());

                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues)
                                .filteredOn(issue -> issue
                                                .getSeverity() == OperationOutcome.IssueSeverity.ERROR)
                                .anySatisfy(issue -> {
                                        assertThat(issue.getCode().toCode()).isEqualTo("processing");
                                        assertThat(issue.getDiagnostics()).isEqualTo(
                                                        "Constraint failed: SHINNY-Bundle-Patient-Encounter-RI: 'Checks for RI between Patient & Encounter' (defined in http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile)");
                                });
        }

        @Test
        void testIgVersion1_3_0ValidBundle() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/AHCHRSNScreeningResponseExample1.3.0.json"));
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();

                sessionSpy = spy(realSession);
                engine.orchestrate(sessionSpy);
                assertThat(engine.getSessions()).hasSize(1);
                assertThat(engine.getSessions().get(0).getPayloads()).containsExactly(payload);
                assertThat(engine.getSessions().get(0).getFhirProfileUrl())
                                .isEqualTo("http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");

                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).isTrue();
                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());

                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues).isNotNull().hasSizeGreaterThan(1);
                assertThat(issues)
                                .extracting(OperationOutcomeIssueComponent::getSeverity)
                                .doesNotContain(OperationOutcome.IssueSeverity.ERROR);
        }

        @Test
        void testIgVersion1_3_InValidBundle_PatientMRNMissingError() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/AHCHRSNScreeningResponseExample1.3.0 -PatientMRNMissingError.json"));
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();

                sessionSpy = spy(realSession);
                engine.orchestrate(sessionSpy);
                assertThat(engine.getSessions()).hasSize(1);
                assertThat(engine.getSessions().get(0).getPayloads()).containsExactly(payload);
                assertThat(engine.getSessions().get(0).getFhirProfileUrl())
                                .isEqualTo("http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile");

                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).isFalse();
                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());

                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues).isNotNull().hasSizeGreaterThan(1);

                assertThat(issues)
                                .filteredOn(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR)
                                .anySatisfy(issue -> {
                                        assertThat(issue.getCode().toCode()).isEqualTo("processing");
                                        assertThat(issue.getDiagnostics()).isEqualTo(
                                                        "Slice 'Patient.identifier:MR': a matching slice is required, but not found (from http://test.shinny.org/us/ny/hrsn/StructureDefinition/shinny-patient|1.3.0). Note that other slices are allowed in addition to this required slice");
                                });
        }

        @Test
        void testInValidProfileUrl() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/AHCHRSNScreeningResponseExample-InvalidProfileUrl.json"));
                OrchestrationEngine.OrchestrationSession realSession = engine.session()
                                .withPayloads(List.of(payload))
                                .withTracer(tracer)
                                .withFhirIGPackages(getIgPackages())
                                .withFhirProfileUrl(
                                                "http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                                .addHapiValidationEngine()
                                .build();

                sessionSpy = spy(realSession);
                engine.orchestrate(sessionSpy);
                assertThat(engine.getSessions()).hasSize(1);
                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).isFalse();
                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());
                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                assertThat(issues).isNotNull().hasSize(1);
                assertThat(issues)
                                .anySatisfy(issue -> {
                                        assertThat(issue.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.FATAL);
                                        assertThat(issue.getCode()).isEqualTo(OperationOutcome.IssueType.EXCEPTION);
                                        assertThat(issue.getDiagnostics()).isEqualTo(
                                                        "The provided bundle profile URL is invalid. Please check and enter the correct bundle profile url");
                                });
        }

        private Map<String, FhirV4Config> getIgPackages() {
                final Map<String, FhirV4Config> igPackages = new HashMap<>();
                FhirV4Config fhirV4Config = new FhirV4Config();
                Map<String, String> basePackages = new HashMap<>();
                basePackages.put("usCore", "ig-packages/fhir-v4/us-core/stu-7.0.0");
                basePackages.put("sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0");
                basePackages.put("uvSdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");
                Map<String, Map<String, String>> shinnyPackages = new HashMap<>();
                Map<String, String> shinnyV123 = new HashMap<>();
                shinnyV123.put("profile-base-url", "http://shinny.org/us/ny/hrsn");
                shinnyV123.put("package-path", "ig-packages/shin-ny-ig/v1.2.3");
                shinnyV123.put("ig-version", "1.2.3");
                Map<String, String> shinnyV130 = new HashMap<>();
                shinnyV130.put("profile-base-url", "http://test.shinny.org/us/ny/hrsn");
                shinnyV130.put("package-path", "ig-packages/shin-ny-ig/v1.3.0");
                shinnyV130.put("ig-version", "1.3.0");
                shinnyPackages.put("shinny-v1-2-3", shinnyV123);
                shinnyPackages.put("shinny-v1-3-0", shinnyV130);
                fhirV4Config.setBasePackages(basePackages);
                fhirV4Config.setShinnyPackages(shinnyPackages);
                igPackages.put("fhir-v4", fhirV4Config);
                return igPackages;
        }
 
        private Map<String, String> getProfileMap() {
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
