package org.techbd.orchestrate.fhir;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.techbd.orchestrate.fhir.OrchestrationEngine.HapiValidationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.OrchestrationSession;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationResult;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;
import org.techbd.util.FHIRUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

public class IgPublicationIssuesTest {
    private static final String FHIR_PROFILE_URL = "https://shinny.org/us/ny/hrsn/StructureDefinition-SHINNYBundleProfile.json";
    private static final Predicate<OperationOutcomeIssueComponent> IS_UNEXPECTED_IG_ISSUE = issue -> issue
            .getDiagnostics()
            .contains("has not been checked because it is unknown") ||
            issue.getDiagnostics().contains("Unknown profile") ||
            issue.getDiagnostics().contains("Unknown extension") ||
            issue.getDiagnostics().contains("Unknown Code System") ||
            (issue.getDiagnostics().startsWith("ValueSet") && issue.getDiagnostics().endsWith("not found")) ||

            issue.getDiagnostics().endsWith(" not found");
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
    private static final String ERROR_MESSAGE_SHINNY_DIAGNOSIS = "Profile reference 'https://shinny.org/us/ny/hrsn/StructureDefinition/shinny-diagnosis' has not been checked because it is unknown";
    private static final String ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY = "ValueSet 'http://hl7.org/fhir/us/core/ValueSet/us-core-condition-category' not found";
    private static final String ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST = "Profile reference 'https://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYSDOHServiceRequest' has not been checked because it is unknown";
    private static final String ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT = "Profile reference 'https://shinny.org/us/ny/hrsn/StructureDefinition//SHINNYSDOHTaskForReferralManagement' has not been checked because it is unknown";
    private static final String URL_AHCHRSNQUESTIONAIRE_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-AHCHRSNQuestionnaireResponseExample.json";
    private static final String URL_AHCHRSNSCREENINGRESPONSE_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-AHCHRSNScreeningResponseExample.json";
    private static final String URL_NYSSCREENING_RESPONSE_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-NYScreeningResponseExample.json";
    private static final String URL_OBSERVATION_ASSESSMENT_FOOD_SECURITY_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-ObservationAssessmentFoodInsecurityExample.json";
    private static final String URL_SERVICE_REQUEST_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-ServiceRequestExample.json";
    private static final String URL_TASK_COMPLETED_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-TaskCompletedExample.json";
    private static final String URL_TASK_EXAMPLE = "https://shinny.org/us/ny/hrsn/Bundle-TaskExample.json";
    private static final String URL_TASK_OUTPUT_PROCEDURE = "https://shinny.org/us/ny/hrsn/Bundle-TaskOutputProcedureExample.json";

    @InjectMocks
    private OrchestrationEngine engine;
    @Mock
    private Tracer tracer;

    @Mock
    private FhirBundleValidator mockValidator;
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
    private static final String INTERACTION_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws Exception {
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        spyHapiEngine = spy(new HapiValidationEngine.Builder()
                .withFhirProfileUrl(
                        "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile")
                .withIgPackages(getIgPackages())
                .withInteractionId(INTERACTION_ID)
                .withTracer(tracer)
                .build());
        Field profileMapField = FHIRUtil.class.getDeclaredField("PROFILE_MAP");
        profileMapField.setAccessible(true);
        profileMapField.set(null, getProfileMap());
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

    @Test
    void testBundle_AHCHRSNScreeningResponseExample() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors("Bundle-AHCHRSNScreeningResponseExample.json");

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
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);

    }

    // @Test
    // void testBundle_AHCHRSNQuestionnaireResponseExample() throws IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(
    // URL_AHCHRSNQUESTIONAIRE_EXAMPLE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_ObservationAssessmentFoodInsecurityExample() throws
    // IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(
    // URL_OBSERVATION_ASSESSMENT_FOOD_SECURITY_EXAMPLE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_DIAGNOSIS);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_ServiceRequestExample() throws IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(
    // URL_SERVICE_REQUEST_EXAMPLE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_TaskCompletedExample() throws IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(
    // URL_TASK_COMPLETED_EXAMPLE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_TaskExample() throws IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(URL_TASK_EXAMPLE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_TaskOutputProcedureExample() throws IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(
    // URL_TASK_OUTPUT_PROCEDURE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

    // @Test
    // void testBundle_NYScreeningResponseExample() throws IOException {
    // final List<OrchestrationEngine.ValidationResult> results =
    // getValidationErrors(
    // URL_NYSSCREENING_RESPONSE_EXAMPLE);
    // final long unexpectedIgIssues = results.stream()
    // .flatMap(result -> result.getIssues().stream())
    // .filter(IS_UNEXPECTED_IG_ISSUE)
    // .map(issue -> issue.getMessage().trim())
    // .distinct()
    // .count();
    // final var softly = new SoftAssertions();
    // softly.assertThat(results).hasSize(1);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
    // assertUnexpectedIgError(softly, results,
    // ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
    // assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
    // softly.assertThat(unexpectedIgIssues).isZero()
    // .withFailMessage("There should be no IG publication issues");
    // throwEachAssertionError(softly);
    // }

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
        final var  payload = Files.readString(Path.of(
                                "src/test/resources/org/techbd/ig-examples/shinny-examples/"+exampleFileName));         
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

    private Map<String, FhirV4Config> getIgPackages() {
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
        shinnyV123.put("package-path", "ig-packages/shinny-packages/shinny/v1.2.3");
        shinnyV123.put("ig-version", "1.2.3");
        shinnyPackages.put("shinny-v1-2-3", shinnyV123);

        // Test Shinny version 1.3.0
        Map<String, String> testShinnyV130 = new HashMap<>();
        testShinnyV130.put("profile-base-url", "http://test.shinny.org/us/ny/hrsn");
        testShinnyV130.put("package-path", "ig-packages/shinny-packages/test-shinny/v1.3.0");
        testShinnyV130.put("ig-version", "1.3.0");
        shinnyPackages.put("test-shinny-v1-3-0", testShinnyV130);

        fhirV4Config.setBasePackages(basePackages);
        fhirV4Config.setShinnyPackages(shinnyPackages);
        igPackages.put("fhir-v4", fhirV4Config);

        return igPackages;
    }

    private String getIgVersion() {
        final String igVersion = "1.3.0";
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
