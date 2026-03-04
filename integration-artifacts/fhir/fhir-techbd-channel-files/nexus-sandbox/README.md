# BridgeLink Channels: FhirBundlleSubmission & FhirBundlleValidation

This repository contains two BridgeLink/Mirth Connect channels used for handling and validating FHIR `Bundle` resources. These channels are implemented using version **4.6.1** of Mirth Connect.

---

## 📦 Channels Overview

### 1. **FhirBundlleSubmission**
- **Channel Name**: `FhirBundlleSubmission`
- **Purpose**: Accept and process FHIR Bundle submissions with validation and persistence to database/data lake.
- **Endpoints**: 
  - `POST /Bundle` or `POST /Bundle/` - Submit FHIR Bundle
  - `GET /` - Root endpoint (channel info)
  - `GET /healthcheck` - Health check endpoint
- **Port**: `9000`
- **Host**: `0.0.0.0`
- **Expected Input**: FHIR Bundle JSON (application/fhir+json or application/json)
- **Required Headers**:
  - `X-TechBD-Tenant-ID` - Tenant identifier (mandatory)
- **Optional Headers**:
  - `X-TechBD-Interaction-ID` - Unique interaction identifier (generated if not provided)
  - `X-Correlation-ID` - Correlation ID (used if X-TechBD-Interaction-ID not provided)
  - `X-TechBD-Group-Interaction-ID` - Group interaction identifier
  - `X-TechBD-Master-Interaction-ID` - Master interaction identifier
  - `X-TechBD-Validation-Severity-Level` - Validation level (default: "error")
  - `X-SHIN-NY-IG-Version` - Implementation Guide version
  - `X-TechBD-Elaboration` - Additional elaboration details
  - `X-TechBD-Override-Request-URI` - Override the request URI for tracking
  - `X-TechBD-Request-URI` - Original request URI
  - `X-TechBD-DataLake-API-URL` - Override DataLake API URL
  - `User-Agent` - Client user agent
- **Query Parameters**:
  - `source` - Data source identifier (required, e.g., "FHIR")
- **Key Features**:
  - Validates FHIR Bundle structure and content
  - Extracts OperationOutcome issues and dispositions
  - Appends validation results to the Bundle
  - Persists to database (JDBC/PostgreSQL)
  - Optionally sends to DataLake API
  - Supports action discard based on validation results
  - Lookup Manager integration for configuration
  - Comprehensive error handling with detailed logging
- **Response**: FHIR OperationOutcome or Bundle (application/fhir+json)
- **Response Variable**: `finalResponse`

---

### 2. **FhirBundlleValidation**
- **Channel Name**: `FhirBundlleValidation`
- **Purpose**: Validate FHIR Bundles without submitting them to database or data lake.
- **Endpoints**: 
  - `POST /Bundle/$validate` or `POST /Bundle/$validate/` - Validate FHIR Bundle
  - `GET /` - Root endpoint (channel info)
  - `GET /healthcheck` - Health check endpoint
- **Port**: `9001`
- **Host**: `0.0.0.0`
- **Expected Input**: FHIR Bundle JSON (application/fhir+json or application/json)
- **Required Headers**:
  - `X-TechBD-Tenant-ID` - Tenant identifier (mandatory)
- **Optional Headers**:
  - `X-TechBD-Interaction-ID` - Unique interaction identifier (generated if not provided)
  - `X-Correlation-ID` - Correlation ID (used if X-TechBD-Interaction-ID not provided)
  - `X-TechBD-Validation-Severity-Level` - Validation level (default: "error")
  - `X-SHIN-NY-IG-Version` - Implementation Guide version
  - `X-TechBD-Elaboration` - Additional elaboration details
  - `X-TechBD-Override-Request-URI` - Override the request URI for tracking
  - `X-TechBD-Request-URI` - Original request URI
  - `User-Agent` - Client user agent
- **Key Features**:
  - Validation only - no persistence or data lake submission
  - Forwards to FHIR validation API endpoint
  - Fixed PSQL error when ZIP file is selected
  - Comprehensive validation reporting
  - Lookup Manager integration for configuration
- **Response**: FHIR OperationOutcome (application/fhir+json)
- **Response Variable**: `finalResponse`

---

## 🔧 Common Features & Comparison

