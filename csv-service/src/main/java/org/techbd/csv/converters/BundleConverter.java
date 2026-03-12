package org.techbd.csv.converters;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.util.CsvConversionUtil;
import org.techbd.corelib.util.CoreFHIRUtil;

import io.micrometer.common.util.StringUtils;

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
    public Bundle generateEmptyBundle(String interactionId, DemographicData demographicData, String baseFHIRUrl, QeAdminData qeAdminData) {
        Bundle bundle = new Bundle();
        bundle.setId(CsvConversionUtil.sha256(UUID.randomUUID().toString()));
        bundle.setType(Bundle.BundleType.TRANSACTION);
        Meta meta = new Meta();
        meta.setLastUpdated(new Date());
        if (StringUtils.isNotEmpty(baseFHIRUrl)) {
            meta.setProfile(List.of(
                    new CanonicalType(CoreFHIRUtil.getProfileUrl(baseFHIRUrl, ResourceType.Bundle.name().toLowerCase()))));
        } else {
            meta.setProfile(List.of(new CanonicalType(CoreFHIRUtil.getBundleProfileUrl())));
        }
        if ("Yes".equalsIgnoreCase(qeAdminData.getVisitPart2Flag())) {
            Coding ethCoding = new Coding();
            ethCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
            ethCoding.setCode("ETH");
            ethCoding.setDisplay("Substance abuse information sensitivity");

            meta.addSecurity(ethCoding);
        }
        bundle.setMeta(meta);
        LOG.info("Empty FHIR Bundle template generated with Meta and one empty entry for interactionId : {}.",
                interactionId);
        return bundle;
    }
}
