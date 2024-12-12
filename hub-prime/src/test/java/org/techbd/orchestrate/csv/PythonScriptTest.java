package org.techbd.orchestrate.csv;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PythonScriptTest {

    @Test
    public void testProcessInput() throws Exception {
        // Define the interaction_id
        String interactionId = "test";

        // Run the Python script
        ProcessBuilder processBuilder = new ProcessBuilder("python3", "src/test/resources/org/techbd/csv/python-script/python-test.py", interactionId);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line);
        }

        // Wait for the process to exit
        int exitCode = process.waitFor();
        assertNotNull(output.toString(), "The output should not be null");
        assert exitCode == 0 : "Python script did not execute successfully";

        // Convert the output to Map (assumes JSON output)
        Map<String, Object> resultMap = parseJsonToMap(output.toString());

        // Perform assertions
        assertNotNull(resultMap);
        assert resultMap.containsKey("interaction_id");
        assert resultMap.containsKey("valid");
        assert "test".equals(resultMap.get("interaction_id"));
        assert Boolean.TRUE.equals(resultMap.get("valid"));
    }

    // Method to parse the JSON string into a Map
    private Map<String, Object> parseJsonToMap(String json) {
        // Here, you could use a library like Jackson or Gson to parse the JSON
        // Using a simple implementation for example purposes
        Map<String, Object> map = new HashMap<>();
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(json);
            map.put("interaction_id", jsonObject.get("interaction_id"));
            map.put("valid", jsonObject.get("valid"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }
}
