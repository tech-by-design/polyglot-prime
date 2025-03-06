package org.techbd.service.converters.shinny;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.util.FHIRUtil;

import io.micrometer.common.util.StringUtils;

@Component
public interface IConverter {

    ResourceType getResourceType();

    CanonicalType getProfileUrl();

    default void convert(Resource resource) {
        setMeta(resource);
    }

    List<BundleEntryComponent> convert(Bundle bundle,DemographicData demographicData,QeAdminData qeAdminData ,
    ScreeningProfileData screeningProfileData ,List<ScreeningObservationData> screeningObservationData,String interactionId,Map<String,String> idsGenerated,String baseFHIRUrl);

    default void setMeta(Resource resource,String baseFHIRUrl) {
        if (null != resource.getMeta()) {
            if (StringUtils.isNotEmpty(baseFHIRUrl)) {
                resource.getMeta().setProfile(List.of(new CanonicalType(FHIRUtil.getProfileUrl(baseFHIRUrl,getResourceType().name().toLowerCase()))));
            } else {
                resource.getMeta().setProfile(List.of(getProfileUrl()));
            }
            // TODO -currently extension is not populated in shinny examples.Hence setting
            // to null
            resource.getMeta().setExtension(null);
        }
    }

    default void setMeta(Resource resource,String baseFHIRUrl,String resourceType) {
        if (null != resource.getMeta()) {
            if (StringUtils.isNotEmpty(baseFHIRUrl) && StringUtils.isNotEmpty(resourceType)) {
                resource.getMeta().setProfile(List.of(new CanonicalType(FHIRUtil.getProfileUrl(baseFHIRUrl,resourceType))));
            } else {
                resource.getMeta().setProfile(List.of(getProfileUrl()));
            }
            // TODO -currently extension is not populated in shinny examples.Hence setting
            // to null
            resource.getMeta().setExtension(null);
        }
    }
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
