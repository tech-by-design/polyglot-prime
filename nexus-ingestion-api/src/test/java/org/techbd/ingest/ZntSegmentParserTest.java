package org.techbd.ingest;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZntSegmentParserTest {

    private final GenericParser parser;

    public ZntSegmentParserTest() {
        parser = new GenericParser();
        parser.setValidationContext(new NoValidation()); // Disable HL7 validation
    }

    // ---------------------------
    // HL7 Messages with ZNT segment
    // ---------------------------

    private static final String HL7_A01 = "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
            +
            "ZNT||ADT|A01|SN_ADT|ClinicianGroupSN|CohortGroupSN_Name^CohortGroup_SN_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_A03 = "MSH|^~\\&|EPIC|EGSMC|||||ADT^A03|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
            +
            "ZNT||ADT|A03|PBAC|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_READMIT = "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
            +
            "ZNT||ADT|A01|READMIT|ClinicianGroupREADMIT|CohortGroupREADMIT_Name^CohortGroupREADMIT_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_SN_ORU = "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
            +
            "ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PBRD = "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
            +
            "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PUSH = "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
            +
            "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|add";

    // New versions with ZNT placed immediately after MSH
    private static final String HL7_SN_ORU_ZNT_AFTER_MSH = "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r"
            +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F";

    private static final String HL7_PBRD_ZNT_AFTER_MSH = "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r"
            +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F";

    private static final String HL7_PUSH_ZNT_AFTER_MSH = "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|add\r"
            +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F";

    // ---------------------------
    // Existing Tests (unchanged)
    // ---------------------------

    @Test
    @DisplayName("A01 - Readmission SN_ADT")
    void testA01() throws HL7Exception {
        assertZntFields(HL7_A01, "SN_ADT", "healthelink:EGSMC", "ADT");
    }

    @Test
    @DisplayName("A03 - Subscribe and Notify PBAC")
    void testA03() throws HL7Exception {
        assertZntFields(HL7_A03, "PBAC", "healthelink:EGSMC", "ADT");
    }

    @Test
    @DisplayName("A01 - Readmission READMIT")
    void testReadmit() throws HL7Exception {
        assertZntFields(HL7_READMIT, "READMIT", "healthelink:EGSMC", "ADT");
    }

    @Test
    @DisplayName("R01 - Subscribe and Notify SN_ORU")
    void testSnOru() throws HL7Exception {
        assertZntFields(HL7_SN_ORU, "SN_ORU", "healthelink:GHC", "ORU");
    }

    @Test
    @DisplayName("R01 - Results Delivery/Identified Clinician PBRD")
    void testPbrd() throws HL7Exception {
        assertZntFields(HL7_PBRD, "PBRD", "healthelink:GHC", "ORU");
    }

    @Test
    @DisplayName("R01 - AdHoc Push from EHR PUSH")
    void testPush() throws HL7Exception {
        assertZntFields(HL7_PUSH, "PUSH", "healthelink:EGSMC", "ORU");
    }

    // ---------------------------
    // New Tests: ZNT after MSH
    // ---------------------------

    @Test
    @DisplayName("R01 - SN_ORU with ZNT after MSH")
    void testSnOruZntAfterMsh() throws HL7Exception {
        assertZntFields(HL7_SN_ORU_ZNT_AFTER_MSH, "SN_ORU", "healthelink:GHC", "ORU");
    }

    @Test
    @DisplayName("R01 - PBRD with ZNT after MSH")
    void testPbrdZntAfterMsh() throws HL7Exception {
        assertZntFields(HL7_PBRD_ZNT_AFTER_MSH, "PBRD", "healthelink:GHC", "ORU");
    }

    @Test
    @DisplayName("R01 - PUSH with ZNT after MSH")
    void testPushZntAfterMsh() throws HL7Exception {
        assertZntFields(HL7_PUSH_ZNT_AFTER_MSH, "PUSH", "healthelink:EGSMC", "ORU");
    }

    // ---------------------------
    // Helper Method
    // ---------------------------
    private void assertZntFields(String hl7Message,
            String expectedDeliveryType,
            String expectedFacility,
            String expectedMessageCode) throws HL7Exception {

        Message message = parser.parse(hl7Message);
        Terser terser = new Terser(message);

        // Use safe extraction for each ZNT field
        String messageCode = extractZntFieldSafely(message, terser, 2, 0);
        String triggerEvent = extractZntFieldSafely(message, terser, 3, 0);
        String deliveryType = extractZntFieldSafely(message, terser, 4, 0);
        String facility = extractZntFieldSafely(message, terser, 8, 0);

        // Assertions
        assertThat(messageCode).isEqualTo(expectedMessageCode);
        assertThat(triggerEvent).isNotEmpty();
        assertThat(deliveryType).isEqualTo(expectedDeliveryType);
        assertThat(facility).isEqualTo(expectedFacility);

        System.out.printf("ZNT parsed: %s | %s | %s | %s%n",
                messageCode, triggerEvent, deliveryType, facility);
    }

    // Helper templates for versioned messages
    private String createPushZntAfterMsh(String version) {
        return "MSH|^~\\&||GHC|||||ORU^R01|||" + version + "\r" +
                "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|add\r"
                +
                "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F";
    }

    private String createPbrdZntAfterPid(String version) {
        return "MSH|^~\\&||GHC|||||ORU^R01|||" + version + "\r" +
                "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r"
                +
                "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";
    }

    // -------------- Version 2.4 --------------
    @Test
    @DisplayName("HL7 v2.4 - PUSH - ZNT after MSH")
    void testPushZntAfterMsh_v24() throws HL7Exception {
        String hl7 = createPushZntAfterMsh("2.4");
        assertZntFields(hl7, "PUSH", "healthelink:EGSMC", "ORU");
    }

    @Test
    @DisplayName("HL7 v2.4 - PBRD - ZNT after PID")
    void testPbrdZntAfterPid_v24() throws HL7Exception {
        String hl7 = createPbrdZntAfterPid("2.4");
        assertZntFields(hl7, "PBRD", "healthelink:GHC", "ORU");
    }

    // -------------- Version 2.6 --------------
    @Test
    @DisplayName("HL7 v2.6 - PUSH - ZNT after MSH")
    void testPushZntAfterMsh_v26() throws HL7Exception {
        String hl7 = createPushZntAfterMsh("2.6");
        assertZntFields(hl7, "PUSH", "healthelink:EGSMC", "ORU");
    }

    @Test
    @DisplayName("HL7 v2.6 - PBRD - ZNT after PID")
    void testPbrdZntAfterPid_v26() throws HL7Exception {
        String hl7 = createPbrdZntAfterPid("2.6");
        assertZntFields(hl7, "PBRD", "healthelink:GHC", "ORU");
    }

    // -------------- Version 2.7 --------------
    @Test
    @DisplayName("HL7 v2.7 - PUSH - ZNT after MSH")
    void testPushZntAfterMsh_v27() throws HL7Exception {
        String hl7 = createPushZntAfterMsh("2.7");
        assertZntFields(hl7, "PUSH", "healthelink:EGSMC", "ORU");
    }

    @Test
    @DisplayName("HL7 v2.7 - PBRD - ZNT after PID")
    void testPbrdZntAfterPid_v27() throws HL7Exception {
        String hl7 = createPbrdZntAfterPid("2.7");
        assertZntFields(hl7, "PBRD", "healthelink:GHC", "ORU");
    }

    // -------------- Version 2.8 --------------
    @Test
    @DisplayName("HL7 v2.8 - PUSH - ZNT after MSH")
    void testPushZntAfterMsh_v28() throws HL7Exception {
        String hl7 = createPushZntAfterMsh("2.8");
        assertZntFields(hl7, "PUSH", "healthelink:EGSMC", "ORU");
    }

    @Test
    @DisplayName("HL7 v2.8 - PBRD - ZNT after PID")
    void testPbrdZntAfterPid_v28() throws HL7Exception {
        String hl7 = createPbrdZntAfterPid("2.8");
        assertZntFields(hl7, "PBRD", "healthelink:GHC", "ORU");
    }
private String extractZntFieldSafely(Message message, Terser terser, int field, int repetition) {
    try {
        // Terser can find segments anywhere in the message by name
        Segment znt = terser.getSegment("ZNT");
        if (znt == null) {
            return null; // ZNT not present
        }

        // Get field safely
        var fieldData = znt.getField(field, repetition);
        if (fieldData instanceof ca.uhn.hl7v2.model.Varies) {
            var varies = (ca.uhn.hl7v2.model.Varies) fieldData;
            var data = varies.getData();
            if (data instanceof ca.uhn.hl7v2.model.Primitive) {
                return ((ca.uhn.hl7v2.model.Primitive) data).getValue();
            } else {
                return data.encode();
            }
        }
        return fieldData.encode();

    } catch (HL7Exception e) {
        // fallback for missing ZNT
        return null;
    }
}

}
