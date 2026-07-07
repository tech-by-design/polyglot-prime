package org.techbd.ingest.integrationtests.util;

/**
 * Shared HL7 and CCD fixture strings for TCP keep-alive integration tests.
 *
 * <h3>Design rationale</h3>
 * <p>Both {@link NettyTcpServerKeepAliveITCase} (HL7 MLLP / port 5555) and
 * {@link NettyTcpServerTcpKeepAliveITCase} (raw TCP / port 6555) need distinct,
 * content-rich payloads so that SQS FIFO deduplication (5-minute window, keyed on
 * message content hash) does not silently suppress a second message that is identical
 * to the first.  Centralising fixtures here keeps both test classes lean and makes
 * the payload intent visible in one place.
 *
 * <h3>SQS FIFO deduplication note</h3>
 * <p>SQS FIFO queues deduplicate within a 5-minute window using a
 * {@code MessageDeduplicationId} that the server derives from the message content.
 * If two messages carry identical bytes the second is silently discarded.  These
 * fixtures are intentionally different in both content and message type to avoid
 * that collision.
 *
 */
public final class NettyTcpServerKeepAliveFixtures {

    private NettyTcpServerKeepAliveFixtures() { /* utility class */ }

    // =========================================================================
    // HL7 fixtures  — used by NettyTcpServerKeepAliveITCase (port 5555, MLLP)
    // =========================================================================

    /**
     * HL7 message 1: ORU^R01 from GHC.
     *
     * <p>ZNT segment: {@code ZNT||ORU|R01|PBRD|||...|healthelink:GHC|...}
     * <br>Expected SQS {@code messageGroupId}: {@code "healthelink_GHC_ORU_PBRD"}
     * (derived as {@code GHC_ORU_PBRD} from ZNT-8 suffix, ZNT-2, ZNT-4).
     */
    public static final String HL7_MSG1_ORU = """
        MSH|^~\\&||GHC|||||ORU^R01|||2.5|
        PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N
        PV1||O|||||C1^Smith^Sid^^^^^^GHC|||||||EO|||||GHC_V1|||||||||||||||||||||||||20111022094500|20111022094500|
        ORC||GHC-P1|GHC-F1||||^^^201110100910||201110100912|||C1^Smith^Sid^^^^^^GHC|GHC||||||||GHC||||||||LAB|
        OBR||GHC-P1|GHC-F1|RGL^Random Glucose^L|||201110101214|||||||201110100937||C1^Smith^Sid^^^^^^GHC||||||201110101227|||F|
        OBX||NM|GLU^GLUCOSE||12.3|mmol/l|70-99||||F|||201110101214|
        ZNT||ORU|R01|PBRD|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:GHC|55003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||||PBRD_Subscription^Patient Results^ReceivingSystem^QE01.GHC||
        """.replace("\n", "\r");

    /**
     * HL7 message 2: ADT^A01 from EPIC/EGSMC.
     *
     * <p>Structurally and content-different from {@link #HL7_MSG1_ORU}, which
     * guarantees the SQS FIFO deduplication window does not suppress it.
     * <br>ZNT segment: {@code ZNT||ADT|A01|SN_ADT|||...|healthelink:EGSMC|...}
     * <br>Expected SQS {@code messageGroupId}: {@code "healthelink_EGSMC_ADT_SN_ADT"}.
     */
    public static final String HL7_MSG2_ADT_A01 = """
        MSH|^~\\&|EPIC|EGSMC|||||ADT^A01|||2.3.1
        EVN||20160102133621|||167489^Zampitello^Liza|20160102133621
        PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N
        CON|1||||||||||||A|20240603140500|20260603140500|
        PD1||||145425^Ingersol^Angela
        NK1|1|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Emergency Contact 1
        NK1|2|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Mother
        PV1||103|ED^^^EGSMC^^^^^^^|ER||| C10^Smith^John^^^^^^EGSMC|||1|||||||||5363788347|||||||||||||||||||||||||20160102133600
        GT1|1|153620341|DeSantis^Stuart^M^||4749 Maple Drive^^Chicago^NC^37789^USA^^^BOULDER|668-202-3982||19890718|F|P/F|MOT|181-48-1624||||Globagy.com|^^^^^USA|||None
        IN1||5200324231|5190155816|KwalSonics Partners|||||||||||1|Cheng^Agnes^Brenda^|Self|19700908|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS|||1***1|||YES||||||||||1484851|148112259||||||STUDENT|F|^^^^^USA|||BOTH
        ZNT||ADT|A01|SN_ADT|ClinicianGroupSN|CohortGroupSN_Name^CohortGroup_SN_Description||healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||||SubscriptionName^SubscriptionSubject^ReceivingSystem^QE01.EGSMC||
        """.replace("\n", "\r");

