## 0. Passing a Summer'24 zip package through Winter'24 API produces the following OperationOutcome behaviours:

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

## 1. TechBD updates existing Summer'24 CSV transformer to include new IG v1.x content. For example:
### Updates in FHIR JSON:
- **Update the following values:**
- `Bundle.fullUrl` for each resource type
- `Bundle.Patient.meta.profile`
- `Bundle.Consent.meta.profile`
- `Bundle.Observation.meta.profile`
- There will likely be at least **6-10 more**.

---

## 2. Data from submitter in existing Summer'24 CSV column must be using IG v1.x constraints (using NYHER value sets). For example:
1. **Observation Status:**
 - `Bundle.Observation.status` must use values from:  
   [SDOHCC-ValueSetObservationStatus](https://hl7.org/fhir/us/sdoh-clinicalcare/STU2.2/ValueSet-SDOHCC-ValueSetObservationStatus.html)  
 - Issue: Existing Summer'24 CSV uses `registered`, which is not in this value set.

2. **Missing Code for Calculations:**
 - `Bundle.entry.resource.where(resourceType = 'Observation' and not(hasMember.exists())).code.coding.code` missing for calculation-based observations.

3. **Additional Constraints:**  
 There will likely be at least **2-3 more**.

---

## 3. Data from submitter in new CSV column that is not in Summer'24 using IG v1.x constraints (using NYHER value sets). For Example:

1. **Consent Resource Fields Required in CSV:**
 - `CONSENT_POLICY_AUTHORITY`
 - `CONSENT_PROVISION_TYPE`
 - `CONSENT_STATUS`
 - `CONSENT_LAST_UPDATED`
 - `CONSENT_SOURCE_REFERENCE`

2. **Meta Fields:**
 - `meta.last_updated` required for every resource, necessitating **8-10 more columns**.

3. **Additional Columns:**  
 There will likely be **2-3 more** columns required to meet IG constraints.

---

## 4. Data from submitter must match Summer'24 Business rules. For example:

### Example:
- Calculated scores must be generated in the FHIR JSON based on the business rules. Winter'24 expects it in the CSVs.

---

## 5. The reasons for the Winter'24 CSV schema updates include :

1. **Field Name Alignment:**
 - IG v1.1 terms and field names differed from CSV field names, so they were aligned.

2. **New CSV for Consent:**
 - IG v1.1 requires consent fields, so a new CSV file was added.

3. **Meta Updates:**
 - IG v1.1 requires `meta.last_updated` for all resource types, so new columns were added in all CSV files.

---

## 6.  Need to enable SFTP server if needed (decision required by TechBD)  
- Enable SFTP server if necessary.

### Code Updates:
- **Languages:** Update code from **Deno**, **TypeScript**, and **SQL** to **Java**.
