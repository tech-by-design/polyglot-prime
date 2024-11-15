package org.techbd.service.converters.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.service.converters.shinny.BundleConverter;
import org.techbd.service.converters.shinny.IConverter;
import org.techbd.service.http.hub.prime.AppConfig;

import ca.uhn.fhir.context.FhirContext;

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

    public String convert(DemographicData demographicData, List<ScreeningData> screeningDataList,
            QeAdminData qeAdminData, String interactionId) {
        Bundle bundle = null;
        try {
            LOG.info("CsvToFhirConvereter::convert - BEGIN for interactionId :{}", interactionId);
            bundle = bundleConverter.generateEmptyBundle(interactionId, appConfig.getIgVersion());
            LOG.info("CsvToFhirConvereter::convert - Bundle entry created :{}", interactionId);
            LOG.info("Conversion of resources - BEGIN for interactionId :{}", interactionId);
            addEntries(bundle, demographicData, screeningDataList, qeAdminData, interactionId);
            LOG.info("Conversion of resources - END for interactionId :{}", interactionId);
            LOG.info("CsvToFhirConvereter::convert - BEGIN for interactionId :{}", interactionId);
        } catch (Exception ex) {
            LOG.error("Exception in Csv conversion for interaction id : {}", interactionId, ex);
        }
        return FhirContext.forR4().newJsonParser().encodeResourceToString(bundle);
    }

    private void addEntries(Bundle bundle, DemographicData demographicData, List<ScreeningData> screeningDataList,
            QeAdminData qeAdminData, String interactionId) {
        List<BundleEntryComponent> entries = new ArrayList<>();
        converters.stream().forEach(converter -> {
            entries.add(converter.convert(bundle, demographicData, screeningDataList, qeAdminData, interactionId));
        });
    }

}
