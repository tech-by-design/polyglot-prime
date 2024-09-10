package org.techbd.orchestrate.fhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationIssue;

import java.util.Map;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.List;

import org.assertj.core.api.SoftAssertions;

import java.io.*;
import java.net.*;
import java.net.http.*;

public class IgPublicationIssuesTest {
        private OrchestrationEngine engine;
        private static final String FHIR_PROFILE_URL = "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json";
        private static final Predicate<ValidationIssue> IS_UNEXPECTED_IG_ISSUE = issue -> issue.getMessage()
                        .contains("has not been checked because it is unknown") ||
                        issue.getMessage().contains("Unknown profile") ||
                        issue.getMessage().contains("Unknown extension") ||
                        issue.getMessage().contains("Unknown Code System") ||
                        (issue.getMessage().startsWith("ValueSet") && issue.getMessage().endsWith("not found")) ||

                        issue.getMessage().endsWith(" not found");
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

        @BeforeEach
        void setUp() {
                engine = new OrchestrationEngine();
        }

        @Test
        void testBundle_AHCHRSNScreeningResponseExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_AHCHRSNSCREENINGRESPONSE_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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

        @Test
        void testBundle_AHCHRSNQuestionnaireResponseExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_AHCHRSNQUESTIONAIRE_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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

