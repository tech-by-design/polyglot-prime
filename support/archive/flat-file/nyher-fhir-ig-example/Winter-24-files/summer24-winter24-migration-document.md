# Summer'24 - Winter'24 CSV migration instructions

## Follow the below instructions for a successful Summer'24 to Winter'24 CSV schema migration:

1. **Additional CSV files:**
  - To maintain the ordinality and cardinality of the data in alignment with the IG v1.2, remove the **screening.csv** file of Summer'24 and add **screening_observation_data.csv** and **screening_profile_data.csv**. These CSV files are **comma-separated (comma-delimited)**. Make sure that now you have the following CSV files:
    - **demographic_data.csv**
    - **qe_admin_data.csv**
    - **screening_observation_data.csv**
    - **screening_profile_data.csv**
  
  - Updates pertaining to each of the files are given below.

2. **Column updates for demographic_data.csv:**
 - Remove the following columns from the Summer'24 CSV set:
    - **MPI_ID**
    - **CONSENT**
    - **ADMINISTRATIVE_SEX_CODE_DESCRIPTION**
    - **ADMINISTRATIVE_SEX_CODE_SYSTEM**

 - Add the following columns to the Winter'24 demographic_data.csv:
    | Column Name | Required/Optional | Cardinality |
    |-------------|-------------------|-------------|
    | MIDDLE_NAME | Optional | 0..1 |
    | EXTENSION_SEX_AT_BIRTH_CODE_VALUE | Optional | 0..1 |
    | DISTRICT | Optional | 0..1 |
    | TELECOM_VALUE | Optional | 0..1 |
    | EXTENSION_PERSONAL_PRONOUNS_CODE | Optional | 0..1 |
    | EXTENSION_PERSONAL_PRONOUNS_SYSTEM | Optional | 0..1 |
    | EXTENSION_PERSONAL_PRONOUNS_DISPLAY | Optional | 0..1
    | EXTENSION_GENDER_IDENTITY_DISPLAY | Optional | 0..1 |
    | EXTENSION_GENDER_IDENTITY_SYSTEM | Optional | 0..1 |
    | EXTENSION_GENDER_IDENTITY_CODE | Optional | 0..1 |
    | **PATIENT_LAST_UPDATED** | **Required** | 1..1 |
    | RELATIONSHIP_PERSON_CODE | Optional | 0..1 |
    | RELATIONSHIP_PERSON_DESCRIPTION | Optional | 0..1 |
    | RELATIONSHIP_PERSON_GIVEN_NAME | Optional | 0..1 |
    | RELATIONSHIP_PERSON_FAMILY_NAME | Optional | 0..1 |
    | RELATIONSHIP_PERSON_TELECOM_VALUE | Optional | 0..1 |
    | SEXUAL_ORIENTATION_VALUE_CODE | Optional | 0..1 |
    | SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION | Optional | 0..1 |
    | SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME | Optional | 0..1 |
    | SEXUAL_ORIENTATION_LAST_UPDATED | **Required** | 1..1|

 - Rename the following columns in demographic_data.csv from Summer'24 CSV to the Winter'24 CSV:
    | Summer'24 Column Name | Winter'24 Column Name | Required/Optional | Cardinality |
    |-------------|-------------------|-------------------|-------------|
    | **PAT_MRN_ID** | **PATIENT_MR_ID_VALUE** | **Required** | 1..1 |
    | MEDICAID_CIN | PATIENT_MA_ID_VALUE | Optional | 0..1 |
    | SSN | PATIENT_SS_ID_VALUE | Optional | 0..1 |
    | **FIRST_NAME** | **GIVEN_NAME** | **Required** | 1..1 |
    | **LAST_NAME** | **FAMILY_NAME** | **Required** | 1..1 |
    | **ADMINISTRATIVE_SEX_CODE** | **GENDER** | **Required** | 1..1 |
    | **PAT_BIRTH_DATE** | **PATIENT_BIRTH_DATE** | **Required** | 1..1 |
    | **PAT_PREFERRED_LANGUAGE_CODE** | **PREFERRED_LANGUAGE_CODE_SYSTEM_CODE** | **Required** | 1..1 |
    | RACE_CODE | EXTENSION_OMBCATEGORY_RACE_CODE | Optional | 0..1 |
    | RACE_CODE_DESCRIPTION | EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION | Optional | 0..1 |
    | RACE_CODE_SYSTEM_NAME | EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME | Optional | 0..1 |
    | ETHNICITY_CODE | EXTENSION_OMBCATEGORY_ETHNICITY_CODE | Optional | 0..1 |
    | ETHNICITY_CODE_DESCRIPTION | EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION | Optional | 0..1 |
    | ETHNICITY_CODE_SYSTEM_NAME | EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME | Optional | 0..1 |

