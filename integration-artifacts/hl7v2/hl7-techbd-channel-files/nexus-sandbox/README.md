# TechBD HL7 Workflow Channel

**Channel Name**: TechBD HL7 Workflow  
**BridgeLink Version**: 4.6.1

This BridgeLink channel provides a unified workflow for processing and validating HL7v2 messages in the Nexus Sandbox environment. It handles HL7 validation, FHIR Bundle conversion, database persistence, and comprehensive error handling with Data Ledger tracking support.

---

## 📦 Channel Overview

**TechBD HL7 Workflow** is a comprehensive HTTP Listener channel that provides multiple endpoints for HL7v2 message processing, validation, and health monitoring.

### Key Features
- HL7v2 message validation with structure checks
- HL7v2 to FHIR Bundle conversion using XSLT transformations
- Database persistence using jOOQ for storing payloads and validation results
- Data Ledger tracking integration for audit trails
- Dynamic configuration via Lookup Manager
- SHA-256 based FHIR resource ID generation
- Custom FHIR Base URL support via headers
- OBX segment validation with question code checks
- Multi-answer component handling in observations

---

## 🌐 API Endpoints

### 1. **Root Endpoint**
- **Path**: `/`
- **Method**: `GET`
- **Purpose**: Returns channel information and available endpoints
- **Response**: JSON with channel metadata and status 200

### 2. **Health Check**
- **Path**: `/healthcheck`
- **Method**: `GET`, `POST`
- **Purpose**: Channel health and readiness check
- **Response**: JSON with status information

### 3. **HL7v2 Bundle Validation**
- **Path**: `/hl7v2/Bundle/$validate` or `/hl7v2/Bundle/$validate/`
- **Method**: `POST`
- **Purpose**: Validates HL7v2 messages against HL7 schema without creating FHIR Bundles
- **Input**: `multipart/form-data` with a `file` field containing HL7v2 message
- **Required Headers**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `Content-Type: multipart/form-data` (mandatory)
- **Optional Headers**:
  - `X-TechBD-Validation-Severity-Level` (default: "error")
- **File Types**: `.hl7`, `.txt`
- **Validation Steps**:
  1. Validates multipart form data structure
  2. Validates file extension (.hl7 or .txt only)
  3. Extracts HL7 message content
  4. Validates HL7 structure (must start with "MSH|")
  5. Validates line format (no lines starting with "|")
  6. Validates against HL7 schema from Lookup Manager
  7. Saves validation results to database
- **Response**: JSON `OperationOutcome` with validation results

### 4. **HL7v2 Bundle Conversion**
- **Path**: `/hl7v2/Bundle` or `/hl7v2/Bundle/`
- **Method**: `POST`
- **Purpose**: Validates HL7v2 messages, converts them to FHIR Bundles, and submits to downstream systems
- **Input**: `multipart/form-data` with a `file` field containing HL7v2 message
- **Required Headers**:
  - `X-TechBD-Tenant-ID` (mandatory)
  - `X-TechBD-CIN` (mandatory) - Client Identification Number
  - `X-TechBD-OrgNPI` **OR** `X-TechBD-OrgTIN` (one is mandatory) - Organization NPI or TIN
  - `X-TechBD-Facility-ID` (mandatory)
  - `X-TechBD-Encounter-Type` (mandatory)
  - `X-TechBD-Organization-Name` (mandatory)
  - `Content-Type: multipart/form-data` (mandatory)
- **Optional Headers**:
  - `X-TechBD-Base-FHIR-URL` (custom FHIR base URL, must be in allowed list from `MC_VALID_FHIR_URLS`)
  - `X-TechBD-Validation-Severity-Level` (default: "error")
  - `X-TechBD-REMOTE-IP` (client IP address)
  - `X-TechBD-Override-Request-URI` (override request URI for routing)
- **Processing Steps**:
  1. Data Ledger tracking - "received" event
  2. File extension validation (.hl7 or .txt)
  3. HL7 structure validation (MSH segment, line format)
  4. Schema validation against HL7v2 validation schema
  5. OBX segment validation (question codes)
  6. FHIR Bundle conversion using XSLT
  7. SHA-256 resource ID generation
  8. Database persistence
  9. Submission to downstream FHIR server
  10. Data Ledger tracking - "sent" event
