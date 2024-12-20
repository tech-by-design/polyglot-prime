package org.techbd.service.converters.shinny;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            String interactionId,Map<String,String> idsGenerated) {
    
        LOG.info("ScreeningResponseObservationConverter::convert BEGIN for interaction id: {}", interactionId);
    
        List<BundleEntryComponent> bundleEntryComponents = new ArrayList<>();
    
        for (ScreeningObservationData data : screeningObservationDataList) {
            Observation observation = new Observation();
            String observationId = CsvConversionUtil.sha256(data.getQuestionCodeDisplay().replace(" ", "")+ data.getQuestionCode()); 
            observation.setId(observationId);
            String fullUrl = "http://shinny.org/us/ny/hrsn/Observation/"+observationId;
            setMeta(observation);
            Meta meta = observation.getMeta();
            meta.setLastUpdated(DateUtil.parseDate(demographicData.getPatientLastUpdated())); // max date available in all
                                                                                              // screening records
            observation.setLanguage("en");
            observation.setStatus(Observation.ObservationStatus.fromCode(screeningProfileData.getScreeningStatusCode()));
            if (!data.getObservationCategorySdohCode().isEmpty()) {
                observation.addCategory(createCategory("http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                    data.getObservationCategorySdohCode(), data.getObservationCategorySdohDisplay()));
            } else {
                observation.addCategory(createCategory("http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                    "sdoh-category-unspecified", "SDOH Category Unspecified"));
            
                if (!data.getObservationCategorySnomedCode().isEmpty()) {
                    observation.addCategory(createCategory("http://snomed.info/sct",
                        data.getObservationCategorySnomedCode(), data.getObservationCategorySnomedDisplay()));
                }
            }
            if(!data.getDataAbsentReasonCode().isEmpty()) {
                CodeableConcept dataAbsentReason = new CodeableConcept();

                dataAbsentReason.addCoding(
                    new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/data-absent-reason")
                        .setCode(data.getDataAbsentReasonCode())
                        .setDisplay(data.getDataAbsentReasonDisplay())
                );
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
            observation.setSubject(new Reference("Patient/" +idsGenerated.get(CsvConstants.PATIENT_ID)));
            if (data.getRecordedTime() != null) {
                observation.setEffective(new DateTimeType(DateUtil.parseDate(data.getRecordedTime())));
            }
            observation.setIssued(DateUtil.parseDate(data.getRecordedTime()));
            CodeableConcept value = new CodeableConcept();
            value.addCoding(new Coding("http://loinc.org", data.getAnswerCode(), data.getAnswerCodeDescription()));
            observation.setValue(value);
            BundleEntryComponent entry = new BundleEntryComponent();
            entry.setFullUrl(fullUrl);
            entry.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST).setUrl("http://shinny.org/us/ny/hrsn/Observation/" + observationId));
            entry.setResource(observation);
            bundleEntryComponents.add(entry);
        }    
        LOG.info("ScreeningResponseObservationConverter::convert  no of observations:{}  END for interaction id: {}",screeningObservationDataList.size(), interactionId);
        return bundleEntryComponents;
    }
    
    private CodeableConcept createCategory(String system, String code, String display) {
        CodeableConcept category = new CodeableConcept();
        category.addCoding(new Coding(system, code, display));
        return category;
    }
}