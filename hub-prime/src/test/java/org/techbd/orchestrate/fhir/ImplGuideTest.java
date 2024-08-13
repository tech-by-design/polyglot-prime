package org.techbd.orchestrate.fhir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.jupiter.api.Test;

public class ImplGuideTest {

    private JSONObject igJsonObject;
    private JSONObject rhettJsonObject;

    @Before
    public void setup() throws Exception {
        // Load ig.json from resources
        igJsonObject = loadJsonFromResource("ig.json");

        // Load Rhett.json from resources
        rhettJsonObject = loadJsonFromResource("Rhett.json");
    }

    private JSONObject loadJsonFromResource(String fileName) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("data/" + fileName);
        if (is == null) {
            System.err.println("File not found in resources: " + fileName);
            throw new IllegalArgumentException("File not found: " + fileName);
        }
        String jsonData = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("Successfully loaded JSON from resource: " + fileName);
        return new JSONObject(jsonData);
    }

    @Test
    public void testStructureDefinitionValidationFixture() {
        try {
            // Validate mandatory elements
            igJsonObject = loadJsonFromResource("ig.json");
            assertNotNull("igJsonObject should not be null", igJsonObject);
            assertEquals("SHINNYBundleProfile", igJsonObject.getString("id"));
            assertEquals("http://shinny.org/StructureDefinition/SHINNYBundleProfile", igJsonObject.getString("url"));
            assertEquals("4.0.1", igJsonObject.getString("fhirVersion"));

            // Validate constraints
            JSONArray constraints = igJsonObject.getJSONObject("snapshot").getJSONArray("element").getJSONObject(0).getJSONArray("constraint");
            assertEquals("bdl-1", constraints.getJSONObject(0).getString("key"));
            assertEquals("bdl-2", constraints.getJSONObject(1).getString("key"));
        } catch (Exception e) {
        }
    }

    @Test
    public void testHRSNDataValidationFixture() {
        try {
            JSONArray entries = rhettJsonObject.getJSONArray("entry");

            // Validate fullUrl uniqueness
            Set<String> fullUrls = new HashSet<>();
            for (int i = 0; i < entries.length(); i++) {
                String fullUrl = entries.getJSONObject(i).getString("fullUrl");
                assertTrue("Duplicate fullUrl found", fullUrls.add(fullUrl));
            }

            // Validate Patient references Organization and Encounter
            for (int i = 0; i < entries.length(); i++) {
                JSONObject resource = entries.getJSONObject(i).getJSONObject("resource");
                if (resource.getString("resourceType").equals("Patient")) {
                    // Example validation for Patient-Organization reference
                    String orgReference = resource.getJSONArray("identifier")
                            .getJSONObject(0)
                            .getJSONObject("assigner")
                            .getString("reference");
                    assertTrue("Invalid Organization reference", orgReference.contains("Organization"));
                }
            }
        } catch (Exception e) {
        }
    }
}
