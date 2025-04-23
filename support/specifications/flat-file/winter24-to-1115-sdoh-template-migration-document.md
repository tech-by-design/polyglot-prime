# Winter'24 - 1115-SDOH-Template CSV migration instructions

## Follow the below instructions for a successful Winter'24 to 1115-SDOH-Template CSV schema migration: 

  1. **Renamed CSV Files**
  - To maintain the ordinality and cardinality of the data in alignment with **IG 1.4.1**, the CSV file naming conventions have been updated. The following files used in **Winter'24** have been **renamed** in **1115-SDOH-Template** while maintaining their structure and format:

    - **demographic_data.csv** → **SDOH_PtInfo_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv**
    - **qe_admin_data.csv** → **SDOH_QEadmin_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv**
    - **screening_profile_data.csv** → **SDOH_ScreeningProf_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv**
    - **screening_observation_data.csv** → **SDOH_ScreeningObs_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv**

  - These files are **comma-separated (comma-delimited)**.

  - Updates pertaining to each of the files are given below.

  2. **Column Updates for SDOH_PtInfo.csv**
  - ##### **Removed Columns:**
    The following columns have been **removed** from the dataset:
    - **RELATIONSHIP_PERSON_CODE**
    - **RELATIONSHIP_PERSON_DESCRIPTION**
    - **RELATIONSHIP_PERSON_GIVEN_NAME**
    - **RELATIONSHIP_PERSON_FAMILY_NAME**
    - **RELATIONSHIP_PERSON_TELECOM_VALUE**
    - **SEXUAL_ORIENTATION_LAST_UPDATED**

  - ##### **Added Columns:**
    The following columns have been **added** to the dataset:
    - **FACILITY_NAME** (Required, 1..1)
    - **ADMINISTRATIVE_SEX_CODE_DESCRIPTION** (Required, 1..1)
    - **ADMINISTRATIVE_SEX_CODE_SYSTEM** (Required, 1..1)
    - **SEX_AT_BIRTH_CODE_DESCRIPTION** (Optional, 0..1)
    - **SEX_AT_BIRTH_CODE_SYSTEM** (Optional, 0..1)
    - **ADDRESS2** (Optional, 0..1)
    - **TELECOM_USE** (Optional, 0..1)
    - **PREFERRED_LANGUAGE_CODE_DESCRIPTION** (Optional, 0..1)

  - ##### **Renamed Columns:**
    The following columns have been **renamed**:

    | **Existing Column** | **New Column Name** | **Required** | **Cardinality** |
    |----------------------|---------------------|--------------|----------------|
    | **PATIENT_MA_ID_VALUE** | PATIENT_MEDICAID_ID | TRUE         | 1..1           |
    | **GENDER**              | ADMINISTRATIVE_SEX_CODE | TRUE         | 1..1           |
    | EXTENSION_SEX_AT_BIRTH_CODE_VALUE | SEX_AT_BIRTH_CODE | NO | 0..1 |
    | **DISTRICT** | COUNTY | TRUE | 1..1 |
    | EXTENSION_PERSONAL_PRONOUNS_CODE | PERSONAL_PRONOUNS_CODE | NO | 0..1 |
    | EXTENSION_PERSONAL_PRONOUNS_DISPLAY | PERSONAL_PRONOUNS_DESCRIPTION | NO | 0..1 |
    | EXTENSION_PERSONAL_PRONOUNS_SYSTEM | PERSONAL_PRONOUNS_SYSTEM | NO | 0..1 |
    | EXTENSION_GENDER_IDENTITY_CODE | GENDER_IDENTITY_CODE | NO | 0..1 |
    | EXTENSION_GENDER_IDENTITY_DISPLAY | GENDER_IDENTITY_CODE_DESCRIPTION | NO | 0..1 |
    | EXTENSION_GENDER_IDENTITY_SYSTEM | GENDER_IDENTITY_CODE_SYSTEM | NO | 0..1 |
    | PREFERRED_LANGUAGE_CODE_SYSTEM_NAME | PREFERRED_LANGUAGE_CODE_SYSTEM | NO | 0..1 |
    | PREFERRED_LANGUAGE_CODE_SYSTEM_CODE | PREFERRED_LANGUAGE_CODE | NO | 0..1 |
    | EXTENSION_OMBCATEGORY_RACE_CODE | RACE_CODE | NO | 0..1 |
    | EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION | RACE_CODE_DESCRIPTION | NO | 0..1 |
    | EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME | RACE_CODE_SYSTEM | NO | 0..1 |
    | EXTENSION_OMBCATEGORY_ETHNICITY_CODE | ETHNICITY_CODE | NO | 0..1 |
    | EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION | ETHNICITY_CODE_DESCRIPTION | NO | 0..1 |
    | EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME | ETHNICITY_CODE_SYSTEM | NO | 0..1 |
    | SEXUAL_ORIENTATION_VALUE_CODE | SEXUAL_ORIENTATION_CODE | NO | 0..1 |
    | SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION | SEXUAL_ORIENTATION_CODE_DESCRIPTION | NO | 0..1 |
    | SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME | SEXUAL_ORIENTATION_CODE_SYSTEM | NO | 0..1 |

  - ##### **No Change Columns:**
    The following columns remain **unchanged**:

    - **PATIENT_MR_ID_VALUE** (Required, 1..1)
    - **PATIENT_SS_ID_VALUE** (Optional, 0..1)
    - **GIVEN_NAME** (Required, 1..1)
    - **MIDDLE_NAME** (Optional, 0..1)
    - **FAMILY_NAME** (Required, 1..1)
    - **PATIENT_BIRTH_DATE** (Required, 1..1)
    - **ADDRESS1** (Required, 1..1)
    - **CITY** (Required, 1..1)
    - **STATE** (Required, 1..1)
    - **ZIP** (Required, 1..1)
    - **TELECOM_VALUE** (Optional, 0..1)
    - **PATIENT_LAST_UPDATED** (Optional, 0..1)


  3. **Column updates for SDOH_QEadmin.csv:**
  - ##### **Removed Columns:**
    The following columns have been **removed** from the dataset:
    - **FACILITY_IDENTIFIER_TYPE_DISPLAY**
    - **FACILITY_IDENTIFIER_TYPE_VALUE**
    - **FACILITY_IDENTIFIER_TYPE_SYSTEM**
    - **FACILITY_IDENTIFIER_TYPE_CODE**

  - ##### **Added Columns:**
    The following columns have been **added** to the dataset:
    - **ORGANIZATION_TYPE_CODE_SYSTEM** (Optional, 0..1)
    - **ENCOUNTER_LOCATION** (Required, 1..1)
    - **FACILITY_ADDRESS2** (Optional, 0..1)

  - ##### **Renamed Columns:**
    The following column has been **renamed**:

    | **Existing Column**   | **New Column Name** | **Required** | **Cardinality** |
    |----------------------|---------------------|--------------|----------------|
    | **FACILITY_DISTRICT** | **FACILITY_COUNTY** | NO | 0..1 |

    ##### **No Change Columns:**
    The following columns remain **unchanged**:

    | **Field Name**                 | **Required** | **Cardinality** |
    |---------------------------------|--------------|----------------|
    | **PATIENT_MR_ID_VALUE**             | TRUE         | 1..1           |
    | **FACILITY_ID**                     | TRUE         | 1..1           |
    | **FACILITY_NAME**                   | TRUE         | 1..1           |
    | ORGANIZATION_TYPE_DISPLAY       | NO          | 0..1           |
    | ORGANIZATION_TYPE_CODE          | NO          | 0..1           |
    | FACILITY_ADDRESS1               | NO          | 0..1           |
    | FACILITY_CITY                   | NO          | 0..1           |
    | FACILITY_STATE                  | NO          | 0..1           |
    | FACILITY_ZIP                    | NO          | 0..1           |
    | **FACILITY_LAST_UPDATED**           | TRUE         | 1..1           |