| Feature | FhirBundlleSubmission | FhirBundlleValidation |
|--------|--------------------|----------------------------|
| **Channel Name** | FhirBundlleSubmission | FhirBundlleValidation |
| **Port** | 9000 | 9001 |
| **HTTP Method** | POST | POST |
| **Context Paths** | `/`, `/healthcheck`, `/Bundle` | `/`, `/healthcheck`, `/Bundle/$validate` |
| **Mirth Version** | 4.6.1 | 4.6.1 |
| **Required Headers** | X-TechBD-Tenant-ID | X-TechBD-Tenant-ID |
| **Optional Headers** | X-TechBD-Interaction-ID, X-Correlation-ID, X-TechBD-Group-Interaction-ID, X-TechBD-Master-Interaction-ID, X-TechBD-Validation-Severity-Level, X-SHIN-NY-IG-Version, X-TechBD-Elaboration, X-TechBD-DataLake-API-URL | X-TechBD-Interaction-ID, X-Correlation-ID, X-TechBD-Validation-Severity-Level, X-SHIN-NY-IG-Version, X-TechBD-Elaboration |
| **Query Parameters** | source (required) | source (optional) |
| **Channel Type** | Processing & Submission | Validation Only |
| **Database Persistence** | Yes (PostgreSQL/JDBC) | No |
| **DataLake Integration** | Yes (optional) | No |
| **Parse Multipart** | No | No |
| **Response Content Type** | application/fhir+json | application/fhir+json |
| **Response Variable** | finalResponse | finalResponse |
| **Processing Threads** | 1 | 1 |
| **Queue Buffer Size** | 1000 | 1000 |
| **Timeout** | 30000ms (30s) | 30000ms (30s) |

---

## 🛡 Security Notes

- **SSL/TLS**: HTTP listener (SSL/TLS not configured in these channels)
- **Authentication**: None (`authType=NONE`)
- **CORS Headers**:
  - `Access-Control-Allow-Origin`: `${HUB_UI_URL}` (dynamic from Lookup Manager)
  - `Access-Control-Allow-Methods`: `GET, POST, OPTIONS`
  - `Access-Control-Allow-Headers`: Content-Type, Authorization, X-TechBD-Base-FHIR-URL, X-TechBD-Tenant-ID, User-Agent, X-TechBD-REMOTE-IP, X-TechBD-Override-Request-URI, X-Correlation-ID, accept, X-TechBD-DataLake-API-URL, DataLake-API-Content-Type, X-TechBD-HealthCheck, X-TechBD-Validation-Severity-Level, X-SHIN-NY-IG-Version
  - `Access-Control-Allow-Credentials`: Not explicitly set
  - `Access-Control-Expose-Headers`: Location, X-TechBD-Tenant-ID, User-Agent, X-TechBD-REMOTE-IP, X-TechBD-Override-Request-URI, X-Correlation-ID, X-TechBD-HealthCheck

---

## 🚀 Deployment Notes

- **Mirth Connect Version**: 4.6.1
- **Channels Start State**: Configured to deploy (check channel deployment settings)
- **Processing Threads**: 1 per channel
- **Queue Buffer Size**: 1000 messages
- **Response Handling**: `respondAfterProcessing=true`
- **Metadata Processing**: Excluded (`includeMetadata=false`)
- **Batch Processing**: Disabled (`processBatch=false`)
- **Binary MIME Types**: Not configured (empty)

---

## 📋 Error Handling

Both channels implement comprehensive error handling:

### Common Error Responses (400 Bad Request)
- Missing required header: `X-TechBD-Tenant-ID`
- Missing or empty request body
- Missing required query parameter: `source` (FhirBundlleSubmission)
- Invalid validation severity level value

### HTTP Status Codes
- `200 OK` - Successful processing/validation
- `400 Bad Request` - Validation errors, missing headers/parameters
- `404 Not Found` - Invalid endpoint
- `405 Method Not Allowed` - Invalid HTTP method (non-POST)
- `500 Internal Server Error` - Lookup Manager failures, database errors
- Response status code: `${status}` (dynamic based on processing result)

---

## 📊 Lookup Manager Integration

Both channels use Lookup Manager for configuration with **20-day cache TTL**:

### FhirBundlleSubmission Lookups
- `MC_JDBC_USERNAME` - Database username (Config-sensitive)
- `MC_JDBC_PASSWORD` - Database password (Config-sensitive)
- `MC_JDBC_URL` - JDBC connection URL (Config-sensitive)
- `TECHBD_NYEC_DATALEDGER_API_KEY` - DataLedger API key (Config-sensitive)
- `TECHBD_NYEC_DATALAKE_API_KEY` - DataLake API key (Config-sensitive)
- `MC_FHIR_BUNDLE_SUBMISSION_API_URL` - FHIR submission API URL (Config)
- `TECHBD_CSV_SERVICE_API_URL` - CSV service API URL (Config)
- `TECHBD_DATALAKE_API_URL` - Default DataLake API URL (Config)
- `BL_FHIR_BUNDLE_VALIDATION_API_URL` - FHIR validation API URL (Config)
- `TECHBD_NYEC_DATALEDGER_API_URL` - DataLedger API URL (Config)
- `DATA_LEDGER_TRACKING` - DataLedger tracking configuration (Config)
- `HUB_UI_URL` - Hub UI URL for CORS (Config)

