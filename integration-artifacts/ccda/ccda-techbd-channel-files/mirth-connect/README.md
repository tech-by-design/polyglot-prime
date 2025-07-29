# BridgeLink Channels: CcdaBundle & CcdaBundleValidate

This repository contains Mirth Connect channel for processing and validating CCDA documents. This channel will validate the uploaded CCDA files using predefined profiles and convert them into FHIR Bundles.

---

## 📦 Channel Overview

### 1. **/Bundle**
- **Purpose**: Accepts and processes CCDA XML documents, transforms them into FHIR Bundles.
- **Endpoint**: `POST /ccda/Bundle/`
- **Port**: `9443`
- **Expected Input**: `multipart/form-data` with a `file` field containing a CCDA XML document.
- **Headers Required**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `X-TechBD-CIN` (mandatory)
  - Either `X-TechBD-OrgNPI` or `X-TechBD-OrgTIN` (one is mandatory)
  - `X-TechBD-Facility-ID` (mandatory)
  - `X-TechBD-Encounter-Type` (mandatory)
  - `X-TechBD-Screening-Code` (optional, if not specified then for Epic files, '100698-0' will be considered as the grouper screening code.)
- **Validation**:
  - Ensures the file is .xml or .txt files and parses correctly.
  - Validates environment variables for XSLT and profile URLs.
  - Generates SHA-256 resource IDs for FHIR resources.
  - Selects XSLT templates based on the EHR vendor (`epic`, `medent`, or default).
- **Consent Logic**:
  - Extracts consent data and sets elaboration metadata to the header variable, `X-TechBD-Elaboration`.
- **Error Handling**: JSON with `OperationOutcome`

---

### 2. **/Bundle/Validate**
- **Purpose**: Validates CCDA files and returns errors or conformance results without creating FHIR Bundles.
- **Endpoint**: `POST /ccda/Bundle/$validate`
- **Port**: `9443`
- **Expected Input**: `multipart/form-data` with a `file` field.
- **Headers Required**:
  - `X-TechBD-Tenant-ID` (mandatory)
- **Validation Steps**:
  - Validates the CCDA structure using xsd schema files.
  - Verifies the XSLT templates and environment setup.
  - Ensures file is not empty and is a valid CCDA XML.
- **Content-Type**: Must be `multipart/form-data`.
- **Error Handling**: JSON with `OperationOutcome`

---

## 🛡 Security Notes

- **SSL**: Disabled by default (used inside a VPN).
- **Authentication**: None (`authType=NONE`)
- **CORS**: Configured to allow requests from `https://hub.dev.techbd.org`
- **Headers Allowed**:
  - `Content-Type`, `Authorization`, `X-TechBD-Tenant-ID`, `X-TechBD-OrgNPI`, `X-TechBD-OrgTIN`, `X-TechBD-CIN`, `User-Agent`, `X-TechBD-Facility-ID`, `X-TechBD-Encounter-Type`, etc.

---

## 🚀 Deployment Notes

- These channels require specific environment variables:
  - `MC_CCDA_SCHEMA_FOLDER` — path to schema & XSLT files.
  - `BASE_FHIR_URL`, `PROFILE_URL_*` — various FHIR profile URLs.
- Make sure schema files like `CDA.xsd`, `cda-phi-filter.xslt`, and vendor-specific XSLTs exist in the configured schema folder.
- Upon deployment, the channel starts automatically and logs processing steps for debugging and traceability.

---

## Requirements

- Mirth Connect 4.5.0+
- Properly configured Mirth Connect environment

---
