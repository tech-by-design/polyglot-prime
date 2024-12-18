package org.techbd.service.converters.shinny;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BundleConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BundleConverter.class.getName());
    public ResourceType getResourceType() {
        return ResourceType.Bundle;
    }

    /**
     * Generates an empty FHIR Bundle with a single empty entry and a default Meta
     * section.
     *
     * @return a Bundle with type set to COLLECTION, one empty entry, and Meta
     *         information.
     */
    public Bundle generateEmptyBundle(String interactionId,String igVersion) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        Meta meta = new Meta();
        meta.setVersionId(igVersion);
        bundle.setMeta(meta);
        LOG.info("Empty FHIR Bundle template generated with Meta and one empty entry for interactionId : {}.",
                interactionId);
        return bundle;
    }
}
