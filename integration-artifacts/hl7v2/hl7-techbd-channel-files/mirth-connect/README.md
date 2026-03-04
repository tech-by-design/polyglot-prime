# TechBD HL7 Workflow Channel

## Overview

The **TechBD HL7 Workflow** channel processes HL7v2 messages via HTTP, providing both validation and FHIR conversion capabilities. This channel accepts HL7v2 files (.hl7, .txt) as multipart/form-data uploads and routes them to different processing paths based on the endpoint URL.

## Channel Configuration

### Source Connector: HTTP Listener

- **Port**: 9006
- **Context Path**: `/`
- **Host**: 0.0.0.0
- **Transport**: HTTP Listener
- **Auth Type**: NONE
- **Response Content Type**: text/json
- **Timeout**: 30000ms (30 seconds)
- **Initial State**: STARTED
- **Message Storage Mode**: DEVELOPMENT
- **Archiving**: Enabled

### Endpoints

The channel provides two distinct endpoints:

#### 1. `/hl7v2/Bundle/$validate`
Validates HL7v2 messages against an XML conformance profile without performing FHIR conversion.

**Request:**
```bash
curl -X POST "http://localhost:9006/hl7v2/Bundle/$validate" \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: your-tenant-id" \
  -F "file=@message.hl7"
```

**Response:** Returns an OperationOutcome JSON with validation results indicating conformance status.

#### 2. `/hl7v2/Bundle`
Validates and converts HL7v2 messages to FHIR Bundle format, then submits to the FHIR Bundle submission API.

**Request:**
```bash
curl -X POST "http://localhost:9006/hl7v2/Bundle" \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: your-tenant-id" \
  -H "X-TechBD-CIN: patient-cin" \
  -H "X-TechBD-OrgNPI: organization-npi" \
  -H "X-TechBD-Facility-ID: facility-code" \
  -H "X-TechBD-Encounter-Type: encounter-type" \
  -F "file=@message.hl7"
```

**Response:** Returns the FHIR Bundle submission API response.

### Request Headers

#### Required Headers (All Endpoints)

| Header | Description | Example |
|--------|-------------|---------|
| `X-TechBD-Tenant-ID` | Tenant identifier | `tenant-123` |
| `Content-Type` | Must be `multipart/form-data` with boundary | `multipart/form-data; boundary=...` |

#### Required Headers (Bundle Endpoint Only)

| Header | Description | Example | Notes |
|--------|-------------|---------|-------|
| `X-TechBD-CIN` | Patient Customer Identification Number | `CIN-12345` | Required for FHIR Bundle conversion |
| `X-TechBD-OrgNPI` | Organization NPI | `1234567890` | Either NPI or TIN is required |
| `X-TechBD-OrgTIN` | Organization TIN | `12-3456789` | Either NPI or TIN is required |
| `X-TechBD-Facility-ID` | Facility identifier | `FAC-001` | Required for FHIR Bundle conversion |
| `X-TechBD-Encounter-Type` | Type of encounter | `office-visit` | Required for FHIR Bundle conversion |

#### Optional Headers

| Header | Description | Default | Example |
|--------|-------------|---------|---------|
| `X-TechBD-Organization-Name` | Organization name | From MSH-6 | `Example Health` |
| `X-TechBD-Validation-Severity-Level` | Validation severity threshold | `error` | `warning`, `error`, `fatal` |
| `X-TechBD-Part2` | Part 2 data flagging | `false` | `true`, `false` |
| `User-Agent` | Client user agent | N/A | `MyApp/1.0` |

### CORS Configuration

The channel is configured with CORS support:
- **Access-Control-Allow-Origin**: `*`
- **Access-Control-Allow-Methods**: `GET, POST, OPTIONS`
- **Access-Control-Allow-Headers**: Comprehensive list including all X-TechBD-* headers
- **Access-Control-Allow-Credentials**: `true`

### File Format Requirements

- **Accepted File Extensions**: `.hl7`, `.txt`
- **Content Format**: HL7v2 message starting with `MSH|` segment
- **Binary MIME Types**: `text/hl7` (regex enabled)
- **Multipart Parsing**: Enabled

