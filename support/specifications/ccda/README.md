# HL7 CCD Schema Preparation, Customization and Validation

This repository focuses on the preparation, customization, and validation of the HL7 CCDA (Consolidated Clinical Document Architecture) XSD schema, specifically tailored to the 1115 Waiver data elements. Below is an overview of the key objectives and files in this project.

## Preparation of HL7 CCD XSD Schema

### Requirements Gathering:
The first step in this project is to understand what 1115 Waiver data elements are essential via CCDs. This involves defining their constraints, data types, and business rules to ensure the schema accurately represents the data needed for the waiver.

### Schema Design:
- **Base Schema**: The HL7 CCD template is used as the foundation for customization.
- **Customizations**: Additions, deletions, and modifications are made to meet the 1115 Waiver-specific requirements, ensuring the schema fully accommodates the necessary data elements.

### Schema Definition:
- **New XSD Schema**: Create a new XSD schema or extend the existing HL7 CCD XSD.
- **XML Elements**: New XML elements are added as needed to accommodate the specific data fields required by the 1115 Waiver.
- **Extensions and Custom Attributes**: Extensions or custom attributes are defined to address waiver-specific use cases, ensuring compliance with HL7 CDA release 2, particularly for structured data.

### Schema Validation:
- **Validation Tools**: XML validation tools such as Oxygen XML or XMLSpy are used to verify the correctness of the schema.
- **Compliance**: The schema is validated against HL7 CCD specifications to ensure conformance and accuracy.

## Specification for CCD Data Exchange

### Document CCD Data Requirements:
- **Data Exchange**: Document the data exchange requirements for SCNs (or CCD sources) to QEs and from QEs to TechBD.
- **Field Mapping**: Map CCD data fields to FHIR elements for subsequent translation.
- **Field Information**: Clearly indicate required, optional, and repeating fields.

### Data Mapping Documentation:
- **Alignment with FHIR**: Create a data mapping document that aligns CCD data fields with the FHIR structure used by TechBD (matching the NYeC FHIR IG).
- **Transformations and Changes**: This mapping will clearly indicate transformations, data type changes, and conditions where the mapping might differ from the NYeC FHIR IG.

## Files Overview
XSD files define the structure and rules for XML documents, ensuring data adheres to a specified schema. They help validate and enforce consistency in XML data. The XSD files from Gravity are kept under the title 'CCDA Gravity Schema Files,' while an updated version of XSD files, which additionally includes the namespace 'sdtc,' is kept under 'CCDA TechBd Schema Files.' The TechBd schema files will be used for validating the CCDA XML files. 

### CCDA Gravity Schema Files

#### 1. `POCD_MT000040.xsd`
An XML schema defining the structure and validation rules for CCDA (Consolidated Clinical Document Architecture) documents.

#### 2. `datatypes.xsd`
An auxiliary XML schema providing definitions for data types used in CCDA documents, referenced by POCD_MT000040.xsd.

#### 3. `CDA.xsl`
A stylesheet file typically used for transforming CCDA XML documents for display or processing.

#### 4. `CDA.xsd`
The main schema for Clinical Document Architecture, including structural rules and data definitions for clinical documents.

#### 5. `NarrativeBlock.xsd`
A schema defining narrative block elements used in CCDA to include human-readable sections in clinical documents.

#### 6. `datatypes-base.xsd`
A base schema file with foundational data types referenced by datatypes.xsd and other XSD files.

#### 7. `voc.xsd`
A vocabulary schema containing controlled terminologies and code system references for CCDA elements.

### CCDA TechBd Schema Files
To validate a CCDA XML file with the XSD files, please ensure to add the following:
1. `<?xml version="1.0"?>` at the very beginning of the file.
2. Schema namespace attribute `xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"` and the Schema Location attribute `xsi:schemaLocation="urn:hl7-org:v3 ../ccda-techbd-schema-files/CDA.xsd"` to the `ClinicalDocument` element.

