package org.techbd.csv.model;

import com.opencsv.bean.CsvBindByName;
import lombok.Getter;
import lombok.Setter;
/**
 * This class represents the data structure for the QeAdmin CSV file, containing details about facilities and 
 * associated identifiers.
 * <p>
 * It is used to bind data from a CSV file where each field corresponds to a column in the CSV file.
 * </p>
 */

@Getter
@Setter
public class QeAdminData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "FACILITY_ID")
    private String facilityId;

    @CsvBindByName(column = "FACILITY_NAME")
    private String facilityName;

    @CsvBindByName(column = "ORGANIZATION_TYPE_CODE")
    private String organizationTypeCode;

    @CsvBindByName(column = "ORGANIZATION_TYPE_DISPLAY")
    private String organizationTypeDisplay;

    @CsvBindByName(column = "ORGANIZATION_TYPE_CODE_SYSTEM")
    private String organizationTypeCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_LOCATION")
    private String encounterLocation;

    @CsvBindByName(column = "FACILITY_ADDRESS1")
    private String facilityAddress1;

    @CsvBindByName(column = "FACILITY_ADDRESS2")
    private String facilityAddress2;

    @CsvBindByName(column = "FACILITY_CITY")
    private String facilityCity;

    @CsvBindByName(column = "FACILITY_STATE")
    private String facilityState;

    @CsvBindByName(column = "FACILITY_ZIP")
    private String facilityZip;

    @CsvBindByName(column = "FACILITY_COUNTY")
    private String facilityCounty;

    @CsvBindByName(column = "FACILITY_LAST_UPDATED")
    private String facilityLastUpdated;

    @CsvBindByName(column = "VISIT_PART_2_FLAG")
    private String visitPart2Flag;

    // Default constructor
    public QeAdminData() {
    }
}
