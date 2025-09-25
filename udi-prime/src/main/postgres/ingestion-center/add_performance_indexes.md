# Add Performance Indexes

This repository contains the SQL script `add_performance_indexes.psql` located in  
`udi-prime/src/main/postgres/ingestion-center/`.  

The purpose of this script is to create new indexes in the `techbd_udi_ingress` schema to improve query performance on frequently accessed tables and columns in the PostgreSQL database.

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

### Step 2: Execute the SQL File
Run the SQL script to create the performance indexes:
```bash
psql -h <hostname> -U <admin_user> -d <database_name> -f add_performance_indexes.psql
```