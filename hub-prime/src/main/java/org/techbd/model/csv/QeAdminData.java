package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;
/**
 * This class represents the data structure for the QeAdmin CSV file, containing details about facilities and 
 * associated identifiers.
 * <p>
 * It is used to bind data from a CSV file where each field corresponds to a column in the CSV file.
 * </p>
 */
@Getter
public class QeAdminData {
    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "FACILITY_ACTIVE")
    private String facilityActive;

    @CsvBindByName(column = "FACILITY_ID")
    private String facilityId;

    @CsvBindByName(column = "FACILITY_NAME")
    private String facilityName;

    @CsvBindByName(column = "ORGANIZATION_TYPE_DISPLAY")
    private String organizationTypeDisplay;

    @CsvBindByName(column = "ORGANIZATION_TYPE_CODE")
    private String organizationTypeCode;

    @CsvBindByName(column = "ORGANIZATION_TYPE_SYSTEM")
    private String organizationTypeSystem;

    @CsvBindByName(column = "FACILITY_ADDRESS1")
    private String facilityAddress1;

    @CsvBindByName(column = "FACILITY_ADDRESS2")
    private String facilityAddress2;

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

    @CsvBindByName(column = "FACILITY_PROFILE")
    private String facilityProfile;

    @CsvBindByName(column = "FACILITY_SCN_IDENTIFIER_TYPE_DISPLAY")
    private String facilityScnIdentifierTypeDisplay;

    @CsvBindByName(column = "FACILITY_SCN_IDENTIFIER_TYPE_VALUE")
    private String facilityScnIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_SCN_IDENTIFIER_TYPE_SYSTEM")
    private String facilityScnIdentifierTypeSystem;

    @CsvBindByName(column = "FACILITY_NPI_IDENTIFIER_TYPE_CODE")
    private String facilityNpiIdentifierTypeCode;

    @CsvBindByName(column = "FACILITY_NPI_IDENTIFIER_TYPE_VALUE")
    private String facilityNpiIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_NPI_IDENTIFIER_TYPE_SYSTEM")
    private String facilityNpiIdentifierTypeSystem;

    @CsvBindByName(column = "FACILITY_CMS_IDENTIFIER_TYPE_CODE")
    private String facilityCmsIdentifierTypeCode;

    @CsvBindByName(column = "FACILITY_CMS_IDENTIFIER_TYPE_VALUE")
    private String facilityCmsIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_CMS_IDENTIFIER_TYPE_SYSTEM")
    private String facilityCmsIdentifierTypeSystem;

    @CsvBindByName(column = "FACILITY_TEXT_STATUS")
    private String facilityTextStatus;
    /**
     * Default constructor for OpenCSV to create an instance of QeAdminData.
     */
    public QeAdminData() {
    }
}

