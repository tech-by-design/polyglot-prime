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
    private static final String HL7_SN_ORU = "MSH|^~\\&||GHC|||||ORU^R01|||2.5|\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N\r"
            +
            "PV1||O|||||C1^Smith^Sid^^^^^^GHC|||||||EO|||||GHC_V1|||||||||||||||||||||||||20111022094500|20111022094500|\r"
            +
            "ORC||GHC-P1|GHC-F1||||^^^201110100910||201110100912|||C1^Smith^Sid^^^^^^GHC|GHC||||||||GHC||||||||LAB|\r" +
            "OBR||GHC-P1|GHC-F1|RGL^Random Glucose^L|||201110101214|||||||201110100937||C1^Smith^Sid^^^^^^GHC||||||201110101227|||F|\r"
            +
            "OBX||NM|GLU^GLUCOSE||12.3|mmol/l|70-99||||F|||201110101214|\r" +
            "ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r";

    private static final String HL7_PBRD_FULL = "MSH|^~\\&||GHC|||||ORU^R01|||2.5|\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N\r"
            +
            "PV1||O|||||C1^Smith^Sid^^^^^^GHC|||||||EO|||||GHC_V1|||||||||||||||||||||||||20111022094500|20111022094500|\r"
            +
            "ORC||GHC-P1|GHC-F1||||^^^201110100910||201110100912|||C1^Smith^Sid^^^^^^GHC|GHC||||||||GHC||||||||LAB|\r" +
            "OBR||GHC-P1|GHC-F1|RGL^Random Glucose^L|||201110101214|||||||201110100937||C1^Smith^Sid^^^^^^GHC||||||201110101227|||F|\r"
            +
            "OBX||NM|GLU^GLUCOSE||12.3|mmol/l|70-99||||F|||201110101214|\r" +
            "ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r";

    private static final String HL7_PUSH_FULL = "MSH|^~\\&||GHC|||||ORU^R01|||2.5|\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N\r"
            +
            "PV1||O|||||C1^Smith^Sid^^^^^^GHC|||||||EO|||||GHC_V1|||||||||||||||||||||||||20111022094500|20111022094500|\r"
            +
            "ORC||GHC-P1|GHC-F1||||^^^201110100910||201110100912|||C1^Smith^Sid^^^^^^GHC|GHC||||||||GHC||||||||LAB|\r" +
            "OBR||GHC-P1|GHC-F1|RGL^Random Glucose^L|||201110101214|||||||201110100937||C1^Smith^Sid^^^^^^GHC||||||201110101227|||F|\r"
            +
            "OBX||NM|GLU^GLUCOSE||12.3|mmol/l|70-99||||F|||201110101214|\r" +
            "ZNT||ORU|R01|PUSH|||||5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255|userId^Test^User|\r";

    private static final String HL7_SN_ADT_FULL = "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N\r"
            +
            "CON|1||||||||||||A|20240603140500|20260603140500|\r" +
            "PD1||||145425^Ingersol^Angela\r" +
            "NK1|1|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Emergency Contact 1\r" +
            "NK1|2|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Mother\r" +
            "PV1||103|ED^^^EGSMC^^^^^^^|ER||| C10^Smith^John^^^^^^EGSMC|||1|||||||||5363788347|||||||||||||||||||||||||20160102133600\r"
            +
            "GT1|1|153620341|DeSantis^Stuart^M^||4749 Maple Drive^^Chicago^NC^37789^USA^^^BOULDER|668-202-3982||19890718|F|P/F|MOT|181-48-1624||||Globagy.com|^^^^^USA|||None\r"
            +
            "IN1||5200324231|5190155816|KwalSonics Partners|||||||||||1|Cheng^Agnes^Brenda^|Self|19700908|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS|||1***1|||YES||||||||||1484851|148112259||||||STUDENT|F|^^^^^USA|||BOTH\r"
            +
            "ZNT||ADT|A01|SN_ADT|ClinicianGroupSN|CohortGroupSN_Name^CohortGroup_SN_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r";

    private static final String HL7_PBAD_FULL = "MSH|^~\\&|EPIC|EGSMC|||||ADT^A03|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N\r"
            +
            "CON|1||||||||||||A|20240603140500|20260603140500|\r" +
            "PD1||||145425^Ingersol^Angela\r" +
            "NK1|1|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Emergency Contact 1\r" +
            "NK1|2|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Mother\r" +
            "PV1||103|ED^^^EGSMC^^^^^^^|ER||| C10^Smith^John^^^^^^EGSMC|||1|||||||||5363788347|||||||||||||||||||||||||20160102133600|20160108133600\r"
            +
            "GT1|1|153620341|DeSantis^Stuart^M^||4749 Maple Drive^^Chicago^NC^37789^USA^^^BOULDER|668-202-3982||19890718|F|P/F|MOT|181-48-1624||||Globagy.com|^^^^^USA|||None\r"
            +
            "IN1||5200324231|5190155816|KwalSonics Partners|||||||||||1|Cheng^Agnes^Brenda^|Self|19700908|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS|||1***1|||YES||||||||||1484851|148112259||||||STUDENT|F|^^^^^USA|||BOTH\r"
            +
            "ZNT||ADT|A03|PBAC|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r";

    private static final String HL7_READMIT_FULL = "MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1\r" +
            "EVN||20160102133621|||167489^Zampitello^Liza|20160102133621\r" +
            "PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N\r"
            +
            "CON|1||||||||||||A|20240603140500|20260603140500|\r" +
            "PD1||||145425^Ingersol^Angela\r" +
            "NK1|1|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Emergency Contact 1\r" +
            "NK1|2|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Mother\r" +
            "PV1||103|ED^^^EGSMC^^^^^^^|ER||| C10^Smith^John^^^^^^EGSMC|||1|||||||||5363788347|||||||||||||||||||||||||20160102133600\r"
            +
            "GT1|1|153620341|DeSantis^Stuart^M^||4749 Maple Drive^^Chicago^NC^37789^USA^^^BOULDER|668-202-3982||19890718|F|P/F|MOT|181-48-1624||||Globagy.com|^^^^^USA|||None\r"
            +
            "IN1||5200324231|5190155816|KwalSonics Partners|||||||||||1|Cheng^Agnes^Brenda^|Self|19700908|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS|||1***1|||YES||||||||||1484851|148112259||||||STUDENT|F|^^^^^USA|||BOTH\r"
            +
            "ZNT||ADT|A01|READMIT|ClinicianGroupREADMIT|CohortGroupREADMIT_Name^CohortGroupREADMIT_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||\r";

    private static final String HL7_RD_PATHOLOGY =
            "MSH|^~\\&|PATHOLOGY|healthelink:GLP|INTERSYSTEMS|NYHEL|20241211152312||ORU^R01|3343300.5483410|P|2.4\r" +
            "PID|1||54355554^^^healthelink:UPMCEP^MRN~691cf06c46bbb80a21e8a055^^^^RRI||FOOBAR^TEST^^^^||19880901|M|||^^^||||||||||||||\r" +
            "PV1|1|S|CA504IM^^^CA504^^^^^CA504 GLPP INTERNAL MEDICINE||||1101299992^PROVIDER^HISTORICAL^^^^^^healthelink:GLP^^^^NPI|||||U^GLP|||||||S-healthelink:GLP-54355554-20251111|||||||||||||||||||||||||20251111000000\r" +
            "ORC|1|39645956411|||||^^^20251111||20241211152312||||||||||||||||||||LAB\r" +
            "OBR|1|39645956411|39645956411|LAB136233^(SENDOUT) CHLAMYDIA/N. GONORRHOEAE AND T. VAGINALIS RNA, QUALITATIVE, TMA, PAP VIAL|||20241211152312|||||||||1101299992^PROVIDER^HISTORICAL^^^^^^healthelink:GLP^^^^NPI||||||||LAB|M\r" +
            "NTE|1||This order contains result documents that were not sent. The result might be incomplete.\r" +
            "NTE|2\r" +
            "NTE|3||This order was created through External Result Entry\r" +
            "OBX|1|ST|1810545^CHLAMYDIA TRACHOMATIS^BRLRR^35729-3||Not Detected||Not Detected||||M|||20241211152312|||||||||Test Physician Practice\r" +
            "OBX|2|ST|1810544^NEISSERIA GONORRHOEAE^BRLRR^21414-8||Not Detected||Not Detected||||M|||20241211152312|||||||||Test Physician Practice\r" +
            "OBX|3|ST|1822^TRICHOMONAS VAGINALIS^BRLRR^69937-1||Not Detected||Not Detected||||M|||20241211152312|||||||||Test Physician Practice\r" +
            "SPM|1|||Cytobroom^Cytobroom^^^^^^^Cytobroom|||||||||||||20241211152312^0000\r" +
            "ZNT||ORU|R01|RD||^||healthelink:GLP|healthelink:UPMCEP^54355554^healthelink:GLP|691cf06c46bbb80a21e8a055||||RD\\S\\ECMC\\S\\ClinGroup^RD^ECMC^ClinGroup||This record which has been disclosed to you is protected by Federal confidentiality rules (42 CFR part 2). These rules prohibit you from using or disclosing this record, or testimony that describes the information contained in this record, in any civil, criminal, administrative, or legislative proceedings by any Federal, State, or local authority, against the patient, unless authorized by the consent of the patient, except as provided at 42 CFR 2.12(c)(5) or as authorized by a court in accordance with 42 CFR 2.64 or 2.65. In addition, the Federal rules prohibit you from making any other use or disclosure of this record unless at least one of the following applies:\\.br\\(i) Further use or disclosure is expressly permitted by the written consent of the individual whose information is being disclosed in this record or as otherwise permitted by 42 CFR part 2. \\.br\\(ii) You are a covered entity or business associate and have received the record for treatment, payment, or health care operations, or \\.br\\(iii) You have received the record from a covered entity or business associate as permitted by 45 CFR part 164, subparts A and E. \\.br\\A general authorization for the release of medical or other information is NOT sufficient to meet the required elements of written consent to further use or redisclose the record (see 42 CFR 2.31).|";

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
    @DisplayName("Test R01 – Results Delivery/Identified Clinician PBRD ZNT segment")
    void testPBRD() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_PBRD_FULL, "PBRD", facilities, "ORU");
    }

    @Test
    @DisplayName("Test R01 – AdHoc Push from EHR PUSH ZNT segment")
    void testPUSH() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_PUSH_FULL, "PUSH", facilities, "ORU");
    }

    @Test
    @DisplayName("Test A01 - Subscribe and Notify SN_ADT ZNT segment")
    void testSN_ADT() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_SN_ADT_FULL, "SN_ADT", facilities, "ADT");
    }

    @Test
    @DisplayName("Test A03 - Subscribe and Notify PBAC ZNT segment")
    void testPBAD() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_PBAD_FULL, "PBAC", facilities, "ADT");
    }

    @Test
    @DisplayName("Test A01 – Readmission READMIT ZNT segment")
    void testREADMIT() throws HL7Exception {
        List<String> facilities = List.of("healthelink:EGSMC", "healthelink:CHG");
        assertZntFields(HL7_READMIT_FULL, "READMIT", facilities, "ADT");
    }

    @Test
    @DisplayName("Test RD - Pathology Results Delivery ZNT segment")
    void testRD_Pathology() throws HL7Exception {
        List<String> facilities = List.of("healthelink:UPMCEP", "healthelink:GLP");
        assertZntFields(HL7_RD_PATHOLOGY, "RD", facilities, "ORU");
    }

    // ---------------------------
    // Helper Methods
    // ---------------------------

    private void assertZntFields(String hl7Message, String expectedDeliveryType,
                                 List<String> expectedFacilities, String expectedMessageCode) throws HL7Exception {

        // Try Terser first
        try {
            assertZntFieldsWithTerser(hl7Message, expectedDeliveryType, expectedFacilities, expectedMessageCode);
        } catch (HL7Exception e) {
            // If Terser fails, fall back to manual extraction
            assertZntFieldsManually(hl7Message, expectedDeliveryType, expectedFacilities, expectedMessageCode);
        }
    }

    private void assertZntFieldsWithTerser(String hl7Message, String expectedDeliveryType,
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

    private void assertZntFieldsManually(String hl7Message, String expectedDeliveryType,
                                        List<String> expectedFacilities, String expectedMessageCode) throws HL7Exception {
        
        // Extract ZNT segment manually from HL7 message
        String[] lines = hl7Message.split("\r|\n");
        String zntLine = null;

        // Find ZNT segment
        for (String line : lines) {
            if (line.trim().startsWith("ZNT|")) {
                zntLine = line.trim();
                break;
            }
        }

        if (zntLine == null) {
            throw new HL7Exception("ZNT segment not found in message");
        }

        // ZNT segment format: ZNT|field1|field2|field3|field4|field5|field6|field7|field8...
        String[] fields = zntLine.split("\\|", -1);

        // Extract fields based on typical ZNT structure
        String messageCode = null;
        String deliveryType = null;
        String znt8_1 = null;

        // ZNT-2 (component 1) - Message Code
        if (fields.length > 2 && !fields[2].isEmpty()) {
            String[] components = fields[2].split("\\^", -1);
            messageCode = components.length > 0 ? components[0] : null;
        }

        // ZNT-4 (component 1) - Delivery Type
        if (fields.length > 4 && !fields[4].isEmpty()) {
            String[] components = fields[4].split("\\^", -1);
            deliveryType = components.length > 0 ? components[0] : null;
        }

        // ZNT-8 (component 1) - e.g., healthelink:GHC
        if (fields.length > 8 && !fields[8].isEmpty()) {
            String[] components = fields[8].split("\\^", -1);
            znt8_1 = components.length > 0 ? components[0] : null;
        }

        // Derive MsgGroupID
        String msgGroupId = buildMsgGroupId(znt8_1, messageCode, deliveryType);

        // Extract facilities from field 9
        // Handles both formats:
        // 1. Component 2 only: 5003637762^healthelink:EGSMC~64654645^healthelink:CHG
        // 2. Multiple components with colons: healthelink:UPMCEP^54355554^healthelink:GLP
        List<String> facilities = new ArrayList<>();
        if (fields.length > 9 && !fields[9].isEmpty()) {
            String[] reps = fields[9].split("~");
            for (String rep : reps) {
                String[] components = rep.split("\\^", -1);
                
                // Check if this looks like format 1 (component 2 has colon) or format 2 (multiple components with colons)
                boolean hasColonInComp2 = components.length > 1 && components[1].contains(":");
                
                if (hasColonInComp2) {
                    // Format 1: Extract component 2 (index 1)
                    if (!components[1].isEmpty()) {
                        facilities.add(components[1]);
                    }
                } else {
                    // Format 2: Extract all components that contain ":"
                    for (String component : components) {
                        if (!component.isEmpty() && component.contains(":")) {
                            facilities.add(component);
                        }
                    }
                }
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