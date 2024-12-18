package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScreeningProfileData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "ENCOUNTER_ID")
    private String encounterId;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE")
    private String encounterClassCode;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE")
    private String encounterStatusCode;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE")
    private String encounterTypeCode;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE_DESCRIPTION")
    private String encounterTypeCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE_SYSTEM")
    private String encounterTypeCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_LAST_UPDATED")
    private String encounterLastUpdated;

    @CsvBindByName(column = "CONSENT_LAST_UPDATED")
    private String consentLastUpdated;

    @CsvBindByName(column = "CONSENT_DATE_TIME")
    private String consentDateTime;

    @CsvBindByName(column = "CONSENT_POLICY_AUTHORITY")
    private String consentPolicyAuthority;

    @CsvBindByName(column = "CONSENT_PROVISION_TYPE")
    private String consentProvisionType;

    @CsvBindByName(column = "SCREENING_LAST_UPDATED")
    private String screeningLastUpdated;

    @CsvBindByName(column = "SCREENING_STATUS_CODE")
    private String screeningStatusCode;

    // Default constructor
    public ScreeningProfileData() {
    }
}
