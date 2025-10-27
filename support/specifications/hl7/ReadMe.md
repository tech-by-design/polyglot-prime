# HL7v2 to FHIR Conversion Process

This document describes the **step-by-step process** for converting **HL7v2 messages** into **FHIR-compliant bundles** using **Mirth Connect**.  
It includes schema validation, XML conversion, and transformation based on the **SHINNY Implementation Guide (IG)**.



## Overview

The conversion process ensures that every HL7v2 message:
1. Passes through **schema validation**.
2. Is transformed into **XML**, then validated.
3. Is converted into a **FHIR Bundle** using an **XSLT mapping**.

The resulting FHIR Bundle contains **Patient**, **Encounter**, **Consent**, **Organization**, and **Observation** resources.



## Folder Structure

```
integration-artifacts/
└── hl7v2/
    ├── hl7-techbd-schema-files/
    │   ├── hl7v2-validation-schema.xml         # Schema file for HL7v2 structure validation
    │   └── hl7v2-fhir-bundle.xslt              # XSLT for HL7v2 → FHIR transformation
    └── hl7-techbd-channel-files/
        └── mirth-connect/
            └── TechBD HL7 Workflow.xml          # Mirth channel configuration
```

## Step-by-Step Process

### **Step 1: Receive HL7v2 Message**
- The process begins when an **HL7v2 message** is received through the **TechBD HL7 Workflow** Mirth Connect channel.
- Mirth triggers the pipeline for transformation and validation.



### **Step 2: Schema Validation Setup**
- The file **`hl7v2-validation-schema.xml`** defines mandatory **segments** and **fields** for HL7v2 messages.
- The current version supports **HL7v2.3**.
- Path:
  ```
  integration-artifacts/hl7v2/hl7-techbd-schema-files/hl7v2-validation-schema.xml
  ```



### **Step 3: Convert HL7v2 to XML**
- Mirth Connect converts the raw HL7 message into **XML format**.
- This XML acts as an intermediate representation for validation and transformation.



### **Step 4: Validate XML**
- The generated XML is validated using the schema file `hl7v2-validation-schema.xml`.
- Validation ensures:
  - Presence of required segments like **MSH**, **PID**, and **PV1**.
  - Mandatory fields are not empty.
- If validation **fails**, the error is logged and the process stops.



### **Step 5: Transform to FHIR**
- Upon successful validation, the XML is converted to **FHIR JSON** using the XSLT:
  ```
  hl7v2-fhir-bundle.xslt
  ```
- This transformation follows the **SHINNY Implementation Guide (IG)** mappings.

- File path:
  ```
  integration-artifacts/hl7v2/hl7-techbd-schema-files/hl7v2-fhir-bundle.xslt
  ```



### **Step 6: Generate FHIR Bundle**
- The transformation produces a **FHIR Bundle** containing:
  - `Patient`
  - `Encounter`
  - `Consent`
  - `Organization`
  - `Observation`

- The final output is a **FHIR-compliant JSON bundle**, ready for downstream systems.



### **Step 7: Mirth Channel Configuration**
- All processes (conversion, validation, transformation) are handled by the **Mirth Connect Channel**:
  ```
  TechBD HL7 Workflow
  ```
- The latest configuration file is available at:
  ```
  integration-artifacts/hl7v2/hl7-techbd-channel-files/mirth-connect/TechBD HL7 Workflow.xml
  ```



## Notes

- The validation and transformation files are versioned to support **HL7v2.3**.
- Future versions may include mappings for **HL7v2.4**, **v2.5**, and beyond.
- Ensure Mirth Connect has read/write access to the `integration-artifacts` directory.
- Logging and error handling are managed within the Mirth Connect channel.

## Required HTTP Headers

When sending the HL7v2 file to the conversion endpoint, include the following **HTTP headers** in the request.  
These headers are required for correct routing, validation, and FHIR bundle generation.

| Header Name | Example Value | Description |
|--------------|----------------|--------------|
| **X-TechBD-Tenant-ID** | `QE-CR` | Identifies the tenant for which the HL7 message belongs. |
| **X-TechBD-CIN** | `AB12345C` | Customer identification number. |
| **X-TechBD-OrgNPI** | `NPI123456` | Organization’s NPI (National Provider Identifier). |
| **X-TechBD-OrgTIN** | `TIN1231423` | Organization’s Tax Identification Number. |
| **X-TechBD-Facility-ID** | `FacilityID-123` | The ID of the submitting facility. |
| **X-TechBD-Encounter-Type** | `405672008` | Encounter type code. |
| **X-TechBD-Validation-Severity-Level** | `error` | Determines how validation errors are handled. |
| **X-TechBD-Organization-Name** | `Abc Corporation` | Name of the Organization. |

> **Note:** All these headers must be provided in every HL7 submission request. Missing or incorrect headers may cause the transformation to fail or produce incomplete FHIR bundles.