## Processing Flow

### Validation Endpoint (`/$validate`)

1. **Header Validation**: Validates required headers (X-TechBD-Tenant-ID, Content-Type)
2. **File Type Check**: Ensures file has .hl7 or .txt extension
3. **HL7 Format Validation**: 
   - Verifies message starts with `MSH|`
   - Checks for lines starting with `|` (invalid format)
   - Extracts HL7 segments
4. **Conformance Profile Validation**:
   - Loads validation schema from `HL7_XSLT_PATH/hl7v2-validation-schema.xml`
   - Validates required segments (MSH, PID, PV1, etc.)
   - Validates required fields within segments
   - Applies conditional validation rules (e.g., OBX segment rules)
   - Validates organization name (header or MSH-6)
   - Validates "one-of" group rules
5. **Response**: Returns OperationOutcome with validation results

**Destination**: Channel Writer (internal routing)

### Bundle Endpoint

1. **Header Validation**: Validates all required headers including CIN, NPI/TIN, Facility ID, Encounter Type
2. **File Processing**: Same validation as `/$validate` endpoint
3. **Data Ledger Tracking** (if enabled):
   - Records interaction metadata
   - Sends to DATA_LEDGER_API_URL with tracking information
4. **HL7 to FHIR Conversion**:
   - Loads XSLT transformation stylesheet from HL7_XSLT_PATH
   - Applies transformation with parameters (CIN, NPI/TIN, Facility ID, etc.)
   - Generates SHA-256 hashes for resource IDs
   - Sets FHIR profile URLs from environment variables
   - Processes observation categories and consent resources
   - Cleans and validates generated FHIR Bundle JSON
5. **FHIR Bundle Submission**:
   - Submits converted bundle to MC_FHIR_BUNDLE_SUBMISSION_API_URL
   - Forwards tenant ID and user agent headers
   - Returns submission API response
6. **Response**: Returns FHIR Bundle submission result or conversion errors

**Destination**: HTTP Sender to MC_FHIR_BUNDLE_SUBMISSION_API_URL

## Environment Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `MC_FHIR_BUNDLE_SUBMISSION_API_URL` | Target URL for FHIR Bundle submissions | `http://localhost:9000` |
| `HL7_XSLT_PATH` | Path to HL7 XSLT transformation files and validation schema | `/opt/mirth/xslt` |
| `BASE_FHIR_URL` | Base FHIR server URL | `http://fhir.example.com/fhir` |

### FHIR Profile URLs

| Variable | Description |
|----------|-------------|
| `PROFILE_URL_BUNDLE` | FHIR Bundle profile URL |
| `PROFILE_URL_PATIENT` | FHIR Patient profile URL |
| `PROFILE_URL_ENCOUNTER` | FHIR Encounter profile URL |
| `PROFILE_URL_CONSENT` | FHIR Consent profile URL |
| `PROFILE_URL_ORGANIZATION` | FHIR Organization profile URL |
| `PROFILE_URL_OBSERVATION` | FHIR Observation profile URL |
| `PROFILE_URL_SEXUAL_ORIENTATION` | FHIR Sexual Orientation Observation profile URL |
| `PROFILE_URL_QUESTIONNAIRE` | FHIR Questionnaire profile URL |
| `PROFILE_URL_QUESTIONNAIRE_RESPONSE` | FHIR QuestionnaireResponse profile URL |
| `PROFILE_URL_PRACTITIONER` | FHIR Practitioner profile URL |
| `PROFILE_URL_PROCEDURE` | FHIR Procedure profile URL |

### Data Ledger Configuration

