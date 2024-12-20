package org.techbd.service.converters.shinny;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
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
import org.techbd.util.CsvConstants;
import org.techbd.util.CsvConversionUtil;
import org.techbd.util.DateUtil;

@Component
@Order(6)
public class ScreeningResponseObservationConverter extends BaseConverter {

    private static final Logger LOG = LoggerFactory.getLogger(SexualOrientationObservationConverter.class.getName());

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
            String interactionId, Map<String, String> idsGenerated) {

        LOG.info("ScreeningResponseObservationConverter::convert BEGIN for interaction id: {}", interactionId);

        List<BundleEntryComponent> bundleEntryComponents = new ArrayList<>();
        /*
         * TODO
         * populate referencesMap--if a question falls in any of reference
         * INTERPERSONAL_SAFETY_GROUP_QUESTIONS
         * AHC_SCREENING_GROUP_QUESTIONS
         * SUPPLEMENTAL_GROUP_QUESTIONS
         * TOTAL_SCORE_QUESTIONS
         * MENTAL_HEALTH_SCORE_QUESTIONS
         * PHYSICAL_ACTIVITY_SCORE_QUESTIONS
         *  
         */
        List<String> interpersonalSafetyQuestions = QUESTIONS_MAP.get(CsvConstants.INTERPERSONAL_SAFETY_GROUP_QUESTIONS);
        List<String> ahcScreeningQuestions = QUESTIONS_MAP.get(CsvConstants.AHC_SCREENING_GROUP_QUESTIONS);
        List<String> supplementalQuestions = QUESTIONS_MAP.get(CsvConstants.SUPPLEMENTAL_GROUP_QUESTIONS);
        List<String> totalScoreQuestions = QUESTIONS_MAP.get(CsvConstants.TOTAL_SCORE_QUESTIONS);
        List<String> mentalHealthScoreQuestions = QUESTIONS_MAP.get(CsvConstants.MENTAL_HEALTH_SCORE_QUESTIONS);
        List<String> physicalActivityScoreQuestions = QUESTIONS_MAP.get(CsvConstants.PHYSICAL_ACTIVITY_SCORE_QUESTIONS);

        Map<String, List<Reference>> referencesMap = new HashMap<>();
        Map<String, String> questionAndAnswerCode = new HashMap<>();
        for (ScreeningObservationData data : screeningObservationDataList) {
            Observation observation = new Observation();
            String observationId = CsvConversionUtil
                    .sha256(data.getQuestionCodeDisplay().replace(" ", "") + data.getQuestionCode());
            observation.setId(observationId);
            String fullUrl = "http://shinny.org/us/ny/hrsn/Observation/" + observationId;
            setMeta(observation);
            Meta meta = observation.getMeta();
            meta.setLastUpdated(DateUtil.parseDate(demographicData.getPatientLastUpdated())); // max date available in
                                                                                              // all
                                                                                              // screening records
            observation.setLanguage("en");
            observation
                    .setStatus(Observation.ObservationStatus.fromCode(screeningProfileData.getScreeningStatusCode()));
            if (data.getObservationCategorySdohCode() != null
                    && !data.getObservationCategorySnomedCode().equals("sdoh-category-unspecified")) {
                observation.addCategory(createCategory(
                        "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                        data.getObservationCategorySdohCode(), data.getObservationCategorySdohDisplay()));
            } else {
                observation.addCategory(createCategory(
                        "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                        "sdoh-category-unspecified", "SDOH Category Unspecified"));

                if (data.getObservationCategorySnomedCode() != null) {
                    observation.addCategory(createCategory("http://snomed.info/sct",
                            data.getObservationCategorySnomedCode(), data.getObservationCategorySnomedDisplay()));
                }
            }
            if (data.getDataAbsentReasonCode() != null) {
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
            code.addCoding(new Coding("http://loinc.org", data.getQuestionCode(), data.getQuestionCodeDisplay()));
            code.setText(data.getQuestionCodeText());
            observation.setCode(code);
            observation.setSubject(new Reference("Patient/" + idsGenerated.get(CsvConstants.PATIENT_ID)));
            if (data.getRecordedTime() != null) {
                observation.setEffective(new DateTimeType(DateUtil.parseDate(data.getRecordedTime())));
            }
            observation.setIssued(DateUtil.parseDate(data.getRecordedTime()));
            CodeableConcept value = new CodeableConcept();
            value.addCoding(new Coding("http://loinc.org", data.getAnswerCode(), data.getAnswerCodeDescription()));
            observation.setValue(value);
            questionAndAnswerCode.put(data.getQuestionCode(), data.getAnswerCode());
            BundleEntryComponent entry = new BundleEntryComponent();
            entry.setFullUrl(fullUrl);
            entry.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST)
                    .setUrl("http://shinny.org/us/ny/hrsn/Observation/" + observationId));
            entry.setResource(observation);
            bundleEntryComponents.add(entry);
        }
        int physicalActivityScore = calculatePhysicalActivityScore(totalScoreQuestions);
        int calculateMentalHealthScore = calculateMentalHealthScore(mentalHealthScoreQuestions);
        int totalSafetyScore = calculateTotalSafetyScore(physicalActivityScoreQuestions);
        LOG.info("ScreeningResponseObservationConverter::convert  no of observations:{}  END for interaction id: {}",
                screeningObservationDataList.size(), interactionId);
        return bundleEntryComponents;
    }

    private int calculateTotalSafetyScore(List<String> physicalActivityScoreQuestions) {
        List<String> questions = QUESTIONS_MAP.get(CsvConstants.TOTAL_SCORE_QUESTIONS);
        // loop through questions and find score using logic in
        // https://view.officeapps.live.com/op/view.aspx?src=https%3A%2F%2Fraw.githubusercontent.com%2Fqe-collaborative-services%2F1115-hub%2Frefs%2Fheads%2Fmain%2Fsupport%2Fdocs%2Fspecifications%2Fahc-hrsn-elt%2Fscreening%2Fahc-hrsn-2024-03-08-omnibus-rules.xlsx&wdOrigin=BROWSELINK
        return 0;
    }

    private int calculateMentalHealthScore(List<String> mentalHealthScoreQuestions) {
      
        return 0;
    }

    private int calculatePhysicalActivityScore(List<String> totalScoreQuestions) {
        return 0;
    }

    private CodeableConcept createCategory(String system, String code, String display) {
        CodeableConcept category = new CodeableConcept();
        category.addCoding(new Coding(system, code, display));
        return category;
    }

    public enum ScreeningAnswer {

        NEVER(1, "LA6270-8", "Never"),
        RARELY(2, "LA10066-1", "Rarely"),
        SOMETIMES(3, "LA10082-8", "Sometimes"),
        FAIRLY_OFTEN(4, "LA16644-9", "Fairly often"),
        FREQUENTLY(5, "LA6482-9", "Frequently");

        private final int score;
        private final String code;
        private final String text;

        ScreeningAnswer(int score, String code, String text) {
            this.score = score;
            this.code = code;
            this.text = text;
        }

        public int getScore() {
            return score;
        }

        public String getCode() {
            return code;
        }

        public String getText() {
            return text;
        }

        public static Map<String, Integer> getCodeToScoreMap() {
            return Stream.of(values())
                    .collect(Collectors.toMap(ScreeningAnswer::getCode, ScreeningAnswer::getScore));
        }
    }
}