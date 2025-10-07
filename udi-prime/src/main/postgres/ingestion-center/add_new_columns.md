# Add new columns in various tables

This repository contains the following SQL scripts located in `udi-prime/src/main/postgres/ingestion-center/`.  
- `add_new_columns.psql`

The purpose of this script is to to add new columns in various tables

## Instructions

### Prerequisites
1. Ensure you have PostgreSQL installed.
2. You must have access to the `psql` command-line tool.
3. The target database must contain the `techbd_udi_ingress` schema and its associated tables. 

### Step 1: cd to the file location
Navigate to the directory containing the script:
```bash
cd udi-prime/src/main/postgres/ingestion-center
```

### Step 2: Execute the SQL File (add_new_columns.psql) to add new columns
Run the SQL script to fill the new column:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f add_new_columns.psql
```