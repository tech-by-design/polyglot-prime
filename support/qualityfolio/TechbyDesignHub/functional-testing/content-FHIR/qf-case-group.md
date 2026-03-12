---
id: GRP-004
SuiteId: SUT-001
planId: ["PLN-001"]
name: "Content - FHIR"
description: "Content FHIR testcases ensures that the relevant tabs for screenings, patients, organizations, and data tracking load accurately with data when a FHIR JSON file is sent to the /Bundle endpoint."
created_by: "Renjitha George"
created_at: "2025-01-23"
tags: ["functional testing"]
---

### Overview

- **Screenings Tab**: Verify that the tab loads with screening data and displays
  screening details when a FHIR JSON is sent.
- **Patients Tab**: Ensure that the Patients tab loads with data and the
  patient's screening details are displayed correctly.
- **Organizations Tab**: Confirm that the Organizations tab loads with data and
  the screening details for the patient are displayed under the respective
  organization.
- **SCN Tab**: Verify that the SCN tab loads with relevant data when a FHIR JSON
  is sent.
- **HRSN Data Tracker Tab**: Ensure that the HRSN Data Tracker tab loads with
  data when the FHIR JSON file is processed.
