package org.techbd.csv.converters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.springframework.stereotype.Component;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.model.ScreeningObservationData;
import org.techbd.csv.model.ScreeningProfileData;

import ca.uhn.fhir.context.FhirContext;

@Component
public class CsvToFhirConverter {
    private final List<IConverter> converters; // todo move other converters inside bundle converter after hl7 changes
    private final BundleConverter bundleConverter;
    private final TemplateLogger LOG;

    public CsvToFhirConverter(BundleConverter bundleConverter, List<IConverter> converters,AppLogger appLogger) {
        this.converters = converters;
        this.bundleConverter = bundleConverter;
        this.LOG = appLogger.getLogger(CsvToFhirConverter.class);
    }

    public String convert(DemographicData demographicData,
            QeAdminData qeAdminData, ScreeningProfileData screeningProfileData,
            List<ScreeningObservationData> screeningDataList, String interactionId,String baseFHIRUrl) {
        Bundle bundle = null;
        try {
            LOG.info("CsvToFhirConvereter::convert - BEGIN for interactionId :{}", interactionId);
            bundle = bundleConverter.generateEmptyBundle(interactionId, demographicData, baseFHIRUrl, qeAdminData);
            LOG.debug("CsvToFhirConvereter::convert - Bundle entry created :{}", interactionId);
            LOG.debug("Conversion of resources - BEGIN for interactionId :{}", interactionId);
            addEntries(bundle, demographicData, screeningDataList, qeAdminData, screeningProfileData, interactionId,baseFHIRUrl);
            LOG.debug("Conversion of resources - END for interactionId :{}", interactionId);
            LOG.info("CsvToFhirConvereter::convert - END for interactionId :{}", interactionId);
        } catch (Exception ex) {
            LOG.error("Exception in Csv conversion for interaction id : {}", interactionId, ex);
        }
        return FhirContext.forR4().newJsonParser().encodeResourceToString(bundle);
    }

    private void addEntries(Bundle bundle, DemographicData demographicData,
            List<ScreeningObservationData> screeningObservationData,
            QeAdminData qeAdminData, ScreeningProfileData screeningProfileData, String interactionId,String baseFHIRUrl) {
        List<BundleEntryComponent> entries = new ArrayList<>();
        Map<String, String> idsGenerated = new HashMap<>();

        // Iterate over the converters
        converters.stream().forEach(converter -> {
            try {
                // Attempt to process using the current converter
                entries.addAll(converter.convert(bundle, demographicData, qeAdminData, screeningProfileData,
                        screeningObservationData, interactionId, idsGenerated,baseFHIRUrl));
            } catch (Exception e) {
                // Log the error and continue with other converters
                LOG.error("Error occurred while processing converter: " + converter.getClass().getName(), e);
            }
        });

        // Add all successful entries to the bundle
        bundle.getEntry().addAll(entries);
    }
}