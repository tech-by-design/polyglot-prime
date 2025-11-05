package org.techbd.ingest;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ZNTParserTest {

    private final GenericParser parser;

    @SuppressWarnings("deprecation")
    public ZNTParserTest() {
        parser = new GenericParser();
        parser.setValidationContext(new NoValidation()); // Disable HL7 validation
    }

    // ---------------------------
    // Sample HL7 Messages
    // ---------------------------

    private static final String HL7_SN_ORU =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5|\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N|\r" +
            "ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PBRD =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5|\r" +
            "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PUSH =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5|\r" +
            "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|";

    private static final String HL7_SN_ADT =
            "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1|\r" +
            "ZNT||ADT|A01|SN_ADT|ClinicianGroupSN|CohortGroupSN_Name^CohortGroup_SN_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PBAD =
            "MSH|^~\\&|EPIC|EGSMC|||||ADT^A03|||2.3.1|\r" +
            "ZNT||ADT|A03|PBAC|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_READMIT =
            "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1|\r" +
            "ZNT||ADT|A01|READMIT|ClinicianGroupREADMIT|CohortGroupREADMIT_Name^CohortGroupREADMIT_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    // ---------------------------
    // Tests
    // ---------------------------

    @Test
    @DisplayName("Test R01 – Subscribe and Notify -SN_ORU ZNT segment")
    void testSN_ORU() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_SN_ORU, "SN_ORU", facilities, "ORU");
    }

    @Test
    @DisplayName("Test R01 – Results Delivery/Identified Clinician  PBRD ZNT segment")
    void testPBRD() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_PBRD, "PBRD", facilities, "ORU");
    }

    @Test
    @DisplayName("Test R01 – AdHoc Push from EHR PUSH ZNT segment")
    void testPUSH() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_PUSH, "PUSH", facilities, "ORU");
    }

    @Test
    @DisplayName("Test A01 - Subscribe and Notify  SN_ADT ZNT segment")
    void testSN_ADT() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_SN_ADT, "SN_ADT", facilities, "ADT");
    }

    @Test
    @DisplayName("Test A03 - Subscribe and Notify PBAC ZNT segment")
    void testPBAD() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_PBAD, "PBAC", facilities, "ADT");
    }

    @Test
    @DisplayName("Test A01 – Readmission READMIT ZNT segment")
    void testREADMIT() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_READMIT, "READMIT", facilities, "ADT");
    }

    // ---------------------------
    // Helper Methods
    // ---------------------------

    private void assertZntFields(String hl7Message, String expectedDeliveryType,
                                 List<String> expectedFacilities, String expectedMessageCode) throws HL7Exception {

        Message message = parser.parse(hl7Message);
        Terser terser = new Terser(message);
        Segment znt = terser.getSegment(".ZNT");

        // Extract key fields
        String messageCode = terser.get("/.ZNT-2-1");  // ZNT.2
        String deliveryType = terser.get("/.ZNT-4-1"); // ZNT.4
        String znt8_1 = terser.get("/.ZNT-8-1");     // ZNT.8.1 (e.g., healthelink:GHC)

        // Derive MsgGroupID = {QE}_{FACILITY}_{MSGTYPE}_{DELIVERYTYPE}
        String msgGroupId = buildMsgGroupId(znt8_1, messageCode, deliveryType);

        // Extract facilities from field 9
        Type[] field9Reps = znt.getField(9);
        List<String> facilities = new ArrayList<>();
        for (int i = 0; i < field9Reps.length; i++) {
            String comp2 = terser.get("/.ZNT-9(" + i + ")-2");
            if (comp2 != null && !comp2.isEmpty()) {
                facilities.add(comp2);
            }
        }

        // Assertions
        assertThat(messageCode).isEqualTo(expectedMessageCode);
        assertThat(deliveryType).isEqualTo(expectedDeliveryType);
        assertThat(facilities).containsExactlyElementsOf(expectedFacilities);

        // New assertion for MsgGroupID
        String expectedMsgGroupId = buildMsgGroupId(znt8_1, expectedMessageCode, expectedDeliveryType);
        assertThat(msgGroupId).isEqualTo(expectedMsgGroupId);
    }

    private String buildMsgGroupId(String znt8_1, String msgType, String deliveryType) {
        if (znt8_1 == null || znt8_1.isEmpty()) {
            return String.format("_%s_%s", msgType, deliveryType);
        }
        String[] parts = znt8_1.split(":");
        String qe = parts.length > 0 ? parts[0] : "";
        String facility = parts.length > 1 ? parts[1] : "";
        return String.format("%s_%s_%s_%s", qe, facility, msgType, deliveryType);
    }
}
