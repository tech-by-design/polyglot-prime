package org.techbd.service.converters.shinny;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;

@Component
public interface IConverter {

    ResourceType getResourceType();

    CanonicalType getProfileUrl();

    default void convert(Resource resource) {
        setMeta(resource);
    }

    BundleEntryComponent convert(Bundle bundle,DemographicData demographicData,List<ScreeningData> screeningDataList,QeAdminData qrAdminData,String interactionId);

    default void setMeta(Resource resource) {
        if (null != resource.getMeta()) {
            resource.getMeta().setProfile(List.of(getProfileUrl()));
            // TODO -currently extension is not populated in shinny examples.Hence setting
            // to null
            resource.getMeta().setExtension(null);
        }
    }

    default void setMeta(Bundle bundle) {
        if (null != bundle.getMeta()) {
            bundle.getMeta().setProfile(List.of(getProfileUrl()));
            // TODO -currently extension is not populated in shinny examples.Hence setting
            // to null
            bundle.getMeta().setExtension(null);
        }
    }

    

    
}
