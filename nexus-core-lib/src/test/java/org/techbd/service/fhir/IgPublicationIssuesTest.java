package org.techbd.service.fhir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.fhir.engine.OrchestrationEngine.ValidationResult;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
public class IgPublicationIssuesTest extends BaseIgValidationTest {

        @Test
        @DisplayName("Validate SHIN-NY IG AHCHRSN QuestionnaireResponse ExampleFile")
        void testValidateShinnyIG_AHCHRSNQuestionnaireResponseExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG Patient Negative Consent ExampleFile")
        void testValidateShinnyIG_PatientNegativeConsentExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-PatientNegativeConsent.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG AHCHRSN ScreeningResponse ExampleFile")
        void testValidateShinnyIG_AHCHRSNScreeningResponseExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG NY ScreeningResponse ExampleFile")
        void testValidateShinnyIG_NYScreeningResponseExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-NYScreeningResponseExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG NY ScreeningResponse Unknown1‑8 ExampleFile")
        void testValidateShinnyIG_NYScreeningResponseExampleUnknown1to8() throws IOException {
            validateFile("shinny-examples/Bundle-NYScreeningResponseExampleUnknown1to8.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG NY ScreeningResponse Declined9‑12 ExampleFile")
        void testValidateShinnyIG_NYScreeningResponseExampleDeclined9to12() throws IOException {
            validateFile("shinny-examples/Bundle-NYScreeningResponseExampleDeclined9to12.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG Food Insecurity Assessment ExampleFile")
        void testValidateShinnyIG_FoodInsecurityAssessmentExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG ServiceRequest ExampleFile")
        void testValidateShinnyIG_ServiceRequestExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-ServiceRequestExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG Task Completed ExampleFile")
        void testValidateShinnyIG_TaskCompletedExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-TaskCompletedExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG Task ExampleFile")
        void testValidateShinnyIG_TaskExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-TaskExample.json");
        }

        @Test
        @DisplayName("Validate SHIN-NY IG Task Output Procedure ExampleFile")
        void testValidateShinnyIG_TaskOutputProcedureExampleFile() throws IOException {
            validateFile("shinny-examples/Bundle-TaskOutputProcedureExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG AHCHRSN ScreeningResponse ExampleFile")
        void testValidateTestShinnyIG_AHCHRSNScreeningResponseExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG NY ScreeningResponse ExampleFile")
        void testValidateTestShinnyIG_NYScreeningResponseExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-NYScreeningResponseExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG Food Insecurity Assessment ExampleFile")
        void testValidateTestShinnyIG_FoodInsecurityAssessmentExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG ServiceRequest ExampleFile")
        void testValidateTestShinnyIG_ServiceRequestExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-ServiceRequestExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG Task Completed ExampleFile")
        void testValidateTestShinnyIG_TaskCompletedExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-TaskCompletedExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG Task ExampleFile")
        void testValidateTestShinnyIG_TaskExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-TaskExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG Task MultipleE ncounters ExampleFile")
        void testValidateTestShinnyIG_TaskMultipleEncountersExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-TaskExampleMultipleEncounters.json");
        }
        
        @Test
        @DisplayName("Validate Test SHIN-NY IG Task Output Procedure ExampleFile")
        void testValidateTestShinnyIG_TaskOutputProcedureExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-TaskOutputProcedureExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG Patient Negative Consent ExampleFile")
        void testValidateTestShinnyIG_PatientNegativeConsentExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-PatientNegativeConsent.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG AHCHRSN QuestionnaireResponse ExampleFile")
        void testValidateTestShinnyIG_AHCHRSNQuestionnaireResponseExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG AHCHRSN ScreeningResponse Unknown1to8 ExampleFile")
        void testValidateTestShinnyIG_AHCHRSNQuestionnaireResponseUnknownExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-NYScreeningResponseExampleUnknown1to8.json");
        }

        @Test
        @DisplayName("Validate Test SHIN-NY IG AHCHRSN ScreeningResponse Declined9to12 ExampleFile")
        void testValidateTestShinnyIG_AHCHRSNQuestionnaireResponseDeclineExampleFile() throws IOException {
                validateFile("test-shinny-examples/Bundle-NYScreeningResponseExampleDeclined9to12.json");
        }

        private void validateFile(String filePath) throws IOException {
                List<OrchestrationEngine.ValidationResult> results = getValidationErrors(filePath);
                assertValidationResults(results);
        }

        private void assertValidationResults(List<OrchestrationEngine.ValidationResult> results) {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);

                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());
                List<String> errorMessages = operationOutcome.getIssue().stream()
                                .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR)
                                .map(issue -> issue.getDiagnostics())
                                .collect(Collectors.toList());
                if (!errorMessages.isEmpty()) {
                        String formattedErrors = """
                                        There should be no validation errors. Found the following errors:
                                        %s
                                        """.formatted(String.join("\n", errorMessages)); 
                        softly.assertThat(errorMessages)
                                        .withFailMessage(formattedErrors).isEmpty();
                }
                softly.assertAll();
        }

        private List<OrchestrationEngine.ValidationResult> getValidationErrors(final String exampleFileName)
                        throws IOException {
                List<ValidationResult> results = new ArrayList<>();
                final var payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/" + exampleFileName));
                OrchestrationEngine.OrchestrationSession session = engine.session()
                                .withPayloads(List.of(payload))
                                .withSessionId(UUID.randomUUID().toString())
                                .withTracer(tracer)
                                .addHapiValidationEngine()
                                .build();
                try {
                        engine.orchestrate(session);
                        results = engine.getSessions().get(session.getSessionId()).getValidationResults();

                } finally {
                        engine.clear(session);
                }
                return results;
        }
}