- **Response**: FHIR Bundle JSON or `OperationOutcome` on error

---

## 🗄️ Database Integration

The channel uses jOOQ to interact with PostgreSQL for storing:
- Original HL7v2 payloads
- Validation results (success/failure)
- Converted FHIR Bundles
- Interaction metadata

### Stored Procedures Called
- `saveOgHL7Payload` - Saves original HL7v2 message
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
- `MC_VALID_FHIR_URLS` - Comma-separated list of allowed FHIR base URLs
- `DATA_LEDGER_API_URL` - Data Ledger API endpoint
- `DATA_LEDGER_TRACKING` - Enable/disable Data Ledger tracking ("true"/"false")
- `HUB_UI_URL` - Hub UI URL for CORS configuration

#### **Config-sensitive** (Credentials and secrets)
- `MC_JDBC_URL` - Database connection URL
- `MC_JDBC_USERNAME` - Database username
- `MC_JDBC_PASSWORD` - Database password
- `DATA_LEDGER_API_KEY` - Data Ledger API authentication key

#### **SchemaFiles** (Validation schemas and XSLT templates)
- `hl7v2-validation-schema` - HL7v2 validation schema XML
- `hl7v2-fhir-bundle.xslt` - XSLT template for converting HL7v2 to FHIR Bundle

---

## 🔄 Processing Workflow

### Validation Workflow (`/hl7v2/Bundle/$validate`)
```
1. Receive multipart/form-data request
2. Validate required headers (X-TechBD-Tenant-ID)
3. Validate file extension (.hl7 or .txt)
4. Extract HL7 message content from multipart data
5. Validate HL7 structure (must start with "MSH|")
6. Validate line format (no lines starting with "|")
7. Load HL7v2 validation schema from Lookup Manager
8. Validate message against HL7v2 schema
9. Save validation results to database
10. Return OperationOutcome JSON
```

### Bundle Conversion Workflow (`/hl7v2/Bundle`)
```
1. Data Ledger tracking - "received" event
2. Receive multipart/form-data request
3. Validate required headers (X-TechBD-Tenant-ID, CIN, NPI/TIN, etc.)
4. Validate file extension (.hl7 or .txt)
5. Extract and validate HL7 message structure
6. Save original HL7 payload to database
7. Validate against HL7v2 schema from Lookup Manager
8. Validate OBX segments (question codes in OBX-3.1)
9. Convert to FHIR Bundle using XSLT from Lookup Manager
10. Generate SHA-256 resource IDs for FHIR resources
11. Apply custom FHIR Base URL if provided and valid
12. Handle multi-answer components in observations
13. Save converted bundle to database
14. Submit FHIR Bundle to downstream server
15. Data Ledger tracking - "sent" event
16. Return FHIR Bundle JSON
```

---

## 🔍 HL7v2 Validation Details

### Structure Validation
1. **MSH Segment Check**: Message must start with `MSH|`
2. **Line Format Check**: No lines can start with `|` (invalid pipe delimiter positioning)
3. **Schema Validation**: Validates against HL7v2 validation schema from Lookup Manager

### OBX Segment Validation
The channel performs specific validation on OBX (Observation) segments:
- **OBX-3.1 (Question Code)**: Must be present and non-empty
- **Resolution**: Version 0.1.24 resolved validation failures caused by missing OBX-3.1

### FHIR Bundle Conversion
- Uses XSLT transformation from Lookup Manager (`hl7v2-fhir-bundle.xslt`)
- Generates SHA-256 hash-based resource IDs for deterministic identifiers
- Handles multiple observation answers in components
- Supports custom FHIR base URLs via `X-TechBD-Base-FHIR-URL` header

---

## 🛡️ Security & Authentication

- **Authentication**: None (`authType=NONE`)
- **SSL**: Disabled by default
- **CORS Enabled**: Yes
  - `Access-Control-Allow-Origin`: Retrieved from Lookup Manager (`HUB_UI_URL`)
  - `Access-Control-Allow-Methods`: `GET, POST, OPTIONS`
  - `Access-Control-Allow-Headers`: Includes custom headers like `X-TechBD-*`
  - `Access-Control-Allow-Credentials`: `true`
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

**API Authentication**: Uses `DATA_LEDGER_API_KEY` from Lookup Manager for authentication.

---

## ⚠️ Error Handling

