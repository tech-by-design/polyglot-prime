---
id: GRP-007
SuiteId: SUT-001
planId: ["PLN-001"]
name: "Content - CSV"
description: "Content CSV testcases ensures that the relevant tabs for screenings, patients, organizations, and data tracking load accurately with data when a CSV Zip file is sent to the Flatfile Bundle endpoint."
created_by: "Renjitha George"
created_at: "2025-01-23"
tags: ["functional testing"]
---

### Overview

- **Screenings Tab**: Verify that the tab loads with screening data and displays
  screening details when a CSV Zip file is sent.
- **Patients Tab**: Ensure that the Patients tab loads with data and the
  patient's screening details are displayed correctly.
- **Organizations Tab**: Confirm that the Organizations tab loads with data and
  the screening details for the patient are displayed under the respective
  organization.
- **SCN Tab**: Verify that the SCN tab loads with relevant data when a CSV Zip
  file is sent.
- **HRSN Data Tracker Tab**: Ensure that the HRSN Data Tracker tab loads with
  data when the CSV Zip file is processed.