4.  **Column updates for SDOH_ScreeningProf.csv:** 
  - ##### **Removed Columns:**
    The following columns have been **removed** from the dataset:
    - **CONSENT_LAST_UPDATED**
    - **CONSENT_POLICY_AUTHORITY**
    - **CONSENT_PROVISION_TYPE**
    - **ENCOUNTER_LAST_UPDATED**

- ##### **Added Columns:**
  The following columns have been **added** to the dataset:
  - **FACILITY_ID** (Required, 1..1)
  - **FACILITY_NAME** (Required, 1..1)
  - **SCREENING_IDENTIFIER** (Required, 1..1)
  - **ENCOUNTER_ID_SYSTEM** (Required, 1..1)
  - **ENCOUNTER_CLASS_CODE_SYSTEM** (Required, 1..1)
  - **ENCOUNTER_CLASS_CODE_DESCRIPTION** (Required, 1..1)
  - **ENCOUNTER_STATUS_CODE_SYSTEM** (Required, 1..1)
  - **ENCOUNTER_STATUS_CODE_DESCRIPTION** (Required, 1..1)
  - **ENCOUNTER_START_DATETIME** (Required, 1..1)
  - **ENCOUNTER_END_DATETIME** (Optional, 0..1)
  - **ENCOUNTER_LOCATION** (Required, 1..1)
  - **PROCEDURE_STATUS_CODE** (Optional, 0..1)
  - **PROCEDURE_CODE_DESCRIPTION** (Optional, 0..1)
  - **PROCEDURE_CODE** (Optional, 0..1)
  - **PROCEDURE_CODE_SYSTEM** (Optional, 0..1)
  - **PROCEDURE_CODE_MODIFIER** (Optional, 0..1)
  - **SCREENING_STATUS_CODE_DESCRIPTION** (Optional, 0..1)
  - **SCREENING_STATUS_CODE_SYSTEM** (Optional, 0..1)
  - **SCREENING_LANGUAGE_DESCRIPTION** (Optional, 0..1)
  - **SCREENING_LANGUAGE_CODE** (Optional, 0..1)
  - **SCREENING_LANGUAGE_CODE_SYSTEM** (Optional, 0..1)
  - **SCREENING_ENTITY_ID** (Required, 1..1)
  - **SCREENING_ENTITY_ID_CODE_SYSTEM** (Required, 1..1)

