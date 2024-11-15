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
    @CsvBindByName(column = "PARENT_MR_ID")
    String parentMrId;

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

    @CsvBindByName(column = "FACILITY_LAST_UPDATED")
    String facilityLastUpdated;

    @CsvBindByName(column = "FACILITY_SCN_IDENTIFIER_TYPE_CODE")
    String facilityScnIdentifierTypeCode;

    @CsvBindByName(column = "FACILITY_SCN_IDENTIFIER_TYPE_VALUE")
    String facilityScnIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_SCN_IDENTIFIER_TYPE_SYSTEM")
    String facilityScnIdentifierTypeSystem;

    @CsvBindByName(column = "FACILITY_NPI_IDENTIFIER_TYPE_CODE")
    String facilityNpiIdentifierTypeCode;

    @CsvBindByName(column = "FACILITY_NPI_IDENTIFIER_TYPE_VALUE")
    String facilityNpiIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_NPI_IDENTIFIER_TYPE_SYSTEM")
    String facilityNpiIdentifierTypeSystem;

    @CsvBindByName(column = "FACILITY_CMS_IDENTIFIER_TYPE_CODE")
    String facilityCmsIdentifierTypeCode;

    @CsvBindByName(column = "FACILITY_CMS_IDENTIFIER_TYPE_VALUE")
    String facilityCmsIdentifierTypeValue;

    @CsvBindByName(column = "FACILITY_CMS_IDENTIFIER_TYPE_SYSTEM")
    String facilityCmsIdentifierTypeSystem;
    /**
     * Default constructor for OpenCSV to create an instance of QeAdminData.
     */
    public QeAdminData() {
    }
}

