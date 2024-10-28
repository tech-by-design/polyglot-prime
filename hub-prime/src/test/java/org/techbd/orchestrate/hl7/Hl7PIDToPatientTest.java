package org.techbd.orchestrate.hl7;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ibm.icu.text.SimpleDateFormat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.message.ORU_R01;
import ca.uhn.hl7v2.model.v27.segment.PID;
import ca.uhn.hl7v2.parser.PipeParser;

public class Hl7PIDToPatientTest {

    @Test
    void testHl7ToFhirConversion() throws Exception {
        HapiContext context = new DefaultHapiContext();
        String hl7Message = "MSH|^~\\&|HIS|RIH|EKG|EKG|202310190830||ORU^R01|MSG00001|P|2.7\r" +
                "PID|||12345^^^Hospital^MR||Doe^John^Bob^Jr.^Dr.||19700101|M";
        context.getParserConfiguration().setValidating(false);
        PipeParser pipeParser = context.getPipeParser();
        pipeParser.getParserConfiguration().setAllowUnknownVersions(true);

        // Parse the HL7 message
        Message message = pipeParser.parse(hl7Message);

        // Cast the parsed message to ORU_R01 and extract the PID segment
        ORU_R01 oruMessage = ((ORU_R01) message);
        oruMessage.getMSH();
        PID pid = oruMessage.getPATIENT_RESULT().getPATIENT().getPID();
        String patientId = pid.getPid3_PatientIdentifierList(0).getCx1_IDNumber().getValue();
        String familyName = pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname().getValue();
        String givenName = pid.getPid5_PatientName(0).getXpn2_GivenName().getValue();
        String middleName = pid.getPid5_PatientName(0).getXpn3_SecondAndFurtherGivenNamesOrInitialsThereof().getValue();
        String prefix = pid.getPid5_PatientName(0).getXpn5_PrefixEgDR().getValue();
        String suffix = pid.getPid5_PatientName(0).getXpn4_SuffixEgJRorIII().getValue();
        String birthDate = pid.getPid7_DateTimeOfBirth().getValue();
        String gender = pid.getPid8_AdministrativeSex().getIdentifier().getValue();
        String isoBirthDate = convertHl7DateToIso(birthDate);
        String lastUpdated = oruMessage.getMSH().getMsh7_DateTimeOfMessage().getValue();
        String isoLastUpdated = convertHl7DateTimeToIso(lastUpdated);
        // Convert HL7 PID segment to FHIR Patient resource
        Patient patient = new Patient();
        patient.addIdentifier().setSystem("http://hospital.org/mrn").setValue(patientId);
        patient.addName()
                .setFamily(familyName)
                .addGiven(givenName)
                .addPrefix(prefix)
                .addSuffix(suffix);

        // Add middle name as an extension
        patient.getNameFirstRep()
                .addExtension(new Extension()
                        .setUrl("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/middle-name")
                        .setValue(new org.hl7.fhir.r4.model.StringType(middleName)));

        patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType(isoBirthDate));
        patient.setGender(gender.equals("M") ? org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE
                : org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
        Meta meta = new Meta();
        meta.setLastUpdatedElement(new InstantType(isoLastUpdated));
        meta.setProfile(List.of(
                new CanonicalType("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/shinny-patient")));
        patient.setMeta(meta);
        // Use FHIR context to parse the resource to a FHIR JSON (optional step for
        // debugging)
        FhirContext fhirContext = FhirContext.forR4();
        IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        String fhirResourceJson = fhirJsonParser.encodeResourceToString(patient);
        System.out.println(fhirResourceJson);
        assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("12345");
        assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Doe");
        assertThat(patient.getNameFirstRep().getGivenAsSingleString()).isEqualTo("John");
        assertThat(patient.getNameFirstRep().getPrefix().get(0).getValue()).isEqualTo("Dr.");
        assertThat(patient.getNameFirstRep().getSuffix().get(0).getValue()).isEqualTo("Jr.");
        assertThat(patient.getNameFirstRep()
                .getExtensionByUrl("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/middle-name")
                .getValue().primitiveValue()).isEqualTo("Bob");
        assertThat(patient.getBirthDateElement().asStringValue()).isEqualTo("1970-01-01");
        assertThat(patient.getGender()).isEqualTo(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
        assertThat(patient.getMeta().getProfile().get(0).getValue())
                .contains("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/shinny-patient");
        // assertThat(patient.getMeta().getLastUpdated())
        //         .isNotNull()
        //         .isEqualTo(new InstantType(isoLastUpdated));
    }

    @Test
    @Disabled("Test is just for viewing generated json")
    public void testViewGeneratedJson() throws Exception {
        // Parse the HL7 message
        String hl7Message = "MSH|^~\\&|HIS|RIH|EKG|EKG|202310190830||ORU^R01|MSG00001|P|2.7\r" +
                "PID|||12345^^^Hospital^MR||Doe^John^Bob^Dr.^Jr.||19700101|M";

        String outputFilePath = "src/test/resources/org/techbd/hl7/orur01/generated-json/patient.json";
        HapiContext context = new DefaultHapiContext();
        PipeParser pipeParser = context.getPipeParser();
        context.getParserConfiguration().setValidating(false);
        pipeParser.getParserConfiguration().setAllowUnknownVersions(true);
        Message message = pipeParser.parse(hl7Message);

        // Cast the parsed message to ORU_R01 and extract the PID segment
        ORU_R01 oruMessage = ((ORU_R01) message);
        PID pid = oruMessage.getPATIENT_RESULT().getPATIENT().getPID();

        // Extract relevant fields from PID
        String patientId = pid.getPid3_PatientIdentifierList(0).getCx1_IDNumber().getValue();
        String familyName = pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname().getValue();
        String givenName = pid.getPid5_PatientName(0).getXpn2_GivenName().getValue();
        String middleName = pid.getPid5_PatientName(0).getXpn3_SecondAndFurtherGivenNamesOrInitialsThereof().getValue();
        String prefix = pid.getPid5_PatientName(0).getXpn5_PrefixEgDR().getValue();
        String suffix = pid.getPid5_PatientName(0).getXpn4_SuffixEgJRorIII().getValue();
        String birthDate = pid.getPid7_DateTimeOfBirth().getValue();
        String gender = pid.getPid8_AdministrativeSex().getName();

        // Extract last updated from MSH segment
        String lastUpdated = oruMessage.getMSH().getMsh7_DateTimeOfMessage().getValue();

        // Convert HL7 date (yyyyMMdd) to ISO date format (yyyy-MM-dd)
        String isoBirthDate = convertHl7DateToIso(birthDate);
        String isoLastUpdated = convertHl7DateTimeToIso(lastUpdated);

        // Create the FHIR Patient resource using fluent style
        Patient patient = new Patient();
        patient.addIdentifier()
                .setValue(patientId)
                .getType().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("MR");

        patient.addName()
                .setFamily(familyName)
                .addGiven(givenName)
                .addPrefix(prefix)
                .addSuffix(suffix);

        // Add middle name as an extension
        patient.getNameFirstRep()
                .addExtension(new Extension()
                        .setUrl("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/middle-name")
                        .setValue(new org.hl7.fhir.r4.model.StringType(middleName)));

        // Set birth date
        patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType(isoBirthDate));
        patient.setGender(gender.equals("M") ? org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE
                : org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);

        // Set lastUpdated in Meta
        Meta meta = new Meta();
        meta.setLastUpdatedElement(new InstantType(isoLastUpdated));
        meta.setProfile(List.of(
                new CanonicalType("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/shinny-patient")));
        patient.setMeta(meta);

        // Convert FHIR resource to JSON
        FhirContext fhirContext = FhirContext.forR4();
        IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        String fhirResourceJson = fhirJsonParser.encodeResourceToString(patient);

        // Write JSON to the specified file
        Path outputPath = Paths.get(outputFilePath);
        Files.createDirectories(outputPath.getParent()); // Ensure directories exist
        Files.writeString(outputPath, fhirResourceJson);
    }

    private String convertHl7DateToIso(String hl7Date) throws Exception {
        SimpleDateFormat hl7Format = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = hl7Format.parse(hl7Date);
        return isoFormat.format(date);
    }

    private String convertHl7DateTimeToIso(String hl7DateTime) {
        return hl7DateTime.substring(0, 4) + "-" +
                hl7DateTime.substring(4, 6) + "-" +
                hl7DateTime.substring(6, 8) + "T" +
                hl7DateTime.substring(8, 10) + ":" +
                hl7DateTime.substring(10, 12) + ":00Z";
    }
}
