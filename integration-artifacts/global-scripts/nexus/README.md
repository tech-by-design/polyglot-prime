# BridgeLink Global Script Configuration

This global script defines shared logic and utilities that apply across all deployed BridgeLink (Mirth Connect) channels. It initializes reusable services, implements logging, metadata handling, header validation, and provides FHIR and CSV processing helpers for consistent behavior across the platform.

---

## ⚙️ Lifecycle Hooks

### 🟢 `Deploy`
Executed once during application start or redeployment.

- **Initializes and stores in `globalMap`**:
  - `fhirService`, `csvService`, `ccdaService` from SpringContext
  - Jackson `ObjectMapper` with JavaTime support
  - Logging functions: `logInfo`, `logError`, `logDebug`
  - Utilities: `convertMapToJson`, `processFHIRBundle`, `processCSVZipFile`, `getRequestParameters`, `getHeaderParameters`, `createProvenanceHeader`, `validate`

### 🔴 `Undeploy`
Executed once on channel undeploy. Currently contains a `return;` statement only.

### 🟡 `Preprocessor`
Runs before every message is processed. For each message:
- Skips processing for:
  - `AwsSqsFifoQueueListener`
  - `MLLP-Client-Arjun`
  - `MLLP - Client`
- Otherwise:
  - Assigns unique `interactionId`
  - Captures start time
  - Extracts headers from the source map
  - Adds `requestParameters` and `headerParameters` to `channelMap`

### 🟣 `Postprocessor`
Runs after every message is processed. Logs completion duration unless the channel is `AwsSqsFifoQueueListener`.

---

## 🔧 Global Services & Utilities

### ✅ Logging Utilities
Available in `globalMap`:
- `logInfo(message, channelMap)`
- `logError(message, channelMap)`
- `logDebug(message, channelMap)`

All logs include:
- `channelId`
- `channelName`
- `interactionId`

---

## 📦 Global Functions

### `getRequestParameters(interactionId, channelMap, sourceMap)`
Extracts and constructs a parameter map with:
- `REQUEST_URI`, `INTERACTION_ID`, `ORIGIN`, `SOURCE_TYPE`
- Constants like `DELETE_USER_SESSION_COOKIE`, `IMMEDIATE`
- Adds timestamp: `OBSERVABILITY_METRIC_INTERACTION_START_TIME`

### `getHeaderParameters(headers, channelMap, sourceMap)`
Builds a `HashMap` of relevant headers:
- Includes keys like `USER_AGENT`, `TENANT_ID`, `CORRELATION_ID`, etc.
- Auto-generates a provenance header using `createProvenanceHeader(...)`

### `createProvenanceHeader(sourceMap, channelMap)`
Generates a JSON header capturing:
- HTTP metadata (method, URI, headers)
- Channel metadata (name, ID, start time, etc.)

### `validate(...)`
General-purpose validator for request headers:
- Rules supported: `isRequired`, `isAllowedValue`, `isAValidUrl`, `isValidUUID`
- Logs errors and adds structured error to `responseMap`

### `convertMapToJson(map)`
Serializes a Java `Map` into a pretty-printed JSON string using Jackson.

### `processFHIRBundle(tenantId, channelMap, connectorMessageOrString, responseMap)`
- Uses `fhirService.processBundle(...)` to validate FHIR Bundles.
- Accepts raw string or connector message.
- Combines request and header params for orchestration.

### `processCSVZipFile(channelMap, sourceMap, connectorMessage, responseMap, processType)`
Handles multipart-encoded ZIP uploads:
- Extracts filename and bytes from form field `name="file"`
- Wraps file in `SimpleMultipartFile`
- Depending on `processType`:
  - `"validate"` → `csvService.validateCsvFile(...)`
  - `"bundle"` → `csvService.processZipFile(...)`

---

## 🛡 Dependencies

- Relies on the following Spring Beans:
  - `FHIRService`, `CsvService`, `CCDAService`
- Java classes used:
  - `ObjectMapper`, `JavaTimeModule`
  - `SimpleMultipartFile` (for CSV ZIP handling)
  - `UUID`, `Date`, `HashMap`
  - `java.net.URL` (for URL validation)

---

## ✅ Best Practices

- All channel-specific scripts should reuse `fhirService`, `csvService`, etc., from `globalMap` to avoid memory leaks and performance issues.
- Always validate headers using `validate(...)` to enforce request consistency.
- Logging should use the provided `logInfo`, `logError`, `logDebug` to ensure traceability via `interactionId`.

---
