package org.techbd.csv.converters;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;
import org.techbd.corelib.util.CoreFHIRUtil;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.model.ScreeningObservationData;
import org.techbd.csv.model.ScreeningProfileData;

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
                resource.getMeta().setProfile(List.of(new CanonicalType(CoreFHIRUtil.getProfileUrl(baseFHIRUrl,getResourceType().name().toLowerCase()))));
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
                resource.getMeta().setProfile(List.of(new CanonicalType(CoreFHIRUtil.getProfileUrl(baseFHIRUrl,resourceType))));
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
