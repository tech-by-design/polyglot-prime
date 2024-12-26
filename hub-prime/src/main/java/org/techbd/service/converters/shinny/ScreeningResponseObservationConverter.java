
package org.techbd.service.converters.shinny;

import java.util.*;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.*;
import org.techbd.util.CsvConstants;
import org.techbd.util.CsvConversionUtil;
import org.techbd.util.DateUtil;
import ca.uhn.fhir.context.FhirContext;

@Component
@Order(6)
public class ScreeningResponseObservationConverter extends BaseConverter {

    private static final Logger LOG = LoggerFactory.getLogger(ScreeningResponseObservationConverter.class);

    // Constants for URLs and systems
    private static final String OBSERVATION_URL_BASE = "http://shinny.org/us/ny/hrsn/Observation/";
    private static final String CATEGORY_URL = "http://terminology.hl7.org/CodeSystem/observation-category";
    private static final String SDOH_CATEGORY_URL = "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes";
    private static final String LOINC_URL = "http://loinc.org";
    private static final String PROFILE_URL = "http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-screening-response";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String DEFAULT_GROUP_DISPLAY_NAME = "Screening Observation Group";

    private Map<String, List<ScreeningObservationData>> screeningCodeGroups;

    @Override
    public ResourceType getResourceType() {
        return ResourceType.Observation;
    }

    @Override
    public List<BundleEntryComponent> convert(
            Bundle bundle,
            DemographicData demographicData,
            QeAdminData qeAdminData,
            ScreeningProfileData screeningProfileData,
            List<ScreeningObservationData> screeningObservationDataList,
            String interactionId,
            Map<String, String> idsGenerated) {

        LOG.info("ScreeningResponseObservationConverter::convert BEGIN for interaction id: {}", interactionId);
        List<String> interpersonalSafetyQuestions = QUESTIONS_MAP
                .get(CsvConstants.INTERPERSONAL_SAFETY_GROUP_QUESTIONS);
        List<String> ahcScreeningQuestions = QUESTIONS_MAP.get(CsvConstants.AHC_SCREENING_GROUP_QUESTIONS);
        List<String> supplementalQuestions = QUESTIONS_MAP.get(CsvConstants.SUPPLEMENTAL_GROUP_QUESTIONS);
        List<String> totalScoreQuestions = QUESTIONS_MAP.get(CsvConstants.TOTAL_SCORE_QUESTIONS);
        List<String> mentalHealthScoreQuestions = QUESTIONS_MAP.get(CsvConstants.MENTAL_HEALTH_SCORE_QUESTIONS);
        List<String> physicalActivityScoreQuestions = QUESTIONS_MAP.get(CsvConstants.PHYSICAL_ACTIVITY_SCORE_QUESTIONS);

        Map<String, List<Reference>> referencesMap = new HashMap<>();
        Map<String, String> questionAndAnswerCode = new HashMap<>();
        List<BundleEntryComponent> bundleEntryComponents = new ArrayList<>();
        // questionAndAnswerCode.clear();
        for (ScreeningObservationData data : screeningObservationDataList) {
            Observation observation = new Observation();
            String observationId = CsvConversionUtil
                    .sha256(data.getQuestionCodeDisplay().replace(" ", "") +
                            data.getQuestionCode());
            observation.setId(observationId);
            // **********try */ data.setObservationId(observationId);
            String fullUrl = "http://shinny.org/us/ny/hrsn/Observation/" + observationId;
            setMeta(observation);
            Meta meta = observation.getMeta();
            meta.setLastUpdated(DateUtil.parseDate(screeningProfileData.getScreeningLastUpdated()));
            // max date
            // available in all
            // screening records
            observation.setLanguage("en");
            observation
                    .setStatus(Observation.ObservationStatus.fromCode(screeningProfileData.getScreeningStatusCode()));
            if (!data.getObservationCategorySdohCode().isEmpty()) {
                observation.addCategory(createCategory(
                        "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                        data.getObservationCategorySdohCode(),
                        data.getObservationCategorySdohDisplay()));
            } else {
                observation.addCategory(createCategory(
                        "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                        "sdoh-category-unspecified", "SDOH Category Unspecified"));

                if (!data.getObservationCategorySnomedCode().isEmpty()) {
                    observation.addCategory(createCategory("http://snomed.info/sct",
                            data.getObservationCategorySnomedCode(),
                            data.getObservationCategorySnomedDisplay()));
                }
            }
            if (!data.getDataAbsentReasonCode().isEmpty()) {
                CodeableConcept dataAbsentReason = new CodeableConcept();

                dataAbsentReason.addCoding(
                        new Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/data-absent-reason")
                                .setCode(data.getDataAbsentReasonCode())
                                .setDisplay(data.getDataAbsentReasonDisplay()));
                dataAbsentReason.setText(data.getDataAbsentReasonText());

                observation.setDataAbsentReason(dataAbsentReason);
            }
            observation.addCategory(createCategory("http://terminology.hl7.org/CodeSystem/observation-category",
                    "social-history", null));
            observation.addCategory(createCategory("http://terminology.hl7.org/CodeSystem/observation-category",
                    "survey", null));
            CodeableConcept code = new CodeableConcept();
            code.addCoding(new Coding("http://loinc.org", data.getQuestionCode(),
                    data.getQuestionCodeDisplay()));
            code.setText(data.getQuestionCodeText());
            observation.setCode(code);
            observation.setSubject(new Reference("Patient/" +
                    idsGenerated.get(CsvConstants.PATIENT_ID)));
            if (data.getRecordedTime() != null) {
                observation.setEffective(new DateTimeType(DateUtil.parseDate(data.getRecordedTime())));
            }
            observation.setIssued(DateUtil.parseDate(data.getRecordedTime()));
            CodeableConcept value = new CodeableConcept();
            value.addCoding(new Coding("http://loinc.org", data.getAnswerCode(),
                    data.getAnswerCodeDescription()));
            observation.setValue(value);
            questionAndAnswerCode.put(data.getQuestionCode(), data.getAnswerCode());
            // debug
            FhirContext ctx = FhirContext.forR4();
            String jsonString = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(observation);
            LOG.debug("Observation JSON: {}", jsonString);
            BundleEntryComponent entry = new BundleEntryComponent();
            entry.setFullUrl(fullUrl);
            entry.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST)
                    .setUrl("http://shinny.org/us/ny/hrsn/Observation/" + observationId));
            entry.setResource(observation);
            bundleEntryComponents.add(entry);
        }

        try {
            return processScreeningGroups(demographicData, screeningProfileData,
                    screeningObservationDataList, idsGenerated, interactionId);
        } catch (Exception e) {
            LOG.error("Error converting screening observations for interaction {}: {}",
                    interactionId, e.getMessage(), e);
            throw new RuntimeException("Error converting screening observations", e);
        } finally {
            LOG.info("ScreeningResponseObservationConverter::convert END for interaction id: {}", interactionId);
        }
    }

