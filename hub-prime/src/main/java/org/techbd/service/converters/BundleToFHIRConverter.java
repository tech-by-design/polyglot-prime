package org.techbd.service.converters;

import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

@Component
public class BundleToFHIRConverter {

    private final List<IConverter> converters;

     public BundleToFHIRConverter(final List<IConverter> converters) {
        this.converters = converters;
    }

    public String convertToShinnyFHIRJson(Bundle bundle) {
        convertBundleMeta(bundle);
        convertEntries(bundle.getEntry());
        return FhirContext.forR4().newJsonParser().encodeResourceToString(bundle);
    }
    public String convertToShinnyFHIRJson(String bundleJson) {
        FhirContext fhirContext = FhirContext.forR4();
        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
        convertBundleMeta(bundle);
        convertEntries(bundle.getEntry());
        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }


    private void convertBundleMeta(Bundle bundle) {
        Optional<IConverter> converterOpt = getConverter(bundle.getResourceType().name());
        if (converterOpt.isPresent()) {
            converterOpt.get().convert(bundle);
        }
        bundle.setType(BundleType.TRANSACTION);
    }

    private void convertEntries(List<BundleEntryComponent> entries) {
        for (BundleEntryComponent entry : entries) {
            Resource resource = entry.getResource();
            if (resource != null) {
                Optional<IConverter> converterOpt = getConverter(resource.getResourceType().name());
                if (converterOpt.isPresent()) {
                    converterOpt.get().convert(resource);
                }
            }
        }
    }

    private Optional<IConverter> getConverter(String resourceType) {
        return converters.stream().filter(c -> c.getResourceType().name().equals(resourceType)).findFirst();
    }
}
