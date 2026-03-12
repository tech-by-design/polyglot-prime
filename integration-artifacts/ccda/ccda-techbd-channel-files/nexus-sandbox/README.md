# TechBD CCD Workflow Channel

**Channel Name**: TechBD CCD Workflow  
**BridgeLink Version**: 4.6.1

This BridgeLink channel provides a unified workflow for processing and validating CCDA (Consolidated Clinical Document Architecture) documents in the Nexus Sandbox environment. It handles CCDA validation, PHI filtering, FHIR Bundle conversion, and database persistence with comprehensive error handling.

---

## 📦 Channel Overview

**TechBD CCD Workflow** is a comprehensive HTTP Listener channel that provides multiple endpoints for CCDA document processing, validation, and health monitoring.

### Key Features
- CCDA XML validation against XSD schemas
- PHI (Protected Health Information) filtering using XSLT transformations
- CCDA to FHIR Bundle conversion with vendor-specific XSLT templates
- Database persistence using jOOQ for storing payloads and validation results
- Data Ledger tracking integration for audit trails
- Dynamic configuration via Lookup Manager
- EHR vendor detection (Epic, Medent, or default)
- SHA-256 based FHIR resource ID generation
- Consent section extraction and elaboration metadata
- Custom FHIR Base URL support via headers

---

## 🌐 API Endpoints

### 1. **Root Endpoint**
- **Path**: `/`
- **Method**: `GET`
- **Purpose**: Returns channel information and available endpoints
- **Response**: JSON with channel metadata

### 2. **Health Check**
- **Path**: `/healthcheck`
- **Method**: `GET`, `POST`
- **Purpose**: Channel health and readiness check
- **Response**: JSON with status information

### 3. **CCDA Bundle Validation**
- **Path**: `/ccda/Bundle/$validate` or `/ccda/Bundle/$validate/`
- **Method**: `POST`
- **Purpose**: Validates CCDA XML files against XSD schemas without creating FHIR Bundles
- **Input**: `multipart/form-data` with a `file` field containing CCDA XML
- **Required Headers**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `Content-Type: multipart/form-data` (mandatory)
- **Optional Headers**:
  - `X-TechBD-Validation-Severity-Level` (default: "error")
- **File Types**: `.xml`, `.txt`
- **Validation Steps**:
  1. Validates multipart form data structure
  2. Extracts ClinicalDocument from raw XML
  3. Applies PHI filtering
  4. Validates against CDA XSD schema
  5. Saves validation results to database
- **Response**: JSON `OperationOutcome` with validation results

### 4. **CCDA Bundle Conversion**
- **Path**: `/ccda/Bundle` or `/ccda/Bundle/`
- **Method**: `POST`
- **Purpose**: Validates CCDA files, converts them to FHIR Bundles, and submits to downstream systems
- **Input**: `multipart/form-data` with a `file` field containing CCDA XML
- **Required Headers**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `X-TechBD-CIN` (mandatory) - Client Identification Number
  - `X-TechBD-OrgNPI` **OR** `X-TechBD-OrgTIN` (one is mandatory) - Organization NPI or TIN
  - `X-TechBD-Facility-ID` (mandatory)
  - `X-TechBD-Encounter-Type` (mandatory)
  - `Content-Type: multipart/form-data` (mandatory)
- **Optional Headers**:
  - `X-TechBD-Screening-Code` (if not provided, defaults to '100698-0' for Epic files)
  - `X-TechBD-Base-FHIR-URL` (custom FHIR base URL, must be in allowed list from `MC_VALID_FHIR_URLS`)
  - `X-TechBD-Validation-Severity-Level` (default: "error")
- **Processing Steps**:
  1. Data Ledger tracking - "received" event
  2. XML structure validation
  3. Mandatory field validation
  4. PHI filtering using vendor-specific XSLT
  5. XSD schema validation
  6. FHIR Bundle conversion using XSLT
  7. Database persistence
  8. Submission to downstream FHIR server
  9. Data Ledger tracking - "sent" event
- **Response**: FHIR Bundle JSON or `OperationOutcome` on error

---

## 🗄️ Database Integration

The channel uses jOOQ to interact with PostgreSQL for storing:
- Original CCDA payloads
- Validation results (success/failure)
- Converted FHIR Bundles
- Interaction metadata

