package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;
import lombok.Setter;

/**
 * This class represents the data structure for the Demographic CSV file, containing patient demographic information 
 * including consent status, personal details, gender, identity, orientation, race, ethnicity, and relationship information.
 * <p>
 * It is used to bind data from a CSV file where each field corresponds to a column in the CSV file.
 * </p>
 * */

@Getter
@Setter
public class DemographicData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "FACILITY_NAME")
    private String facilityName;

    @CsvBindByName(column = "PATIENT_MEDICAID_ID")
    private String patientMedicaidId;

    @CsvBindByName(column = "PATIENT_SS_ID_VALUE")
    private String patientSsIdValue;

    @CsvBindByName(column = "FAMILY_NAME")
    private String familyName;

    @CsvBindByName(column = "GIVEN_NAME")
    private String givenName;

    @CsvBindByName(column = "MIDDLE_NAME")
    private String middleName;

    @CsvBindByName(column = "ADMINISTRATIVE_SEX_CODE")
    private String administrativeSexCode;

    @CsvBindByName(column = "ADMINISTRATIVE_SEX_CODE_DESCRIPTION")
    private String administrativeSexCodeDescription;

    @CsvBindByName(column = "ADMINISTRATIVE_SEX_CODE_SYSTEM")
    private String administrativeSexCodeSystem;

    @CsvBindByName(column = "SEX_AT_BIRTH_CODE")
    private String sexAtBirthCode;

    @CsvBindByName(column = "SEX_AT_BIRTH_CODE_DESCRIPTION")
    private String sexAtBirthCodeDescription;

    @CsvBindByName(column = "SEX_AT_BIRTH_CODE_SYSTEM")
    private String sexAtBirthCodeSystem;

    @CsvBindByName(column = "PATIENT_BIRTH_DATE")
    private String patientBirthDate;

    @CsvBindByName(column = "ADDRESS1")
    private String address1;

    @CsvBindByName(column = "ADDRESS2")
    private String address2;

    @CsvBindByName(column = "CITY")
    private String city;

    @CsvBindByName(column = "STATE")
    private String state;

    @CsvBindByName(column = "ZIP")
    private String zip;

    @CsvBindByName(column = "COUNTY")
    private String county;

    @CsvBindByName(column = "TELECOM_VALUE")
    private String telecomValue;

    @CsvBindByName(column = "TELECOM_USE")
    private String telecomUse;

    @CsvBindByName(column = "RACE_CODE")
    private String raceCode;

    @CsvBindByName(column = "RACE_CODE_DESCRIPTION")
    private String raceCodeDescription;

    @CsvBindByName(column = "RACE_CODE_SYSTEM")
    private String raceCodeSystem;

    @CsvBindByName(column = "ETHNICITY_CODE")
    private String ethnicityCode;

    @CsvBindByName(column = "ETHNICITY_CODE_DESCRIPTION")
    private String ethnicityCodeDescription;

    @CsvBindByName(column = "ETHNICITY_CODE_SYSTEM")
    private String ethnicityCodeSystem;

    @CsvBindByName(column = "PERSONAL_PRONOUNS_CODE")
    private String personalPronounsCode;

    @CsvBindByName(column = "PERSONAL_PRONOUNS_DESCRIPTION")
    private String personalPronounsDescription;

    @CsvBindByName(column = "PERSONAL_PRONOUNS_SYSTEM")
    private String personalPronounsSystem;

    @CsvBindByName(column = "GENDER_IDENTITY_CODE")
    private String genderIdentityCode;

    @CsvBindByName(column = "GENDER_IDENTITY_CODE_DESCRIPTION")
    private String genderIdentityCodeDescription;

    @CsvBindByName(column = "GENDER_IDENTITY_CODE_SYSTEM")
    private String genderIdentityCodeSystem;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE")
    private String preferredLanguageCode;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE_DESCRIPTION")
    private String preferredLanguageCodeDescription;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE_SYSTEM")
    private String preferredLanguageCodeSystem;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE")
    private String sexualOrientationCode;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_DESCRIPTION")
    private String sexualOrientationCodeDescription;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_SYSTEM")
    private String sexualOrientationCodeSystem;

    @CsvBindByName(column = "PATIENT_LAST_UPDATED")
    private String patientLastUpdated;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_LAST_UPDATED")
    private String sexualOrientationLastUpdated;

    public DemographicData() {
    }
}