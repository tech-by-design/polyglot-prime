# BridgeLink Channels: HL7V2 Bundle

This repository contains Nexus Mirth Connect (BridgeLink) channel for processing and validating HL7 documents. These channels convert uploaded HL7 files into FHIR Bundles and validate them using predefined profiles.

---

## 📦 Channels Overview

### 1. **HL7V2 Bundle**
- **Purpose**: Accepts and processes HL7 documents, transforms them into FHIR Bundles.
- **Endpoint**: `POST /hl7v2/Bundle/`
- **Port**: `9006`
- **Expected Input**: `multipart/form-data` with a `file` field containing a hl7 XML document.
- **Headers Required**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `X-TechBD-CIN` (mandatory)
  - Either `X-TechBD-OrgNPI` or `X-TechBD-OrgTIN` (one is mandatory)
  - `X-TechBD-Facility-ID` (mandatory)
  - `X-TechBD-Encounter-Type` (mandatory)
- **Validation**:
  - Ensures the file is HL7 and parses correctly.
  - Validates environment variables for XSLT and profile URLs.
  - Generates SHA-256 resource IDs for FHIR resources.
  - Selects XSLT templates based on the EHR vendor (`epic`, `medent`, or default).
- **Consent Logic**:
  - Extracts consent data and sets elaboration metadata.

---

## 🔧 Common Features

| Feature | HL7Bundle |
|--------|------------|
| **Port** | 9006 |
| **HTTP Method** | POST |
| **Context Path** | `/hl7v2/Bundle/` |
| **File Type** | HL7 via multipart form | 
| **Error Handling** | JSON with `OperationOutcome` |

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
  - `HL7_XSLT_PATH` — path to schema & XSLT files. (eg: /opt/connect/hl7-techbd-schema-files)
  - `BASE_FHIR_URL`, `PROFILE_URL_*` — various FHIR profile URLs.
- Upon deployment, the channel starts automatically and logs processing steps for debugging and traceability.

---

## Requirements

- BridgeLink 4.5.3+
- Properly configured Nexus environment

---