    // =========================================================================
    // TCP fixtures  — used by NettyTcpServerTcpKeepAliveITCase (port 6555, raw TCP)
    // =========================================================================

    /**
     * TCP message 1: CCD (Continuity of Care Document) — HL7 CDA / XML format.
     *
     * <p>Sent over the raw-TCP port 6555 ({@code responseType=tcp}).  The server
     * stores this under the hold-flow bucket and enqueues to {@code test.fifo}.
     * <br>Expected SQS {@code messageGroupId}: {@code "6555"} (port number, since
     * the TCP port-config does not specify a ZNT-based group).
     *
     * <p>The document describes patient John Doe (DOB 1980-01-15) with a diagnosis
     * of Hypertension, authored on 2026-04-24.
     */
    public static final String TCP_MSG1_CCD_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ClinicalDocument xmlns="urn:hl7-org:v3"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

                <!-- Header -->
                <typeId root="2.16.840.1.113883.1.3" extension="POCD_HD000040"/>

                <templateId root="2.16.840.1.113883.10.20.22.1.2"/>

                <id root="2.16.840.1.113883.19.5.99999.1"
                    extension="CCD-12345"/>
                <code code="34133-9"
                      codeSystem="2.16.840.1.113883.6.1"
                      displayName="Summarization of Episode Note"/>
                <title>Continuity of Care Document</title>
                <effectiveTime value="20260424120000"/>
                <recordTarget>
                    <patientRole>
                        <id extension="123456" root="2.16.840.1.113883.4.1"/>
                        <patient>
                            <name>
                                <given>John</given>
                                <family>Doe</family>
                            </name>
                            <administrativeGenderCode code="M"/>
                            <birthTime value="19800115"/>
                        </patient>
                    </patientRole>
                </recordTarget>
                <author>
                    <time value="20260424120000"/>
                    <assignedAuthor>
                        <id extension="PROV001"/>
                        <assignedPerson>
                            <name>
                                <given>Jane</given>
                                <family>Smith</family>
                            </name>
                        </assignedPerson>
                    </assignedAuthor>
                </author>
                <!-- Body -->
                <component>
                    <structuredBody>
                        <!-- Problems Section -->
                        <component>
                            <section>
                                <templateId root="2.16.840.1.113883.10.20.22.2.5.1"/>
                                <code code="11450-4"
                                      codeSystem="2.16.840.1.113883.6.1"
                                      displayName="Problem List"/>
                                <title>Problems</title>
                                <text>
                                    Hypertension
                                </text>
                                <entry>
                                    <act classCode="ACT" moodCode="EVN">
                                        <entryRelationship typeCode="SUBJ">
                                            <observation classCode="OBS" moodCode="EVN">
                                                <code code="75326-9"
                                                      displayName="Problem"/>
                                                <value xsi:type="CD"
                                                       code="38341003"
                                                       codeSystem="2.16.840.1.113883.6.96"
                                                       displayName="Hypertension"/>
                                            </observation>
                                        </entryRelationship>
                                    </act>
                                </entry>
                            </section>
                        </component>
                        <!-- Medications Section -->
                        <component>
                            <section>
                                <code code="10160-0"
                                      codeSystem="2.16.840.1.113883.6.1"
                                      displayName="History of Medication Use"/>
                                <title>Medications</title>
                                <text>
                                    Lisinopril 10mg daily
                                </text>
                            </section>
                        </component>
                    </structuredBody>
                </component>
            </ClinicalDocument>
            """;

    /**
     * TCP message 2: ADT^A03 (discharge) from EPIC/EGSMC — HL7 v2.3.1 pipe-delimited.
     *
     * <p>Content-distinct from {@link #TCP_MSG1_CCD_XML} (different format, different
     * message type, different patient encounter details) — SQS FIFO deduplication
     * will not suppress it.
     * <br>ZNT segment: {@code ZNT||ADT|A03|PBAC|||...|healthelink:EGSMC|...}
     * <br>Expected SQS {@code messageGroupId}: {@code "6555"} (port number).
     */
    public static final String TCP_MSG2_ADT_A03 = """
            MSH|^~\\&|EPIC|EGSMC|||||ADT^A03|||2.3.1
            EVN||20160102133621|||167489^Zampitello^Liza|20160102133621
            PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N
            CON|1||||||||||||A|20240603140500|20260603140500|
            PD1||||145425^Ingersol^Angela
            NK1|1|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Emergency Contact 1
            NK1|2|Xavier^Richard^^|Mother|8953 Franklin Blvd^^Tampa^TN^10864^USA|750-923-5821||Mother
            PV1||103|ED^^^EGSMC^^^^^^^|ER||| C10^Smith^John^^^^^^EGSMC|||1|||||||||5363788347|||||||||||||||||||||||||20160102133600|20160108133600
            GT1|1|153620341|DeSantis^Stuart^M^||4749 Maple Drive^^Chicago^NC^37789^USA^^^BOULDER|668-202-3982||19890718|F|P/F|MOT|181-48-1624||||Globagy.com|^^^^^USA|||None
            IN1||5200324231|5190155816|KwalSonics Partners|||||||||||1|Cheng^Agnes^Brenda^|Self|19700908|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS|||1***1|||YES||||||||||1484851|148112259||||||STUDENT|F|^^^^^USA|||BOTH
            ZNT||ADT|A03|PBAC|||C10^SMITH^JOHN^EGSMC^ATT~SMITHJ^SMITH^JOHN^CHG^CON|healthelink:EGSMC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||
            """.replace("\n", "\r");

    // =========================================================================
    // Expected SQS messageGroupId constants
    // =========================================================================
    /**
     * Expected {@code messageGroupId} for {@link #HL7_MSG1_ORU}.
     *
     * <p>
     * Derived from ZNT-14.4 ({@code QE01.GHC} → QE=QE01, facility=GHC),
     * ZNT-2 ({@code ORU}), and ZNT-4 ({@code PBRD}).
     */
    public static final String GROUP_ID_HL7_MSG1 = "QE01_GHC_ORU_PBRD";

    /**
     * Expected {@code messageGroupId} for {@link #HL7_MSG2_ADT_A01}.
     *
     * <p>
     * Derived from ZNT-14.4 ({@code QE01.EGSMC} → QE=QE01, facility=EGSMC),
     * ZNT-2 ({@code ADT}), and ZNT-4 ({@code SN_ADT}).
     */
    public static final String GROUP_ID_HL7_MSG2 = "QE01_EGSMC_ADT_SN_ADT";

    /**
     * Expected {@code messageGroupId} for both TCP messages (port 6555).
     *
     * <p>The TCP port-config ({@code responseType=tcp}) does not perform ZNT-based
     * group-ID derivation; the server uses the destination port number instead.
     */
    public static final String GROUP_ID_TCP = "6555";
}