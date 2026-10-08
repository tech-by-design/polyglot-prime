# Mirth Connect Channel: TechBD CCD Workflow

This folder contains one Mirth Connect channel export: `TechBD CCD Workflow`. The channel handles CCDA ingestion on one listener and routes traffic to either validation-only processing or conversion-and-submission processing.

---

## 📦 Channel Overview

### Channel Name
- `TechBD CCD Workflow`

### Listener Configuration
- **Transport**: HTTP Listener
- **Port**: `9443`
- **Context Path**: `/`
- **Authentication**: `NONE`
- **Response Variable**: `finalResponse`
- **Response Content Type**: `application/json`

### Supported API Paths
- `POST /ccda/Bundle` and `POST /ccda/Bundle/`
- `POST /ccda/Bundle/$validate` and `POST /ccda/Bundle/$validate/`

### Destination Routing
- `dest_bundle_validate` (Channel Writer) for `$validate` path
- `dest_bundle` (HTTP Sender) for `/ccda/Bundle` path

---

## 🔀 Endpoint Behavior

### 1) `/ccda/Bundle`
- Validates request headers and multipart content.
- Extracts and normalizes CCDA payload from multipart body.
- Applies PHI filter and XSD validation.
- Performs mandatory-field validation.
- Converts CCDA to FHIR Bundle using XSLT.
- Saves lifecycle payloads/status to DB.
- Sends converted FHIR Bundle to downstream endpoint via `dest_bundle`.
- Adds query param `source=CCDA` on downstream call.

### 2) `/ccda/Bundle/$validate`
- Runs the same structural validation flow (multipart checks, extraction, PHI filter, XSD, mandatory fields).
- Returns OperationOutcome-style validation response.
- Does not perform bundle submission to external FHIR API.
- Routes through `dest_bundle_validate`.

---

## 📥 Request Requirements

### Common Required Headers
- `X-TechBD-Tenant-ID`
- `Content-Type: multipart/form-data` (boundary expected)

### Additional Required Headers for `/ccda/Bundle`
- `X-TechBD-CIN`
- One of:
  - `X-TechBD-OrgNPI`
  - `X-TechBD-OrgTIN`
- `X-TechBD-Facility-ID`
- `X-TechBD-Encounter-Type`

### Optional Headers
- `User-Agent`
- `X-TechBD-Validation-Severity-Level` (default used when missing: `error`)
- `X-TechBD-Screening-Code`
- `X-TechBD-Part2`
- `X-TechBD-Base-FHIR-URL`
- `X-TechBD-Override-Request-URI`

### File Requirements
- Multipart file is required.
- Supported extensions: `.xml`, `.txt`
- File must contain extractable `ClinicalDocument` content.

---

## 🧩 Processing Details

- Vendor-aware mapping is applied (e.g., Epic/Medent/AthenaHealth handling).
- SHA-256 IDs are generated for multiple FHIR resources during conversion.
- Consent status is derived from CCDA and mapped into elaboration metadata.
- Data Ledger sync calls are controlled by `DATA_LEDGER_TRACKING`.
- For `/ccda/Bundle`, downstream request uses `application/fhir+json` with propagated headers.

---

## 🔐 CORS and Security

- **Authentication**: `authType=NONE`
- **CORS Allow-Origin**: `*`
- **CORS Allow-Methods**: `GET, POST, OPTIONS`
- **CORS Allow-Credentials**: `true`
- **Allowed Headers** include:
  - `Content-Type`, `Authorization`, `X-TechBD-Tenant-ID`, `User-Agent`
  - `X-TechBD-REMOTE-IP`, `X-TechBD-Override-Request-URI`, `accept`
  - `X-TechBD-CIN`, `X-TechBD-OrgNPI`, `X-TechBD-OrgTIN`
  - `X-TechBD-Base-FHIR-URL`, `X-TechBD-Validation-Severity-Level`
  - `X-TechBD-Facility-ID`, `X-TechBD-Encounter-Type`, `X-TechBD-Screening-Code`

---

## 🌐 Required Environment Variables

### Core Runtime
- `MC_FHIR_BUNDLE_SUBMISSION_API_URL`
- `MC_CCDA_SCHEMA_FOLDER`
- `MC_JDBC_URL`
- `MC_JDBC_USERNAME`
- `MC_JDBC_PASSWORD`

### Data Ledger
- `DATA_LEDGER_API_URL`
- `TECHBD_NYEC_DATALEDGER_API_KEY`
- `DATA_LEDGER_TRACKING`

### FHIR Base URL Validation
- `MC_VALID_FHIR_URLS`

### FHIR Profile URL Parameters
- `BASE_FHIR_URL`
- `PROFILE_URL_BUNDLE`
- `PROFILE_URL_PATIENT`
- `PROFILE_URL_ENCOUNTER`
- `PROFILE_URL_CONSENT`
- `PROFILE_URL_ORGANIZATION`
- `PROFILE_URL_OBSERVATION`
- `PROFILE_URL_SEXUAL_ORIENTATION`
- `PROFILE_URL_QUESTIONNAIRE`
- `PROFILE_URL_QUESTIONNAIRE_RESPONSE`
- `PROFILE_URL_PRACTITIONER`
- `PROFILE_URL_PROCEDURE`
- `PROFILE_URL_LOCATION`

---

## 🚀 Deployment Notes

- Channel `initialState` is `STARTED`.
- Message storage mode is `DEVELOPMENT`.
- `archiveEnabled=true` in pruning settings.
- Ensure schema/XSLT files are present under `MC_CCDA_SCHEMA_FOLDER` (for example `CDA.xsd`, `cda-phi-filter.xslt`, `cda-fhir-bundle*.xslt`).

---

## Requirements

- Mirth Connect 4.5.x compatible runtime
- PostgreSQL connectivity for interaction persistence
- Correctly configured environment variables listed above

---
