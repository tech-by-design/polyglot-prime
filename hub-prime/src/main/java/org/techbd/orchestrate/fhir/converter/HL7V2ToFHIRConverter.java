package org.techbd.orchestrate.fhir.converter;

import org.techbd.orchestrate.fhir.OrchestrationEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.linuxforhealth.hl7.HL7ToFHIRConverter;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
public class HL7V2ToFHIRConverter {

    public static void main(String a[]) throws Exception {
        final var hl7message = "MSH|^~\\&|ADT1|GOOD HEALTH HOSPITAL|GHH LAB, INC.|GOOD HEALTH HOSPITAL|198808181126|SECURITY|ADT^A01^ADT_A01|MSG00001|P|2.8||\n"
                + "EVN|A01|200708181123||\n"
                + "PID|1||PATID1234^5^M11^ADT1^MR^GOOD HEALTH HOSPITAL~123456789^^^USSSA^SS||EVERYMAN^ADAM^A^III||19610615|M||C|2222 HOME STREET^^GREENSBORO^NC^27401-1020|GL|(555) 555-2004|(555)555-2004||S||PATID12345001^2^M10^ADT1^AN^A|444333333|987654^NC|\n"
                + "NK1|1|NUCLEAR^NELDA^W|SPO^SPOUSE||||NK^NEXT OF KIN\n"
                + "PV1|1|I|2000^2012^01||||004777^ATTEND^AARON^A|||SUR||||ADM|A0|";
        final var hl7Message2 = loadFile("hub-prime/src/main/resources/hl7-files/Example_1 _HRSN_HL7_Transcription.hl7");
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        String output = ftv.convert(hl7Message2);
        wrtieToFile(output);
        System.out.println(output);
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
