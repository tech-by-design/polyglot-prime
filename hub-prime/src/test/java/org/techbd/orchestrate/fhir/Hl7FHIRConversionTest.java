package org.techbd.orchestrate.fhir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.linuxforhealth.hl7.HL7ToFHIRConverter;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class Hl7FHIRConversionTest {


    @Test
    public void test() throws Exception {
        final var hl7Message2 = loadFile("hub-prime/src/main/resources/hl7-files/Example_1 _HRSN_HL7_Transcription.hl7");
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        String output = ftv.convert(hl7Message2);
        wrtieToFile(output);
    }

    
 private static String loadFile(final String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            throw new IOException("Failed to load the file: " + filename);
        }
    
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            return content.toString();
        }
    }
    private static void wrtieToFile(String jsonString) throws Exception {
        String outputFileName = "hub-prime/src/main/resources/fhir/fhir-message1.json";
        File outputFile = new File(outputFileName);
        // Use ObjectMapper to pretty print the JSON
        ObjectMapper mapper = new ObjectMapper();
        Object jsonObject = mapper.readValue(jsonString, Object.class);  // Convert JSON string to Object
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        String prettyJson = writer.writeValueAsString(jsonObject);  // Pretty-print JSON

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile, false))) {
            bufferedWriter.write(prettyJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to file", e);
        }
    }
}
