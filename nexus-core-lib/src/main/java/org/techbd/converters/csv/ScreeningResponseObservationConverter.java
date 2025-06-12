package org.techbd.converters.csv;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.csv.CsvConstants;
import org.techbd.util.csv.CsvConversionUtil;
import org.techbd.util.DateUtil;

@Component
@Order(6)
public class ScreeningResponseObservationConverter extends BaseConverter {

        public ScreeningResponseObservationConverter(CodeLookupService codeLookupService) {
                super(codeLookupService);
        }

        private static final Logger LOG = LoggerFactory.getLogger(ScreeningResponseObservationConverter.class);

        // Constants for URLs and systems
        private static final String OBSERVATION_URL_BASE = "http://shinny.org/us/ny/hrsn/Observation/";
        private static final String CATEGORY_URL = "http://terminology.hl7.org/CodeSystem/observation-category";
        private static final String SDOH_CATEGORY_URL = "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes";

        private static final Set<String> INTERPERSONAL_SAFETY_REFS = Set.of(
                        "95618-5",
                        "95617-7",
                        "95616-9",
                        "95615-1");

        private static final Set<String> PHYSICAL_ACTIVITY_REFS = Set.of(
                        "89555-7",
                        "68516-4");

        private static final Set<String> MENTAL_STATE_REFS = Set.of(
                        "44250-9",
                        "44255-8");

