# CCDA TechBD Schema Files

This directory contains Consolidated Clinical Document Architecture (CCDA) schema files used for validating and processing CCDA documents within the Tech by Design integration platform.

## 📄 Overview

CCDA schemas define the structure and constraints for clinical documents exchanged between healthcare systems. These files are essential for:
- Validating incoming and outgoing CCDA XML documents
- Ensuring interoperability and compliance with healthcare standards
- Supporting automated testing and integration workflows

## 📁 Folder Structure

All schema files in this folder are referenced by integration channels and validation tools. Please do not modify these files unless you are updating to a new CCDA specification or patching a known issue.

## � File List

| File Name                      | Description (if known)                |
|------------------------------- |---------------------------------------|
| CDA.xsd                        | Core CCDA schema                      |
| CDA.xsl                        | XSL stylesheet for CCDA               |
| NarrativeBlock.xsd             | Narrative block schema                |
| POCD_MT000040.xsd              | Main CCDA document schema             |
| cda-fhir-bundle-epic.xslt      | XSLT for FHIR bundle (Epic)           |
| cda-fhir-bundle-medent.xslt    | XSLT for FHIR bundle (Medent)         |
| cda-fhir-bundle.xslt           | XSLT for FHIR bundle                  |
| cda-phi-filter-epic.xslt       | XSLT for PHI filtering (Epic)         |
| cda-phi-filter-medent.xslt     | XSLT for PHI filtering (Medent)       |
| cda-phi-filter.xslt            | XSLT for PHI filtering                |
| datatypes-base.xsd             | Datatypes base schema                 |
| datatypes.xsd                  | Datatypes schema                      |
| sdtc.xsd                       | SDTC schema extension                 |
| voc.xsd                        | Vocabulary schema                     |


## �🔗 Related Documentation

- [CCDA Channel Files (Mirth Connect)](../ccda-techbd-channel-files/mirth-connect/README.md)
- [CCDA Channel Files (Nexus)](../ccda-techbd-channel-files/nexus/README.md)
- [Integration Artifacts Index](../../README.md)

---
