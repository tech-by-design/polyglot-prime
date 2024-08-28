package org.techbd.orchestrate.fhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;

public class IgPublicationIssuesTest {
        private OrchestrationEngine engine;

        private static final Logger LOG = LoggerFactory.getLogger(IgPublicationIssuesTest.class);

        @BeforeEach
        void setUp() {
                engine = new OrchestrationEngine();
        }

        @Test
        void testBundleAHCHRSNQuestionnaireResponseExample() throws IOException {
                final var bundleAHCHRSNQuestionnaireResponseExample = loadFile(
                                "org/techbd/ig-examples/Bundle-AHCHRSNQuestionnaireResponseExample.json");
                final var bundleAHCHRSNScreeningResponseExample = loadFile(
                                "org/techbd/ig-examples/Bundle-AHCHRSNScreeningResponseExample.json");
                final var bundleNYScreeningResponseExample = loadFile(
                                "org/techbd/ig-examples/Bundle-NYScreeningResponseExample.json");
                final var bundleObservationAssessmentFoodInsecurityExample = loadFile(
                                "org/techbd/ig-examples/Bundle-ObservationAssessmentFoodInsecurityExample.json");
                final var bundleServiceRequestExample = loadFile(
                                "org/techbd/ig-examples/Bundle-ServiceRequestExample.json");
                final var bundleTaskCompletedExample = loadFile(
                                "org/techbd/ig-examples/Bundle-TaskCompletedExample.json");
                final var bundleTaskExample = loadFile("org/techbd/ig-examples/Bundle-TaskExample.json");
                final var bundleTaskOutputProcedureExample = loadFile(
                                "org/techbd/ig-examples/Bundle-TaskOutputProcedureExample.json");
                // Construct the orchestration engine using Builder pattern
                OrchestrationEngine.OrchestrationSession session = engine.session()
                                .withPayloads(List.of(bundleAHCHRSNQuestionnaireResponseExample,
                                                bundleAHCHRSNScreeningResponseExample,
                                                bundleNYScreeningResponseExample,
                                                bundleObservationAssessmentFoodInsecurityExample,
                                                bundleServiceRequestExample,
                                                bundleTaskCompletedExample,
                                                bundleTaskExample,
                                                bundleTaskOutputProcedureExample))
                                .withFhirProfileUrl(
                                                "https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json")
                                .withFhirStructureDefinitionUrls(getStructureDefinitionUrls())
                                .withFhirValueSetUrls(getValueSetUrls())
                                .withFhirCodeSystemUrls(getCodeSystemUrls())
                                .addHapiValidationEngine()
                                .build();
                engine.orchestrate(session);
                List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
                assertThat(results).hasSize(8);
                long igIssuesCount = results.stream()
                                .flatMap(result -> result.getIssues().stream())
                                .filter(m -> m.getMessage().contains("has not been checked because it is unknown")
                                                || m.getMessage().contains("Unknown profile")
                                                || m.getMessage().contains("Unknown extension")
                                                || m.getMessage().contains("Unknown Code System")
                                                || m.getMessage().contains("not found"))
                                .distinct()
                                .peek(m -> LOG.warn("IG Issue: Severity: {} Message: {}", m.getSeverity(),
                                                m.getMessage()))
                                .count();
                LOG.warn("Total count of IG publication issues: {}", igIssuesCount);
                // TODO - when the IG publication issues are resolved change the assertion to
                // verify for zero errors
                assertThat(igIssuesCount)
                                .as("There should be exactly 80 IG publication issues.")
                                .isEqualTo(92);
        }

        private Map<String, String> getStructureDefinitionUrls() {
                Map<String, String> structureDefUrls = new HashMap<>();
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
                Map<String, String> codeSystemUrls = new HashMap<>();
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
                Map<String, String> valueSetUrls = new HashMap<>();
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

        private String loadFile(String filename) throws IOException {
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
