package org.techbd.orchestrate.fhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.techbd.orchestrate.fhir.OrchestrationEngine.ValidationIssue;

import java.util.Map;
import java.util.function.Predicate;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import org.assertj.core.api.SoftAssertions;

public class IgPublicationIssuesTest {
        private OrchestrationEngine engine;
        private static final String FHIR_PROFILE_URL = "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json";
        private static final Predicate<ValidationIssue> IS_UNEXPECTED_IG_ISSUE = issue -> issue.getMessage()
                        .contains("has not been checked because it is unknown") ||
                        issue.getMessage().contains("Unknown profile") ||
                        issue.getMessage().contains("Unknown extension") ||
                        issue.getMessage().contains("Unknown Code System") ||
                        issue.getMessage().contains("not found");
        private static final String ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS = "Unknown extension http://shinny.org/StructureDefinition/shinny-personal-pronouns";
        private static final String ERROR_MESSAGE_CTS_VALUE_SET = "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.24 not found";
        private static final String ERROR_MESSAGE_SHINNY_MIDDLE_NAME = "http://shinny.org/StructureDefinition/middle-name";
        private static final String ERROR_MESSAGE_SHNNY_COUNTY = "http://shinny.org/StructureDefinition/county";
        private static final String ERROR_MESSAGE_SHNNY_PATIENT = "http://shinny.org/StructureDefinition/shinny-patient";
        private static final String ERROR_MESSAGE_SHNNY_ENCOUNTER = "http://shinny.org/StructureDefinition/shin-ny-encounter";
        private static final String ERROR_MESSAGE_SHNNY_CONSENT = "http://shinny.org/StructureDefinition/shinny-consent";
        private static final String ERROR_MESSAGE_SHNNY_ORGANIZATION = "http://shinny.org/StructureDefinition/shin-ny-organization";
        private static final String ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE = "http://shinny.org/StructureDefinition/shinny-questionnaire-response";
        private static final String ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE = "http://shinny.org/StructureDefinition/SHINNYBundleProfile";
        private static final String ERROR_MESSAGE_SHINNY_DIAGNOSIS = "Profile reference 'http://shinny.org/StructureDefinition/shinny-diagnosis' has not been checked because it is unknown";
        private static final String ERROR_MESSAGE_SHINNY_US_CORE_CONDITION_CATEGORY = "ValueSet http://hl7.org/fhir/us/core/ValueSet/us-core-condition-category not found";
        private static final String ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST = "Profile reference 'http://shinny.org/StructureDefinition/SHINNYSDOHServiceRequest' has not been checked because it is unknown";
        private static final String ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT = "Profile reference 'http://shinny.org/StructureDefinition//SHINNYSDOHTaskForReferralManagement' has not been checked because it is unknown";

        @BeforeEach
        void setUp() {
                engine = new OrchestrationEngine();
        }

        @Test
        void testBundle_AHCHRSNScreeningResponseExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-AHCHRSNScreeningResponseExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_CTS_VALUE_SET);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_COUNTY);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_PATIENT);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_ENCOUNTER);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_CONSENT);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_QUESTIONAIRE_RESPONSE);
                assertUnexpectedIgError(softly, results,
                                ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                softly.assertAll();
        }

        @Test
        void testBundle_AHCHRSNQuestionnaireResponseExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
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
                softly.assertAll();
        }

        @Test
        void testBundle_ObservationAssessmentFoodInsecurityExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
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
                softly.assertAll();
        }

        @Test
        void testBundle_ServiceRequestExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-ServiceRequestExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
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
                softly.assertAll();
        }

        @Test
        void testBundle_TaskCompletedExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-TaskCompletedExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_SERVICE_REQUEST);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                softly.assertAll();
        }

        @Test
        void testBundle_TaskExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-TaskExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_SDOH_REFERAL_MANAGEMENT);
                softly.assertAll();
        }

        @Test
        void testBundle_TaskOutputProcedureExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-TaskOutputProcedureExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_PERSONAL_PRONOUNS);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_CTS_VALUE_SET);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHINNY_MIDDLE_NAME);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_COUNTY);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_PATIENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ENCOUNTER);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_CONSENT);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_ORGANIZATION);
                assertUnexpectedIgError(softly, results, ERROR_MESSAGE_SHNNY_BUNDLE_PROFILE);
                softly.assertAll();
        }

        @Test
        void testBundle_NYScreeningResponseExample() throws IOException {
                final List<OrchestrationEngine.ValidationResult> results = getValidationErrors(
                                "org/techbd/ig-examples/Bundle-NYScreeningResponseExample.json");
                final long unexpectedIgIssues = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(message -> IS_UNEXPECTED_IG_ISSUE.test(message))
                                .distinct()
                                .count();
                final var softly = new SoftAssertions();
                softly.assertThat(results).hasSize(1);
                softly.assertThat(unexpectedIgIssues).isZero();
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
                softly.assertAll();
        }

        private void assertUnexpectedIgError(final SoftAssertions softly,
                        final List<OrchestrationEngine.ValidationResult> results,
                        final String unexpectedIgErrorMessage) {
                final boolean containsTest = results.get(0).getIssues().stream()
                                .anyMatch(message -> message.getMessage().contains("test"));
                softly.assertThat(containsTest)
                                .as(unexpectedIgErrorMessage)
                                .isTrue();
        }

        private List<OrchestrationEngine.ValidationResult> getValidationErrors(final String payload)
                        throws IOException {
                final var bundleAHCHRSNQuestionnaireResponseExample = loadFile(payload);
                final OrchestrationEngine.OrchestrationSession session = engine.session()
                                .withPayloads(List.of(bundleAHCHRSNQuestionnaireResponseExample))
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

        private String loadFile(final String filename) throws IOException {
                final var inputStream = getClass().getClassLoader().getResourceAsStream(filename);
                if (inputStream == null) {
                        throw new IOException("Failed to load the file: " + filename);
                }

                try (final var reader = new BufferedReader(
                                new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
                        final var content = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                                content.append(line).append(System.lineSeparator());
                        }
                        return content.toString();
                }
        }
}
