package org.techbd.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

public class FhirBundleUtilTest {

    private static final Logger LOG = LoggerFactory.getLogger(FhirBundleUtilTest.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode originalBundle;
    private JsonNode generatedBundle;

    @BeforeEach
    void setup() throws Exception {
        File origFile = Paths.get("src/test/resources/org/techbd/replay/originalbundle.json").toFile();
        File genFile = Paths.get("src/test/resources/org/techbd/replay/correctedbundle.json").toFile();
        originalBundle = mapper.readTree(origFile);
        generatedBundle = mapper.readTree(genFile);
    }

    @Test
    void testCopyResourceIds_AllScenarios() {
        JsonNode updatedBundle = FhirBundleUtil.copyResourceIds(
                originalBundle,
                generatedBundle,
                "replay-123",
                "int-456",
                "bundle-789",
                "tenant-xyz",
                "0.662.2"
        );

        SoftAssertions softly = new SoftAssertions();

        Map<String, JsonNode> originalByKey = toResourceMap(originalBundle);
        Map<String, JsonNode> generatedByKey = toResourceMap(updatedBundle);

        // --- IDs, fullUrls, request.urls ---
        for (Map.Entry<String, JsonNode> entry : generatedByKey.entrySet()) {
            String key = entry.getKey();
            JsonNode genRes = entry.getValue();
            JsonNode origRes = originalByKey.get(key);

            if (origRes == null) {
                LOG.warn("JUnit WARN: Generated resource {} not found in original bundle", key);
                continue;
            }

            softly.assertThat(genRes.path("id").asText())
                    .as("ID must match for " + key)
                    .isEqualTo(origRes.path("id").asText());

            softly.assertThat(getFullUrl(updatedBundle, key))
                    .as("fullUrl must match for " + key)
                    .isEqualTo(getFullUrl(originalBundle, key));

            softly.assertThat(getRequestUrl(updatedBundle, key))
                    .as("request.url must match for " + key)
                    .isEqualTo(getRequestUrl(originalBundle, key));
        }

        // --- References ---
        Set<String> originalRefs = collectAllReferences(originalBundle);
        Set<String> generatedRefs = collectAllReferences(updatedBundle);

        for (String ref : generatedRefs) {
            if (!originalRefs.contains(ref)) {
                LOG.warn("JUnit WARN: Reference {} exists in generated bundle but not in original", ref);
            }
            softly.assertThat(originalRefs)
                    .as("Reference " + ref + " should exist in original")
                    .contains(ref);
        }

        // --- Grouper issued ---
        for (String key : generatedByKey.keySet()) {
            JsonNode genRes = generatedByKey.get(key);
            JsonNode origRes = originalByKey.get(key);
            if (origRes == null) continue;

            String type = genRes.path("resourceType").asText();
            if ("Group".equals(type) || "List".equals(type)) {
                softly.assertThat(genRes.path("issued").asText())
                        .as("issued must match for " + key)
                        .isEqualTo(origRes.path("issued").asText());
            }
        }

        // --- Consent.dateTime ---
        for (String key : generatedByKey.keySet()) {
            JsonNode genRes = generatedByKey.get(key);
            JsonNode origRes = originalByKey.get(key);
            if (origRes == null) continue;

            if ("Consent".equals(genRes.path("resourceType").asText())) {
                softly.assertThat(genRes.path("dateTime").asText())
                        .as("Consent.dateTime must match for " + key)
                        .isEqualTo(origRes.path("dateTime").asText());
            }
        }

        // --- Missing resources ---
        for (String origKey : originalByKey.keySet()) {
            if (!generatedByKey.containsKey(origKey)) {
                LOG.warn("JUnit WARN: Original resource {} missing in generated bundle", origKey);
                softly.fail("Original resource " + origKey + " should exist in generated bundle");
            }
        }

        // --- Additional resources ---
        for (String genKey : generatedByKey.keySet()) {
            if (!originalByKey.containsKey(genKey)) {
                LOG.warn("JUnit WARN: Generated resource {} not found in original bundle", genKey);
                softly.fail("Generated resource " + genKey + " should exist in original bundle");
            }
        }

        softly.assertAll();
    }

    // --- Helpers ---
    private Map<String, JsonNode> toResourceMap(JsonNode bundle) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id")) {
                map.put(res.path("resourceType").asText() + "/" + res.path("id").asText(), res);
            }
        }
        return map;
    }

    private String getFullUrl(JsonNode bundle, String key) {
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id")) {
                String idKey = res.path("resourceType").asText() + "/" + res.path("id").asText();
                if (key.equals(idKey)) {
                    return entry.path("fullUrl").asText();
                }
            }
        }
        return null;
    }

    private String getRequestUrl(JsonNode bundle, String key) {
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id")) {
                String idKey = res.path("resourceType").asText() + "/" + res.path("id").asText();
                if (key.equals(idKey) && entry.has("request")) {
                    return entry.path("request").path("url").asText();
                }
            }
        }
        return null;
    }

    private Set<String> collectAllReferences(JsonNode bundle) {
        Set<String> refs = new HashSet<>();
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode res = entry.path("resource");
            collectReferencesRecursive(res, refs);
        }
        return refs;
    }

    private void collectReferencesRecursive(JsonNode node, Set<String> refs) {
        if (node == null || node.isMissingNode()) return;

        if (node.isObject()) {
            if (node.has("reference")) {
                refs.add(node.path("reference").asText());
            }
            node.fields().forEachRemaining(f -> collectReferencesRecursive(f.getValue(), refs));
        } else if (node.isArray()) {
            for (JsonNode arrItem : node) {
                collectReferencesRecursive(arrItem, refs);
            }
        }
    }
}