| Variable | Description | Required |
|----------|-------------|----------|
| `DATA_LEDGER_API_URL` | Data Ledger API endpoint | Yes (if tracking enabled) |
| `TECHBD_NYEC_DATALEDGER_API_KEY` | API key for Data Ledger authentication | Yes (if tracking enabled) |
| `DATA_LEDGER_TRACKING` | Enable/disable data ledger tracking | Optional (default: false) |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MC_VALID_FHIR_URLS` | Comma-separated list of valid FHIR URLs for validation | N/A |

## HL7v2 Validation Rules

### Segment Validation

The channel validates HL7v2 messages against a conformance profile that includes:

- **Required Segments**: MSH, PID, PV1, OBR (and others as defined in schema)
- **Segment Order**: Validated based on MessageStructure definition
- **Field Requirements**: Each segment has required and optional fields defined

### Field Validation

For each segment, the channel validates:
- **Required Fields**: Must be present and non-empty
- **Field Components**: Validates sub-components within fields
- **One-of Groups**: Ensures at least one field from a group is present
- **Data Types**: Validates field values against expected types

### Special Validation Rules

1. **OBX Segment Conditional Validation**: Special handling for OBX segments with consent-related exceptions
2. **Organization Name Validation**: Must be present in either header (`X-TechBD-Organization-Name`) or MSH-6 field
3. **NPI/TIN Validation**: At least one must be provided for Bundle endpoint
4. **MSH Segment**: Must be first segment starting with `MSH|`

## XSLT Transformation

The channel uses XSLT 2.0 for HL7v2 to FHIR conversion:

### Transformation Parameters

The following parameters are passed to the XSLT stylesheet:
- `baseFhirUrl`: Base FHIR server URL
- `patientCIN`: Patient Customer Identification Number
- `organizationNPI`: Organization NPI (if provided)
- `organizationTIN`: Organization TIN (if provided)
- `facilityID`: Facility identifier
- `encounterType`: Type of encounter
- `OrganizationName`: Organization name
- `X-TechBD-Part2`: Part 2 data flag
- All FHIR profile URLs (11 total)

### Generated Resource IDs

Resource IDs in the FHIR Bundle are generated using SHA-256 hashes of:
- Source content
- Resource name
- Additional identifying information (e.g., CIN, NPI)

### Post-Transformation Processing

1. **JSON Cleaning**: Removes empty values, null properties, and empty objects/arrays
2. **Consent Status Extraction**: Extracts consent resource status if present
3. **Observation Categories**: Processes and categorizes observation resources
4. **Multiple Answer Handling**: Processes questionnaire responses with multiple answers

## Response Formats

### Success Response (Validation)

```json
{
  "resourceType": "OperationOutcome",
  "interactionId": "uuid",
  "result": [{
    "severity": "information",
    "code": "valid",
    "details": {
      "text": "HL7 message is valid against XML conformance profile."
    }
  }]
}
```

### Error Response (Validation)

```json
{
  "resourceType": "OperationOutcome",
  "interactionId": "uuid",
  "result": [{
    "severity": "error",
    "code": "missing-segment",
    "details": {
      "text": "Missing required segment: PID at Line X: <segment content>"
    }
  }]
}
```

### Success Response (Bundle)

Returns the FHIR Bundle submission API response (typically the created/validated FHIR Bundle).

### Error Response (Bundle)

```json
{
  "status": 400,
  "message": "Bad Request: Missing required header X-TechBD-CIN"
}
```

## Error Handling

The channel implements comprehensive error handling:

1. **Missing Headers**: Returns 400 with list of missing required headers
2. **Invalid File Type**: Returns 400 for non-.hl7/.txt files
3. **Empty File**: Returns 400 if uploaded file is empty
4. **Invalid HL7 Format**: Returns OperationOutcome with specific validation errors
5. **Missing Environment Variables**: Returns 500 for missing required configuration
6. **XSLT Transformation Errors**: Returns error details with transformation failure information
7. **Data Ledger Errors**: Logs errors but continues processing

All validation failures are optionally saved to a database (if `saveHL7Payload` function is configured).

## Deployment Notes

### Channel Properties
- **Processing Threads**: 1 (source connector)
- **Thread Count**: 1 (destination connectors)
- **Queue Enabled**: false
- **Retry Count**: 0
- **Wait For Previous**: true
- **Clear Global Channel Map**: true

### Data Type Transformations
- **Source**: XML → HL7V2
- **Destination 1 (Validation)**: HL7V2 → XML
- **Destination 2 (Bundle)**: HL7V2 → XML → JSON (FHIR Bundle)

### Metadata Columns
- `SOURCE`: String field for tracking message source
- `TYPE`: String field for tracking message type

## Comparison: Validation vs. Bundle Endpoints

| Feature | /$validate | /Bundle |
|---------|-----------|---------|
| **Purpose** | Validation only | Validation + FHIR conversion + submission |
| **Required Headers** | Minimal (Tenant ID only) | Extended (CIN, NPI/TIN, Facility, Encounter) |
| **File Processing** | HL7v2 validation against schema | HL7v2 → FHIR transformation |
| **Output** | OperationOutcome (validation results) | FHIR Bundle (from submission API) |
| **Destination** | Channel Writer (internal) | HTTP Sender (external API) |
| **Data Ledger** | No | Yes (if enabled) |
| **Performance** | Faster (validation only) | Slower (includes transformation) |
| **Use Case** | Pre-submission validation | Production submission workflow |

## Usage Examples

### Example 1: Validate HL7 Message

```bash
curl -X POST "http://localhost:9006/hl7v2/Bundle/$validate" \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: tenant-001" \
  -F "file=@sample-oru.hl7"
