package org.techbd.csv.converters;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.DateUtil;
import org.techbd.corelib.util.CoreFHIRUtil;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.model.ScreeningObservationData;
import org.techbd.csv.model.ScreeningProfileData;
import org.techbd.csv.service.CodeLookupService;
import org.techbd.csv.util.CsvConstants;
import org.techbd.csv.util.CsvConversionUtil;

@Component
@Order(3)
public class SexualOrientationObservationConverter extends BaseConverter {
    private final TemplateLogger LOG;

    public SexualOrientationObservationConverter(CodeLookupService codeLookupService,final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig, AppLogger appLogger) {
        super(codeLookupService,coreUdiPrimeJpaConfig);
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