3. **Column updates for qe_admin_data.csv:**
 - Remove the following columns from the Summer'24 CSV set:
    - **VISIT_PART_2_FLAG**
    - **VISIT_OMH_FLAG**
    - **VISIT_OPWDD_FLAG**

 - Add the following columns to the Winter'24 qe_admin_data.csv:
    | Column Name | Required/Optional | Cardinality |
    |-------------|-------------------|-------------|
    | **FACILITY_LAST_UPDATED** | **Required** | 1..1 |
    | ORGANIZATION_TYPE_CODE | Optional | 0..1 |
    | FACILITY_DISTRICT | Optional | 0..1 |
    | FACILITY_IDENTIFIER_TYPE_DISPLAY | Optional | 0..1 |
    | FACILITY_IDENTIFIER_TYPE_VALUE | Optional | 0..1 |
    | FACILITY_IDENTIFIER_TYPE_SYSTEM | Optional | 0..1 |

  - Rename the following columns in qe_admin_data.csv from Summer'24 CSV to the Winter'24 CSV:
    | Summer'24 Column Name | Winter'24 Column Name | Required/Optional | Cardinality |
    |-------------|-------------------|-------------------|-------------|
    | **PAT_MRN_ID** | **PATIENT_MR_ID_VALUE** | **Required** | 1..1 |
    | **FACILITY_LONG_NAME** | **FACILITY_NAME** | **Required** | 1..1 |
    | ORGANIZATION_TYPE | ORGANIZATION_TYPE_DISPLAY | Optional | 0..1 |

4. **screening.csv** file has been removed and the data from screening is covered under the files **screening_observation_data.csv** and **screening_profile_data.csv** 

5.  **Column updates for screening_observation_data.csv:**
 - Add the following columns to the Winter'24 screening_observation_data.csv:
    | Column Name | Required/Optional | Cardinality |
    |-------------|-------------------|-------------|
    | **PATIENT_MR_ID_VALUE** | **Required** | 1..1 |
    | **ENCOUNTER_ID** | **Required** | 1..1 |
    | **SCREENING_CODE** | **Required** | 1..1 |
    | **SCREENING_CODE_DESCRIPTION** | **Required** | 1..1 |
    | **RECORDED_TIME** | **Required** | 1..1 |
    | **QUESTION_CODE** | **Required** | 1..1 |
    | **QUESTION_CODE_DISPLAY** | **Required** | 1..1 |
    | **QUESTION_CODE_TEXT** | **Required** | 1..1 |
    | OBSERVATION_CATEGORY_SDOH_TEXT | Optional | 0..1 |
    | OBSERVATION_CATEGORY_SDOH_CODE | Optional | 0..1 |
    | OBSERVATION_CATEGORY_SDOH_DISPLAY | Optional | 0..1 |
    | OBSERVATION_CATEGORY_SNOMED_CODE | Optional | 0..1 |
    | OBSERVATION_CATEGORY_SNOMED_DISPLAY | Optional | 0..1 |
    | ANSWER_CODE | Optional | 0..1 |
    | ANSWER_CODE_DESCRIPTION | Optional | 0..1 |
    | DATA_ABSENT_REASON_CODE | Optional | 0..1 |
    | DATA_ABSENT_REASON_DISPLAY | Optional | 0..1 |
    | DATA_ABSENT_REASON_TEXT | Optional | 0..1 |

6.  **Column updates for screening_profile_data.csv:**
 - Add the following columns to the Winter'24 screening_profile_data.csv:
    | Column Name | Required/Optional | Cardinality |
    |-------------|-------------------|-------------|
    | **PATIENT_MR_ID_VALUE** | **Required** | 1..1 |
    | **ENCOUNTER_ID** | **Required** | 1..1 |
    | **ENCOUNTER_CLASS_CODE** | **Required** | 1..1 |
    | **ENCOUNTER_STATUS_CODE** | **Required** | 1..1 |
    | ENCOUNTER_TYPE_CODE | Optional | 0..1 |
    | ENCOUNTER_TYPE_CODE_DESCRIPTION | Optional | 0..1 |
    | ENCOUNTER_TYPE_CODE_SYSTEM | Optional | 0..1 |
    | **ENCOUNTER_LAST_UPDATED** | **Required** | 1..1 |
    | **CONSENT_LAST_UPDATED** | **Required** | 1..1 |
    | **CONSENT_DATE_TIME** | **Required** | 1..1 |
    | CONSENT_POLICY_AUTHORITY | Optional | 0..1 |
    | CONSENT_PROVISION_TYPE | Optional | 0..1 |
    | **SCREENING_STATUS_CODE** | **Required** | 1..1 |
    | **SCREENING_LAST_UPDATED** | **Required** | 1..1 |
    