```

### Example 2: Submit HL7 for FHIR Conversion

```bash
curl -X POST "http://localhost:9006/hl7v2/Bundle" \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: tenant-001" \
  -H "X-TechBD-CIN: PT123456" \
  -H "X-TechBD-OrgNPI: 1234567890" \
  -H "X-TechBD-Facility-ID: FAC-MAIN" \
  -H "X-TechBD-Encounter-Type: ambulatory" \
  -H "X-TechBD-Organization-Name: Memorial Hospital" \
  -F "file=@admission-message.hl7"
```

### Example 3: With Optional Headers

```bash
curl -X POST "http://localhost:9006/hl7v2/Bundle" \
  -H "Content-Type: multipart/form-data" \
  -H "X-TechBD-Tenant-ID: tenant-001" \
  -H "X-TechBD-CIN: PT123456" \
  -H "X-TechBD-OrgNPI: 1234567890" \
  -H "X-TechBD-Facility-ID: FAC-MAIN" \
  -H "X-TechBD-Encounter-Type: ambulatory" \
  -H "X-TechBD-Validation-Severity-Level: warning" \
  -H "X-TechBD-Part2: true" \
  -H "User-Agent: HealthIntegrationApp/2.0" \
  -F "file=@patient-data.hl7"
```

## Troubleshooting

### Common Issues

1. **"Missing required header X-TechBD-Tenant-ID"**
   - Ensure the X-TechBD-Tenant-ID header is included in all requests

2. **"Content-Type must be 'multipart/form-data' with boundary details"**
   - Use proper multipart/form-data encoding with file upload
   - Most HTTP clients (curl, Postman) handle this automatically with `-F` flag

3. **"Not a valid HL7 message. HL7 must start with 'MSH|'"**
   - Verify the file content starts with MSH| segment
   - Check for hidden characters or BOM at file start

4. **"Invalid file format: Only .hl7 and .txt files are allowed"**
   - Rename file with .hl7 or .txt extension
   - Ensure filename is properly specified in multipart form

5. **"Missing required header X-TechBD-OrgNPI and X-TechBD-OrgTIN. One is mandatory."**
   - Bundle endpoint requires either NPI or TIN (or both)
   - Add at least one of these headers

6. **Environment variable errors (500 status)**
   - Verify all required environment variables are set in Mirth Connect
   - Check HL7_XSLT_PATH points to valid directory with transformation files
   - Confirm all PROFILE_URL_* variables are configured

### Logging

The channel logs detailed information at various stages:
- Request URL and headers
- File validation results
- Segment extraction and validation
- XSLT transformation progress
- Data Ledger API calls
- Destination responses

Check Mirth Connect logs for detailed diagnostic information.