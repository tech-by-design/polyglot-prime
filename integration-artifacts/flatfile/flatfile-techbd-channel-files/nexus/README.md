# BridgeLink Channels: FlatFileCsvBundle & FlatFileCsvBundleValidate

This repository contains two BridgeLink channels for handling and validating zipped CSV files containing FHIR data. These channels process flat files (CSV format) and convert them into FHIR Bundles.

---

## 📦 Channels Overview

### 1. **FlatFileCsvBundle**
- **Purpose**: Accept and process CSV files (within ZIP archives) and convert them into FHIR Bundles.
- **Endpoint**: `POST /flatfile/csv/Bundle`
- **Port**: `9004`
- **Expected Input**: `multipart/form-data` or binary ZIP file containing CSVs.
- **Required Header**:
  - `X-TechBD-Tenant-ID`
- **Optional Header**:
  - `X-Correlation-ID` (validated as UUID if provided)
- **Validation & Processing Steps**:
  - Validates required headers.
  - Extracts and processes ZIP contents using the `processCSVZipFile()` function from the `globalMap`.
  - Generates FHIR Bundles.
- **Response**: JSON with status and message.

---

### 2. **FlatFileCsvBundleValidate**
- **Purpose**: Validates the CSV ZIP file format and data content without submitting it to a downstream system.
- **Endpoint**: `POST /flatfile/csv/Bundle/$validate`
- **Port**: `9005`
- **Expected Input**: `multipart/form-data` or binary ZIP file containing CSVs.
- **Required Header**:
  - `X-TechBD-Tenant-ID`
- **Optional Header**:
  - `X-Correlation-ID` (validated as UUID if provided)
- **Validation Only**:
  - Uses the same `processCSVZipFile()` function but in validation mode.
  - Does not create FHIR Bundles.
- **Response**: JSON result of validation.

---

## 🔧 Common Features

| Feature | FlatFileCsvBundle | FlatFileCsvBundleValidate |
|--------|--------------------|----------------------------|
| **Port** | 9004 | 9005 |
| **HTTP Method** | POST | POST |
| **Context Path** | `/flatfile/csv/Bundle` | `/flatfile/csv/Bundle/$validate` |
| **Preprocessing** | Header & UUID validation | Header & UUID validation |
| **Zip File Handling** | via `processCSVZipFile` | via `processCSVZipFile` |
| **Channel Type** | Submission | Validation |
| **Destination** | Internal VM channel writer | Internal VM channel writer |

---

## 🛡 Security Notes

- **SSL**: Disabled by default.
- **Authentication**: None (`authType=NONE`)
- **CORS Headers**:
  - `Access-Control-Allow-Origin`: `https://hub.dev.techbd.org`
  - Methods: `GET, POST, OPTIONS`
  - Headers: Custom headers like `X-TechBD-Tenant-ID`, `X-Correlation-ID`, etc.

---

## 🚀 Deployment Notes

- Channels are set to start automatically (`initialState=STARTED`).
- Archive pruning enabled (`archiveEnabled=true`).
- Processing and validation logic rely on `processCSVZipFile` function, which should be injected into the globalMap at runtime or via deployment scripting.

---

## Requirements

- BridgeLink 4.5.3+
- Properly configured Nexus environment

---
