package org.techbd.orchestrate.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
class OrchestrationEngineTest extends BaseIgValidationTest {
        private static final String INTERACTION_ID = UUID.randomUUID().toString();  

        @Test
        void testOrchestrateSingleSession() {
                String payload = "{ \"resourceType\": \"Bundle\", \"id\": \"AHCHRSNScreeningResponseExample\", \"meta\": { \"lastUpdated\": \"2024-02-23T00:00:00Z\", \"profile\": [\"http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile\"] } }";
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withTracer(tracer)
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();
                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);
                        assertThat(engine.getSessions().get(0).getPayloads()).isNotNull();
                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
                        assertThat(results).hasSize(1);
                        assertThat(results.get(0).isValid()).isFalse();
                        OperationOutcome operationOutcome = (OperationOutcome) FhirContext.forR4().newJsonParser()
                                        .parseResource(results.get(0).getOperationOutcome());
                        List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                        assertThat(issues)
                                        .isNotNull()
                                        .hasSizeGreaterThan(1);

                } finally {
                        engine.clear(realSession);

                }
        }

        @Test
        void testOrchestrateMultipleSessions() {
                String payload = "{ \"resourceType\": \"Bundle\", \"id\": \"AHCHRSNScreeningResponseExample\", \"meta\": { \"lastUpdated\": \"2024-02-23T00:00:00Z\", \"profile\": [\"http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile\"] } }";
                String payload2 = "{ \"resourceType\": \"Bundle\", \"id\": \"AHCHRSNScreeningResponseExample\", \"meta\": { \"lastUpdated\": \"2024-02-23T00:00:00Z\", \"profile\": [\"http://test.shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile\"] } }";
                OrchestrationEngine.OrchestrationSession realSession = null;
                OrchestrationEngine.OrchestrationSession realSession2 = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();
                        sessionSpy = spy(realSession);
                        realSession2 = engine.session()
                                        .withPayloads(List.of(payload2))
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withTracer(tracer)
                                        .withInteractionId(INTERACTION_ID)
                                        .addHl7ValidationApiEngine()
                                        .build();
                        sessionSpy2 = spy(realSession2);
                        engine.orchestrate(sessionSpy, sessionSpy2);
                        assertThat(engine.getSessions()).hasSize(2);
                        OrchestrationEngine.OrchestrationSession retrievedSession1 = engine.getSessions().get(0);
                        assertThat(retrievedSession1.getPayloads()).isNotNull(); 
                        assertThat(retrievedSession1.getValidationResults()).hasSize(1);
                        OrchestrationEngine.OrchestrationSession retrievedSession2 = engine.getSessions().get(1);
                        assertThat(retrievedSession2.getPayloads()).isNotNull();
                        assertThat(retrievedSession2.getValidationResults()).hasSize(1);
                } finally {
                        engine.clear(realSession);
                        engine.clear(realSession2);
                }
        }

        @Test
        void testValidationEngineCaching() {
                OrchestrationEngine.OrchestrationSession session1 = null;
                OrchestrationEngine.OrchestrationSession session2 = null;
                try {
                        session1 = engine.session()
                                        .withPayloads(List.of("payload1"))
                                        .withTracer(tracer)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();

                        session2 = engine.session()
                                        .withPayloads(List.of("payload2"))
                                        .withTracer(tracer)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();

                        engine.orchestrate(session1, session2);

                        Map<String, String> codeSystemMap = new HashMap<>();
                        codeSystemMap.put("shinnyConsentProvisionTypesVS",
                                        "http://shinny.org/us/ny/hrsn/shinnyConsentProvision");
                        assertThat(engine.getSessions()).hasSize(2);
                        assertThat(engine.getValidationEngine(OrchestrationEngine.ValidationEngineIdentifier.HAPI));
                } finally {
                        engine.clear(session1);
                        engine.clear(session2);
                }
        }

        @Test
        void testValidationAgainstLatestShinnyIgHasNoErrors() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json"));
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();

                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);
                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
                        assertThat(results).hasSize(1);
                        assertThat(results.get(0).isValid()).isTrue();
                        IParser parser = FhirContext.forR4().newJsonParser();
                        OperationOutcome operationOutcome = (OperationOutcome) parser
                                        .parseResource(results.get(0).getOperationOutcome());

                        List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                        assertThat(issues).filteredOn(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR).isEmpty();
                } finally {
                        engine.clear(realSession);
                }
        }

        @Test
        void testValidationAgainstShinnyIgLatestVersion_ReferentialIntegrityError() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/shinny-examples/Bundle-AHCHRSNScreeningResponseExample-HasErrors.json"));
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();

                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);

                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
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
                                                assertThat(issue.getDiagnostics()).contains(
                                                                "Constraint failed: SHINNY-Bundle-Patient-Org-RI: 'Checks for RI between Patient & Assigning Org'");
                                        });
                } finally {
                        engine.clear(realSession);
                }
        }

        @Test
        void testValidationAgainstLatestTestShinnyIgHasNoErrors() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/test-shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json"));
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .withInteractionId(INTERACTION_ID)
                                        .addHapiValidationEngine()
                                        .build();

                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);
                        
                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
                        assertThat(results).hasSize(1);
                        assertThat(results.get(0).isValid()).isTrue();
                        IParser parser = FhirContext.forR4().newJsonParser();
                        OperationOutcome operationOutcome = (OperationOutcome) parser
                                        .parseResource(results.get(0).getOperationOutcome());

                        List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                        assertThat(issues).filteredOn(issue -> issue
                                                        .getSeverity() == OperationOutcome.IssueSeverity.ERROR).isEmpty();
                } finally {
                        engine.clear(realSession);
                }
        }

        @Test
        void testValidationAgainstLatestShinnyIg_PatientMRNMissingError() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/shinny-examples/Bundle-AHCHRSNScreeningResponseExample-HasErrors.json"));
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withInteractionId(INTERACTION_ID)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .addHapiValidationEngine()
                                        .build();

                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);
                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
                        assertThat(results).hasSize(1);
                        assertThat(results.get(0).isValid()).isFalse();
                        IParser parser = FhirContext.forR4().newJsonParser();
                        OperationOutcome operationOutcome = (OperationOutcome) parser
                                        .parseResource(results.get(0).getOperationOutcome());

                        List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                        assertThat(issues).isNotNull().hasSizeGreaterThan(1);

                        assertThat(issues)
                                        .filteredOn(issue -> issue
                                                        .getSeverity() == OperationOutcome.IssueSeverity.ERROR)
                                        .anySatisfy(issue -> {
                                                assertThat(issue.getCode().toCode()).isEqualTo("processing");
                                                assertThat(issue.getDiagnostics()).contains(
                                                                "Constraint failed: SHINNY-Patient-MRN:");
                                        });
                } finally {
                        engine.clear(realSession);
                }
        }

            @Test
        void testValidationAgainstLatestTestShinnyIg_PatientMRNMissingError() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/test-shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample-Errors.json"));
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withInteractionId(INTERACTION_ID)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .addHapiValidationEngine()
                                        .build();

                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);
                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
                        assertThat(results).hasSize(1);
                        assertThat(results.get(0).isValid()).isFalse();
                        IParser parser = FhirContext.forR4().newJsonParser();
                        OperationOutcome operationOutcome = (OperationOutcome) parser
                                        .parseResource(results.get(0).getOperationOutcome());

                        List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                        assertThat(issues).isNotNull().hasSizeGreaterThan(1);

                        assertThat(issues)
                                        .filteredOn(issue -> issue
                                                        .getSeverity() == OperationOutcome.IssueSeverity.ERROR)
                                        .anySatisfy(issue -> {
                                                assertThat(issue.getCode().toCode()).isEqualTo("processing");
                                                assertThat(issue.getDiagnostics()).contains(
                                                                "Constraint failed: SHINNY-Patient-MRN:");
                                        });
                } finally {
                        engine.clear(realSession);
                }
        }

        @Test
        void testValidationWhenTheIncomingPayloadHasInValidProfileUrl() throws Exception {
                String payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/shinny-examples/Bundle-AHCHRSNScreeningResponseExample-InvalidProfileUrl.json"));
                OrchestrationEngine.OrchestrationSession realSession = null;
                try {
                        realSession = engine.session()
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withInteractionId(INTERACTION_ID)
                                        .withSessionId(UUID.randomUUID().toString())
                                        .addHapiValidationEngine()
                                        .build();

                        sessionSpy = spy(realSession);
                        engine.orchestrate(sessionSpy);
                        assertThat(engine.getSessions()).hasSize(1);
                        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0)
                                        .getValidationResults();
                        assertThat(results).hasSize(1);
                        assertThat(results.get(0).isValid()).isFalse();
                        IParser parser = FhirContext.forR4().newJsonParser();
                        OperationOutcome operationOutcome = (OperationOutcome) parser
                                        .parseResource(results.get(0).getOperationOutcome());
                        List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                        assertThat(issues).isNotNull().hasSize(1);
                        assertThat(issues)
                                        .anySatisfy(issue -> {
                                                assertThat(issue.getSeverity())
                                                                .isEqualTo(OperationOutcome.IssueSeverity.FATAL);
                                                assertThat(issue.getCode())
                                                                .isEqualTo(OperationOutcome.IssueType.EXCEPTION);
                                                assertThat(issue.getDiagnostics()).isEqualTo(
                                                                "The provided bundle profile URL is invalid. Please check and enter the correct bundle profile url");
                                        });
                } finally {
                        engine.clear(realSession);
                }
        }
}