    /**
     * Processes screening observation data into groups and creates corresponding
     * FHIR resources
     * 
     * @param demographicData              Patient demographic information
     * @param screeningProfileData         Screening profile data
     * @param screeningObservationDataList List of screening observations
     * @param idsGenerated                 Map of generated IDs
     * @param interactionId                Interaction identifier
     * @return List of bundle entry components
     */
    private List<BundleEntryComponent> processScreeningGroups(
            DemographicData demographicData,
            ScreeningProfileData screeningProfileData,
            List<ScreeningObservationData> screeningObservationDataList,
            Map<String, String> idsGenerated,
            String interactionId) {

        List<BundleEntryComponent> bundleEntryComponents = new ArrayList<>();

        // Group observations by screening code
        screeningCodeGroups = screeningObservationDataList.stream()
                .collect(Collectors.groupingBy(ScreeningObservationData::getScreeningCode));

        LOG.debug("Found {} different screening code groups", screeningCodeGroups.size());
        logScreeningDetails(screeningObservationDataList);

        // Process each screening code group
        screeningCodeGroups.forEach((screeningCode, groupData) -> {
            LOG.info("Processing screening code: {} with {} observations", screeningCode, groupData.size());

            // Create individual observations and store their IDs
            Map<String, String> individualObservationIds = processIndividualObservations(
                    groupData, demographicData, screeningProfileData, idsGenerated, bundleEntryComponents);

            // Create and add group observation
            BundleEntryComponent groupEntry = createGroupObservation(
                    screeningCode, groupData, demographicData, screeningProfileData,
                    individualObservationIds, idsGenerated, interactionId);
            bundleEntryComponents.add(groupEntry);
        });

        logGroupObservations(bundleEntryComponents);
        return bundleEntryComponents;
    }

