# CCDA TechBD Schema Files

This directory contains Consolidated Clinical Document Architecture (CCDA) schema files used for validating and processing CCDA documents within the Tech by Design integration platform.

## 📄 Overview

CCDA schemas define the structure and constraints for clinical documents exchanged between healthcare systems. These files are essential for:
- Validating incoming and outgoing CCDA XML documents
- Ensuring interoperability and compliance with healthcare standards
- Supporting automated testing and integration workflows

## 📁 Folder Structure

All schema files in this folder are referenced by integration channels and validation tools. Please do not modify these files unless you are updating to a new CCDA specification or patching a known issue.

## 📋 File List

**Total Files**: 16 schema/transformation files

| File Name                         | Description                                                      |
|-----------------------------------|------------------------------------------------------------------|
| CDA.xsd                           | Core CCDA schema defining the root ClinicalDocument structure   |
| CDA.xsl                           | XSL stylesheet for CCDA document rendering                       |
| NarrativeBlock.xsd                | Schema for human-readable narrative sections                     |
| POCD_MT000040.xsd                 | Main CCDA document schema with message type definitions          |
| cda-fhir-bundle.xslt              | Default XSLT template for CCDA to FHIR Bundle conversion         |
| cda-fhir-bundle-athenahealth.xslt | AthenaHealth-specific XSLT for FHIR Bundle conversion            |
| cda-fhir-bundle-epic.xslt         | Epic-specific XSLT for FHIR Bundle conversion                    |
| cda-fhir-bundle-medent.xslt       | Medent-specific XSLT for FHIR Bundle conversion                  |
| cda-phi-filter.xslt               | Default XSLT template for PHI filtering                          |
| cda-phi-filter-athenahealth.xslt  | AthenaHealth-specific XSLT for PHI filtering                     |
| cda-phi-filter-epic.xslt          | Epic-specific XSLT for PHI filtering                             |
| cda-phi-filter-medent.xslt        | Medent-specific XSLT for PHI filtering                           |
| datatypes-base.xsd                | Base datatypes schema for CCDA elements                          |
| datatypes.xsd                     | Extended datatypes schema with clinical data types               |
| sdtc.xsd                          | Structured Data Capture (SDTC) schema extension                  |
| voc.xsd                           | Vocabulary and code system schema definitions                    |


## �🔗 Related Documentation

- [CCDA Channel Files (Mirth Connect)](../ccda-techbd-channel-files/mirth-connect/README.md)
- [CCDA Channel Files (Nexus)](../ccda-techbd-channel-files/nexus/README.md)
- [Integration Artifacts Index](../../README.md)

---
