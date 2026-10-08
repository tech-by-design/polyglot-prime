# BridgeLink Channels: FlatFileCsvBundle & FlatFileCsvBundleValidate

This repository contains two BridgeLink channels for handling and validating zipped CSV files containing FHIR data. These channels process flat files (CSV format) and convert them into FHIR Bundles.

---

## 📦 Channels Overview

### 1. **FlatFileCsvBundle**
- **Channel Name**: `FlatFileCsvBundle`
- **Purpose**: Accept and process CSV files (within ZIP archives) and convert them into FHIR Bundles.
- **Endpoint**: `POST /flatfile/csv/Bundle` or `POST /flatfile/csv/Bundle/`
- **Port**: `9004`
- **Host**: `0.0.0.0`
- **Expected Input**: `multipart/form-data` with ZIP file containing CSV files.
- **Required Headers**:
  - `X-TechBD-Tenant-ID` - Tenant identifier (mandatory)
  - `Content-Type` - Must be `multipart/form-data` with boundary
- **Optional Headers**:
  - `X-TechBD-DataLake-API-URL` - Override DataLake API URL
  - `X-TechBD-BL-BaseURL` - BridgeLink base URL for dynamic resolution
  - `X-TechBD-Base-FHIR-URL` - Custom base FHIR URL
  - `X-TechBD-Validation-Severity-Level` - Validation severity (default: "error")
  - `User-Agent` - Client user agent
- **Key Features**:
  - Supports dynamic BridgeLink base URL resolution via `X-TechBD-BL-BaseURL` header
  - Validates ZIP file must contain at least one CSV file
  - Validates empty ZIP files are rejected
  - Retrieves CSV service API URL from lookup/secrets manager
  - Supports `immediate` query parameter (default: true)
  - Root endpoint (`/`) returns channel info
  - Health check endpoint: `/healthcheck`
- **Validation Steps**:
  1. Validates required headers (X-TechBD-Tenant-ID, Content-Type)
  2. Checks multipart form data format
  3. Validates file is present and not empty
  4. Validates ZIP file contains at least one CSV file
  5. Extracts and forwards to CSV service API
- **Response**: JSON with FHIR Bundle or error details
- **Response Variable**: `fhirResponse`

---

### 2. **FlatFileCsvBundleValidate**
- **Channel Name**: `FlatFileCsvBundleValidate`
- **Purpose**: Validates the CSV ZIP file format and content without processing or submitting to downstream systems.
- **Endpoint**: `POST /flatfile/csv/Bundle/$validate` or `POST /flatfile/csv/Bundle/$validate/`
- **Port**: `9005`
- **Host**: `0.0.0.0`
- **Expected Input**: `multipart/form-data` with ZIP file containing CSV files.
- **Required Headers**:
  - `X-TechBD-Tenant-ID` - Tenant identifier (mandatory)
  - `Content-Type` - Must be `multipart/form-data` with boundary
- **Optional Headers**:
  - `X-TechBD-Validation-Severity-Level` - Validation severity level (default: "error")
  - `User-Agent` - Client user agent
- **Key Features**:
  - Fixed blank 200 OK response issue when invalid URL, port, or method was provided
  - Fixed blank response when empty ZIP file is selected
  - Validates ZIP file must contain at least one CSV file
  - Validation only - no bundle creation or submission
  - Forwards to validation service: `https://nexus.csv.sandbox.techbd.org/flatfile/csv/Bundle/$validate`
  - Root endpoint (`/`) returns channel info
  - Health check endpoint: `/healthcheck`
- **Validation Steps**:
  1. Validates required headers (X-TechBD-Tenant-ID, Content-Type)
  2. Checks multipart form data format
  3. Validates file is present and not empty
  4. Validates file extension is `.zip`
  5. Validates ZIP file is not corrupted
  6. Validates ZIP contains at least one file (empty folders rejected)
  7. Validates at least one CSV file exists in ZIP
  8. Forwards to validation service endpoint
- **Response**: JSON validation result
- **Response Variable**: `fhirResponse`

---

## 🔧 Common Features & Comparison

| Feature | FlatFileCsvBundle | FlatFileCsvBundleValidate |
|--------|--------------------|----------------------------|
| **Channel Name** | FlatFileCsvBundle | FlatFileCsvBundleValidate |
| **Port** | 9004 | 9005 |
| **HTTP Method** | POST | POST |
| **Context Paths** | `/`, `/healthcheck`, `/flatfile/csv/Bundle` | `/`, `/healthcheck`, `/flatfile/csv/Bundle/$validate` |
| **Required Headers** | X-TechBD-Tenant-ID, Content-Type | X-TechBD-Tenant-ID, Content-Type |
| **Optional Headers** | X-TechBD-DataLake-API-URL, X-TechBD-BL-BaseURL, X-TechBD-Base-FHIR-URL, X-TechBD-Validation-Severity-Level | X-TechBD-Validation-Severity-Level |
| **Zip File Validation** | Must contain ≥1 CSV file | Must contain ≥1 CSV file |
| **Empty ZIP Handling** | Rejected with error | Rejected with error (fixed in 0.8.5) |
| **Channel Type** | Processing & Submission | Validation Only |
| **Multipart Parsing** | Enabled | Enabled |
| **Binary MIME Types** | `application/.*(?<!json\|xml)$\|image/.*\|video/.*\|audio/.*\|application/zip` | `application/.*(?<!json\|xml)$\|image/.*\|video/.*\|audio/.*\|application/zip` |
| **Response Content Type** | application/json | application/json |
| **Response Variable** | fhirResponse | fhirResponse |
| **Destination URL** | Dynamic (from lookup/secrets) | `https://nexus.csv.sandbox.techbd.org/flatfile/csv/Bundle/$validate` |

