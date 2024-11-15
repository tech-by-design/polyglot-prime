package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;

/**
 * This class represents the data structure for the Demographic CSV file, containing patient demographic information 
 * including consent status, personal details, gender, identity, orientation, race, ethnicity, and relationship information.
 * <p>
 * It is used to bind data from a CSV file where each field corresponds to a column in the CSV file.
 * </p>
 */
@Getter
public class DemographicData {

    @CsvBindByName(column = "PATIENT_MR_ID")
    String patientMrId;

    @CsvBindByName(column = "FACILITY_ID")
    String facilityId;

    @CsvBindByName(column = "CONSENT_STATUS")
    String consentStatus;

    @CsvBindByName(column = "CONSENT_TIME")
    String consentTime;

    @CsvBindByName(column = "GIVEN_NAME")
    String givenName;

    @CsvBindByName(column = "MIDDLE_NAME")
    String middleName;

    @CsvBindByName(column = "FAMILY_NAME")
    String familyName;

    @CsvBindByName(column = "GENDER")
    String gender;

    @CsvBindByName(column = "SEX_AT_BIRTH_CODE")
    String sexAtBirthCode;

    @CsvBindByName(column = "SEX_AT_BIRTH_CODE_DESCRIPTION")
    String sexAtBirthCodeDescription;

    @CsvBindByName(column = "SEX_AT_BIRTH_CODE_SYSTEM")
    String sexAtBirthCodeSystem;

    @CsvBindByName(column = "PATIENT_BIRTH_DATE")
    String patientBirthDate;

    @CsvBindByName(column = "ADDRESS1")
    String address1;

    @CsvBindByName(column = "ADDRESS2")
    String address2;

    @CsvBindByName(column = "CITY")
    String city;

    @CsvBindByName(column = "DISTRICT")
    String district;

    @CsvBindByName(column = "STATE")
    String state;

    @CsvBindByName(column = "ZIP")
    String zip;

    @CsvBindByName(column = "PHONE")
    String phone;

    @CsvBindByName(column = "SSN")
    String ssn;

    @CsvBindByName(column = "PERSONAL_PRONOUNS_CODE")
    String personalPronounsCode;

    @CsvBindByName(column = "PERSONAL_PRONOUNS_CODE_DESCRIPTION")
    String personalPronounsCodeDescription;

    @CsvBindByName(column = "PERSONAL_PRONOUNS_CODE_SYSTEM_NAME")
    String personalPronounsCodeSystemName;

    @CsvBindByName(column = "GENDER_IDENTITY_CODE")
    String genderIdentityCode;

    @CsvBindByName(column = "GENDER_IDENTITY_CODE_DESCRIPTION")
    String genderIdentityCodeDescription;

    @CsvBindByName(column = "GENDER_IDENTITY_CODE_SYSTEM_NAME")
    String genderIdentityCodeSystemName;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE")
    String sexualOrientationCode;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_DESCRIPTION")
    String sexualOrientationCodeDescription;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_SYSTEM_NAME")
    String sexualOrientationCodeSystemName;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE")
    String preferredLanguageCode;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE_DESCRIPTION")
    String preferredLanguageCodeDescription;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE_SYSTEM_NAME")
    String preferredLanguageCodeSystemName;

    @CsvBindByName(column = "RACE_CODE")
    String raceCode;

    @CsvBindByName(column = "RACE_CODE_DESCRIPTION")
    String raceCodeDescription;

    @CsvBindByName(column = "RACE_CODE_SYSTEM_NAME")
    String raceCodeSystemName;

    @CsvBindByName(column = "ETHNICITY_CODE")
    String ethnicityCode;

    @CsvBindByName(column = "ETHNICITY_CODE_DESCRIPTION")
    String ethnicityCodeDescription;

    @CsvBindByName(column = "ETHNICITY_CODE_SYSTEM_NAME")
    String ethnicityCodeSystemName;

    @CsvBindByName(column = "MEDICAID_CIN")
    String medicaidCin;

    @CsvBindByName(column = "PATIENT_LAST_UPDATED")
    String patientLastUpdated;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_CODE")
    String relationshipPersonCode;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_DESCRIPTION")
    String relationshipPersonDescription;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_SYSTEM")
    String relationshipPersonSystem;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_GIVEN_NAME")
    String relationshipPersonGivenName;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_FAMILY_NAME")
    String relationshipPersonFamilyName;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_TELECOM_SYSTEM")
    String relationshipPersonTelecomSystem;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_TELECOM_VALUE")
    String relationshipPersonTelecomValue;

    /**
     * Default constructor for OpenCSV to create an instance of DemographicData.
     */
    public DemographicData() {
    }
}
