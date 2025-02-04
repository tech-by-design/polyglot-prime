---
id: GRP-005
SuiteId: SUT-001
planId: ["PLN-001"]
name: "Interactions - CSV"
description: "Interaction CSV test cases focus on verifying various aspects of interactions and functionalities related to sending CSV Zip files to Flatfile Validate and Bundle endpoints."
created_by: "Renjitha George"
created_at: "2025-01-23"
tags: ["functional testing"]
---

### Overview

- **Interaction Rows**: Validate the correct number of interaction rows with
  consistent interaction IDs.
- **Payload Display**: Ensure payloads are correctly displayed for all CSV
  interactions
- **Disposition States**: Validate techByDesignDisposition as "accept" for valid
  files.
- **Metrics**: Ensure correct display of File Count, FHIR Count, FHIR Success
  Count, and FHIR Failed Count in the CSV via HTTP tab.