        @Test
        void testBundle_ObservationAssessmentFoodInsecurityExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_OBSERVATION_ASSESSMENT_FOOD_SECURITY_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_DIAGNOSIS);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);                
                softly.assertThat(unexpectedIgIssues).isZero()
                                .withFailMessage("There should be no IG publication issues");
                throwEachAssertionError(softly);
        }

        @Test
        void testBundle_ServiceRequestExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_SERVICE_REQUEST_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);                
                softly.assertThat(unexpectedIgIssues).isZero()
                                .withFailMessage("There should be no IG publication issues");
                throwEachAssertionError(softly);
        }

        @Test
        void testBundle_TaskCompletedExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_TASK_COMPLETED_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);                
                softly.assertThat(unexpectedIgIssues).isZero()
                                .withFailMessage("There should be no IG publication issues");
                throwEachAssertionError(softly);
        }

        @Test
        void testBundle_TaskExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(URL_TASK_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);                
                softly.assertThat(unexpectedIgIssues).isZero()
                                .withFailMessage("There should be no IG publication issues");
                throwEachAssertionError(softly);
        }

        @Test
        void testBundle_TaskOutputProcedureExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_TASK_OUTPUT_PROCEDURE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_NLM_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTM_CTS_VALUE_SET);                
                softly.assertThat(unexpectedIgIssues).isZero()
                                .withFailMessage("There should be no IG publication issues");
                throwEachAssertionError(softly);
        }

        @Test
        void testBundle_NYScreeningResponseExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                URL_NYSSCREENING_RESPONSE_EXAMPLE);
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(IS_UNEXPECTED_IG_ISSUE)
                                .map(issue -> issue.getMessage().trim())
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
                final boolean containsTest = results.get(0).getIssues().stream()
                                .anyMatch(message -> message.getMessage().contains(unexpectedIgErrorMessage));
                softly.assertThat(containsTest)
                                .withFailMessage(unexpectedIgErrorMessage)
                                .isFalse();
        }

        private List<OrchestrationEngine.ValidationResult> getValidationErrors(final String exampleUrl)
                        throws IOException {
                final var payload = readJsonFromUrl(exampleUrl);
                final OrchestrationEngine.OrchestrationSession session = engine.session()
                                .withPayloads(List.of(payload))
                                .withFhirProfileUrl(FHIR_PROFILE_URL)
                                .withFhirStructureDefinitionUrls(getStructureDefinitionUrls())
                                .withFhirValueSetUrls(getValueSetUrls())
                                .withFhirCodeSystemUrls(getCodeSystemUrls())
                                .addHapiValidationEngine()
                                .build();
                engine.orchestrate(session);
                return engine.getSessions().get(0).getValidationResults();
        }

        private Map<String, String> getStructureDefinitionUrls() {
                final Map<String, String> structureDefUrls = new HashMap<>();
                structureDefUrls.put("shinnyEncounter",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-encounter.json");
                structureDefUrls.put("shinnyConsent",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-Consent.json");
                structureDefUrls.put("shinnyOrganization",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shin-ny-organization.json");
                structureDefUrls.put("shinnyPatient",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-patient.json");
                structureDefUrls.put("shinnyPractitioner",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shin-ny-practitioner.json");
                structureDefUrls.put("shinnyObservationScreeningResponse",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-observation-screening-response.json");
                structureDefUrls.put("shinnyQuestionaire",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-questionnaire.json");
                structureDefUrls.put("shinnyQuestionaireResponse",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-questionnaire-response.json");
                structureDefUrls.put("shinnyObservationAssessment",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-observation-assessment.json");
                structureDefUrls.put("shinnySDOHProcedure",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-sdoh-procedure.json");
                structureDefUrls.put("shinnySDOHServiceRequest",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYSDOHServiceRequest.json");
                structureDefUrls.put("shinnySDOHTaskForReferralManagement",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYSDOHTaskForReferralManagement.json");
                structureDefUrls.put("shinnySDOHCCCondition",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNY-SDOHCC-Condition.json");
                structureDefUrls.put("shinnySDOHCCGoal",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-SDOHCCGoal.json");
                structureDefUrls.put("shinnyObservationSexualOrientation",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shin-ny-observation-sexual-orientation.json");
                structureDefUrls.put("shinnyMetaData",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYMeta.json");
                structureDefUrls.put("shinnyCountry",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-county.json");
                structureDefUrls.put("shinnyMiddleName",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-middle-name.json");
                structureDefUrls.put("shinnyPersonalPronouns",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-personal-pronouns.json");
                structureDefUrls.put("shinnyGenderIdentity",
                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-shinny-gender-identity.json");
                structureDefUrls.put("hl7ObservationScreeningResponse",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/StructureDefinition-SDOHCC-ObservationScreeningResponse.json");
                structureDefUrls.put("hl7UsCoreBirthSex",
                                "http://hl7.org/fhir/us/core/STU7/StructureDefinition-us-core-birthsex.json");
                structureDefUrls.put("hl7UsCoreRace",
                                "http://hl7.org/fhir/us/core/STU7/StructureDefinition-us-core-race.json");
                structureDefUrls.put("hl7UsCoreEthnicity",
                                "http://hl7.org/fhir/us/core/STU7/StructureDefinition-us-core-ethnicity.json");
                structureDefUrls.put("hl7UsCorePractitioner",
                                "http://hl7.org/fhir/us/core/STU7/StructureDefinition-us-core-practitioner.json");
                structureDefUrls.put("hl7UsSDOHCCObservationAssessment",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/StructureDefinition-SDOHCC-ObservationAssessment.json");
                structureDefUrls.put("hl7USECRDisabilityStatus",
                                "https://hl7.org/fhir/us/ecr/2021Jan/StructureDefinition-disability-status.json");
                structureDefUrls.put("hl7SDOHCCProcedure",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/StructureDefinition-SDOHCC-Procedure.json");
                return structureDefUrls;
        }

        private Map<String, String> getCodeSystemUrls() {
                final Map<String, String> codeSystemUrls = new HashMap<>();
                codeSystemUrls.put("nyCountyCodes",
                                "https://shinny.org/ImplementationGuide/HRSN/CodeSystem-nys-county-codes.json");
                codeSystemUrls.put("nyHRSNQuestionnaire",
                                "https://shinny.org/ImplementationGuide/HRSN/CodeSystem-NYS-HRSN-Questionnaire.json");
                codeSystemUrls.put("hl7SDOHCCCodeSystemTemporaryCodes",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/CodeSystem-SDOHCC-CodeSystemTemporaryCodes.json");
                codeSystemUrls.put("hl7CodeSystemConditionCategory",
                                "http://hl7.org/fhir/us/core/STU7/CodeSystem-condition-category.json");
                return codeSystemUrls;
        }

        private Map<String, String> getValueSetUrls() {
                final Map<String, String> valueSetUrls = new HashMap<>();
                valueSetUrls.put("shinnyConsentProvisionTypesVS",
                                "https://shinny.org/ImplementationGuide/HRSN/ValueSet-SHINNYConsentProvisionTypeVS.json");
                valueSetUrls.put("shinnyCountyVS",
                                "https://shinny.org/ImplementationGuide/HRSN/ValueSet-SHINNYCountyVS.json");
                valueSetUrls.put("shinnyHttpVerbsVS",
                                "https://shinny.org/ImplementationGuide/HRSN/ValueSet-SHINNYHTTPVerbsVS.json");
                valueSetUrls.put("shinnyPersonalPronounsVS",
                                "https://shinny.org/ImplementationGuide/HRSN/ValueSet-SHINNYPersonalPronounsVS.json");
                valueSetUrls.put("shinnyScreeningVS",
                                "https://shinny.org/ImplementationGuide/HRSN/ValueSet-SHINNYScreeningVS.json");
                valueSetUrls.put("usCoreOmbRaceCategory",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-omb-race-category.json");
                valueSetUrls.put("usCoreEthnicityCategoty",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-omb-ethnicity-category.json");
                valueSetUrls.put("usCoreUsPsState",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-us-core-usps-state.json");
                valueSetUrls.put("usCoreSimpleLanguage",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-simple-language.json");
                valueSetUrls.put("usCoreEncounterType",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-us-core-encounter-type.json");
                valueSetUrls.put("usCoreValueSetObservationStatus",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/ValueSet-SDOHCC-ValueSetObservationStatus.json");
                valueSetUrls.put("usCoreConditionCode",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-us-core-condition-code.json");
                valueSetUrls.put("usCoreProcedureCode",
                                "http://hl7.org/fhir/us/core/STU7/ValueSet-us-core-procedure-code.json");
                valueSetUrls.put("sdohValueSetObservationStatus",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/ValueSet-SDOHCC-ValueSetObservationStatus.json");
                valueSetUrls.put("sdohValueSetLoincSnomedct",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/ValueSet-SDOHCC-ValueSetLOINCSNOMEDCT.json");
                valueSetUrls.put("sdohValueSetReferralStatus",
                                "http://hl7.org/fhir/us/sdoh-clinicalcare/STU2.1/ValueSet-SDOHCC-ValueSetReferralTaskStatus.json");
                return valueSetUrls;
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
