package org.techbd.service.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.service.converters.csv.CsvToFhirConverter;

import com.opencsv.bean.CsvToBeanBuilder;

@Service
public class CsvService {
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class.getName());
    private final CsvToFhirConverter csvToFhirConverter;

    public CsvService(CsvToFhirConverter csvToFhirConverter){
        this.csvToFhirConverter = csvToFhirConverter;
    }
    /**
     * Processes a Zip file uploaded as a MultipartFile and extracts data into
     * corresponding lists.
     *
     * @param file The uploaded zip file (MultipartFile).
     * @throws Exception If an error occurs during processing the zip file or CSV
     *                   parsing.
     */
    public void processZipFile(MultipartFile file) throws Exception {
        String qeAdminDataStr = getQeAdminCsvData();
        String screeningDataStr = getScreeningCsv();
        String demographicDataStr = getDemographicData();

        List<DemographicData> demographicData = convertCsvStringToDemographicData(demographicDataStr);
        List<ScreeningData> screeningData = convertCsvStringToScreeningData(screeningDataStr);
        List<QeAdminData> qeAdminData = convertCsvStringToQeAdminData(qeAdminDataStr);
        if (CollectionUtils.isEmpty(demographicData) || CollectionUtils.isEmpty(screeningData) || CollectionUtils.isEmpty(qeAdminData)) {
            throw new IllegalArgumentException("Invalid Zip File"); //TODO later change with custom exception
        }
        csvToFhirConverter.convert(demographicData.get(0),screeningData,qeAdminData.get(0), "int-id");//TODO -pass interaction id

    }
    private String getQeAdminCsvData() {
        return """
                PAT_MRN_ID|FACILITY_ID|FACILITY_LONG_NAME|ORGANIZATION_TYPE|FACILITY_ADDRESS1|FACILITY_ADDRESS2|FACILITY_CITY|FACILITY_STATE|FACILITY_ZIP|VISIT_PART_2_FLAG|VISIT_OMH_FLAG|VISIT_OPWDD_FLAG
                qcs-test-20240603-testcase4-MRN|CNYSCN|Crossroads NY Social Care Network|SCN|25 W 45th st|Suite 16|New York|New York|10036|No|No|No
                """;
    }

    private String getScreeningCsv() {
        return "PATIENT_MR_ID|FACILITY_ID|ENCOUNTER_ID|ENCOUNTER_CLASS_CODE|ENCOUNTER_CLASS_CODE_DESCRIPTION|ENCOUNTER_CLASS_CODE_SYSTEM|ENCOUNTER_STATUS_CODE|ENCOUNTER_STATUS_CODE_DESCRIPTION|ENCOUNTER_STATUS_CODE_SYSTEM|ENCOUNTER_TYPE_CODE|ENCOUNTER_TYPE_CODE_DESCRIPTION|ENCOUNTER_TYPE_CODE_SYSTEM|ENCOUNTER_START_TIME|ENCOUNTER_END_TIME|ENCOUNTER_LAST_UPDATED|LOCATION_NAME|LOCATION_STATUS|LOCATION_TYPE_CODE|LOCATION_TYPE_SYSTEM|LOCATION_ADDRESS1|LOCATION_ADDRESS2|LOCATION_CITY|LOCATION_DISTRICT|LOCATION_STATE|LOCATION_ZIP|LOCATION_PHYSICAL_TYPE_CODE|LOCATION_PHYSICAL_TYPE_SYSTEM|SCREENING_STATUS_CODE|SCREENING_CODE|SCREENING_CODE_DESCRIPTION|SCREENING_CODE_SYSTEM_NAME|RECORDED_TIME|QUESTION_CODE|QUESTION_CODE_DESCRIPTION|QUESTION_CODE_SYSTEM_NAME|UCUM_UNITS|SDOH_DOMAIN|PARENT_QUESTION_CODE|ANSWER_CODE|ANSWER_CODE_DESCRIPTION|ANSWER_CODE_SYSTEM_NAME\n" +
               "11223344|CUMC|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|115 Broadway Suite #1601||New York|MANHATTAN|NY|10006|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|unknown|96777-8|Accountable health communities (AHC) health-related social needs screening (HRSN) tool|http://loinc.org|2023-07-12T16:08:00.000Z|71802-3|What is your living situation today?|http://loinc.org||Homelessness, Housing Instability||LA31993-1|I have a steady place to live|http://loinc.org\n" +
               "11223344|CUMC|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|116 Broadway Suite #1601||New York|MANHATTAN|NY|10007|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|unknown|96777-8|Accountable health communities (AHC) health-related social needs screening (HRSN) tool|http://loinc.org|2023-07-12T16:08:00.000Z|96778-6|Think about the place you live. Do you have problems with any of the following?|http://loinc.org||Inadequate Housing||LA28580-1|Mold|http://loinc.org";
    }
    
    private String getDemographicData() {
        return "PATIENT_MR_ID|FACILITY_ID|CONSENT_STATUS|CONSENT_TIME|GIVEN_NAME|MIDDLE_NAME|FAMILY_NAME|GENDER|SEX_AT_BIRTH_CODE|SEX_AT_BIRTH_CODE_DESCRIPTION|SEX_AT_BIRTH_CODE_SYSTEM|PATIENT_BIRTH_DATE|ADDRESS1|ADDRESS2|CITY|DISTRICT|STATE|ZIP|PHONE|SSN|PERSONAL_PRONOUNS_CODE|PERSONAL_PRONOUNS_CODE_DESCRIPTION|PERSONAL_PRONOUNS_CODE_SYSTEM_NAME|GENDER_IDENTITY_CODE|GENDER_IDENTITY_CODE_DESCRIPTION|GENDER_IDENTITY_CODE_SYSTEM_NAME|SEXUAL_ORIENTATION_CODE|SEXUAL_ORIENTATION_CODE_DESCRIPTION|SEXUAL_ORIENTATION_CODE_SYSTEM_NAME|PREFERRED_LANGUAGE_CODE|PREFERRED_LANGUAGE_CODE_DESCRIPTION|PREFERRED_LANGUAGE_CODE_SYSTEM_NAME|RACE_CODE|RACE_CODE_DESCRIPTION|RACE_CODE_SYSTEM_NAME|ETHNICITY_CODE|ETHNICITY_CODE_DESCRIPTION|ETHNICITY_CODE_SYSTEM_NAME|MEDICAID_CIN|PATIENT_LAST_UPDATED|RELATIONSHIP_PERSON_CODE|RELATIONSHIP_PERSON_DESCRIPTION|RELATIONSHIP_PERSON_SYSTEM|RELATIONSHIP_PERSON_GIVEN_NAME|RELATIONSHIP_PERSON_FAMILY_NAME|RELATIONSHIP_PERSON_TELECOM_SYSTEM|RELATIONSHIP_PERSON_TELECOM_VALUE\n" +
               "11223344|CUMC|active|2024-02-23T00:00:00Z|Jon|Bob|Doe|male|M|Male|http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex|1981-07-16|115 Broadway Apt2||New York|MANHATTAN|NY|10032|1234567890|999-34-2964|LA29518-0|he/him/his/his/himself|http://loinc.org|LA22878-5|Identifies as male|http://loinc.org|LA4489-6|Unknown|http://loinc.org|en|English|urn:ietf:bcp:47|2028-9|Asian|urn:oid:2.16.840.1.113883.6.238|2135-2|Hispanic or Latino|urn:oid:2.16.840.1.113883.6.238|AA12345C|2024-02-23T00:00:00.00Z|MTH|Mother|http://terminology.hl7.org/CodeSystem/v2-0063|Joyce|Doe|Phone|1234567890";
    }
    
    /**
     * Converts a CSV string to a list of objects of the specified type.
     *
     * @param csvData The CSV string containing the data.
     * @param clazz   The class type to which the data should be converted.
     * @param separator The separator used in the CSV string (e.g., '|').
     * @param <T> The type of the object to convert the CSV to (DemographicData, ScreeningData, etc.).
     * @return List of objects of the specified type.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static <T> List<T> convertCsvStringToObjectList(String csvData, Class<T> clazz, char separator) throws IOException {
        List<T> dataList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvData))) {
            dataList = new CsvToBeanBuilder<T>(reader)
                    .withType(clazz)
                    .withSeparator(separator) // Specify the separator
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
        }
        return dataList;
    }

    /**
     * Converts a CSV string to a list of DemographicData objects.
     *
     * @param csvData The CSV string containing the demographic data.
     * @return List of DemographicData objects.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static List<DemographicData> convertCsvStringToDemographicData(String csvData) throws IOException {
        return convertCsvStringToObjectList(csvData, DemographicData.class, '|');
    }

    /**
     * Converts a CSV string to a list of ScreeningData objects.
     *
     * @param csvData The CSV string containing the screening data.
     * @return List of ScreeningData objects.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static List<ScreeningData> convertCsvStringToScreeningData(String csvData) throws IOException {
        return convertCsvStringToObjectList(csvData, ScreeningData.class, '|');
    }

    /**
     * Converts a CSV string to a list of QeAdminData objects.
     *
     * @param csvData The CSV string containing the QeAdmin data.
     * @return List of QeAdminData objects.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static List<QeAdminData> convertCsvStringToQeAdminData(String csvData) throws IOException {
        return convertCsvStringToObjectList(csvData, QeAdminData.class, '|');
    }

}