# BridgeLink Channels: CcdaBundle & CcdaBundleValidate

This repository contains BridgeLink channels for processing and validating CCDA documents in the Nexus Sandbox environment. These channels validate uploaded CCDA files using predefined profiles and convert them into FHIR Bundles.

---

## 📦 Channels Overview

### 1. **CcdaBundle**
- **Purpose**: Accepts and processes CCDA XML documents, transforms them into FHIR Bundles.
- **Endpoint**: `POST /ccda/Bundle`
- **Port**: `9002`
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

### 2. **CcdaBundleValidate**
- **Purpose**: Validates CCDA files and returns errors or conformance results without creating FHIR Bundles.
- **Endpoint**: `POST /ccda/Bundle/$validate`
- **Port**: `9003`
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

---

## 🔧 Common Features

| Feature | CcdaBundle | CcdaBundleValidate |
|---------|------------|--------------------|
| **Port** | 9002 | 9003 |
| **HTTP Method** | POST | POST |
| **Context Path** | `/ccda/Bundle` | `/ccda/Bundle/$validate` |
| **Input Format** | `multipart/form-data` | `multipart/form-data` |
| **File Types** | .xml, .txt | .xml, .txt |
| **Channel Type** | Submission | Validation |
| **Destination** | Internal VM channel writer | Internal VM channel writer |

---

## 🛡 Security Notes

- **SSL**: Disabled by default.
- **Authentication**: None (`authType=NONE`)
- **CORS Headers**:
  - `Access-Control-Allow-Origin`: `https://hub.sandbox.dev.techbd.org`
  - Methods: `GET, POST, OPTIONS`
  - Headers: Custom headers like `X-TechBD-Tenant-ID`, `X-TechBD-CIN`, `X-TechBD-OrgNPI`, etc.

---

## 🚀 Deployment Notes

- Channels are set to start automatically (`initialState=STARTED`).
- Archive pruning enabled (`archiveEnabled=true`).
- These channels require specific environment variables:
  - `MC_CCDA_SCHEMA_FOLDER` — path to schema & XSLT files.
  - `BASE_FHIR_URL`, `PROFILE_URL_*` — various FHIR profile URLs.
- Make sure schema files like `CDA.xsd`, `cda-phi-filter.xslt`, and vendor-specific XSLTs exist in the configured schema folder.
- Processing logic includes EHR vendor detection (`epic`, `medent`, or default) for XSLT template selection.

---

## Requirements

- BridgeLink 4.5.3+
- Properly configured Nexus Sandbox environment

---
