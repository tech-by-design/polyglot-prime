# HL7 CCDA to FHIR Conversion Process

This document provides a detailed step-by-step description of the **HL7 CCDA (Consolidated Clinical Document Architecture) to FHIR conversion process** implemented using **Mirth Connect**.  
It includes schema validation, PHI exclusion, and transformation logic based on the **SHINNY Implementation Guide (IG)** and **TechBD FHIR structure** aligned with **NYeC FHIR IG**.


## Overview

The conversion process ensures that every incoming **HL7 CCDA XML file**:
1. Passes through **schema validation** using multiple `.xsd` files.  
2. Excludes PHI (Protected Health Information) before validation.  
3. Converts the validated CCDA XML into a **FHIR-compliant Bundle**.  
4. Submits the generated **FHIR Bundle** to the **FHIR Bundle Submission API endpoint**.

## Folder Structure

```
integration-artifacts/
└── ccda/
    ├── ccda-techbd-schema-files/
    │   ├── POCD_MT000040.xsd
    │   ├── datatypes.xsd
    │   ├── CDA.xsl
    │   ├── CDA.xsd
    │   ├── NarrativeBlock.xsd
    │   ├── datatypes-base.xsd
    │   ├── voc.xsd
    │   ├── sdtc.xsd
    │   └── cda-phi-filter-athenahealth.xslt
    │   ├── cda-phi-filter-medent.xslt
    │   ├── cda-phi-filter-epic.xslt
    │   ├── cda-phi-filter.xslt
    │   └── cda-fhir-bundle-athenahealth.xslt
    │   ├── cda-fhir-bundle-medent.xslt
    │   ├── cda-fhir-bundle-epic.xslt
    │   ├── cda-fhir-bundle.xslt
    └── ccda-techbd-channel-files/
        └── mirth-connect/
            └── TechBD CCD Workflow.xml
```

## Step-by-Step Process

### **Step 1: Receive HL7 CCDA File**
- The process begins when a **CCDA XML file** is received through the **TechBD CCDA Workflow** channel in **Mirth Connect**.

#### **Required HTTP Headers**

When sending the CCDA file to the conversion endpoint, include the following **HTTP headers** in the request.  
These headers are required for correct routing, validation, and FHIR bundle generation.

| Header Name | Example Value | Description |
|--------------|----------------|--------------|
| **X-TechBD-Tenant-ID** | `QE-CR` | Identifies the tenant for which the CCDA message belongs. |
| **X-TechBD-CIN** | `AB12345C` | Customer identification number. |
| **X-TechBD-OrgNPI** | `NPI123456` | Organization’s NPI (National Provider Identifier). |
| **X-TechBD-OrgTIN** | `TIN1231423` | Organization’s Tax Identification Number. |
| **X-TechBD-Facility-ID** | `FacilityID-123` | The ID of the submitting facility. |
| **X-TechBD-Encounter-Type** | `405672008` | Encounter type code. |
| **X-TechBD-Validation-Severity-Level** | `error` | Determines how validation errors are handled. |
| **X-TechBD-Screening-Code** | `100698-0` | Grouper Screening code linked to the Observation resources. |

> **Note:** All these headers must be provided in every CCDA submission request. Missing or incorrect headers may cause the transformation to fail or produce incomplete FHIR bundles.

### **Step 2: Exclude PHI Data**
- Before validation, Mirth Connect applies an **XSLT transformation** to remove **PHI data** (Protected Health Information).  
- A cleaned XML file is generated for validation and conversion.
- This ensures data privacy and compliance during ingestion.

### **Step 3: Schema Validation**
- The PHI-excluded XML file is validated using **seven .xsd schema files** stored in:  
  ```
  integration-artifacts/ccda/ccda-techbd-schema-files/
  ```
- Validation ensures:
  - Mandatory sections and elements are present.
  - XML tag elements are in the correct order.
  - Data types match the expected schema definitions.

### **Step 4: CCDA to FHIR Conversion**
- After successful validation, the CCDA XML is converted to a **FHIR Bundle** using **source-specific XSLT files**:
  - `cda-fhir-bundle-epic.xslt`
  - `cda-fhir-bundle-medent.xslt`
  - `cda-fhir-bundle-athenahealth.xslt`
- These XSLT files implement mappings based on **SHINNY IG rules**, ensuring field alignment between CCDA and FHIR structures.
- All XSLT files are located in:  
  ```
  integration-artifacts/ccda/ccda-techbd-schema-files/
  ```

### **Step 5: Mirth Channel Processing**
- The entire process—PHI exclusion, schema validation, transformation—is performed within the **Mirth Connect channel**:
  ```
  TechBD CCD Workflow
  ```
- The channel configuration file is located in:
  ```
  integration-artifacts/ccda/ccda-techbd-channel-files/mirth-connect/TechBD CCD Workflow.xml
  ```

### **Step 6: FHIR Bundle Generation**
- The output **FHIR Bundle** includes the following **FHIR Resources** derived from CCDA XPath locations:

| Resource | CCDA XPath | Description |
|-----------|-------------|--------------|
| **Patient** | `recordTarget/patientRole` | Patient demographic and identification details. |
| **Organization** | `author/assignedAuthor/representedOrganization` | Submitting or responsible organization. |
| **Encounter** | `componentOf/encompassingEncounter` | Details of the healthcare encounter. |
| **Consent** | `authorization/consent` | Patient consent information. |
| **Observation** | `component/structuredBody/component` | Clinical observations and results. |
| **Sexual Orientation** | Derived from observation entries | Sexual orientation data extracted from structured components. |
| **Grouper Observation** | Derived from multiple components | Grouped observation resources for specific sections. |

> Note: The XPath mappings may vary slightly depending on the **source system** (Epic, Medent, AthenaHealth).

### **Step 7: FHIR Bundle Submission**
- The validated and transformed FHIR Bundle is sent to the **FHIR Bundle Submission API** endpoint for further processing and storage.

## XSD Schema File Descriptions

### 1. `POCD_MT000040.xsd`
Defines the structure and validation rules for CCDA documents.  
Updates include:
- Replaced `<xs:sequence>` with `<xs:choice maxOccurs="unbounded">` in `POCD_MT000040.Patient` to allow flexible ordering.
- Added `sdtc:deceasedInd` element.
- Added `sdtc:desc` element under `POCD_MT000040.Person`.

### 2. `datatypes.xsd`
Provides reusable data type definitions referenced by `POCD_MT000040.xsd`.

### 3. `CDA.xsl`
A stylesheet used for transforming or displaying CCDA XML data.

### 4. `CDA.xsd`
Defines structural rules and data definitions for the **Clinical Document Architecture**.

### 5. `NarrativeBlock.xsd`
Defines narrative block elements for human-readable clinical content.

### 6. `datatypes-base.xsd`
Provides base type definitions used across all other schema files.

### 7. `voc.xsd`
Defines controlled terminologies and code system references used within CCDA.

### 8. `sdtc.xsd`
Defines elements within the namespace `urn:hl7-org:sdtc`, including:
- `deceasedInd` (boolean type)
- `desc` (string type)