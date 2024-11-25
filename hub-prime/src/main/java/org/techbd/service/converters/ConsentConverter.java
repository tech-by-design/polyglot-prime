package org.techbd.service.converters;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.ResourceType;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.model.csv.ScreeningResourceData;
import org.techbd.service.converters.shinny.BaseConverter;
import org.techbd.util.DateUtil;

public class ConsentConverter extends BaseConverter {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.Consent;
    }

    @Override
    public BundleEntryComponent convert(Bundle bundle,DemographicData demographicData,List<ScreeningData> screeningDataList,QeAdminData qrAdminData,ScreeningResourceData screeningResourceData,String interactionId) {
        Consent consent = new Consent();
        setMeta(consent);
        consent.getMeta().setLastUpdated(DateUtil.convertStringToDate(screeningResourceData.getConsentLastUpdated()));
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        bundleEntryComponent.setResource(consent);
        return bundleEntryComponent;
    }

}
