# CCDA Sample Files

This directory contains **sample CCDA (Consolidated Clinical Document Architecture) files** used for testing, validation, and CCDA → FHIR conversion workflows.

The samples come from different **EHR vendors and testing scenarios** to ensure compatibility with various CCDA formats.

---

# Folder Structure

```
sample-ccda-files
│
├── AthenaHealth
│ ├── Sample.CCDA.v3.with.screener.consent.txt
│ └── SamplesPREV.with.Test.Patient.txt
│
├── Epic
│ ├── AHC_Sample_CDA.xml
│ ├── epic_sample_ccd_noauth_021725.txt
│ ├── epic_sample_ccd_auth1_021725.txt
│ ├── epic_sample_ccd_auth2_021725.txt
│ ├── epic_sample_cda_1_021125.xml
│ ├── epic_sample_cda_2_021125.xml
│ └── Sample_CCD_EPIC_via_OBH_via_HEALTHIX.xml
│
├── Greenway
│ └── Test_05368091_Greenway.xml
│
└── Medent
├── JPedAs_11.xml
├── JPedAs_12.xml
├── JPedAs_13.xml
├── JPedAs_14.xml
├── JPedAs_15.xml
├── JPedAs_16.xml
├── JPedAs_17.xml
├── JPedAs_18.xml
├── JPedAs_19.xml
└── JPedAs_20.xml
```


# Notes

- These files represent **real-world CCDA structures from different EHR vendors**.
- Some files are provided in **`.xml` format**, while others are provided as **`.txt` containing CCDA XML payloads**.
- The variation helps ensure robust parsing and transformation logic.