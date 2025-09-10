package org.techbd.replay;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FhirBundleUtil {

    private static final FhirContext fhirContext = FhirContext.forR4();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Copy resource IDs, fullUrl, and request.url from originalBundle to
     * generatedBundle
     * and return updated bundle as JsonNode.
     */
    public static JsonNode copyResourceIds(String originalBundle, String generatedBundle) {
        // Parse both bundles
        Bundle original = fhirContext.newJsonParser().parseResource(Bundle.class, originalBundle);
        Bundle generated = fhirContext.newJsonParser().parseResource(Bundle.class, generatedBundle);

        // Build map of IDs/fullUrls/requestUrls from original
        Map<String, IdMapping> idMap = buildIdMap(original);

        // Apply mappings to generated
        List<BundleEntryComponent> genEntries = generated.getEntry();
        for (BundleEntryComponent entry : genEntries) {
            Resource resource = entry.getResource();
            if (resource == null)
                continue;

            String key;
            if (resource instanceof Observation obs && obs.hasCode() && obs.getCode().hasCoding()) {
                key = obs.getCode().getCodingFirstRep().getCode();
            } else {
                key = resource.getResourceType().name();
            }

            IdMapping mapping = idMap.get(key);
            if (mapping != null) {
                // Copy resource id
                resource.setId(mapping.id);

                // Copy fullUrl
                if (mapping.fullUrl != null) {
                    entry.setFullUrl(mapping.fullUrl);
                }

                // Copy request URL
                if (mapping.requestUrl != null && entry.hasRequest()) {
                    entry.getRequest().setUrl(mapping.requestUrl);
                }
            }
        }

        // Convert back to JSON
        String updatedJson = fhirContext.newJsonParser().encodeResourceToString(generated);
        try {
            return objectMapper.readTree(updatedJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert updated bundle to JsonNode", e);
        }
    }

    /**
     * Build a map from resourceType (or Observation code) to ID/fullUrl/requestUrl.
     */
    private static Map<String, IdMapping> buildIdMap(Bundle original) {
        Map<String, IdMapping> map = new HashMap<>();

        for (BundleEntryComponent entry : original.getEntry()) {
            Resource resource = entry.getResource();
            if (resource == null || !resource.hasIdElement())
                continue;

            String id = resource.getIdElement().getIdPart();
            String fullUrl = entry.hasFullUrl() ? entry.getFullUrl() : null;
            String requestUrl = entry.hasRequest() ? entry.getRequest().getUrl() : null;

            String key;
            if (resource instanceof Observation obs && obs.hasCode() && obs.getCode().hasCoding()) {
                key = obs.getCode().getCodingFirstRep().getCode();
            } else {
                key = resource.getResourceType().name();
            }

            map.put(key, new IdMapping(id, fullUrl, requestUrl));
        }

        return map;
    }

    /**
     * Helper class to hold mappings.
     */
    private static class IdMapping {
        String id;
        String fullUrl;
        String requestUrl;

        IdMapping(String id, String fullUrl, String requestUrl) {
            this.id = id;
            this.fullUrl = fullUrl;
            this.requestUrl = requestUrl;
        }
    }
}