## Notes

## Passing a Summer'24 zip package through Winter'24 API produces the following OperationOutcome behaviours:

### a. Checked the new JSON specification against the old CSV files (3 CSV files). 
- **Error:**
The data package has an error: descriptor is not valid (The data resource has an error: None is not of type 'string' at property 'path')
- **Analysis:**  
The issue seems to be related to the `path` property in the data resource descriptor. The value for `path` is being interpreted as `None`, which does not match the expected data type (`string`).

### b. Modified JSON Specification (Removed 4 Array Fields) and Revalidated
- **Error:**
- **Missing Label in Data Header:**
  ```json
  {
    "description": "Based on the schema there should be a label that is missing in the data's header.",
    "message": "There is a missing label in the header's field \"FACILITY_ACTIVE\" at position \"2\""
  }
  ```
- **Header Field Name Mismatch:**
  ```json
  {
    "description": "One of the data source header does not match the field name defined in the schema.",
    "message": "The cell \"None\" in row at position \"2\" and field \"FACILITY_NAME\" at position \"4\" does not conform to a constraint: constraint \"required\" is \"True\""
  }
  ```

### c. Observation
- The Summer'24 CSV files allowed flexible field locations. Winter'24 CSV files needs to have specific order of field locations.

---

## TechBD updates existing Summer'24 CSV transformer to include new IG v1.2 content. For example:
### Updates in FHIR JSON:
- **Update the following values:**
- `Bundle.fullUrl` for each resource type
- `Bundle.Patient.meta.profile`
- `Bundle.Consent.meta.profile`
- `Bundle.Observation.meta.profile`
- There will likely be at least **6-10 more**.

---

## Data from submitter in existing Summer'24 CSV column must be using IG v1.2 constraints (using NYHER value sets). For example:
1. **Observation Status:**
 - `Bundle.Observation.status` must use values from:  
   [SDOHCC-ValueSetObservationStatus](https://hl7.org/fhir/us/sdoh-clinicalcare/STU2.2/ValueSet-SDOHCC-ValueSetObservationStatus.html)  
 - Issue: Existing Summer'24 CSV uses `registered`, which is not in this value set.

2. **Missing Code for Calculations:**
 - `Bundle.entry.resource.where(resourceType = 'Observation' and not(hasMember.exists())).code.coding.code` missing for calculation-based observations.

3. **Additional Constraints:**  
 There will likely be at least **2-3 more**.

---

## Data from submitter in new CSV column that is not in Summer'24 using IG v1.2 constraints (using NYHER value sets). For Example:

1. **Consent Resource Fields Required in CSV:**
 - `CONSENT_POLICY_AUTHORITY`
 - `CONSENT_PROVISION_TYPE`
 - `CONSENT_LAST_UPDATED`

2. **Meta Fields:**
 - `meta.last_updated` required for every resource, necessitating **8-10 more columns**.

3. **Additional Columns:**  
 There will likely be **2-3 more** columns required to meet IG constraints.

---

## Data from submitter must match Summer'24 Business rules. For example:

### Example:
- Calculated scores must be generated in the FHIR JSON based on the business rules. Winter'24 expects it in the CSVs.

---

## 5. The reasons for the Winter'24 CSV schema updates include :

1. **Field Name Alignment:**
 - IG v1.2 terms and field names differed from CSV field names, so they were aligned.

2. **New CSV for Screening Profile:**
 - IG v1.2 requires consent fields along with other encounter fields, so a new CSV file was added.

3. **Meta Updates:**
 - IG v1.2 requires `meta.last_updated` for all resource types, so new columns were added in all CSV files.

---

## 6.  Need to enable SFTP server if needed (decision required by TechBD)  
- Enable SFTP server if necessary.

### Code Updates:
- **Languages:** Update code from **Deno**, **TypeScript**, and **SQL** to **Java**.


7. **Meta Updates:**
 - IG v1.2 requires `meta.last_updated` for all resource types, so new columns were added in all CSV files.  