---

## 🛡 Security Notes

- **SSL/TLS**: HTTP listener (SSL/TLS not configured in these channels)
- **Authentication**: None (`authType=NONE`)
- **CORS Headers**:
  - `Access-Control-Allow-Origin`: `https://hub.dev.techbd.org`
  - `Access-Control-Allow-Methods`: `GET, POST, OPTIONS`
  - `Access-Control-Allow-Headers`: Includes Content-Type, Authorization, X-TechBD-Base-FHIR-URL, X-TechBD-Tenant-ID, User-Agent, X-TechBD-REMOTE-IP, X-TechBD-Override-Request-URI, X-Correlation-ID, accept, X-TechBD-DataLake-API-URL, DataLake-API-Content-Type, X-TechBD-HealthCheck, X-TechBD-Validation-Severity-Level, X-SHIN-NY-IG-Version
  - `Access-Control-Allow-Credentials`: `true`
  - `Access-Control-Expose-Headers`: Location, X-TechBD-Tenant-ID, User-Agent, X-TechBD-REMOTE-IP, X-TechBD-Override-Request-URI, X-Correlation-ID, X-TechBD-HealthCheck

---

## 🚀 Deployment Notes

- **BridgeLink Version**: 4.6.1
- **Channels Start State**: Configured to deploy (check channel deployment settings)
- **Processing Threads**: 1 per channel
- **Queue Buffer Size**: 1000 messages
- **Response Handling**: `respondAfterProcessing=true`
- **Metadata Processing**: Excluded (`includeMetadata=false`)
- **Batch Processing**: Disabled (`processBatch=false`)

---

## 📋 Error Handling

Both channels implement comprehensive error handling:

### Common Error Responses (400 Bad Request)
- Missing required header: `X-TechBD-Tenant-ID`
- Invalid `Content-Type` (must be `multipart/form-data`)
- No file provided in request
- Uploaded file is empty or missing
- Invalid file extension (must be `.zip`)
- Invalid or corrupted ZIP file
- ZIP contains no files (empty folders not allowed)
- ZIP must contain at least one CSV file

### HTTP Status Codes
- `200 OK` - Successful processing/validation
- `400 Bad Request` - Validation errors
- `404 Not Found` - Invalid endpoint
- `405 Method Not Allowed` - Invalid HTTP method
- Response status code: `${status}` (dynamic based on processing result)

---

## 🔍 Channel Endpoints

### FlatFileCsvBundle Endpoints
- **Root (`/`)**: Returns channel information and available endpoints
- **Health Check (`/healthcheck`)**: Returns service health status
- **Main Endpoint (`/flatfile/csv/Bundle`)**: Processes CSV bundles

### FlatFileCsvBundleValidate Endpoints
- **Root (`/`)**: Returns channel information and available endpoints
- **Health Check (`/healthcheck`)**: Returns service health status
- **Validation Endpoint (`/flatfile/csv/Bundle/$validate`)**: Validates CSV bundles

---

## 📊 Lookup Manager Integration

**FlatFileCsvBundle** uses Lookup Manager for:
- `TECHBD_CSV_SERVICE_API_URL` - CSV service endpoint URL
- `MC_VALID_FHIR_URLS` - Comma-separated list of valid FHIR URLs
- Lookup cache TTL: 20 days (480 hours)

---

## ⚙️ Query Parameters

**FlatFileCsvBundle** supports:
- `source` - Set to "CSV" (automatically added)
- `immediate` - Process immediately (default: "true")

**FlatFileCsvBundleValidate** supports:
- `source` - Set to "CSV" (automatically added)

---

---

## 💻 Usage Examples

### FlatFileCsvBundle - Submit CSV Bundle

```bash
curl -X POST http://localhost:9004/flatfile/csv/Bundle \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: my-tenant-id" \
  -H "X-TechBD-DataLake-API-URL: https://datalake.example.com/api" \
  -H "X-TechBD-BL-BaseURL: https://bridgelink.example.com" \
  -H "X-TechBD-Validation-Severity-Level: warning" \
  -F "file=@/path/to/your/csvfiles.zip"
```

### FlatFileCsvBundleValidate - Validate CSV Bundle

```bash
curl -X POST http://localhost:9005/flatfile/csv/Bundle/\$validate \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: my-tenant-id" \
  -H "X-TechBD-Validation-Severity-Level: error" \
  -F "file=@/path/to/your/csvfiles.zip"
```

### Health Check

```bash
# FlatFileCsvBundle
curl -X GET http://localhost:9004/healthcheck

# FlatFileCsvBundleValidate
curl -X GET http://localhost:9005/healthcheck
```

---

## Requirements

- **BridgeLink**: Version 4.6.1 or higher
- **Java Runtime**: Compatible with BridgeLink 4.6.1
- **Dependencies**: Apache HttpClient libraries (included with BridgeLink)
- **Environment**: Nexus Sandbox or compatible environment
- **Lookup Manager**: Configured with required lookup values (for FlatFileCsvBundle)

---