### Stored Procedures Called
- `saveOgCCDAPayload` - Saves original CCDA XML
- `saveValidationSuccess` - Saves successful validation results
- `saveValidationFailed` - Saves validation errors
- `saveConversionSuccess` - Saves converted FHIR Bundle

### Database Connection
Connection details are retrieved from Lookup Manager (`Config-sensitive` group):
- `MC_JDBC_URL`
- `MC_JDBC_USERNAME`
- `MC_JDBC_PASSWORD`

---

## 🔐 Configuration via Lookup Manager

The channel uses BridgeLink's Lookup Manager for dynamic configuration management. All configurations are cached for 20-24 hours.

### Configuration Groups

#### **Config** (Non-sensitive configurations)
- `MC_FHIR_BUNDLE_SUBMISSION_API_URL` - Target FHIR server URL
- `MC_CCDA_SCHEMA_FOLDER` - Path to schema files
- `MC_VALID_FHIR_URLS` - Comma-separated list of allowed FHIR base URLs
- `MC_CSV_SERVICE_API_URL` - CSV service endpoint
- `MC_DEFAULT_DATALAKE_API_URL` - Default data lake URL
- `MC_HUB_UI_URL` - Hub UI URL for CORS
- `DATA_LEDGER_API_URL` - Data Ledger API endpoint
- `DATA_LEDGER_TRACKING` - Enable/disable Data Ledger tracking ("true"/"false")

#### **Config-sensitive** (Credentials and secrets)
- `MC_JDBC_PASSWORD` - Database password
- `MC_JDBC_USERNAME` - Database username
- `TECHBD_NYEC_DATALEDGER_API_KEY` - Data Ledger API key

#### **SchemaFiles** (XSD and XSLT templates)
- `index.txt` - List of all XSD files (newline-separated)
- `CDA.xsd` - Main CDA schema file
- All referenced XSD files from `index.txt`
- `cda-phi-filter` - PHI filtering XSLT (without .xslt extension)
- `ccda-fhir-bundle-epic` - Epic FHIR conversion XSLT
- `ccda-fhir-bundle-medent` - Medent FHIR conversion XSLT
- `ccda-fhir-bundle` - Default FHIR conversion XSLT

> **Note**: XSLT file keys in Lookup Manager should **not** include the `.xslt` extension

---

## 🔄 Processing Workflow

### Validation Workflow (`/ccda/Bundle/$validate`)
```
1. Receive multipart/form-data request
2. Validate required headers (X-TechBD-Tenant-ID)
3. Extract ClinicalDocument from raw XML
4. Apply PHI filtering XSLT transformation
5. Load XSD schemas from Lookup Manager to temp directory (/tmp/cda_xsd_cache)
6. Validate PHI-filtered XML against CDA.xsd
7. Save validation results to database
8. Return OperationOutcome JSON
```

### Bundle Conversion Workflow (`/ccda/Bundle`)
```
1. Data Ledger tracking - "received" event
2. Receive multipart/form-data request
3. Validate required headers (X-TechBD-Tenant-ID, CIN, NPI/TIN, etc.)
4. Extract and validate ClinicalDocument structure
5. Validate mandatory fields (dates, identifiers)
6. Detect EHR vendor (Epic, Medent, or default)
7. Apply vendor-specific PHI filtering XSLT
8. Check for Consent section and set X-TechBD-Elaboration header
9. Validate against XSD schemas
10. Convert to FHIR Bundle using vendor-specific XSLT
11. Generate SHA-256 resource IDs
12. Clean JSON (remove empty values)
13. Save converted bundle to database
14. Submit FHIR Bundle to downstream server
15. Data Ledger tracking - "sent" event
16. Return FHIR Bundle JSON
```

---

## 🔍 EHR Vendor Detection

The channel detects the EHR vendor from the CCDA XML to select appropriate XSLT templates:

### Detection Logic
Extracts manufacturer model name from:
```xml
<author>
  <assignedAuthoringDevice>
    <manufacturerModelName>...</manufacturerModelName>
  </assignedAuthoringDevice>
</author>
```

### Supported Vendors
- **Epic** - Uses `SchemaFiles/ccda-fhir-bundle-epic`
- **Medent** - Uses `SchemaFiles/ccda-fhir-bundle-medent`
- **Default** - Uses `SchemaFiles/ccda-fhir-bundle` for unknown vendors

---

## 🛡️ Security & Authentication

