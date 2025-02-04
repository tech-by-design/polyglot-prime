---
id: GRP-009
SuiteId: SUT-001
planId: ["PLN-001"]
name: "Data Quality - SFTP"
description: "Data Quality SFTP test cases validate that various UI tabs and columns correctly display data and metrics related to CSV Zip files sent to the Flatfile Bundle endpoint via SFTP."
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
- **CSV Data Quality Tab**: Ensure this tab loads properly to show relevant data
  quality insights for the CSV files submitted.
- **File Not Processed Tab**: Confirm that this tab displays a detailed overview
  of files within the submitted CSV zip archive that could not be processed,
  especially when there are file format issues.
- **Incomplete Groups Tab**: Verify that errors related to incomplete file
  groups (i.e., missing files) are displayed correctly.
- **Data Integrity Errors Tab**: Ensure that any errors related to the integrity
  of data within the submitted CSV file (such as incorrect data formats) are
  displayed in the Data Integrity Errors tab.
