# To get the InteractIons for a given date range

This repository contains the following SQL script located in `support/database/`.  
- `get_ccds_with_multi_screening_date_mismatch.psql`
- `get_incomplete_ccda_submissions.psql`
- `get_incomplete_csv_submissions.psql`
- `get_incomplete_fhir_submissions.psql`
- `get_incomplete_hl7_submissions.psql`
  
## Instructions

### Prerequisites
1. Ensure you have PostgreSQL installed.
2. You must have access to the `psql` command-line tool.
3. The target database must contain the `techbd_udi_ingress` schema and its associated tables.
4. The target tables should be there in the `techbd_udi_ingress` schema.

### Step 1: cd to the file location
Navigate to the directory containing the script:
```bash
cd support/database
```

### Step 2: Execute the psql File to find CCDs that would have had multiple screenings come in and effectiveDateTime mismatch from the previously sent bundle
```bash
psql \
  -h <hostname> \
  -U <admin_user> \
  -d <database_name> \
  -v start_date="'<start_date>'" \
  -v end_date="'<end_date>'" \
  -f get_ccds_with_multi_screening_date_mismatch.psql
```

### Step 2: Execute the psql File to get all the incomplete CCDA submissions with full details including which nature is missing till the complete FHIR conversion process.
```bash
psql \
  -h <hostname> \
  -U <admin_user> \
  -d <database_name> \
  -v start_date="'<start_date>'" \
  -v end_date="'<end_date>'" \
  -f get_incomplete_ccda_submissions.psql
```

### Step 2: Execute the psql File to get all the incomplete CSV submissions with full details including which nature is missing till the complete FHIR conversion process.
```bash
psql \
  -h <hostname> \
  -U <admin_user> \
  -d <database_name> \
  -v start_date="'<start_date>'" \
  -v end_date="'<end_date>'" \
  -f get_incomplete_csv_submissions.psql
```

### Step 2: Execute the psql File to get all the incomplete FHIR submissions with full details including which nature is missing.
```bash
psql \
  -h <hostname> \
  -U <admin_user> \
  -d <database_name> \
  -v start_date="'<start_date>'" \
  -v end_date="'<end_date>'" \
  -f get_incomplete_fhir_submissions.psql
```

### Step 2: Execute the psql File to get all the incomplete HL7 submissions with full details including which nature is missing till the complete FHIR conversion process.
```bash
psql \
  -h <hostname> \
  -U <admin_user> \
  -d <database_name> \
  -v start_date="'<start_date>'" \
  -v end_date="'<end_date>'" \
  -f get_incomplete_hl7_submissions.psql
```