- ##### **No Change Columns:**
  The following columns remain **unchanged**:

  | **Field Name**                    | **Required** | **Cardinality** |
  |------------------------------------|--------------|----------------|
  | **PATIENT_MR_ID_VALUE**               | TRUE         | 1..1           |
  | **ENCOUNTER_ID**                       | TRUE         | 1..1           |
  | **ENCOUNTER_CLASS_CODE**              | TRUE         | 1..1           |
  | **ENCOUNTER_STATUS_CODE**              | TRUE         | 1..1           |
  | **ENCOUNTER_TYPE_CODE**               | TRUE          | 1..1           |
  | ENCOUNTER_TYPE_CODE_DESCRIPTION    | NO          | 0..1           |
  | ENCOUNTER_TYPE_CODE_SYSTEM         | NO          | 0..1           |
  | **CONSENT_STATUS**                     | TRUE         | 1..1           |
  | **CONSENT_DATE_TIME**                  | TRUE         | 1..1           |
  | SCREENING_LAST_UPDATED             | NO          | 0..1           |
  | SCREENING_STATUS_CODE              | NO          | 1..1           |


5.  **Column updates for SDOH_ScreeningObs.csv:** 

  - ##### **Removed Columns:**
    The following columns have been **removed** from the dataset:
    - **RECORDED_TIME**
    - **QUESTION_CODE_TEXT**
    - **OBSERVATION_CATEGORY_SDOH_DISPLAY**
    - **OBSERVATION_CATEGORY_SNOMED_CODE**
    - **OBSERVATION_CATEGORY_SNOMED_DISPLAY**
    - **DATA_ABSENT_REASON_TEXT**

  - ##### **Added Columns:**
    The following columns have been **added** to the dataset:
    - **FACILITY_ID** (Required, 1..1)
    - **FACILITY_NAME** (Required, 1..1)
    - **SCREENING_IDENTIFIER** (Required, 1..1)
    - **ENCOUNTER_ID_SYSTEM** (Required, 1..1)
    - **SCREENING_CODE_SYSTEM** (Required, 1..1)
    - **QUESTION_CODE_SYSTEM** (Required, 1..1)
    - **ANSWER_CODE_SYSTEM** (Optional, 0..1)
    - **POTENTIAL_NEED_INDICATED** (Required, 1..1)
    - **SCREENING_START_DATETIME** (Required, 1..1)
    - **SCREENING_END_DATETIME** (Optional, 0..1)

- ##### **Renamed Columns:**
  - **QUESTION_CODE_DISPLAY** → **QUESTION_CODE_DESCRIPTION** (Renamed)

- ##### **No Change Columns:**
  The following columns remain **unchanged**:

  | **Field Name**                         | **Required** | **Cardinality** |
  |-----------------------------------------|--------------|----------------|
  | **PATIENT_MR_ID_VALUE**                     | TRUE         | 1..1           |
  | **ENCOUNTER_ID**                             | TRUE         | 1..1           |
  | **SCREENING_CODE**                           | TRUE         | 1..1           |
  | **SCREENING_CODE_DESCRIPTION**               | TRUE         | 1..1           |
  | **QUESTION_CODE**                            | TRUE         | 1..1           |
  | **ANSWER_CODE**                              | NO         | 0..1           |
  | **ANSWER_CODE_DESCRIPTION**                  | NO         | 0..1           |
  | **OBSERVATION_CATEGORY_SDOH_TEXT**           | TRUE         | 0..1           |
  | **OBSERVATION_CATEGORY_SDOH_CODE**           | TRUE         | 1..1           |
  | DATA_ABSENT_REASON_CODE                  | NO          | 0..1           |
  | DATA_ABSENT_REASON_DISPLAY               | NO          | 0..1           |