### FhirBundlleValidation Lookups
- `MC_JDBC_USERNAME` - Database username (Config-sensitive)
- `MC_JDBC_PASSWORD` - Database password (Config-sensitive)
- `MC_JDBC_URL` - JDBC connection URL (Config-sensitive)
- `TECHBD_NYEC_DATALEDGER_API_KEY` - DataLedger API key (Config-sensitive)
- `TECHBD_NYEC_DATALAKE_API_KEY` - DataLake API key (Config-sensitive)
- `MC_FHIR_BUNDLE_SUBMISSION_API_URL` - FHIR submission channel URL (Config)
- `TECHBD_CSV_SERVICE_API_URL` - CSV service API URL (Config)
- `TECHBD_DATALAKE_API_URL` - Default DataLake API URL (Config)
- `BL_FHIR_BUNDLE_VALIDATION_API_URL` - FHIR validation API URL (Config)
- `HUB_UI_URL` - Hub UI URL for CORS (Config)

---

## 🔍 Channel Endpoints

### FhirBundlleSubmission Endpoints
- **Root (`/`)**: Returns channel information and available endpoints (JSON)
- **Health Check (`/healthcheck`)**: Returns service health status (JSON)
- **Main Endpoint (`/Bundle` or `/Bundle/`)**: Processes and validates FHIR Bundles

### FhirBundlleValidation Endpoints
- **Root (`/`)**: Returns channel information and available endpoints (JSON)
- **Health Check (`/healthcheck`)**: Returns service health status (JSON)
- **Validation Endpoint (`/Bundle/$validate` or `/Bundle/$validate/`)**: Validates FHIR Bundles without persistence

---

## 💻 Usage Examples

### FhirBundlleSubmission - Submit FHIR Bundle

```bash
curl -X POST http://localhost:9000/Bundle?source=FHIR \
  -H "Content-Type: application/fhir+json" \
  -H "X-TechBD-Tenant-ID: my-tenant-id" \
  -H "X-TechBD-Interaction-ID: $(uuidgen)" \
  -H "X-TechBD-Validation-Severity-Level: warning" \
  -H "X-SHIN-NY-IG-Version: 1.0.0" \
  -d @bundle.json
```

### FhirBundlleValidation - Validate FHIR Bundle

```bash
curl -X POST http://localhost:9001/Bundle/\$validate \
  -H "Content-Type: application/fhir+json" \
  -H "X-TechBD-Tenant-ID: my-tenant-id" \
  -H "X-TechBD-Validation-Severity-Level: error" \
  -H "X-SHIN-NY-IG-Version: 1.0.0" \
  -d @bundle.json
```

### Health Check

```bash
# FhirBundlleSubmission
curl -X GET http://localhost:9000/healthcheck

# FhirBundlleValidation
curl -X GET http://localhost:9001/healthcheck
```

### Root Endpoint

```bash
# FhirBundlleSubmission
curl -X GET http://localhost:9000/

# FhirBundlleValidation
curl -X GET http://localhost:9001/
```

---

## ⚙️ Pre/Post Processing

### Preprocessing
Both channels perform similar preprocessing:
- Validate required `X-TechBD-Tenant-ID` header
- Validate request body is not empty
- Extract or generate `interactionId` from headers
- Extract validation severity level (default: "error")
- Extract IG version, elaboration, and other optional headers
- Handle URI overrides via `X-TechBD-Override-Request-URI`

### Postprocessing
- **FhirBundlleSubmission**: Database persistence, DataLake submission, destination response handling
- **FhirBundlleValidation**: Destination response forwarding only

---

## Requirements

- **Mirth Connect**: Version 4.6.1 or higher
- **Java Runtime**: Compatible with Mirth Connect 4.6.1
- **Database**: PostgreSQL (for FhirBundlleSubmission)
- **Dependencies**: 
  - Apache HttpClient libraries (included with Mirth)
  - PostgreSQL JDBC driver
  - AWS SDK (if using Secrets Manager - currently commented out)
- **Environment**: Nexus Sandbox or compatible environment
- **Lookup Manager**: Configured with required lookup values

---
