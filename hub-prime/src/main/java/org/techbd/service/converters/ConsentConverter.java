package org.techbd.service.converters;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.ResourceType;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.service.converters.shinny.BaseConverter;
import org.techbd.util.CsvConversionUtil;

public class ConsentConverter extends BaseConverter {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.Consent;
    }

    @Override
    public BundleEntryComponent convert(Bundle bundle,DemographicData demographicData,List<ScreeningData> screeningDataList,QeAdminData qrAdminData,String interactionId) {
        Consent consent = new Consent();
        setMeta(consent);
        consent.setId(CsvConversionUtil.sha256("consentFor" + demographicData.getPatientMrId());
        consent.getMeta().setLastUpdated(getMaxLastUpdatedDate(screeningDataList));
       

        // Wrap in a BundleEntryComponent
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        bundleEntryComponent.setResource(consent);

        return bundleEntryComponent;
    }

}
