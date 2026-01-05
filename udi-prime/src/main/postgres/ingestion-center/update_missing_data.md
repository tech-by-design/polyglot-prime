# Update FHIR Count, CSV Validation Status and tenant_id values in various tables

This repository contains the following SQL scripts located in `udi-prime/src/main/postgres/ingestion-center/`.  
- `update_fhir_count.psql`
- `update_csv_validation_status.psql`
- `update_tenant_ids.psql`
- `update_tenant_ids_of_http_requests.psql`
- `add_partial_indexes.psql`

The purpose of this script is to 
- fill the `number_of_fhir_bundles_generated_from_zip_file` column and `data_validation_status` column into `sat_interaction_zip_file_request` table for showing the `FHIR Count` and `CSV Validation Status` in `CSV via HTTPs` page.
- fill the newly added tenant_id column in various tables

## Instructions

### Prerequisites
1. Ensure you have PostgreSQL installed.
2. You must have access to the `psql` command-line tool.
3. The target database must contain the `techbd_udi_ingress` schema and its associated tables.
4. The `sat_interaction_zip_file_request` table should be there in the `techbd_udi_ingress` schema. 

### Step 1: cd to the file location
Navigate to the directory containing the script:
```bash
cd udi-prime/src/main/postgres/ingestion-center
```

### Step 2: Execute the SQL File (update_fhir_count.psql) to fill the `number_of_fhir_bundles_generated_from_zip_file` column
Run the SQL script to fill the new column:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f update_fhir_count.psql
```

### Step 3: Execute the SQL File (update_csv_validation_status.psql) to fill the `data_validation_status` column
Run the SQL script to fill the new column:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f update_csv_validation_status.psql
```

### Step 3: Execute the SQL File (update_tenant_ids.psql) to fill the `tenant_id` column in various tables
Run the SQL script to fill the new column:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f update_tenant_ids.psql
```

### Step 3: Execute the SQL File (update_tenant_ids_of_http_requests.psql) to fill the `tenant_id` column in sat_interaction_http_request and sat_interaction_fhir_validation_issue tables
Run the SQL script to fill the new column:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f update_tenant_ids_of_http_requests.psql
```

### Step 4: Execute the SQL file to add a partial index and optimize the performance of the `get_fhir_session_diagnostics` function.
Run the SQL script to add the partial index:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f add_partial_indexes.psql
```