---
id: GRP-002
SuiteId: SUT-001
planId: ["PLN-001"]
name: "Interactions - FHIR"
description: "Interaction FHIR test cases focus on verifying various aspects of interactions and functionalities related to sending FHIR JSON files to FHIR Validate and Bundle endpoints."
created_by: "Renjitha George"
created_at: "2025-01-23"
tags: ["functional testing"]
---

### Overview

- **Tab Verification**: Confirm that critical tabs like FHIR via HTTPS, FAILED,
  HTTP Interactions, Performance Overview, Provenance, and User Sessions load
  data accurately.
- **Interaction Rows**: Validate the correct number of interaction rows (e.g.,
  five for /Bundle, one for /Bundle/$validate) with consistent interaction IDs.
- **Payload Display**: Ensure payloads are correctly displayed for all
  interactions
- **Disposition States**: Verify techByDesignDisposition values (accept, reject,
  discard) based on input conditions like incorrect lastUpdated date or
  truncated files.
- **Filters and Sorting**: Confirm that filters and sorting features function
  correctly.
- **Error Handling**: Validate error-related interactions in the FAILED tab,
  ensuring appropriate payloads for Forwarded HTTP Response Error.