    private BundleEntryComponent createGroupObservation(
            String screeningCode,
            List<ScreeningObservationData> groupData,
            DemographicData demographicData,
            ScreeningProfileData screeningProfileData,
            Map<String, String> individualObservationIds,
            Map<String, String> idsGenerated,
            String interactionId) {

        Observation groupObservation = new Observation();
        String observationId = CsvConversionUtil.sha256("group-" + screeningCode);
        groupObservation.setId(observationId);

        // Set meta information
        Meta meta = new Meta();
        meta.addProfile(PROFILE_URL);
        meta.setLastUpdated(DateUtil.parseDate(demographicData.getPatientLastUpdated()));
        groupObservation.setMeta(meta);

        groupObservation.setLanguage("en");

        // Set status
        String screeningStatusCode = screeningProfileData.getScreeningStatusCode();
        if (screeningStatusCode != null && !screeningStatusCode.isEmpty()) {
            groupObservation.setStatus(Observation.ObservationStatus.fromCode(screeningStatusCode));
        } else {
            LOG.warn("No valid screening status code found for interaction id: {}", interactionId);
            groupObservation.setStatus(Observation.ObservationStatus.UNKNOWN);
        }

        // Add standard categories
        groupObservation.addCategory(createCategory(CATEGORY_URL, "social-history", null));
        groupObservation.addCategory(createCategory(CATEGORY_URL, "survey", null));

        // Add SDOH categories from group members
        groupData.stream()
                .map(ScreeningObservationData::getObservationCategorySdohCode)
                .filter(code -> code != null && !code.isEmpty())
                .distinct()
                .forEach(sdohCode -> {
                    ScreeningObservationData data = groupData.stream()
                            .filter(d -> sdohCode.equals(d.getObservationCategorySdohCode()))
                            .findFirst()
                            .get();
                    groupObservation.addCategory(createCategory(
                            SDOH_CATEGORY_URL,
                            sdohCode,
                            data.getObservationCategorySdohDisplay()));
                });

        // Set code
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding(LOINC_URL, screeningCode, getScreeningDisplayName(screeningCode)));
        groupObservation.setCode(code);