All errors are returned as `OperationOutcome` JSON:

```json
{
  "resourceType": "OperationOutcome",
  "interactionId": "<uuid>",
  "result": [{
    "severity": "error",
    "code": "invalid-msh",
    "details": {
      "text": "Error description"
    }
  }]
}
```

### Common Error Scenarios
- Missing required headers → HTTP 400
- Invalid file format (not .hl7 or .txt) → HTTP 400
- Empty file content → HTTP 400
- Invalid HL7 structure (missing MSH|) → HTTP 400 with error code `invalid-msh`
- Line format error (line starts with |) → HTTP 400 with error code `line-error`
- Schema validation failure → HTTP 400 with validation details
- Missing OBX-3.1 (Question Code) → HTTP 400
- Invalid endpoint → HTTP 404
- Invalid HTTP method → HTTP 405
- XSLT transformation errors → HTTP 400 with error details

---

## 🚀 Deployment Requirements

### BridgeLink
- **Version**: 4.6.1 or higher
- **Port**: 9006
- **Required Libraries**:
  - PostgreSQL JDBC driver
  - jOOQ library
  - HL7v2 data type processor

### System Requirements
- **Temp Directory**: No special temp directory required (unlike CCDA)
- **Memory**: Adequate heap space for XSLT transformations

### Lookup Manager Configuration
Before deploying, ensure all configuration entries exist in Lookup Manager:

1. **Create `Config` group** with:
   - `MC_FHIR_BUNDLE_SUBMISSION_API_URL`
   - `MC_VALID_FHIR_URLS`
   - `DATA_LEDGER_API_URL`
   - `DATA_LEDGER_TRACKING`
   - `HUB_UI_URL`

2. **Create `Config-sensitive` group** with:
   - `MC_JDBC_URL`
   - `MC_JDBC_USERNAME`
   - `MC_JDBC_PASSWORD`
   - `DATA_LEDGER_API_KEY`

3. **Create `SchemaFiles` group** with:
   - `hl7v2-validation-schema` (HL7v2 validation schema XML content)
   - `hl7v2-fhir-bundle.xslt` (XSLT transformation template content)

### Database Setup
- PostgreSQL database with required stored procedures
- Database connection details in Lookup Manager
- Proper user permissions for read/write operations

---

## 📝 Change Log

### Version 0.1.24
- Resolved HL7 validation failure caused by missing OBX-3.1 (Question Code) in OBX segment
- Enhanced OBX segment validation

---

## 🧪 Testing

### Health Check
```bash
curl -X GET http://localhost:9006/healthcheck
```

### Validation Only
```bash
curl -X POST http://localhost:9006/hl7v2/Bundle/$validate \
  -H "X-TechBD-Tenant-ID: test-tenant" \
  -F "file=@sample-hl7.hl7"
```

### Full Bundle Conversion
```bash
curl -X POST http://localhost:9006/hl7v2/Bundle \
  -H "X-TechBD-Tenant-ID: test-tenant" \
  -H "X-TechBD-CIN: 12345" \
  -H "X-TechBD-OrgNPI: 1234567890" \
  -H "X-TechBD-Facility-ID: FAC-001" \
  -H "X-TechBD-Encounter-Type: ambulatory" \
  -H "X-TechBD-Organization-Name: Test Org" \
  -F "file=@sample-hl7.hl7"
```

### Sample HL7v2 Message
```
MSH|^~\&|SENDING_APP|SENDING_FAC|RECEIVING_APP|RECEIVING_FAC|20240303120000||ORU^R01|MSG00001|P|2.5.1
PID|1||12345^^^MRN||DOE^JOHN||19800101|M
OBR|1||ORDER123|1234^Lab Test^LN
OBX|1|ST|12345-6^Question^LN||Answer Text||||||F
```

---

## 📚 Additional Resources

- HL7v2 Specification: [HL7 Version 2](http://www.hl7.org/implement/standards/product_brief.cfm?product_id=185)
- FHIR Specification: [HL7 FHIR R4](https://www.hl7.org/fhir/)
- BridgeLink Documentation: [Innovar BridgeLink](https://github.com/Innovar-Healthcare/BridgeLink)
- HL7 to FHIR Conversion: [FHIR HL7v2 Implementation Guide](https://build.fhir.org/ig/HL7/v2-to-fhir/)

---
