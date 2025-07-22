# BridgeLink Channels: CcdaBundle & CcdaBundleValidate

This repository contains two Nexus Mirth Connect (BridgeLink) channels for processing and validating CCDA documents. These channels convert uploaded CCDA XML files into FHIR Bundles and validate them using predefined profiles.

---

## 📦 Channels Overview

### 1. **CcdaBundle**
- **Purpose**: Accepts and processes CCDA XML documents, transforms them into FHIR Bundles.
- **Endpoint**: `POST /ccda/Bundle/`
- **Port**: `9002`
- **Expected Input**: `multipart/form-data` with a `file` field containing a CCDA XML document.
- **Headers Required**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `X-TechBD-CIN` (mandatory)
  - Either `X-TechBD-OrgNPI` or `X-TechBD-OrgTIN` (one is mandatory)
  - `X-TechBD-Facility-ID` (mandatory)
  - `X-TechBD-Encounter-Type` (mandatory)
- **Validation**:
  - Ensures the file is XML and parses correctly.
  - Validates environment variables for XSLT and profile URLs.
  - Generates SHA-256 resource IDs for FHIR resources.
  - Selects XSLT templates based on the EHR vendor (`epic`, `medent`, or default).
- **Consent Logic**:
  - Extracts consent data and sets elaboration metadata.

---

### 2. **CcdaBundleValidate**
- **Purpose**: Validates CCDA files and returns errors or conformance results without creating FHIR Bundles.
- **Endpoint**: `POST /ccda/Bundle/$validate`
- **Port**: `9003`
- **Expected Input**: `multipart/form-data` with a `file` field.
- **Validation Steps**:
  - Header validation (same as `CcdaBundle`).
  - Validates the CCDA structure using schema (`CDA.xsd`).
  - Verifies the XSLT templates and environment setup.
  - Ensures file is not empty and is a valid CCDA XML.
  - Applies vendor-specific logic and generates resource identifiers.
- **Content-Type**: Must be `multipart/form-data`.

---

## 🔧 Common Features

| Feature | CcdaBundle | CcdaBundleValidate |
|--------|------------|--------------------|
| **Port** | 9002 | 9003 |
| **HTTP Method** | POST | POST |
| **Context Path** | `/ccda/Bundle/` | `/ccda/Bundle/$validate` |
| **File Type** | XML via multipart form | XML via multipart form |
| **Error Handling** | JSON with `OperationOutcome` | JSON with `OperationOutcome` |

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

- BridgeLink 4.5.3+
- Properly configured Nexus environment

---
