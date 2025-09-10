package org.techbd.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BundleResourceIdCopierTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String readFile(String relativePath) throws IOException {
        Path path = Path.of("src/test/resources/org/techbd/replay", relativePath);
        return Files.readString(path);
    }

    @Test
    void testCopyResourceIds() throws IOException {
        // Read original and generated bundles from resources
        String originalBundleJson = readFile("originalbundle.json");
        String newBundleJson = readFile("newbundle.json");

        // Call util
        JsonNode updatedBundle = FhirBundleUtil.copyResourceIds(originalBundleJson, newBundleJson);

        // Parse original bundle for comparison
        JsonNode originalBundle = objectMapper.readTree(originalBundleJson);

        // Assertions for each entry
        assertThat(updatedBundle.get("entry")).isNotNull();
        assertThat(originalBundle.get("entry")).isNotNull();

        int entryCount = originalBundle.get("entry").size();
        assertThat(updatedBundle.get("entry").size()).isEqualTo(entryCount);

        for (int i = 0; i < entryCount; i++) {
            JsonNode originalEntry = originalBundle.get("entry").get(i);
            JsonNode updatedEntry = updatedBundle.get("entry").get(i);

            // Assert fullUrl
            assertThat(updatedEntry.get("fullUrl").asText())
                    .isEqualTo(originalEntry.get("fullUrl").asText());

            // Assert request.url
            if (originalEntry.has("request") && originalEntry.get("request").has("url")) {
                assertThat(updatedEntry.get("request").get("url").asText())
                        .isEqualTo(originalEntry.get("request").get("url").asText());
            }

            // Assert resource.id
            if (originalEntry.has("resource") && originalEntry.get("resource").has("id")) {
                assertThat(updatedEntry.get("resource").get("id").asText())
                        .isEqualTo(originalEntry.get("resource").get("id").asText());
            }
        }
    }
}
