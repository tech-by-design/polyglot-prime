package org.techbd.orchestrate.fhir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationResult;

import io.opentelemetry.api.trace.Tracer;

public class IgPublicationIssuesTest {
    @InjectMocks
    private OrchestrationEngine engine;
    @Mock
    private Tracer tracer;
    private static final String FHIR_PROFILE_URL = "https://shinny.org/us/ny/hrsn/StructureDefinition-SHINNYBundleProfile.json";
    // private static final Predicate<ValidationIssue> IS_UNEXPECTED_IG_ISSUE = issue -> issue.getMessage()
    //         .contains("has not been checked because it is unknown") ||
    //         issue.getMessage().contains("Unknown profile") ||
    //         issue.getMessage().contains("Unknown extension") ||
    //         issue.getMessage().contains("Unknown Code System") ||
    //         (issue.getMessage().startsWith("ValueSet") && issue.getMessage().endsWith("not found")) ||

    //         issue.getMessage().endsWith(" not found");
    private static final String ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS = "Unknown extension http://shinny.org/StructureDefinition/shinny-personal-pronouns";
    private static final String ERROR_MESSAGE_CTS_VALUE_SET = "ValueSet 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.24' not found";
    private static final String ERROR_MESSAGE_CTM_CTS_VALUE_SET = "ValueSet 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.32' not found";
    private static final String ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET = "ValueSet 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1240.11' not found";
    private static final String ERROR_MESSAGE_SHINNY_MIDDLE_NAME = "Unknown extension http://shinny.org/StructureDefinition/middle-name";
    private static final String ERROR_MESSAGE_SHNNY_COUNTY = "Unknown extension http://shinny.org/StructureDefinition/county";
    private static final String ERROR_MESSAGE_SHNNY_PATIENT = "Unknown extension http://shinny.org/StructureDefinition/shinny-patient";
    private static final String ERROR_MESSAGE_SHNNY_ENCOUNTER = "Unknown extension http://shinny.org/StructureDefinition/shin-ny-encounter";
    private static final String ERROR_MESSAGE_SHNNY_CONSENT = "Unknown extension http://shinny.org/StructureDefinition/shinny-consent";
    private static final String ERROR_MESSAGE_SHNNY_ORGANIZATION = "Unknown extension http://shinny.org/StructureDefinition/shin-ny-organization";
    private static final String ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE = "Unknown extension http://shinny.org/StructureDefinition/shinny-questionnaire-response";
    private static final String ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE = "Unknown extension http://shinny.org/StructureDefinition/SHINNYBundleProfile";
    private static final String ERROR_MESSAGE_SHINNY_DIAGNOSIS = "Profile reference 'http://shinny.org/StructureDefinition/shinny-diagnosis' has not been checked because it is unknown";
    private static final String ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY = "ValueSet 'http://hl7.org/fhir/us/core/ValueSet/us-core-condition-category' not found";
    private static final String ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST = "Profile reference 'http://shinny.org/StructureDefinition/SHINNYSDOHServiceRequest' has not been checked because it is unknown";
    private static final String ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT = "Profile reference 'http://shinny.org/StructureDefinition//SHINNYSDOHTaskForReferralManagement' has not been checked because it is unknown";
    private static final String URL_AHCHRSNQUESTIONAIRE_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-AHCHRSNQuestionnaireResponseExample.json";
    private static final String URL_AHCHRSNSCREENINGRESPONSE_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-AHCHRSNScreeningResponseExample.json";
    private static final String URL_NYSSCREENING_RESPONSE_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-NYScreeningResponseExample.json";
    private static final String URL_OBSERVATION_ASSESSMENT_FOOD_SECURITY_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-ObservationAssessmentFoodInsecurityExample.json";
    private static final String URL_SERVICE_REQUEST_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-ServiceRequestExample.json";
    private static final String URL_TASK_COMPLETED_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-TaskCompletedExample.json";
    private static final String URL_TASK_EXAMPLE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-TaskExample.json";
    private static final String URL_TASK_OUTPUT_PROCEDURE = "https://shinny.org/ImplementationGuide/HRSN/Bundle-TaskOutputProcedureExample.json";

  
    // @Test
    // void testBundle_AHCHRSNScreeningResponseExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_AHCHRSNSCREENINGRESPONSE_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);

    // }

    // @Test
    // void testBundle_AHCHRSNQuestionnaireResponseExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_AHCHRSNQUESTIONAIRE_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_ObservationAssessmentFoodInsecurityExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_OBSERVATION_ASSESSMENT_FOOD_SECURITY_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_DIAGNOSIS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_ServiceRequestExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_SERVICE_REQUEST_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_TaskCompletedExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_TASK_COMPLETED_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_TaskExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(URL_TASK_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_TaskOutputProcedureExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_TASK_OUTPUT_PROCEDURE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_NYScreeningResponseExample() throws IOException {
    //     final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
    //             URL_NYSSCREENING_RESPONSE_EXAMPLE);
    //     final long unexpectedIgIssues = results.stream()
    //             .flatMap(result -> result.getIssues().stream())
    //             .filter(IS_UNEXPECTED_IG_ISSUE)
    //             .map(issue -> issue.getMessage().trim())
    //             .distinct()
    //             .count();
    //     final var softly = new SoftAssertions();
    //     softly.assertThat(results).hasSize(1);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    //     assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    //     softly.assertThat(unexpectedIgIssues).isZero()
    //             .withFailMessage("There should be no IG publication issues");
    //     throwEachAssertionError(softly);
    // }

    private void throwEachAssertionError(final SoftAssertions softly) {
        final List<AssertionError> errors = softly.assertionErrorsCollected();
        softly.assertAll();
        // Throw each collected error individually
        for (final AssertionError error : errors) {
            throw error;
        }
    }

    // private void assertUnexpectedIgError(final SoftAssertions softly,
    //         final List<OrchestrationEngine.ValidationResult> results,
    //         final String unexpectedIgErrorMessage) {
    //     final boolean containsTest = results.get(0).getIssues().stream()
    //             .anyMatch(message -> message.getMessage().contains(unexpectedIgErrorMessage));
    //     softly.assertThat(containsTest)
    //             .withFailMessage(unexpectedIgErrorMessage)
    //             .isFalse();
    // }

    private List<OrchestrationEngine.ValidationResult> getValidationErrors(final String exampleUrl)
            throws IOException {
        final var payload = readJsonFromUrl(exampleUrl);
        final OrchestrationEngine.OrchestrationSession session = engine.session()
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(FHIR_PROFILE_URL)
                .withFhirIGPackages(getIgPackages())
                .withIgVersion(getIgVersion())
                .addHapiValidationEngine()
                .build();
        engine.orchestrate(session);
        List<ValidationResult> results = engine.getSessions().get(0).getValidationResults();
        engine.clear(session);
        return results;
    }

    private Map<String, Map<String, String>> getIgPackages() {
        final Map<String, Map<String, String>> igPackages = new HashMap<>();

        Map<String, String> igMap = new HashMap<>();
        igMap.put("shinNy", "ig-packages/shin-ny-ig/v1.2.3");
        igMap.put("usCore", "ig-packages/fhir-v4/us-core/stu-7.0.0");
        igMap.put("sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0");
        igMap.put("uvSdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");
        igPackages.put("fhir-v4", igMap);

        return igPackages;
    }

    private String getIgVersion() {
        final String igVersion = "1.2.3";
        return igVersion;
    }

    private static String readJsonFromUrl(final String url) {
        final var client = HttpClient.newHttpClient();
        final var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        String bundleJson = "";
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            bundleJson = response.body();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return bundleJson;
    }
}
