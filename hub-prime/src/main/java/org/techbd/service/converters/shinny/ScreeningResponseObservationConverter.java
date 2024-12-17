package org.techbd.service.converters.shinny;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.util.DateUtil;

@Component
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
            String interactionId) {
    
        LOG.info("SexualOrientationObservationConverter::convert BEGIN for interaction id: {}", interactionId);
    
        List<BundleEntryComponent> bundleEntryComponents = new ArrayList<>();
    
        for (ScreeningObservationData data : screeningObservationDataList) {
            Observation observation = new Observation();
            String observationId = "Observation/"+data.getScreeningCode(); // Use screening code as ID
            String fullUrl = "http://shinny.org/us/ny/hrsn/Observation/" + observationId;
            Meta meta = new Meta();
            meta.setLastUpdated(DateUtil.parseDate(data.getRecordedTime()));
            meta.addProfile("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-screening-response");
            observation.setMeta(meta);
            observation.setLanguage("en");
            Narrative narrative = new Narrative();
            narrative.setStatus(NarrativeStatus.GENERATED);
            observation.setText(narrative);
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.addCategory(createCategory("http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                    "housing-instability", "Housing Instability"));
            observation.addCategory(createCategory("http://terminology.hl7.org/CodeSystem/observation-category",
                    "social-history", null));
            observation.addCategory(createCategory("http://terminology.hl7.org/CodeSystem/observation-category",
                    "survey", null));
            CodeableConcept code = new CodeableConcept();
            code.addCoding(new Coding("http://loinc.org", data.getScreeningCode(), data.getScreeningCodeDescription()));
            code.setText(data.getQuestionCodeText());
            observation.setCode(code);
            observation.setSubject(new Reference("Patient/" + data.getPatientMrIdValue()));
            if (data.getRecordedTime() != null) {
                observation.setEffective(new DateTimeType(DateUtil.parseDate(data.getRecordedTime())));
            }
            observation.setIssued(DateUtil.parseDate(data.getRecordedTime()));
            CodeableConcept value = new CodeableConcept();
            value.addCoding(new Coding("http://loinc.org", data.getAnswerCode(), data.getAnswerCodeDescription()));
            observation.setValue(value);
            BundleEntryComponent entry = new BundleEntryComponent();
            entry.setFullUrl(fullUrl);
            entry.setResource(observation);
            bundleEntryComponents.add(entry);
        }    
        LOG.info("SexualOrientationObservationConverter::convert END for interaction id: {}", interactionId);
        return bundleEntryComponents;
    }
    
    private CodeableConcept createCategory(String system, String code, String display) {
        CodeableConcept category = new CodeableConcept();
        category.addCoding(new Coding(system, code, display));
        return category;
    }
}