package org.techbd.model.csv;

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

    @CsvBindByName(column = "ORGANIZATION_TYPE_DISPLAY")
    private String organizationTypeDisplay;

    @CsvBindByName(column = "ORGANIZATION_TYPE_CODE")
    private String organizationTypeCode;

    @CsvBindByName(column = "FACILITY_ADDRESS1")
    private String facilityAddress1;

    @CsvBindByName(column = "FACILITY_CITY")
    private String facilityCity;

    @CsvBindByName(column = "FACILITY_STATE")
    private String facilityState;

    @CsvBindByName(column = "FACILITY_DISTRICT")
    private String facilityDistrict;

    @CsvBindByName(column = "FACILITY_ZIP")
    private String facilityZip;

    @CsvBindByName(column = "FACILITY_LAST_UPDATED")
    private String facilityLastUpdated;

    @CsvBindByName(column = "FACILITY_IDENTIFIER_TYPE_DISPLAY")
    private String facilityIdentifierTypeDisplay;

    @CsvBindByName(column = "FACILITY_IDENTIFIER_TYPE_VALUE")
    private String facilityIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_IDENTIFIER_TYPE_SYSTEM")
    private String facilityIdentifierTypeSystem;

    // Default constructor
    public QeAdminData() {
    }
}
