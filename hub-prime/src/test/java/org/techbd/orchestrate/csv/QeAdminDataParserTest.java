package org.techbd.orchestrate.csv;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

public class QeAdminDataParserTest {

    private static final String CSV_PATH_QE_ADMIN_DATA = "src/test/resources/org/techbd/csv/data/QE_ADMIN_DATA.csv";

    public static class FacilityData {
        @CsvBindByName(column = "PAT_MRN_ID")
        String patMrnId;
        @CsvBindByName(column = "FACILITY_ID")
        String facilityId;
        @CsvBindByName(column = "FACILITY_LONG_NAME")
        String facilityLongName;
        @CsvBindByName(column = "ORGANIZATION_TYPE")
        String organizationType;
        @CsvBindByName(column = "FACILITY_ADDRESS1")
        String facilityAddress1;
        @CsvBindByName(column = "FACILITY_ADDRESS2")
        String facilityAddress2;
        @CsvBindByName(column = "FACILITY_CITY")
        String facilityCity;
        @CsvBindByName(column = "FACILITY_STATE")
        String facilityState;
        @CsvBindByName(column = "FACILITY_ZIP")
        String facilityZip;
        @CsvBindByName(column = "VISIT_PART_2_FLAG")
        String visitPart2Flag;
        @CsvBindByName(column = "VISIT_OMH_FLAG")
        String visitOmhFlag;
        @CsvBindByName(column = "VISIT_OPWDD_FLAG")
        String visitOpwddFlag;

        // Default constructor for OpenCSV to use
        public FacilityData() {
        }

        // Constructor for easy instantiation (if needed)
        public FacilityData(String patMrnId, String facilityId, String facilityLongName, String organizationType,
                String facilityAddress1, String facilityAddress2, String facilityCity, String facilityState,
                String facilityZip, String visitPart2Flag, String visitOmhFlag, String visitOpwddFlag) {
            this.patMrnId = patMrnId;
            this.facilityId = facilityId;
            this.facilityLongName = facilityLongName;
            this.organizationType = organizationType;
            this.facilityAddress1 = facilityAddress1;
            this.facilityAddress2 = facilityAddress2;
            this.facilityCity = facilityCity;
            this.facilityState = facilityState;
            this.facilityZip = facilityZip;
            this.visitPart2Flag = visitPart2Flag;
            this.visitOmhFlag = visitOmhFlag;
            this.visitOpwddFlag = visitOpwddFlag;
        }

        // Getters for the fields
        public String getPatMrnId() {
            return patMrnId;
        }

        public String getFacilityId() {
            return facilityId;
        }

        public String getFacilityLongName() {
            return facilityLongName;
        }

        public String getOrganizationType() {
            return organizationType;
        }

        public String getFacilityAddress1() {
            return facilityAddress1;
        }

        public String getFacilityAddress2() {
            return facilityAddress2;
        }

        public String getFacilityCity() {
            return facilityCity;
        }

        public String getFacilityState() {
            return facilityState;
        }

        public String getFacilityZip() {
            return facilityZip;
        }

        public String getVisitPart2Flag() {
            return visitPart2Flag;
        }

        public String getVisitOmhFlag() {
            return visitOmhFlag;
        }

        public String getVisitOpwddFlag() {
            return visitOpwddFlag;
        }
    }

    @Test
    public void testCsvParsingAndAssertions() throws IOException {

        // Create a FileReader to read the CSV file
        FileReader fileReader = new FileReader(CSV_PATH_QE_ADMIN_DATA);

        // Parse the CSV into FacilityData objects
        CsvToBean<FacilityData> csvToBean = new CsvToBeanBuilder<FacilityData>(fileReader)
                .withType(FacilityData.class)
                .withIgnoreLeadingWhiteSpace(true)
                .withSeparator('|')
                .build();

        List<FacilityData> records = csvToBean.parse();

        // Create SoftAssertions instance for non-blocking assertions
        SoftAssertions softly = new SoftAssertions();
        
        // Iterate through records and assert each field
        for (FacilityData record : records) {
            softly.assertThat(record.getPatMrnId())
                    .as("PAT_MRN_ID should match the expected value")
                    .isEqualTo("qcs-test-20240603-testcase4-MRN");

            softly.assertThat(record.getFacilityId())
                    .as("FACILITY_ID should match the expected value")
                    .isEqualTo("CNYSCN");

            softly.assertThat(record.getFacilityLongName())
                    .as("FACILITY_LONG_NAME should match the expected value")
                    .isEqualTo("Crossroads NY Social Care Network");

            softly.assertThat(record.getOrganizationType())
                    .as("ORGANIZATION_TYPE should match the expected value")
                    .isEqualTo("SCN");

            softly.assertThat(record.getFacilityState())
                    .as("FACILITY_STATE should match the expected value")
                    .isEqualTo("New York");

            softly.assertThat(record.getFacilityZip())
                    .as("FACILITY_ZIP should match the expected value")
                    .isEqualTo("10036");

            // Add more assertions based on the expected values for other fields
        }

        // Perform all assertions at once after testing all records
        softly.assertAll();
    }

    // @Test
    // public void testCsvParsingAndAssertions1() throws IOException {

    //     String csvData = "PAT_MRN_ID|FACILITY_ID|FACILITY_LONG_NAME|ORGANIZATION_TYPE|FACILITY_ADDRESS1|FACILITY_ADDRESS2|FACILITY_CITY|FACILITY_STATE|FACILITY_ZIP|VISIT_PART_2_FLAG|VISIT_OMH_FLAG|VISIT_OPWDD_FLAG\n"
    //             +
    //             "qcs-test-20240603-testcase4-MRN|CNYSCN|Crossroads NY Social Care Network|SCN|25 W 45th st|Suite 16|New York|New York|10036|No|No|No";

    //     // Use StringReader to read from the string
    //     StringReader stringReader = new StringReader(csvData);

    //     // Parse the CSV into FacilityData objects
    //     CsvToBean<FacilityData> csvToBean = new CsvToBeanBuilder<FacilityData>(stringReader)
    //             .withType(FacilityData.class)
    //             .withIgnoreLeadingWhiteSpace(true)
    //             .withSeparator('|') // Specify the delimiter as '|'
    //             .build();

    //     List<FacilityData> records = csvToBean.parse();

    //     // Create SoftAssertions instance for non-blocking assertions
    //     SoftAssertions softly = new SoftAssertions();

    //     // Iterate through records and assert each field
    //     for (FacilityData record : records) {
    //         softly.assertThat(record.getPatMrnId())
    //                 .as("PAT_MRN_ID should match the expected value")
    //                 .isEqualTo("qcs-test-20240603-testcase4-MRN");

    //         softly.assertThat(record.getFacilityId())
    //                 .as("FACILITY_ID should match the expected value")
    //                 .isEqualTo("CNYSCN");

    //         softly.assertThat(record.getFacilityLongName())
    //                 .as("FACILITY_LONG_NAME should match the expected value")
    //                 .isEqualTo("Crossroads NY Social Care Network");

    //         softly.assertThat(record.getOrganizationType())
    //                 .as("ORGANIZATION_TYPE should match the expected value")
    //                 .isEqualTo("SCN");

    //         softly.assertThat(record.getFacilityState())
    //                 .as("FACILITY_STATE should match the expected value")
    //                 .isEqualTo("New York");

    //         softly.assertThat(record.getFacilityZip())
    //                 .as("FACILITY_ZIP should match the expected value")
    //                 .isEqualTo("10036");

    //         // Add more assertions based on the expected values for other fields
    //     }

    //     // Perform all assertions at once after testing all records
    //     softly.assertAll();
    // }
}