        private static final Map<String, Set<String>> QUESTION_CODE_REF_MAP = Map.of(
                        "95614-4", INTERPERSONAL_SAFETY_REFS,
                        "77594-0", PHYSICAL_ACTIVITY_REFS,
                        "71969-0", MENTAL_STATE_REFS);

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
                        Map<String, String> idsGenerated,String baseFHIRUrl) {

                LOG.info("ScreeningResponseObservationConverter::convert BEGIN for interaction id: {}", interactionId);
                Map<String, String> questionAndAnswerCode = new HashMap<>();
                List<BundleEntryComponent> bundleEntryComponents = new ArrayList<>();
                Map<String, List<Reference>> derivedFromMap = new HashMap<>();

                for (ScreeningObservationData data : screeningObservationDataList) {
                        Observation observation = new Observation();
                        String observationId = data.getScreeningIdentifier();
                        String observationIdHashed = CsvConversionUtil.sha256(observationId);
                        // CsvConversionUtil
                        //                 .sha256(data.getQuestionCodeDescription().replace(" ", "") +
                        //                                 data.getQuestionCode() + data.getEncounterId());
                        observation.setId(observationIdHashed);
                        data.setObservationId(observationId);
                        String fullUrl = "http://shinny.org/us/ny/hrsn/Observation/" + observationIdHashed;
                        setMeta(observation,baseFHIRUrl);
                        Meta meta = observation.getMeta();
                        if (StringUtils.isNotEmpty(screeningProfileData.getScreeningLastUpdated())) {
                                meta.setLastUpdated(DateUtil.convertStringToDate(screeningProfileData.getScreeningLastUpdated()));
                        } else {
                                meta.setLastUpdated(new Date());
                        }
                        populateScreeningIdentifier(observation, data);
                        // max date
                        // available in all
                        // screening records
                        observation.setLanguage(fetchCode(screeningProfileData.getScreeningLanguageCode(), CsvConstants.SCREENING_LANGUAGE_CODE, interactionId));
                        observation
                                        .setStatus(Observation.ObservationStatus
                                                        .fromCode(fetchCode(screeningProfileData.getScreeningStatusCode(), CsvConstants.SCREENING_STATUS_CODE, interactionId)));
                        if (!data.getObservationCategorySdohCode().isEmpty()) {
                                observation.addCategory(createCategory(
                                                "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                                                fetchCode(data.getObservationCategorySdohCode(), CsvConstants.OBSERVATION_CATEGORY_SDOH_CODE, interactionId),
                                                data.getObservationCategorySdohText()));
                        } else {
                                observation.addCategory(createCategory(
                                                "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                                                "sdoh-category-unspecified", "SDOH Category Unspecified"));

                        }

                        Set<String> excludedQuestionCodes = Set.of("95614-4", "77594-0", "71969-0");
                        if (!excludedQuestionCodes.contains(data.getQuestionCode())) {
                            if ("96778-6".equals(data.getQuestionCode())) {
                                if (!data.getAnswerCode().isEmpty() && !data.getAnswerCodeDescription().isEmpty()) {
                                    Observation.ObservationComponentComponent component = new Observation.ObservationComponentComponent();

                                    CodeableConcept componentCode = new CodeableConcept();
                                    componentCode.addCoding(new Coding(
                                            fetchSystem(data.getQuestionCodeSystem(), CsvConstants.QUESTION_CODE,
                                                    interactionId),
                                            data.getQuestionCode(),
                                            fetchDisplay(data.getQuestionCode(), data.getQuestionCodeDescription(), CsvConstants.QUESTION_CODE, interactionId)));
                                    component.setCode(componentCode);

                                    CodeableConcept value = new CodeableConcept();
                                    String answerCode = fetchCode(data.getAnswerCode(), CsvConstants.ANSWER_CODE, interactionId);
                                    value.addCoding(new Coding(
                                            fetchSystem(data.getAnswerCodeSystem(), CsvConstants.ANSWER_CODE, interactionId),
                                            answerCode,
                                            fetchDisplay(answerCode, data.getAnswerCodeDescription(), CsvConstants.ANSWER_CODE, interactionId)));
                                    component.setValue(value);

                                    observation.addComponent(component);
                                }
                            } else {
                                if (!data.getAnswerCode().isEmpty() && !data.getAnswerCodeDescription().isEmpty()) {
                                    CodeableConcept value = new CodeableConcept();
                                    String answerCode = fetchCode(data.getAnswerCode(), CsvConstants.ANSWER_CODE, interactionId);
                                    value.addCoding(new Coding(
                                            fetchSystem(data.getAnswerCodeSystem(), CsvConstants.ANSWER_CODE,
                                                    interactionId),
                                            answerCode,
                                            fetchDisplay(answerCode, data.getAnswerCodeDescription(), CsvConstants.ANSWER_CODE, interactionId)));
                                    observation.setValue(value);
                                } else if (!data.getDataAbsentReasonCode().isEmpty()) {
                                    CodeableConcept dataAbsentReason = new CodeableConcept();
                                    dataAbsentReason.addCoding(
                                            new Coding()
                                                    .setSystem(
                                                            "http://terminology.hl7.org/CodeSystem/data-absent-reason")
                                                    .setCode(fetchCode(data.getDataAbsentReasonCode(),
                                                            CsvConstants.DATA_ABSENT_REASON_CODE, interactionId))
                                                    .setDisplay(data.getDataAbsentReasonDisplay()));
                                    observation.setDataAbsentReason(dataAbsentReason);
                                }
                            }
                        }        
                        observation.addCategory(
                                        createCategory("http://terminology.hl7.org/CodeSystem/observation-category",
                                                        "social-history", null));
                        observation.addCategory(
                                        createCategory("http://terminology.hl7.org/CodeSystem/observation-category",
                                                        "survey", null));
                        CodeableConcept code = new CodeableConcept();
                        code.addCoding(new Coding(fetchSystem(data.getQuestionCodeSystem(), CsvConstants.QUESTION_CODE, interactionId), data.getQuestionCode(),
                                        fetchDisplay(data.getQuestionCode(), data.getQuestionCodeDescription(), CsvConstants.QUESTION_CODE, interactionId)));
                        observation.setCode(code);
                        observation.setSubject(new Reference("Patient/" +
                                idsGenerated.get(CsvConstants.PATIENT_ID)));
                        if (data.getScreeningStartDateTime() != null && data.getScreeningEndDateTime() != null) {
                            Period period = new Period();
                            period.setStartElement(
                                    new DateTimeType(DateUtil.convertStringToDate(data.getScreeningStartDateTime())));
                            period.setEndElement(
                                    new DateTimeType(DateUtil.convertStringToDate(data.getScreeningEndDateTime())));
                            observation.setEffective(period);
                        } else if (data.getScreeningStartDateTime() != null) {
                            observation.setEffective(
                                    new DateTimeType(DateUtil.convertStringToDate(data.getScreeningStartDateTime())));
                        }
                        observation.setIssued(DateUtil.convertStringToDate(data.getScreeningStartDateTime()));
                        String encounterId = idsGenerated.getOrDefault(CsvConstants.ENCOUNTER_ID, null);
                        if (encounterId != null) {
                            observation.setEncounter(new Reference("Encounter/" + encounterId));
                        }
                        String organizationId = idsGenerated.getOrDefault(CsvConstants.ORGANIZATION_ID, null);
                        if (organizationId != null) {
                            observation.addPerformer(new Reference("Organization/" + organizationId));
                        }
                        CodeableConcept interpretation = new CodeableConcept();
                        interpretation.addCoding(
                                new Coding("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                                        fetchCode(data.getPotentialNeedIndicated(), CsvConstants.POTENTIAL_NEED_INDICATED, interactionId), "Positive"));
                        observation.addInterpretation(interpretation);
                        //TO-DO
                        questionAndAnswerCode.put(data.getQuestionCode(), data.getAnswerCode());

                        switch (data.getQuestionCode()) {
                                case "95614-4", "71969-0" ->  { // Interpersonal safety or Mental state
                                        if (!data.getAnswerCodeDescription().isEmpty()) {
                                                CodeableConcept coding = new CodeableConcept();
                                                String answerCode = fetchCode(data.getAnswerCode(), CsvConstants.ANSWER_CODE, interactionId);
                                                coding.addCoding(new Coding("http://unitsofmeasure.org", null,
                                                                "{Number}"));
                                                coding.setText(fetchDisplay(answerCode, data.getAnswerCodeDescription(), CsvConstants.ANSWER_CODE, interactionId));
                                                observation.setValue(coding);
                                        } else if (!data.getDataAbsentReasonCode().isEmpty()) {
                                                CodeableConcept dataAbsentReason = new CodeableConcept();
                
                                                dataAbsentReason.addCoding(
                                                        new Coding()
                                                        .setSystem("http://terminology.hl7.org/CodeSystem/data-absent-reason")
                                                        .setCode(fetchCode(data.getDataAbsentReasonCode(), CsvConstants.DATA_ABSENT_REASON_CODE, interactionId) )
                                                        .setDisplay(data.getDataAbsentReasonDisplay()));
                
                                                observation.setDataAbsentReason(dataAbsentReason);
                                        }
                                }
                                case "77594-0" ->  { // Physical Activity
                                        Quantity quantity = new Quantity();
                                        String answerCode = fetchCode(data.getAnswerCode(), CsvConstants.ANSWER_CODE, interactionId);
                                         String codeDescription = fetchDisplay(answerCode, data.getAnswerCodeDescription(), CsvConstants.ANSWER_CODE, interactionId);
                                          if (codeDescription != null && !codeDescription.isEmpty()) {
                                                try{
                                                      Optional<Double> doubleValue = Optional.of(Double.valueOf(codeDescription));
                                                        doubleValue.ifPresent(doubleVal -> {
                                                            quantity.setValue(doubleVal);
                                                        });
                                               }catch(NumberFormatException nfe){
                                                LOG.warn("Unexpected value: "+codeDescription+" could not be converted to a double.", nfe);
                                                quantity.setValue(0.0);
                                               }
                                        }  else if (!data.getDataAbsentReasonCode().isEmpty()) {
                                                CodeableConcept dataAbsentReason = new CodeableConcept();
                
                                                dataAbsentReason.addCoding(
                                                        new Coding()
                                                        .setSystem("http://terminology.hl7.org/CodeSystem/data-absent-reason")
                                                        .setCode(fetchCode(data.getDataAbsentReasonCode(), CsvConstants.DATA_ABSENT_REASON_CODE, interactionId) )
                                                        .setDisplay(data.getDataAbsentReasonDisplay()));
                
                                                observation.setDataAbsentReason(dataAbsentReason);
                                        } else {
                                            quantity.setValue(0.0);
                                            quantity.setUnit("minutes per week");
                                            quantity.setSystem("http://unitsofmeasure.org");
                                            observation.setValue(quantity);
                                        }
                                }
                                default -> {
                        }

                        }

                        if (QUESTION_CODE_REF_MAP.containsKey(data.getQuestionCode())) {
                                Set<String> questionCodeSet = QUESTION_CODE_REF_MAP.get(data.getQuestionCode());
                                List<Reference> derivedFromRefs = screeningObservationDataList.stream()
                                                .filter(obs -> questionCodeSet.contains(obs.getQuestionCode()))
                                                .map(obs -> {
                                                        String derivedFromId = CsvConversionUtil.sha256(obs.getObservationId());
                                                        return new Reference("Observation/" + derivedFromId);
                                                })
                                                .collect(Collectors.toList());
                                derivedFromMap.put(observationId, derivedFromRefs);
                        }
                        List<Reference> derivedRefs = derivedFromMap.get(observationId);

                        if (derivedRefs != null && !derivedRefs.isEmpty()) {
                                observation.setDerivedFrom(derivedRefs);
                                if (LOG.isDebugEnabled()) {
                                        derivedRefs.forEach(
                                                        ref -> LOG.debug("Added reference {} for the observation {}",
                                                                        ref.getReference(), observationId));
                                }
                        }

                        BundleEntryComponent entry = new BundleEntryComponent();
                        entry.setFullUrl(fullUrl);
                        entry.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST)
                                        .setUrl("http://shinny.org/us/ny/hrsn/Observation/" + observationIdHashed));
                        entry.setResource(observation);
                        bundleEntryComponents.add(entry);
                }

                try {
                        return processScreeningGroups(demographicData, screeningProfileData,
                                        screeningObservationDataList, idsGenerated, interactionId, bundleEntryComponents,baseFHIRUrl);
                } catch (Exception e) {
                        LOG.error("Error converting screening observations for interaction {}: {}",
                                        interactionId, e.getMessage(), e);
                        throw new RuntimeException("Error converting screening observations", e);
                } finally {
                        LOG.info("ScreeningResponseObservationConverter::convert END for interaction id: {}",
                                        interactionId);
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
                        String interactionId, List<BundleEntryComponent> bundleEntryComponents,String baseFhirUrl) {

                // Group observations by screening code
                Map<String, List<ScreeningObservationData>> screeningCodeGroups = screeningObservationDataList.stream()
                                .collect(Collectors.groupingBy(
                                                data -> fetchCode(data.getScreeningCode(), CsvConstants.SCREENING_CODE,
                                                                interactionId)));

                LOG.debug("Found {} different screening code groups", screeningCodeGroups.size());
                // logScreeningDetails(screeningObservationDataList);

                // Process each screening code group
                screeningCodeGroups.forEach((screeningCode, groupData) -> {
                        LOG.info("Processing screening code: {} with {} observations", screeningCode,
                                        groupData.size());

                        // Create and add group observation
                        BundleEntryComponent groupEntry = createGroupObservation(
                                        screeningCode, groupData, demographicData, screeningProfileData,
                                        idsGenerated, interactionId,baseFhirUrl);
                        bundleEntryComponents.add(groupEntry);
                });

                // logGroupObservations(bundleEntryComponents);
                return bundleEntryComponents;
        }

        /**
         * Creates a group observation
         */
        private BundleEntryComponent createGroupObservation(
                        String screeningCode,
                        List<ScreeningObservationData> groupData,
                        DemographicData demographicData,
                        ScreeningProfileData screeningProfileData,
                        Map<String, String> idsGenerated,
                        String interactionId,String baseFhirUrl) {

                Observation groupObservation = new Observation();
                String observationId = CsvConversionUtil.sha256("group-" + screeningCode + screeningProfileData.getEncounterId());   
                groupObservation.setId(observationId);
                setMeta(groupObservation,baseFhirUrl);
                Meta meta = groupObservation.getMeta();
                if (StringUtils.isNotEmpty(screeningProfileData.getScreeningLastUpdated())) {
                        meta.setLastUpdated(
                                        DateUtil.convertStringToDate(screeningProfileData.getScreeningLastUpdated()));
                } else {
                        meta.setLastUpdated(new Date());
                }
                groupObservation.setMeta(meta);
                groupObservation.setLanguage(fetchCode(screeningProfileData.getScreeningLanguageCode(), CsvConstants.SCREENING_LANGUAGE_CODE, interactionId));

                // Set status from screening profile
                String screeningStatusCode = screeningProfileData.getScreeningStatusCode();
                if (screeningStatusCode != null && !screeningStatusCode.isEmpty()) {
                        screeningStatusCode = fetchCode(screeningStatusCode, CsvConstants.SCREENING_STATUS_CODE, interactionId);
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
                                .map(data -> fetchCode(data.getObservationCategorySdohCode(),
                                                CsvConstants.OBSERVATION_CATEGORY_SDOH_CODE, interactionId))
                                .filter(code -> code != null && code instanceof String && !((String) code).isEmpty())
                                .distinct()
                                .forEach(sdohCode -> {
                                        ScreeningObservationData data = groupData.stream()
                                                        .filter(d -> fetchCode(d.getObservationCategorySdohCode(),
                                                                        CsvConstants.OBSERVATION_CATEGORY_SDOH_CODE, interactionId)
                                                                        .equals(sdohCode))
                                                        .findFirst()
                                                        .get();
                                        groupObservation.addCategory(createCategory(
                                                        SDOH_CATEGORY_URL,
                                                        sdohCode,
                                                        data.getObservationCategorySdohText()));
                                });

                // Set code from groupData (take from first available)
                ScreeningObservationData firstData = groupData.stream().findFirst().orElse(null);
                if (firstData != null) {
                    CodeableConcept code = new CodeableConcept();

                        String scrngCode = fetchCode(firstData.getScreeningCode(), CsvConstants.SCREENING_CODE, interactionId);
                        String system = fetchSystem(firstData.getScreeningCodeSystem(), CsvConstants.SCREENING_CODE, interactionId);
                        String display = firstData.getScreeningCodeDescription();

                if ("NYSAHCHRSN".equalsIgnoreCase(scrngCode) || "NYS-AHC-HRSN".equalsIgnoreCase(scrngCode)) {
                        code.addCoding(new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("100698-0")
                        .setDisplay("Social Determinants of Health screening report Document"));

                        code.addCoding(new Coding()
                        .setSystem(system)
                        .setCode(scrngCode)
                        .setDisplay(display));
                } else {
                        code.addCoding(new Coding()
                        .setSystem(system)
                        .setCode(scrngCode)
                        .setDisplay(display));
                }

                    groupObservation.setCode(code);
                }   

                // Set subject, effective time, and issued date
                String patientId = idsGenerated.getOrDefault(CsvConstants.PATIENT_ID, null);
                if (patientId != null){
                        groupObservation.setSubject(new Reference("Patient/" + patientId));
                }
                String encounterId = idsGenerated.getOrDefault(CsvConstants.ENCOUNTER_ID, null);
                if (encounterId != null){
                        groupObservation.setEncounter( new Reference("Encounter/" + encounterId));
                }
                String organizationId = idsGenerated.getOrDefault(CsvConstants.ORGANIZATION_ID, null);
                if (organizationId != null) {
                    groupObservation.addPerformer(new Reference("Organization/" + organizationId));
                }
                String screeningStartDateTime = groupData.stream()
                        .map(ScreeningObservationData::getScreeningStartDateTime)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

                if (screeningStartDateTime != null) {
                    Date parsedDate = DateUtil.parseDate(screeningStartDateTime);
                    if (parsedDate != null) {
                        groupObservation.setEffective(new DateTimeType(parsedDate));
                        groupObservation.setIssued(parsedDate);
                    }
                }       
                
                CodeableConcept interpretation = new CodeableConcept();

                boolean hasPositiveInterpretation = groupData.stream()
                        .map(ScreeningObservationData::getPotentialNeedIndicated)
                        .filter(Objects::nonNull)
                        .anyMatch(indicated -> indicated.equalsIgnoreCase("POS"));

                if (hasPositiveInterpretation) {
                    interpretation.addCoding(new Coding(
                            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                            "POS", "Positive"));
                } else {
                    interpretation.addCoding(new Coding(
                            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                            "NEG", "Negative"));
                }
                groupObservation.addInterpretation(interpretation);

                // Add member references using observationId directly from the model
                List<Reference> hasMemberReferences = groupData.stream()
                                .map(data -> new Reference("Observation/" + CsvConversionUtil.sha256(data.getObservationId())))
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

        /**
         * Creates a category CodeableConcept
         */
        private CodeableConcept createCategory(String system, String code, String display) {
                CodeableConcept category = new CodeableConcept();
                Coding coding = new Coding(system, code, display);
                category.addCoding(coding);
                return category;
        }

        private static void populateScreeningIdentifier(Observation observation, ScreeningObservationData data) {
            if (StringUtils.isNotEmpty(data.getScreeningIdentifier())) {
                Identifier identifier = new Identifier();
                identifier.setValue(data.getScreeningIdentifier());
                observation.addIdentifier(identifier);
            }
        }
         
}