package org.techbd.replay;

import java.util.*;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.context.FhirContext;

/**
 * Utility class for handling FHIR Bundles during CCDA replay.
 */
public class FhirBundleUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FhirBundleUtil.class);

    private static final FhirContext fhirContext = FhirContext.forR4();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> SCREENING_CODES = Set.of("100698-0", "97023-6", "96777-8","NYS-AHC-HRSN","NYSAHCHRSN");

    /**
     * Copies resource IDs, fullUrls, request.urls, references, Consent.dateTime,
     * and Grouper/List issued fields.
     */
    public static JsonNode copyResourceIds(
            JsonNode originalBundleNode,
            JsonNode generatedBundleNode,
            String replayInteractionId,
            String interactionId,
            String bundleId,
            String tenantId,
            String techbdVersion) {

        LOG.info("CCDA-REPLAY COPY RESOURCE IDS START [bundleId={}]", bundleId);

        if (originalBundleNode == null || generatedBundleNode == null)
            return generatedBundleNode;

        // Validate JSON parse via HAPI (optional)
        try {
            fhirContext.newJsonParser().parseResource(Bundle.class, originalBundleNode.toString());
            fhirContext.newJsonParser().parseResource(Bundle.class, generatedBundleNode.toString());
        } catch (Exception e) {
            LOG.warn("Bundle parse failed: {}", e.getMessage());
        }

        // Group by resource type
        Map<String, List<ObjectNode>> origByType = groupEntriesByType(originalBundleNode);
        Map<String, List<ObjectNode>> genByType = groupEntriesByType(generatedBundleNode);

        Map<String, String> idMapping = new HashMap<>();
        Set<String> allTypes = new HashSet<>();
        allTypes.addAll(origByType.keySet());
        allTypes.addAll(genByType.keySet());

        for (String type : allTypes) {
            List<ObjectNode> origList = origByType.getOrDefault(type, Collections.emptyList());
            List<ObjectNode> genList = genByType.getOrDefault(type, Collections.emptyList());

            Map<String, ObjectNode> origByIdentifier = buildIdentifierLookup(origList);
            Map<String, ObjectNode> genByIdentifier = buildIdentifierLookup(genList);

            Set<ObjectNode> matchedOrig = new HashSet<>();
            Set<ObjectNode> matchedGen = new HashSet<>();

            // Match by identifier
            for (Map.Entry<String, ObjectNode> genEntry : genByIdentifier.entrySet()) {
                if (origByIdentifier.containsKey(genEntry.getKey())) {
                    ObjectNode genRes = genEntry.getValue();
                    ObjectNode origRes = origByIdentifier.get(genEntry.getKey());
                    mapIdsForPair(type, genRes, origRes, idMapping);
                    matchedOrig.add(origRes);
                    matchedGen.add(genRes);
                }
            }

            // Observation code matching
            if ("Observation".equals(type)) {
                Map<String, List<ObjectNode>> origByCode = groupByObservationCode(origList);
                for (ObjectNode genRes : genList) {
                    if (matchedGen.contains(genRes))
                        continue;
                    String genCode = getObservationCode(genRes);
                    if (genCode == null)
                        continue;
                    List<ObjectNode> candidates = origByCode.getOrDefault(genCode, Collections.emptyList());
                    ObjectNode chosen = candidates.stream().filter(r -> !matchedOrig.contains(r)).findFirst()
                            .orElse(null);
                    if (chosen != null) {
                        mapIdsForPair(type, genRes, chosen, idMapping);
                        matchedOrig.add(chosen);
                        matchedGen.add(genRes);
                    }
                }
            }

            // Positional fallback
            List<ObjectNode> remOrig = origList.stream().filter(r -> !matchedOrig.contains(r))
                    .collect(Collectors.toList());
            List<ObjectNode> remGen = genList.stream().filter(r -> !matchedGen.contains(r))
                    .collect(Collectors.toList());
            for (int i = 0; i < Math.min(remOrig.size(), remGen.size()); i++) {
                mapIdsForPair(type, remGen.get(i), remOrig.get(i), idMapping);
            }

            if (origList.size() != genList.size()) {
                LOG.warn("Resource count mismatch for type={} original={} generated={}", type, origList.size(),
                        genList.size());
            }
        }

        // Copy fullUrl, request.url, Consent.dateTime, Grouper/List issued
        copyFullUrlAndRequest(originalBundleNode, generatedBundleNode, idMapping);
        copyConsentAndGrouperFields(
                originalBundleNode,
                generatedBundleNode,
                idMapping,
                replayInteractionId,
                interactionId,
                bundleId,
                tenantId,
                techbdVersion);

        // Update all references recursively, including hasMember,
        // member.entity.reference, entry.item.reference
        updateReferences((ArrayNode) generatedBundleNode.path("entry"), idMapping);

        // Detect missing/additional resources
        detectMissingOrAdditional(originalBundleNode, generatedBundleNode);

        LOG.info("CCDA-REPLAY COPY RESOURCE IDS COMPLETED [bundleId={}]", bundleId);
        return generatedBundleNode;
    }

    // -------------------------
    // Helper Methods
    // -------------------------

    private static Map<String, List<ObjectNode>> groupEntriesByType(JsonNode bundleNode) {
        Map<String, List<ObjectNode>> map = new LinkedHashMap<>();
        for (JsonNode entry : bundleNode.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.isMissingNode())
                continue;
            String type = res.path("resourceType").asText(null);
            if (type == null)
                continue;
            map.computeIfAbsent(type, k -> new ArrayList<>()).add((ObjectNode) res);
        }
        return map;
    }

    private static Map<String, ObjectNode> buildIdentifierLookup(List<ObjectNode> resources) {
        Map<String, ObjectNode> map = new HashMap<>();
        for (ObjectNode res : resources) {
            if (res.has("identifier") && res.path("identifier").isArray()) {
                for (JsonNode ident : res.withArray("identifier")) {
                    String system = ident.path("system").asText("");
                    String value = ident.path("value").asText("");
                    if (!system.isEmpty() || !value.isEmpty())
                        map.putIfAbsent(system + "|" + value, res);
                }
            }
        }
        return map;
    }

    private static Map<String, List<ObjectNode>> groupByObservationCode(List<ObjectNode> obsList) {
        Map<String, List<ObjectNode>> map = new HashMap<>();
        for (ObjectNode obs : obsList) {
            String code = getObservationCode(obs);
            if (code != null)
                map.computeIfAbsent(code, k -> new ArrayList<>()).add(obs);
        }
        return map;
    }

    private static String getObservationCode(ObjectNode obs) {
        JsonNode coding = obs.path("code").path("coding");
        if (coding.isArray() && coding.size() > 0)
            return coding.get(0).path("code").asText(null);
        return null;
    }

    private static void mapIdsForPair(String type, ObjectNode genRes, ObjectNode origRes,
            Map<String, String> idMapping) {
        if (!genRes.has("id") || !origRes.has("id"))
            return;
        String genId = genRes.path("id").asText();
        String origId = origRes.path("id").asText();
        genRes.put("id", origId);
        idMapping.put(type + "/" + genId, type + "/" + origId);
    }

    private static void copyFullUrlAndRequest(JsonNode originalBundleNode, JsonNode generatedBundleNode,
            Map<String, String> idMapping) {
        Map<String, JsonNode> origEntryMap = new HashMap<>();
        for (JsonNode entry : originalBundleNode.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id")) {
                origEntryMap.put(res.path("resourceType").asText() + "/" + res.path("id").asText(), entry);
            }
        }
        for (JsonNode genEntry : generatedBundleNode.path("entry")) {
            JsonNode genRes = genEntry.path("resource");
            if (!genRes.has("resourceType") || !genRes.has("id"))
                continue;
            String key = genRes.path("resourceType").asText() + "/" + genRes.path("id").asText();
            JsonNode origEntry = origEntryMap.get(key);
            if (origEntry != null) {
                if (origEntry.has("fullUrl"))
                    ((ObjectNode) genEntry).put("fullUrl", origEntry.path("fullUrl").asText());
                if (origEntry.has("request") && origEntry.path("request").has("url")) {
                    ObjectNode req = genEntry.has("request") ? (ObjectNode) genEntry.path("request")
                            : mapper.createObjectNode();
                    req.put("url", origEntry.path("request").path("url").asText());
                    ((ObjectNode) genEntry).set("request", req);
                }
            }
        }
    }

    private static void copyConsentAndGrouperFields(JsonNode originalBundleNode, JsonNode generatedBundleNode,
            Map<String, String> idMapping,
            String replayInteractionId, String interactionId, String bundleId, String tenantId, String techbdVersion) {

        // Build original resource map for quick lookup by key
        Map<String, JsonNode> origMap = new HashMap<>();
        for (JsonNode entry : originalBundleNode.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id")) {
                origMap.put(res.path("resourceType").asText() + "/" + res.path("id").asText(), res);
            }
        }

        for (JsonNode genEntry : generatedBundleNode.path("entry")) {
            JsonNode genRes = genEntry.path("resource");
            if (!genRes.has("resourceType") || !genRes.has("id"))
                continue;
            String key = genRes.path("resourceType").asText() + "/" + genRes.path("id").asText();
            JsonNode origRes = origMap.get(key);
            if (origRes == null)
                continue;

            String type = genRes.path("resourceType").asText();
            ObjectNode genObj = (ObjectNode) genRes;

            // Consent.dateTime
            if ("Consent".equals(type) && origRes.has("dateTime")) {
                genObj.put("dateTime", origRes.path("dateTime").asText());
                LOG.info("CCDA-REPLAY CONSENT FIELD COPIED - {} Consent.dateTime copied for {}",
                        replayInteractionId, key);
            }

            // Grouper/List/Group issued
            if (("List".equals(type) || "Group".equals(type)) && origRes.has("issued")) {
                genObj.put("issued", origRes.path("issued").asText());
                LOG.info("CCDA-REPLAY GROUPER FIELD COPIED - {} issued copied for {}",
                        replayInteractionId, key);
            }

            // Screening Observations issued
            if ("Observation".equals(type) && origRes.has("issued")) {
                String code = null;
                JsonNode codingArray = origRes.path("code").path("coding");
                if (codingArray.isArray() && codingArray.size() > 0) {
                    code = codingArray.get(0).path("code").asText(null);
                }
                if (code != null && SCREENING_CODES.contains(code)) {
                    genObj.put("issued", origRes.path("issued").asText());
                    LOG.info("CCDA-REPLAY SCREENING OBSERVATION FIELD COPIED - {} issued copied for Observation/{}",
                            replayInteractionId, genObj.path("id").asText());
                }
            }
        }
    }

    private static void updateReferences(ArrayNode entries, Map<String, String> idMapping) {
        for (JsonNode entry : entries) {
            JsonNode res = entry.path("resource");
            if (!res.isMissingNode())
                updateReferencesRecursive((ObjectNode) res, idMapping);
        }
    }

    private static void updateReferencesRecursive(ObjectNode node, Map<String, String> idMapping) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        List<Map.Entry<String, JsonNode>> snapshot = new ArrayList<>();
        fields.forEachRemaining(snapshot::add);
        for (Map.Entry<String, JsonNode> field : snapshot) {
            JsonNode value = field.getValue();
            String fieldName = field.getKey();

            if (value.isObject()) {
                ObjectNode obj = (ObjectNode) value;
                if (obj.has("reference")) {
                    String ref = obj.path("reference").asText();
                    String newRef = resolveReference(ref, idMapping);
                    if (newRef != null)
                        obj.put("reference", newRef);
                }
                updateReferencesRecursive(obj, idMapping);

            } else if (value.isArray()) {
                for (JsonNode arrItem : value) {
                    if (arrItem.isObject()) {
                        ObjectNode arrObj = (ObjectNode) arrItem;
                        // Update reference if exists
                        if (arrObj.has("reference")) {
                            String ref = arrObj.path("reference").asText();
                            String newRef = resolveReference(ref, idMapping);
                            if (newRef != null)
                                arrObj.put("reference", newRef);
                        }
                        // Recurse inside
                        updateReferencesRecursive(arrObj, idMapping);
                    }
                }
            }
        }
    }

    private static String resolveReference(String ref, Map<String, String> idMapping) {
        if (idMapping.containsKey(ref))
            return idMapping.get(ref);
        if (idMapping.containsValue(ref))
            return ref;
        return null;
    }

    private static void detectMissingOrAdditional(JsonNode originalBundleNode, JsonNode generatedBundleNode) {
        Set<String> origIds = new HashSet<>();
        for (JsonNode entry : originalBundleNode.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id"))
                origIds.add(res.path("resourceType").asText() + "/" + res.path("id").asText());
        }
        Set<String> genIds = new HashSet<>();
        for (JsonNode entry : generatedBundleNode.path("entry")) {
            JsonNode res = entry.path("resource");
            if (res.has("resourceType") && res.has("id"))
                genIds.add(res.path("resourceType").asText() + "/" + res.path("id").asText());
        }
        for (String origId : origIds)
            if (!genIds.contains(origId))
                LOG.warn("Original resource {} missing in generated bundle.", origId);
        for (String genId : genIds)
            if (!origIds.contains(genId))
                LOG.warn("Generated resource {} not found in original bundle.", genId);
    }
}
