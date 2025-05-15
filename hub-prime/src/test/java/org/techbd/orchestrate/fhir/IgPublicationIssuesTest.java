package org.techbd.orchestrate.fhir;

import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationResult;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
public class IgPublicationIssuesTest extends BaseIgValidationTest {
        private static final Predicate<OperationOutcomeIssueComponent> IS_UNEXPECTED_IG_ISSUE = issue -> issue
                        .getDiagnostics()
                        .contains("has not been checked because it is unknown") ||
                        issue.getDiagnostics().contains("Unknown profile") ||
                        issue.getDiagnostics().contains("Unknown extension") ||
                        issue.getDiagnostics().contains("Unknown Code System") ||
                        (issue.getDiagnostics().startsWith("ValueSet") && issue.getDiagnostics().endsWith("not found"))
                        || issue.getDiagnostics().endsWith(" not found");
        private static final String ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns";
        private static final String ERROR_MESSAGE_CTS_VALUE_SET = "ValueSet 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.24' not found";
        private static final String ERROR_MESSAGE_CTM_CTS_VALUE_SET = "ValueSet 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.32' not found";
        private static final String ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET = "ValueSet 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1240.11' not found";
        private static final String ERROR_MESSAGE_SHINNY_MIDDLE_NAME = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/middle-name";
        private static final String ERROR_MESSAGE_SHNNY_COUNTY = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/county";
        private static final String ERROR_MESSAGE_SHNNY_PATIENT = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/shinny-patient";
        private static final String ERROR_MESSAGE_SHNNY_ENCOUNTER = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-encounter";
        private static final String ERROR_MESSAGE_SHNNY_CONSENT = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/shinny-consent";
        private static final String ERROR_MESSAGE_SHNNY_ORGANIZATION = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-organization";
        private static final String ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/shinny-questionnaire-response";
        private static final String ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE = "Unknown extension https://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile";

        static Stream<Arguments> provideShinnyExampleFiles() {
                return Stream.of(
                                Arguments.of("shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json",
                                                "Validate SHIN-NY Bundle-AHCHRSNQuestionnaireResponseExample.json"),
                                Arguments.of("shinny-examples/Bundle-PatientNegativeConsent.json",
                                                "Validate SHIN-NY Bundle-PatientNegativeConsent.json"),
                                Arguments.of("shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json",
                                                "Validate SHIN-NY Bundle-AHCHRSNScreeningResponseExample.json"),
                                Arguments.of("shinny-examples/Bundle-NYScreeningResponseExample.json",
                                                "Validate SHIN-NY Bundle-NYScreeningResponseExample.json"),
                                Arguments.of("shinny-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json",
                                                "Validate SHIN-NY Bundle-ObservationAssessmentFoodInsecurityExample.json"),
                                Arguments.of("shinny-examples/Bundle-ServiceRequestExample.json",
                                                "Validate SHIN-NY Bundle-ServiceRequestExample.json"),
                                Arguments.of("shinny-examples/Bundle-TaskCompletedExample.json",
                                                "Validate SHIN-NY Bundle-TaskCompletedExample.json"),
                                Arguments.of("shinny-examples/Bundle-TaskExample.json",
                                                "Validate SHIN-NY Bundle-TaskExample.json"),
                                Arguments.of("shinny-examples/Bundle-TaskOutputProcedureExample.json",
                                                "Validate SHIN-NY Bundle-TaskOutputProcedureExample.json"),
                                Arguments.of("shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json",
                                                "Validate SHIN-NY Bundle-AHCHRSNQuestionnaireResponseExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json",
                                                "Validate Test SHIN-NY Bundle-AHCHRSNScreeningResponseExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-NYScreeningResponseExample.json",
                                                "Validate Test SHIN-NY Bundle-NYScreeningResponseExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json",
                                                "Validate Test SHIN-NY Bundle-ObservationAssessmentFoodInsecurityExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-ServiceRequestExample.json",
                                                "Validate Test SHIN-NY Bundle-ServiceRequestExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-TaskCompletedExample.json",
                                                "Validate Test SHIN-NY Bundle-TaskCompletedExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-TaskExample.json",
                                                "Validate Test SHIN-NY Bundle-TaskExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-TaskOutputProcedureExample.json",
                                                "Validate Test SHIN-NY Bundle-TaskOutputProcedureExample.json"),
                                Arguments.of("test-shinny-examples/Bundle-PatientNegativeConsent.json",
                                                "Validate Test SHIN-NY Bundle-PatientNegativeConsent.json"),
                                Arguments.of("test-shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json",
                                                "Validate Test SHIN-NY Bundle-AHCHRSNQuestionnaireResponseExample.json"));
        }

        @ParameterizedTest(name = "{index} - Validating: {1}")
        @MethodSource("provideShinnyExampleFiles")
        @DisplayName("Validate SHIN-NY Example Bundle")
        void testValidateIgExamples(String filePath, String displayName) throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(filePath);

                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());

                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                final long unexpectedIgIssues = issues.stream()
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getDiagnostics().trim())
                                .distinct()
                                .count();

                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);

                softly.assertThat(unexpectedIgIssues)
                                .isZero()
                                .withFailMessage("There should be no IG publication issues");

                throwEachAssertionError(softly);
        }

        private void throwEachAssertionError(final SoftAssertions softly) {
                final List<AssertionError> errors = softly.assertionErrorsCollected();
                softly.assertAll();
                // Throw each collected error individually
                for (final AssertionError error : errors) {
                        throw error;
                }
        }

        private void assertUnexpectedIgError(final SoftAssertions softly,
                        final List<OrchestrationEngine.ValidationResult> results,
                        final String unexpectedIgErrorMessage) {
                IParser parser = FhirContext.forR4().newJsonParser();
                OperationOutcome operationOutcome = (OperationOutcome) parser
                                .parseResource(results.get(0).getOperationOutcome());

                List<OperationOutcomeIssueComponent> issues = operationOutcome.getIssue();
                final boolean containsTest = issues.stream()
                                .anyMatch(message -> message.getDiagnostics().contains(unexpectedIgErrorMessage));
                softly.assertThat(containsTest)
                                .withFailMessage(unexpectedIgErrorMessage)
                                .isFalse();
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
                        sessionSpy = spy(session);
                        engine.orchestrate(session);
                        results = engine.getSessions().get(0).getValidationResults();

                } finally {
                        engine.clear(session);
                }
                return results;
        }
}
