# HL7 CCD Schema Customization and Data Mapping for 1115 Waiver

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

### Files Overview

### 1. `POCD_MT000040.xsd`
An XML schema defining the structure and validation rules for CCDA (Consolidated Clinical Document Architecture) documents.

### 2. `datatypes.xsd`
An auxiliary XML schema providing definitions for data types used in CCDA documents, referenced by POCD_MT000040.xsd.

### 3. `CDA.xsl`
A stylesheet file typically used for transforming CCDA XML documents for display or processing.

### 4. `CDA.xsd`
The main schema for Clinical Document Architecture, including structural rules and data definitions for clinical documents.

### 5. `NarrativeBlock.xsd`
A schema defining narrative block elements used in CCDA to include human-readable sections in clinical documents.

### 6. `datatypes-base.xsd`
A base schema file with foundational data types referenced by datatypes.xsd and other XSD files.

### 7. `voc.xsd`
A vocabulary schema containing controlled terminologies and code system references for CCDA elements.

### 8. `SampleCDAQuestionnaireResponse.xml`
A sample CCDA document showcasing how questionnaire response data is structured and represented in XML format.