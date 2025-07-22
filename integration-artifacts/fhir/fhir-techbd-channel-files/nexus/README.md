# BridgeLink Channels: Bundle & BundleValidate

This repository contains two BridgeLink channels used for handling and validating FHIR `Bundle` resources. These channels are implemented using version **4.5.3** of BridgeLink.

---

## 📦 Channels Overview

### 1. **Bundle**
- **Purpose**: Accept and process FHIR Bundle submissions.
- **Endpoint**: `POST /Bundle`
- **Port**: `9000`
- **Validation**: Performs basic header checks and processes the FHIR bundle using a `processFHIRBundle` function (injected via `globalMap`).
- **CORS Support**: Configured to allow requests from `https://hub.dev.techbd.org`.
- **Content-Type Handling**: Supports binary MIME types such as images, videos, and audio (via regex).
- **Header Requirements**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - Optional headers used for logging/tracking: `X-Correlation-ID`, `X-TechBD-Base-FHIR-URL`, `User-Agent`, etc.
- **Validation & Processing Steps**:
  1. Check required headers.
  2. Call `processFHIRBundle()` with necessary parameters.
  3. Return JSON response (`status`, `message`).

### 2. **BundleValidate**
- **Purpose**: Validate FHIR Bundles without submitting them.
- **Endpoint**: `POST /Bundle/$validate`
- **Port**: `9001`
- **Validation**: Similar to `Bundle` but used only for checking correctness and structure.
- **Response Type**: JSON (`application/json`)
- **CORS Support**: Same as above.
- **Header Requirements**:
  - `X-TechBD-Tenant-ID` (mandatory)

---

## 🔧 Common Features

| Feature | Bundle | BundleValidate |
|--------|--------|----------------|
| **Port** | 9000 | 9001 |
| **HTTP Method** | POST | POST |
| **Context Path** | `/Bundle` | `/Bundle/$validate` |
| **Preprocessing** | Validates headers | Optional header checks (commented out) |
| **JavaScript Logic** | Validates headers & processes Bundle | Validates headers & performs validation |
| **Global JS Functions** | `createJsonResponse`, `setErrorResponse` | Same |
| **Destination** | Channel Writer (internal VM) | Channel Writer (internal VM) |

---

## ✅ Channel Filters

Both channels apply rule-based filters:
- Accept only `POST` requests.
- Match specific context paths exactly (`/Bundle`, `/Bundle/$validate`).

---

## 🛡 Security Notes

- **SSL**: Disabled by default.
- **Authentication**: None (`authType=NONE`)
- **CORS Headers**:
  - `Access-Control-Allow-Origin`: `https://hub.dev.techbd.org`
  - Methods: `GET, POST, OPTIONS`
  - Headers: Includes custom headers like `X-TechBD-*`

---

## Requirements

- BridgeLink 4.5.3+
- Properly configured Nexus environment

---