        // Set subject, effective time, and issued date
        groupObservation.setSubject(new Reference("Patient/" + idsGenerated.get(CsvConstants.PATIENT_ID)));
        groupObservation.setEncounter(new Reference("Encounter/" + idsGenerated.get(CsvConstants.ENCOUNTER_ID)));
        groupObservation.setEffective(new DateTimeType(new Date()));
        groupObservation.setIssued(new Date());
        CodeableConcept interpretation = new CodeableConcept();
        interpretation.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "POS", "Positive"));
        groupObservation.addInterpretation(interpretation);
        // Add member references
        List<Reference> hasMemberReferences = groupData.stream()
                .map(data -> new Reference("Observation/" + individualObservationIds.get(data.getQuestionCode())))
                .collect(Collectors.toList());
        groupObservation.setHasMember(hasMemberReferences);

        // Create bundle entry
        String fullUrl = OBSERVATION_URL_BASE + observationId;
        BundleEntryComponent groupEntry = new BundleEntryComponent();
        groupEntry.setFullUrl(fullUrl);
        groupEntry.setResource(groupObservation);
        groupEntry.setRequest(new Bundle.BundleEntryRequestComponent()
                .setMethod(HTTPVerb.POST)
                .setUrl("Observation/" + observationId));

        return groupEntry;
    }

    private Map<String, String> processIndividualObservations(
            List<ScreeningObservationData> groupData,
            DemographicData demographicData,
            ScreeningProfileData screeningProfileData,
            Map<String, String> idsGenerated,
            List<BundleEntryComponent> bundleEntryComponents) {

        Map<String, String> individualObservationIds = new HashMap<>();
        groupData.forEach(data -> {
            BundleEntryComponent individualEntry = createIndividualObservation(
                    data, demographicData, screeningProfileData, idsGenerated);
            bundleEntryComponents.add(individualEntry);
            individualObservationIds.put(data.getQuestionCode(), individualEntry.getResource().getId());
        });
        return individualObservationIds;
    }

    private BundleEntryComponent createIndividualObservation(
            ScreeningObservationData data,
            DemographicData demographicData,
            ScreeningProfileData screeningProfileData,
            Map<String, String> idsGenerated) {

        Observation observation = new Observation();
        String observationId = CsvConversionUtil.sha256(data.getQuestionCode());
        observation.setId(observationId);

        // Set meta information
        Meta meta = new Meta();
        meta.addProfile(PROFILE_URL);
        meta.setLastUpdated(DateUtil.parseDate(demographicData.getPatientLastUpdated()));
        observation.setMeta(meta);

        observation.setLanguage("en");

        // Set status
        String statusCode = screeningProfileData.getScreeningStatusCode();
        observation.setStatus(statusCode != null && !statusCode.isEmpty()
                ? Observation.ObservationStatus.fromCode(statusCode)
                : Observation.ObservationStatus.UNKNOWN);

        // Add categories
        if (data.getObservationCategorySdohCode() != null && !data.getObservationCategorySdohCode().isEmpty()) {
            observation.addCategory(createCategory(
                    SDOH_CATEGORY_URL,
                    data.getObservationCategorySdohCode(),
                    data.getObservationCategorySdohDisplay()));
        }
        observation.addCategory(createCategory(CATEGORY_URL, "social-history", null));
        observation.addCategory(createCategory(CATEGORY_URL, "survey", null));

        // Set code
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding(LOINC_URL, data.getQuestionCode(), data.getQuestionCodeDisplay()));
        observation.setCode(code);

        // Set value
        if (data.getAnswerCode() != null && !data.getAnswerCode().isEmpty()) {
            CodeableConcept value = new CodeableConcept();
            value.addCoding(new Coding(LOINC_URL, data.getAnswerCode(), data.getAnswerCodeDescription()));
            observation.setValue(value);
        } else if (data.getDataAbsentReasonCode() != null && !data.getDataAbsentReasonCode().isEmpty()) {
            CodeableConcept dataAbsentReason = new CodeableConcept();
            dataAbsentReason.addCoding(new Coding(LOINC_URL,
                    data.getDataAbsentReasonCode(),
                    data.getDataAbsentReasonDisplay()));
            observation.setDataAbsentReason(dataAbsentReason);
        }

        // Set other fields
        observation.setSubject(new Reference("Patient/" + idsGenerated.get(CsvConstants.PATIENT_ID)));
        Date recordedTime = DateUtil.parseDate(data.getRecordedTime());
        observation.setEffective(new DateTimeType(recordedTime));
        observation.setIssued(recordedTime);

        // Create bundle entry
        String fullUrl = OBSERVATION_URL_BASE + observationId;
        BundleEntryComponent entry = new BundleEntryComponent();
        entry.setFullUrl(fullUrl);
        entry.setResource(observation);
        entry.setRequest(new Bundle.BundleEntryRequestComponent()
                .setMethod(HTTPVerb.POST)
                .setUrl("Observation/" + observationId));

        return entry;
    }

    private String getScreeningDisplayName(String screeningCode) {
        Map<String, String> displayNames = new HashMap<>();
        displayNames.put("96777-8",
                "Accountable health communities (AHC) health-related social needs screening (HRSN) tool");
        displayNames.put("97023-6",
                "Accountable health communities (AHC) health-related social needs (HRSN) supplemental questions");
        return displayNames.getOrDefault(screeningCode, "Screening Observation Group");
    }

    private CodeableConcept createCategory(String system, String code, String display) {
        CodeableConcept category = new CodeableConcept();
        Coding coding = new Coding(system, code, display);
        category.addCoding(coding);
        return category;
    }

    private void logScreeningDetails(List<ScreeningObservationData> screeningObservationDataList) {
        LOG.debug("Initial screeningObservationDataList size: {}", screeningObservationDataList.size());
        LOG.info("Initial screening codes present: {}",
                screeningObservationDataList.stream()
                        .map(ScreeningObservationData::getScreeningCode)
                        .distinct()
                        .collect(Collectors.toList()));
    }

    private void logGroupObservations(List<BundleEntryComponent> bundleEntryComponents) {
        FhirContext ctx = FhirContext.forR4();
        LOG.info("Created {} group observations:", screeningCodeGroups.size());
        bundleEntryComponents.stream()
                .filter(entry -> entry.getResource() instanceof Observation)
                .map(entry -> (Observation) entry.getResource())
                .filter(obs -> !obs.getHasMember().isEmpty())
                .forEach(groupObs -> {
                    String json = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(groupObs);
                    LOG.info("Group Observation JSON for screening code {}: {}",
                            groupObs.getCode().getCodingFirstRep().getCode(), json);
                });
    }
}
