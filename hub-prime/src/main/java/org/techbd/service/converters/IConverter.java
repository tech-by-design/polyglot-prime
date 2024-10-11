package org.techbd.service.converters;

import java.util.List;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;

@Component
public interface IConverter {
    default void convert(Resource resource) {
        setMeta(resource);
    }

    ResourceType getResourceType();

    CanonicalType getProfileUrl();

    default void setMeta(Resource resource) {
        if (null != resource.getMeta()) {
            resource.getMeta().setProfile(List.of(getProfileUrl()));
        }
        // TODO -currently extension is not populated in shinny examples.Hence setting to null
        resource.getMeta().setExtension(null);
    }
}
