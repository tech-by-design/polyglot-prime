package org.techbd.converters.csv;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.jooq.DSLContext;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.AppLogger;
import org.techbd.util.DateUtil;
import org.techbd.util.TemplateLogger;
import org.techbd.util.csv.CsvConstants;
import org.techbd.util.csv.CsvConversionUtil;
import org.techbd.util.fhir.CoreFHIRUtil;

@Component
@Order(3)
public class SexualOrientationObservationConverter extends BaseConverter {
    private final TemplateLogger LOG;

    public SexualOrientationObservationConverter(CodeLookupService codeLookupService, @Qualifier("primaryDslContext") final DSLContext primaryDslContext, AppLogger appLogger) {
        super(codeLookupService,primaryDslContext);
        LOG = appLogger.getLogger(SexualOrientationObservationConverter.class);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.Observation;
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(CoreFHIRUtil.getProfileUrl("observationSexualOrientation"));
    }

    @Override
    public List<BundleEntryComponent> convert(Bundle bundle, DemographicData demographicData, QeAdminData qeAdminData,
            ScreeningProfileData screeningProfileData, List<ScreeningObservationData> screeningObservationData,
            String interactionId, Map<String, String> idsGenerated,String baseFHIRUrl) {
        LOG.info("SexualOrientationObservationConverter:: convert BEGIN for interaction id :{} ", interactionId);
        if (StringUtils.isNotEmpty(demographicData.getSexualOrientationCodeSystem()) ||
                StringUtils.isNotEmpty(demographicData.getSexualOrientationCode()) ||
                StringUtils.isNotEmpty(demographicData.getSexualOrientationCodeDescription())) {
            Observation observation = new Observation();
            setMeta(observation,baseFHIRUrl,"observationSexualOrientation");
            observation.setId(CsvConversionUtil.sha256("SexualOrientation-" + screeningProfileData.getPatientMrIdValue()
                    + screeningProfileData.getEncounterId()));
            Meta meta = observation.getMeta();
            String fullUrl = "http://shinny.org/us/ny/hrsn/Observation/" + observation.getId();
            
            if (StringUtils.isNotEmpty(demographicData.getSexualOrientationLastUpdated())) {
                meta.setLastUpdated(DateUtil.parseDate(demographicData.getSexualOrientationLastUpdated()));
            } else {
                meta.setLastUpdated(new Date());
            }
            
            observation.setStatus(Observation.ObservationStatus.fromCode("final")); // TODO : remove static reference
            Reference subjectReference = new Reference();
            subjectReference.setReference("Patient/" + idsGenerated.get(CsvConstants.PATIENT_ID)); // TODO : remove static reference
            observation.setSubject(subjectReference);
            CodeableConcept code = new CodeableConcept();
            code.addCoding(new Coding("http://loinc.org", // TODO : remove static reference
                    "76690-7", "Sexual orientation")); // TODO : remove static reference
            observation.setCode(code);

            CodeableConcept value = new CodeableConcept();
            String originalCode = fetchCode(demographicData.getSexualOrientationCode(), CsvConstants.SEXUAL_ORIENTATION_CODE, interactionId);
            String mappedCode;

            if ("ASKU".equalsIgnoreCase(originalCode)) {
                mappedCode = "asked-unknown";
            } else if ("UNK".equalsIgnoreCase(originalCode)) {
                mappedCode = "unknown";
            } else {
                mappedCode = originalCode;
            }

            value.addCoding(new Coding(
                    fetchSystem(originalCode, demographicData.getSexualOrientationCodeSystem(), CsvConstants.SEXUAL_ORIENTATION_CODE, interactionId),
                    mappedCode,
                    fetchDisplay(originalCode, demographicData.getSexualOrientationCodeDescription(), CsvConstants.SEXUAL_ORIENTATION_CODE, interactionId)
            ));

            observation.setValue(value);
            
            BundleEntryComponent entry = new BundleEntryComponent();
            entry.setFullUrl(fullUrl);
            entry.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST)
                    .setUrl("http://shinny.org/us/ny/hrsn/Observation/" + observation.getId()));
            entry.setResource(observation);
            LOG.info("SexualOrientationObservationConverter:: convert END for interaction id :{} ", interactionId);
            return List.of(entry);
        } else {
            LOG.info(
                    "SexualOrientationObservationConverter:: No data for sexual orientation, observation will not be created for interaction id :{} ",
                    interactionId);
            return List.of();
        }
    }

}
