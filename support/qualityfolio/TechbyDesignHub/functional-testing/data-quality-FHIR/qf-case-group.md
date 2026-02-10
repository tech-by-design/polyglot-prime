---
id: GRP-003
SuiteId: SUT-001
planId: ["PLN-001"]
name: "Data Quality - FHIR"
description: "Data Quality FHIR test cases validate that various UI tabs and columns correctly display data and metrics related to FHIR JSON files sent to the FHIR /Bundle endpoint."
created_by: "Renjitha George"
created_at: "2025-01-23"
tags: ["functional testing"]
---

### Overview

- **Needs Attention Tab**: Verify data loads correctly, identifying transactions
  requiring review.
- **Metrics and Drill-Downs**: Ensure columns and drill-downs in Tech by Design
  and Data Lake tabs accurately display bundle counts, including sent,
  successful, failed, and discarded bundles.
- **FHIR Data Quality Tab**: Confirm validation results, such as errors,
  warnings, and informational messages, are displayed accurately.
- **IG Publication Issues and FHIR Rules Tabs**: Validate these tabs load
  properly, providing insights into implementation guide issues and applied
  validation rules.
