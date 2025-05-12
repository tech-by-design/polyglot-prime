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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.orchestrate.fhir.OrchestrationEngine.HapiValidationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.OrchestrationSession;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationResult;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;
import org.techbd.util.FHIRUtil;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

@ExtendWith(MockitoExtension.class)
public class IgPublicationIssuesTest {
    private static final String SHINNY_FHIR_PROFILE_URL = "https://shinny.org/us/ny/hrsn/StructureDefinition-SHINNYBundleProfile.json";
    private static final String TEST_SHINNY_FHIR_PROFILE_URL = "https://shinny.org/us/ny/hrsn/StructureDefinition-SHINNYBundleProfile.json";
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

    @Disabled
    @Test
    void testAHCHRSNScreeningResponseExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json", SHINNY_FHIR_PROFILE_URL);

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

    @Disabled
    @Test
    void testAHCHRSNQuestionnaireResponseExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testObservationAssessmentFoodInsecurityExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_DIAGNOSIS);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testServiceRequestExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-ServiceRequestExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testTaskCompletedExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-TaskCompletedExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testTaskExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-TaskExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testTaskOutputProcedureExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-TaskOutputProcedureExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testNYScreeningResponseExampleAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "shinny-examples/Bundle-NYScreeningResponseExample.json", SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testPatientNegativeConsentAgainstShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-PatientNegativeConsent.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testAHCHRSNScreeningResponseExampleAgainstTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-AHCHRSNScreeningResponseExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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

    @Disabled
    @Test
    void testAHCHRSNQuestionnaireResponseExampleAgainstTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testObservationAssessmentFoodInsecurityExampleAgainstTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json",
                TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_DIAGNOSIS);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testServiceRequestExampleAgainstTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-ServiceRequestExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testTaskCompletedExampleAgainstTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-TaskCompletedExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testTaskExampleAgainstTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-TaskExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testTaskOutputProcedureExampleTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-TaskOutputProcedureExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testNYScreeningResponseExampleTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-NYScreeningResponseExample.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
                .withFailMessage("There should be no IG publication issues");
        throwEachAssertionError(softly);
    }

    @Disabled
    @Test
    void testPatientNegativeConsentTestShinnyIG() throws IOException {
        final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                "test-shinny-examples/Bundle-PatientNegativeConsent.json", TEST_SHINNY_FHIR_PROFILE_URL);

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
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
        assertUnexpectedIgError(softly, results,
                ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
        assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);
        softly.assertThat(unexpectedIgIssues).isZero()
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

    private List<OrchestrationEngine.ValidationResult> getValidationErrors(final String exampleFileName,
            final String profileUrl)
            throws IOException {
        List<ValidationResult> results = new ArrayList<>();
        final var payload = Files.readString(Path.of(
                "src/test/resources/org/techbd/ig-examples/" + exampleFileName));
        try {
            final OrchestrationEngine.OrchestrationSession session = engine.session()
                    .withPayloads(List.of(payload))
                    .withFhirProfileUrl(profileUrl)
                    .withTracer(tracer)
                    .withFhirIGPackages(getIgPackages())
                    .withIgVersion(getIgVersion())
                    .addHapiValidationEngine()
                    .build();
            sessionSpy = spy(session);
            engine.orchestrate(session);
            results = engine.getSessions().get(0).getValidationResults();

        } finally {
            engine.clear(session);
        }
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
        shinnyV123.put("package-path", "ig-packages/shin-ny-ig/shinny/v1.3.0");
        shinnyV123.put("ig-version", "1.3.0");
        shinnyPackages.put("shinny-v1-3-0", shinnyV123);

        // Test Shinny version 1.3.0
        Map<String, String> testShinnyV130 = new HashMap<>();
        testShinnyV130.put("profile-base-url", "http://test.shinny.org/us/ny/hrsn");
        testShinnyV130.put("package-path", "ig-packages/shin-ny-ig/test-shinny/v1.4.5");
        testShinnyV130.put("ig-version", "1.4.5");
        shinnyPackages.put("test-shinny-v1-4-5", testShinnyV130);

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