- **Authentication**: None (`authType=NONE`)
- **SSL**: Disabled by default
- **CORS Enabled**: Yes
  - `Access-Control-Allow-Origin`: Retrieved from Lookup Manager (`MC_HUB_UI_URL`)
  - `Access-Control-Allow-Methods`: `GET, POST, OPTIONS`
  - `Access-Control-Allow-Headers`: Includes custom headers like `X-TechBD-*`
- **User-Agent Forwarding**: Original User-Agent is forwarded to downstream systems

---

## 📊 Data Ledger Integration

When `DATA_LEDGER_TRACKING` is set to `"true"`, the channel tracks data flow events:

### Tracked Events
1. **Received Event** - When data is received by TechBD
   ```json
   {
     "executedAt": "2026-03-03T12:00:00.123456Z",
     "actor": "TechBD",
     "action": "received",
     "destination": "TechBD",
     "dataId": "<interaction-id>",
     "payloadType": "hrsnBundle"
   }
   ```

2. **Sent Event** - When data is forwarded to destination
   ```json
   {
     "executedAt": "2026-03-03T12:00:05.654321Z",
     "actor": "TechBD",
     "action": "sent",
     "destination": "<fhir-server-url>",
     "dataId": "<interaction-id>",
     "payloadType": "hrsnBundle"
   }
   ```

---

## ⚠️ Error Handling

All errors are returned as FHIR `OperationOutcome` JSON:

```json
{
  "OperationOutcome": {
    "validationResults": [{
      "operationOutcome": {
        "resourceType": "OperationOutcome",
        "issue": [{
          "severity": "error",
          "code": "invalid",
          "details": {
            "text": "Error description"
          }
        }]
      }
    }]
  }
}
```

### Common Error Scenarios
- Missing required headers → HTTP 400
- Invalid file format → HTTP 400
- Empty file content → HTTP 400
- XSD validation failure → HTTP 400 with validation details
- Invalid endpoint → HTTP 404
- Invalid HTTP method → HTTP 405
- XSLT transformation errors → HTTP 400 with error details

---

## 🚀 Deployment Requirements

### BridgeLink
- **Version**: 4.6.1 or higher
- **Required Libraries**:
  - PostgreSQL JDBC driver
  - jOOQ library
  - AWS SDK (for potential Secrets Manager integration)

### System Requirements
- **Temp Directory**: `/tmp/cda_xsd_cache` for XSD validation (auto-created)
- **Write Permissions**: Channel must have write access to temp directory

### Lookup Manager Configuration
Before deploying, ensure all configuration entries exist in Lookup Manager:
1. Create `Config` group with non-sensitive values
2. Create `Config-sensitive` group with credentials
3. Create `SchemaFiles` group with:
   - All XSD files listed in `index.txt`
   - All XSLT templates (without `.xslt` extension in key names)

### Database Setup
- PostgreSQL database with required stored procedures
- Database connection details in Lookup Manager
- Proper user permissions for read/write operations

---

## 📝 Change Log

### Version 0.5.17
- Fixed blank 200 OK response when invalid URL, port, or method was provided
- Enhanced error handling with proper HTTP status codes

---

## 🧪 Testing

### Health Check
```bash
curl -X GET http://localhost:8080/healthcheck
```

### Validation Only
```bash
curl -X POST http://localhost:8080/ccda/Bundle/$validate \
  -H "X-TechBD-Tenant-ID: test-tenant" \
  -F "file=@sample-ccda.xml"
```

### Full Bundle Conversion
```bash
curl -X POST http://localhost:8080/ccda/Bundle \
  -H "X-TechBD-Tenant-ID: test-tenant" \
  -H "X-TechBD-CIN: 12345" \
  -H "X-TechBD-OrgNPI: 1234567890" \
  -H "X-TechBD-Facility-ID: FAC-001" \
  -H "X-TechBD-Encounter-Type: ambulatory" \
  -F "file=@sample-ccda.xml"
```

---

## 📚 Additional Resources

- CCDA Schema: [HL7 CDA Release 2](https://www.hl7.org/implement/standards/product_brief.cfm?product_id=7)
- FHIR Specification: [HL7 FHIR R4](https://www.hl7.org/fhir/)
- BridgeLink Documentation: [Innovar BridgeLink](https://github.com/Innovar-Healthcare/BridgeLink)

---
