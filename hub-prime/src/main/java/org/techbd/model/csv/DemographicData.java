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

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "PATIENT_MR_ID_SYSTEM")
    private String patientMrIdSystem;

    @CsvBindByName(column = "PATIENT_MR_ID_TYPE_CODE")
    private String patientMrIdTypeCode;

    @CsvBindByName(column = "PATIENT_MR_ID_TYPE_SYSTEM")
    private String patientMrIdTypeSystem;

    @CsvBindByName(column = "PATIENT_MA_ID_VALUE")
    private String patientMaIdValue;

    @CsvBindByName(column = "PATIENT_MA_ID_SYSTEM")
    private String patientMaIdSystem;

    @CsvBindByName(column = "PATIENT_MA_ID_TYPE_CODE")
    private String patientMaIdTypeCode;

    @CsvBindByName(column = "PATIENT_MA_ID_TYPE_SYSTEM")
    private String patientMaIdTypeSystem;

    @CsvBindByName(column = "PATIENT_SS_ID_VALUE")
    private String patientSsIdValue;

    @CsvBindByName(column = "PATIENT_SS_ID_SYSTEM")
    private String patientSsIdSystem;

    @CsvBindByName(column = "PATIENT_SS_ID_TYPE_CODE")
    private String patientSsIdTypeCode;

    @CsvBindByName(column = "PATIENT_SS_ID_TYPE_SYSTEM")
    private String patientSsIdTypeSystem;

    @CsvBindByName(column = "GIVEN_NAME")
    private String givenName;

    @CsvBindByName(column = "MIDDLE_NAME")
    private String middleName;

    @CsvBindByName(column = "MIDDLE_NAME_EXTENSION_URL")
    private String middleNameExtensionUrl;

    @CsvBindByName(column = "FAMILY_NAME")
    private String familyName;

    @CsvBindByName(column = "PREFIX_NAME")
    private String prefixName;

    @CsvBindByName(column = "SUFFIX_NAME")
    private String suffixName;

    @CsvBindByName(column = "GENDER")
    private String gender;

    @CsvBindByName(column = "EXTENSION_SEX_AT_BIRTH_CODE_VALUE")
    private String extensionSexAtBirthCodeValue;

    @CsvBindByName(column = "EXTENSION_SEX_AT_BIRTH_CODE_URL")
    private String extensionSexAtBirthCodeUrl;

    @CsvBindByName(column = "PATIENT_BIRTH_DATE")
    private String patientBirthDate;

    @CsvBindByName(column = "ADDRESS1")
    private String address1;

    @CsvBindByName(column = "ADDRESS2")
    private String address2;

    @CsvBindByName(column = "CITY")
    private String city;

    @CsvBindByName(column = "DISTRICT")
    private String district;

    @CsvBindByName(column = "STATE")
    private String state;

    @CsvBindByName(column = "ZIP")
    private String zip;

    @CsvBindByName(column = "TELECOM_VALUE")
    private String telecomValue;

    @CsvBindByName(column = "TELECOM_SYSTEM")
    private String telecomSystem;

    @CsvBindByName(column = "TELECOM_USE")
    private String telecomUse;

    @CsvBindByName(column = "SSN")
    private String ssn;

    @CsvBindByName(column = "EXTENSION_PERSONAL_PRONOUNS_URL")
    private String extensionPersonalPronounsUrl;

    @CsvBindByName(column = "EXTENSION_PERSONAL_PRONOUNS_CODE")
    private String extensionPersonalPronounsCode;

    @CsvBindByName(column = "EXTENSION_PERSONAL_PRONOUNS_DISPLAY")
    private String extensionPersonalPronounsDisplay;

    @CsvBindByName(column = "EXTENSION_PERSONAL_PRONOUNS_SYSTEM")
    private String extensionPersonalPronounsSystem;

    @CsvBindByName(column = "EXTENSION_GENDER_IDENTITY_URL")
    private String extensionGenderIdentityUrl;

    @CsvBindByName(column = "EXTENSION_GENDER_IDENTITY_CODE")
    private String extensionGenderIdentityCode;

    @CsvBindByName(column = "EXTENSION_GENDER_IDENTITY_DISPLAY")
    private String extensionGenderIdentityDisplay;

    @CsvBindByName(column = "EXTENSION_GENDER_IDENTITY_SYSTEM")
    private String extensionGenderIdentitySystem;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE_SYSTEM_NAME")
    private String preferredLanguageCodeSystemName;

    @CsvBindByName(column = "PREFERRED_LANGUAGE_CODE_SYSTEM_CODE")
    private String preferredLanguageCodeSystemCode;

    @CsvBindByName(column = "EXTENSION_RACE_URL")
    private String extensionRaceUrl;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_RACE_URL")
    private String extensionOmbCategoryRaceUrl;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_RACE_CODE")
    private String extensionOmbCategoryRaceCode;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION")
    private String extensionOmbCategoryRaceCodeDescription;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME")
    private String extensionOmbCategoryRaceCodeSystemName;

    @CsvBindByName(column = "EXTENSION_TEXT_RACE_URL")
    private String extensionTextRaceUrl;

    @CsvBindByName(column = "EXTENSION_TEXT_RACE_CODE_VALUE")
    private String extensionTextRaceCodeValue;

    @CsvBindByName(column = "EXTENSION_ETHNICITY_URL")
    private String extensionEthnicityUrl;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_ETHNICITY_URL")
    private String extensionOmbCategoryEthnicityUrl;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_ETHNICITY_CODE")
    private String extensionOmbCategoryEthnicityCode;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION")
    private String extensionOmbCategoryEthnicityCodeDescription;

    @CsvBindByName(column = "EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME")
    private String extensionOmbCategoryEthnicityCodeSystemName;

    @CsvBindByName(column = "EXTENSION_TEXT_ETHNICITY_URL")
    private String extensionTextEthnicityUrl;

    @CsvBindByName(column = "EXTENSION_TEXT_ETHNICITY_CODE_VALUE")
    private String extensionTextEthnicityCodeValue;

    @CsvBindByName(column = "MEDICAID_CIN")
    private String medicaidCin;

    @CsvBindByName(column = "PATIENT_LAST_UPDATED")
    private String patientLastUpdated;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_CODE")
    private String relationshipPersonCode;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_DESCRIPTION")
    private String relationshipPersonDescription;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_SYSTEM")
    private String relationshipPersonSystem;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_GIVEN_NAME")
    private String relationshipPersonGivenName;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_FAMILY_NAME")
    private String relationshipPersonFamilyName;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_TELECOM_SYSTEM")
    private String relationshipPersonTelecomSystem;

    @CsvBindByName(column = "RELATIONSHIP_PERSON_TELECOM_VALUE")
    private String relationshipPersonTelecomValue;

    @CsvBindByName(column = "PATIENT_TEXT_STATUS")
    private String patientTextStatus;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_VALUE_CODE")
    private String sexualOrientationValueCode;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION")
    private String sexualOrientationValueCodeDescription;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME")
    private String sexualOrientationValueCodeSystemName;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_LAST_UPDATED")
    private String sexualOrientationLastUpdated;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_PROFILE")
    private String sexualOrientationProfile;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_STATUS")
    private String sexualOrientationStatus;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_TEXT_STATUS")
    private String sexualOrientationTextStatus;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_CODE")
    private String sexualOrientationCodeCode;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_DISPLAY")
    private String sexualOrientationCodeDisplay;

    @CsvBindByName(column = "SEXUAL_ORIENTATION_CODE_SYSTEM_NAME")
    private String sexualOrientationCodeSystemName;

    /**
     * Default constructor for OpenCSV to create an instance of DemographicData.
     */
    public DemographicData() {
    }
}
