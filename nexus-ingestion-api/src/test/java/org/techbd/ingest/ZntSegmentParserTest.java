package org.techbd.ingest;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
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

    private static final String HL7_A01 =
            "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r" +
            "ZNT||ADT|A01|SN_ADT|ClinicianGroupSN|CohortGroupSN_Name^CohortGroup_SN_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_A03 =
            "MSH|^~\\&|EPIC|EGSMC|||||ADT^A03|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r" +
            "ZNT||ADT|A03|PBAC|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_READMIT =
            "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r" +
            "ZNT||ADT|A01|READMIT|ClinicianGroupREADMIT|CohortGroupREADMIT_Name^CohortGroupREADMIT_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_SN_ORU =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r" +
            "ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PBRD =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r" +
            "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||";

    private static final String HL7_PUSH =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F\r" +
            "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|add";

    // New versions with ZNT placed immediately after MSH
    private static final String HL7_SN_ORU_ZNT_AFTER_MSH =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F";

    private static final String HL7_PBRD_ZNT_AFTER_MSH =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F";

    private static final String HL7_PUSH_ZNT_AFTER_MSH =
            "MSH|^~\\&||GHC|||||ORU^R01|||2.5\r" +
            "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|add\r" +
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

    private void assertZntFields(String hl7Message, String expectedDeliveryType,
                                 String expectedFacility, String expectedMessageCode) throws HL7Exception {

        Message message = parser.parse(hl7Message);

        // Use Terser to access ZNT segment dynamically
        Terser terser = new Terser(message);

        String messageCode = terser.get("/ZNT-2");
        String triggerEvent = terser.get("/ZNT-3");
        String deliveryType = terser.get("/ZNT-4");
        String facility = terser.get("/ZNT-8");

        assertThat(messageCode).isEqualTo(expectedMessageCode);
        assertThat(triggerEvent).isNotEmpty();
        assertThat(deliveryType).isEqualTo(expectedDeliveryType);
        assertThat(facility).isEqualTo(expectedFacility);

        System.out.printf("ZNT parsed: %s | %s | %s | %s%n",
                messageCode, triggerEvent, deliveryType, facility);
    }
}
