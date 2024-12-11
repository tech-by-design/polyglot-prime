package org.techbd.service.converters.csv;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.BundleConverter;
import org.techbd.service.converters.shinny.IConverter;
import org.techbd.service.http.hub.prime.AppConfig;

import ca.uhn.fhir.context.FhirContext;

@Component
public class CsvToFhirConverter {

    private final AppConfig appConfig;
    private final List<IConverter> converters; // todo move other converters inside bundle converter after hl7 changes
    private final BundleConverter bundleConverter;
    private static final Logger LOG = LoggerFactory.getLogger(CsvToFhirConverter.class.getName());

    public CsvToFhirConverter(AppConfig appConfig, BundleConverter bundleConverter, List<IConverter> converters) {
        this.appConfig = appConfig;
        this.converters = converters;
        this.bundleConverter = bundleConverter;
    }

    public String convert(DemographicData demographicData, List<ScreeningObservationData> screeningDataList,
            QeAdminData qeAdminData, ScreeningProfileData screeningProfileData,String interactionId) {
        Bundle bundle = null;
        try {
            LOG.info("CsvToFhirConvereter::convert - BEGIN for interactionId :{}", interactionId);
            bundle = bundleConverter.generateEmptyBundle(interactionId, appConfig.getIgVersion());
            LOG.debug("CsvToFhirConvereter::convert - Bundle entry created :{}", interactionId);
            LOG.debug("Conversion of resources - BEGIN for interactionId :{}", interactionId);
            addEntries(bundle, demographicData, screeningDataList, qeAdminData, screeningProfileData,interactionId);
            setReferences(bundle);
            LOG.debug("Conversion of resources - END for interactionId :{}", interactionId);
            LOG.info("CsvToFhirConvereter::convert - END for interactionId :{}", interactionId);
        } catch (Exception ex) {
            LOG.error("Exception in Csv conversion for interaction id : {}", interactionId, ex);
        }
        return FhirContext.forR4().newJsonParser().encodeResourceToString(bundle);
    }

    private void setReferences(Bundle bundle) {
        // TODO : after conversion.set generated ids of resources in other resources
        throw new UnsupportedOperationException("Unimplemented method 'setReferences'");
    }

    private void addEntries(Bundle bundle, DemographicData demographicData, List<ScreeningObservationData> screeningObservationData,
            QeAdminData qeAdminData,ScreeningProfileData screeningProfileData, String interactionId) {
        List<BundleEntryComponent> entries = new ArrayList<>();
        converters.stream().forEach(converter -> {
            entries.add(converter.convert(bundle, demographicData,  qeAdminData,screeningProfileData,screeningObservationData, interactionId));
        });
    }

}