```xml
<?xml version="1.0"?>
<ClinicalDocument xmlns="urn:hl7-org:v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="urn:hl7-org:v3 ../ccda-techbd-schema-files/CDA.xsd">
  <realmCode code="US" />
  <typeId extension="POCD_HD000040" root="2.16.840.1.113883.1.3" />
</ClinicalDocument>
```
#### 1. `POCD_MT000040.xsd`
An XML schema defining the structure and validation rules for CCDA (Consolidated Clinical Document Architecture) documents.
The `POCD_MT000040.Patient` element has been updated to replace `<xs:sequence>` with `<xs:choice maxOccurs="unbounded">`, allowing child elements to appear in any order, and adds the `sdtc:deceasedInd` element. Additionally, the `POCD_MT000040.Person` element has been updated to allow the `sdtc:desc` as a child element, permitting an additional `desc` element of type `xs:string`.

#### 2. `datatypes.xsd`
An auxiliary XML schema providing definitions for data types used in CCDA documents, referenced by POCD_MT000040.xsd.

#### 3. `CDA.xsl`
A stylesheet file typically used for transforming CCDA XML documents for display or processing.

#### 4. `CDA.xsd`
The main schema for Clinical Document Architecture, including structural rules and data definitions for clinical documents.

#### 5. `NarrativeBlock.xsd`
A schema defining narrative block elements used in CCDA to include human-readable sections in clinical documents.

#### 6. `datatypes-base.xsd`
A base schema file with foundational data types referenced by datatypes.xsd and other XSD files.

#### 7. `voc.xsd`
A vocabulary schema containing controlled terminologies and code system references for CCDA elements.

#### 8. `sdtc.xsd`
This XSD schema defines two elements, deceasedInd and desc, within the namespace "urn:hl7-org:sdtc." The deceasedInd element is a complex type with a required boolean attribute named "value," while the desc element is a simple type restricted to the string values.

# CCDA Validation Using XML-Spy

