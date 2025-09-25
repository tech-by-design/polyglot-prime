# Denormalize the validation errors

This repository contains the SQL scripts `denormalize_validation_errors.psql` and `denormalize_data_integrity_errors.psql` located in `udi-prime/src/main/postgres/ingestion-center/`.  

The purpose of these scripts is to fill the `file not processed`, `incomplete groups` and `data_integrity` errors into `sat_csv_fhir_processing_errors` table for interactions that have already been ingested, in order to improve query performance.

## Instructions

### Prerequisites
1. Ensure you have PostgreSQL installed.
2. You must have access to the `psql` command-line tool.
3. The target database must contain the `techbd_udi_ingress` schema and its associated tables.
4. The `sat_csv_fhir_processing_errors` table should be there in the `techbd_udi_ingress` schema. 

### Step 1: cd to the file location
Navigate to the directory containing the script:
```bash
cd udi-prime/src/main/postgres/ingestion-center
```

### Step 2: Execute the SQL File (denormalize_validation_errors.psql) to insert `file not processed` and `incomplete groups` errors
Run the SQL script to fill the error details into the table:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f denormalize_validation_errors.psql
```

### Step 3: Execute the SQL File (denormalize_data_integrity_errors.psql) to insert `data_integrity` errors
Run the SQL script to fill the error details into the table:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f denormalize_data_integrity_errors.psql
```