## Notes

## Passing a Winter'24 zip package through 1115-SDOH-Template API produces the following OperationOutcome behaviours:

###  Checked the new JSON specification against the old CSV files (4 CSV files). 

### a. File Naming Validation Issue  
- **Error:**  
  ```json
  {
    "type": "files-not-processed",
    "description": "Filenames must start with one of the following prefixes: SDOH_PtInfo, SDOH_QEadmin, SDOH_ScreeningProf, SDOH_ScreeningObs",
    "message": "Files not processed: in input zip file : QE_ADMIN_DATA_ACCESS.csv, DEMOGRAPHIC_DATA_ACCESS.csv, SCREENING_OBSERVATION_DATA_ACCESS.csv, SCREENING_PROFILE_DATA_ACCESS.csv"
  }
  ```
- **Analysis:**  
  - The input zip file contains files that do not match the expected naming conventions.  
  - The API expects filenames to start with one of the following prefixes:
    - **SDOH_PtInfo**  
    - **SDOH_QEadmin**  
    - **SDOH_ScreeningProf**  
    - **SDOH_ScreeningObs**  
  - The presence of incorrectly named files prevents them from being processed.

### b. Resolution Steps  
- Ensure all CSV filenames follow the required naming convention before packaging them into the zip file.  
- Rename files as follows:  
  - `QE_ADMIN_DATA_ACCESS.csv` ? `SDOH_QEadmin_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv`  
  - `DEMOGRAPHIC_DATA_ACCESS.csv` ? `SDOH_PtInfo_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv`  
  - `SCREENING_OBSERVATION_DATA_ACCESS.csv` ? `SDOH_ScreeningObs_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv.csv`  
  - `SCREENING_PROFILE_DATA_ACCESS.csv` ? `SDOH_ScreeningProf_{OrganizationName}_{groupIdentifier}_YYYYMMDDhhmmss.csv.csv`  
- Revalidate the zip package through the 1115-SDOH-Template API.

### c. Observation  
- Unlike previous versions, Winter'24 enforces strict file naming conventions before processing.  
- Any deviation in file names results in the files being ignored, impacting data validation and ingestion.

---

## TechBD updates existing Winter'24 CSV transformer to include new IG v1.4.1 content.  

### Updates in FHIR JSON:  
Update the following values:  
- `Bundle.Observation.id`  
- `Bundle.Encounter.class.system`  
- `Bundle.Encounter.period.start` 
- `Bundle.Encounter.period.end`  
- `Bundle.Encounter.location.location.reference`  
- `Bundle.Procedure.status`  
- `Bundle.Procedure.code.coding.display`   

There will likely be at least **6-10 more** updates.  

---

## Data from submitter in existing Winter'24 CSV column must be using IG v1.4.1 constraints (using NYHER value sets). For example:
1. **Observation Status:**
 - `Bundle.Observation.status` must use values from:  
   [SDOHCC-ValueSetObservationStatus](https://hl7.org/fhir/us/sdoh-clinicalcare/STU2.2/ValueSet-SDOHCC-ValueSetObservationStatus.html)  
 - Issue: Existing Winter'24 CSV uses `registered`, which is not in this value set.

2. **Missing Code for Calculations:**
 - `Bundle.entry.resource.where(resourceType = 'Observation' and not(hasMember.exists())).code.coding.code` missing for calculation-based observations.

3. **Additional Constraints:**  
 There will likely be at least **2-3 more**.

---

## Data from submitter in new CSV column that is not in Winter'24 using IG v1.4.1 constraints (using NYHER value sets). For Example:

1. **Consent Resource Fields Required in CSV:**
 - `CONSENT_POLICY_AUTHORITY`
 - `CONSENT_PROVISION_TYPE`
 - `CONSENT_LAST_UPDATED`


---

## Data from submitter must match Winter'24 Business rules. For example:

### Example:
- Calculated scores must be generated in the FHIR JSON based on the business rules. 1115-SDOH-Template expects it in the CSVs.

---

## 5. The reasons for the 1115-SDOH-Template CSV schema updates include :

1. **Field Name Alignment:**
 - IG v1.4.1 terms and field names differed from CSV field names, so they were aligned.

2. **New CSV for Columns for all CSV Files:**
 - IG v1.4.1 requires description and system along with code, so a new CSV file was added.
 
 