## Overview
This section explains [XML-Spy](https://www.altova.com/xmlspy-xml-editor) and associated XSD schema files to validate CCDA (Consolidated Clinical Document Architecture) documents. The validation ensures structural and semantic accuracy, aligning with CCDA and HL7 standards.

## Tools for Validation

### 1. **XML-Spy**
**Key Features:**
- Advanced XML validation against XSD schemas.
- Intuitive error detection and resolution for CCDA documents.
- Visual and structured editing of XML files.

### 2. **XSD Schema Files**
The following schema files are required for validation and should be placed in the `ccda/ccda-gravity-schema-files/` folder:
- **`POCD_MT000040.xsd`**: Main CCDA structure schema.
- **`datatypes.xsd`**: Provides definitions for CCDA data types.
- **`CDA.xsd`**: Schema for Clinical Document Architecture.
- **`NarrativeBlock.xsd`**: Schema for human-readable narrative blocks.
- **`datatypes-base.xsd`**: Base schema for common data types.
- **`voc.xsd`**: Schema for controlled terminologies and code systems.

### 2. **Directory Structure**
```
support/
 └── specifications/
     └── ccda/
        └── ccda-gravity-schema-files/
            ├── POCD_MT000040.xsd  # Main schema for CCDA validation
            ├── datatypes.xsd  # Schema for CCDA data types
            ├── CDA.xsd  # Main schema for Clinical Document Architecture
            ├── NarrativeBlock.xsd  # Defines human-readable sections in CCDA
            ├── datatypes-base.xsd  # Base schema for foundational data types
            └── voc.xsd  # Vocabulary schema for terminologies and code systems
            ├── NYHRSN-CCDA-examples/  # Contains example CCDA files
                │   ├── AHCHRSNScreeningResponseCCDExample.xml
                │   └── Additional_CCDA_Examples.xml (in futur0)
        └── ccda-techbd-schema-files/
            ├── POCD_MT000040.xsd  # Main schema for CCDA validation
            ├── datatypes.xsd  # Schema for CCDA data types
            ├── CDA.xsd  # Main schema for Clinical Document Architecture
            ├── NarrativeBlock.xsd  # Defines human-readable sections in CCDA
            ├── datatypes-base.xsd  # Base schema for foundational data types
            └── voc.xsd  # Vocabulary schema for terminologies and code systems
            └── sdtc.xsd  # Defines schema for deceasedInd and desc elements
            ├── sample-ccda-xml-files/  # Contains examples of updated CCDA files
                ├── AHC_Sample_CDA.xml
                └── Additional_CCDA_Examples.xml (in futur0)
```

## How to Validate a CCDA File

### **Step 1: Prepare the Files**
Ensure the following:
1. **Example CCDA files** should be stored in the `ccda/NYHRSN-CCDA-examples` folder.

2. **All XSD schema files** should also be placed in the same `ccda/ccda-gravity-schema-files/` folder.

Alternatively, if the example CCDA files are stored in a different folder, ensure that you update the `schemaLocation` in the CCDA file to point to the correct location of the `CDA.xsd` file, like this:
```
schemaLocation="file:///path/to/ccda/folder/CDA.xsd"
```

### **Step 2: Load the CCDA File in XML-Spy**
1. Open XML-Spy.
2. Load the CCDA file (e.g., `AHCHRSNScreeningResponseCCDExample.xml`).
3. If the `schemaLocation` in the CCDA file is correctly set (e.g., pointing to `CDA.xsd`), XML-Spy will automatically link to the schema without needing additional manual linking. Just ensure that all files are in the same folder.

### **Step 3: Perform Validation**
1. Click the **Validate** button or press `F8`.
2. Review the errors and warnings in the **Validation Results** pane.
3. Use schema definitions to address and resolve the reported issues.

### **Step 4: Save the Corrected File**
1. After resolving all errors, save the validated CCDA file in the same folder.
2. Ensure the corrected file retains its naming convention for version tracking.

## Example Validation Workflow
1. Place the file `AHCHRSNScreeningResponseCCDExample.xml` in `ccda/`.
2. Open XML-Spy and load the file.
3. Link the XSD file `CDA.xsd` located in the `ccda/ccda-gravity-schema-files/` folder.
4. Perform validation and review the results in the **Validation Results** pane.
5. Address errors or warnings, and save the updated file in the same folder after validation.

## Notes
- Use the provided `voc.xsd` file for vocabulary and code system validation.
- Always ensure that schema files are up-to-date and compatible with the CCDA version being validated.


# Technical Implementation Steps for Protecting PHI in HL7 CCD Screening Data Ingestion

This document focuses on the preparation of the XSLT file which is used for protecting PHI data from CCD files to be ingested by the QEs.

## 1. XSLT Development:

- ### TechBD will analyze CCD file structures to identify screening data tags.

    - The CCD file structures were analyzed to identify the following patient and screening data tags:

       - a. Patient (recordTarget/patientRole)
       - b. Organization (author/assignedAuthor/representedOrganization)
       - c. Encounter (componentOf/encompassingEncounter)
       - d. Consent (authorization/consent)
       - e. Observation (component/structuredBody/component)

- ### Create an XSLT file with transformation rules to exclude non-relevant tags.

- ### Test the XSLT file with multiple CCD examples to ensure correct functionality.

- ### Publish the XSLT file on TechBD’s public GitHub repository.
  - The XSLT file, cda-phi-filter.xslt, is saved in the GitHub repository under the folder support/specifications/ccda.

## 2. Distribution to Senders:
- Share the XSLT file and detailed instructions for its use through TechBD’s GitHub repository.

- Offer support to help senders integrate the XSLT file into their workflows.

## 3. Incoming API Quality Check:
- Integrate a cleaning pipeline into the CCD HTTP API that applies the XSLT to all incoming CCD files.

- Validate that only screening data is included in the cleaned payloads.

- Maintain logs to track and audit the quality checks performed on each CCD file.

## 4. Monitoring and Compliance:
- Implement logging and notification mechanisms to ensure all CCD files adhere to the expected structure.

- Periodically review logs and metrics to confirm compliance with data submission guidelines.