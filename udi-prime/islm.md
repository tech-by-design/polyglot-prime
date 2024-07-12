# Information Schema Lifecycle Manager (ISLM) PostgreSQL-native Schema Migration System

## Overview

The Information Schema Lifecycle Manager (ISLM) is a PostgreSQL-native schema migration system designed to manage and automate database schema changes. This document outlines the process for setting up and using ISLM for schema migrations, including the creation of necessary schema procedures and functions. The SQL scripts provided here are generated mainly from `sql-aide`.

## Introduction

ISLM aims to encapsulate schema migration logic within PostgreSQL stored procedures, providing a robust and manageable approach to database schema evolution. ISLM will operate within a dedicated schema named `info_schema_lifecycle` with a centralized controller procedure named `islm_migrate_ctl` orchestrating the migrations based on passed arguments.

## Analysis of Alternatives

### Flyway

#### Versioned Migrations
- **Description:** Each migration has a version, a description, and a checksum.
- **Languages:** Migrations can be written in SQL (with database-specific syntax) or Java (for advanced data transformations or dealing with LOBs).

#### Checksum Validation
- **Description:** Every applied migration has its checksum stored in the database.
- **Benefit:** Allows Flyway to validate on startup that the migrations applied to the database match the ones available locally.

#### Repair Functionality
- **Description:** Flyway provides a repair command that will correct the schema history table if needed.

#### Callbacks
- **Description:** Custom operations can be performed before/after each migration, or before/after all migrations.

#### Configuration Options
- **Description:** Extensive configuration options available through files, environment variables, and command-line arguments.

#### Cross-Team Development
- **Description:** Supports working in a team environment with branching and merging.

### Liquibase

#### XML, YAML, JSON, and SQL Formats
- **Description:** Changelog files can be written in various formats including XML, YAML, JSON, and SQL.

#### Database-Agnostic Syntax
- **Description:** Offers a database-agnostic syntax for changesets, which are translated into database-specific SQL.

#### Changeset Execution
- **Description:** Changesets can include preconditions, rollback code, and can be executed in transactions.

#### Database Refactoring
- **Description:** Ability to manage a sequence of changes to the database schema, including complex refactoring.

#### Command Line and Maven/Gradle Integration
- **Description:** Offers command-line tools and integrates with Maven and Gradle.

### ISLM

#### Simplicity
- **Description:** ISLM is simple and straightforward to use.
- **Benefit:** Instead of creating an external strategy for PostgreSQL, ISLM does everything inside Postgres.



**Flyway** is well-suited for projects needing versioned migrations, checksum validation, and extensive configuration options, particularly in Java-based environments.
**Liquibase** is ideal for projects requiring a database-agnostic approach, multiple changelog formats, and integration with build tools like Maven and Gradle.
**ISLM** is best for teams looking for a straightforward solution that operates entirely within PostgreSQL without the need for external tools.


## System Architecture

### Database Setup

#### Schema

Establish a dedicated schema `info_schema_lifecycle` to house all migration logic and metadata.

#### Metadata Table

Create a table named `islm_governance` within `info_schema_lifecycle` schema.

#### Stored Procedures

Each migration will be represented by a unique version encapsulated within three stored procedures: `migrate_<version>`, `migration_<version>_undo`, and `migration_<version>_status`.

### Migration Versioning

Versioning naming convention: ` V<arbitrary_version>_YYYYMMDDHHMMSS` where:

- `arbitrary_version` can be any text valid as a stored procedure name.
- `YYYYMMDD_HHMMSS` represents the timestamp when the migration was created.

### Migration Format

Every migration will be represented by three stored procedures encapsulated in a .psql file.

## Core Components

### Initialization Procedure

The `islm_init` procedure checks for the existence of the metadata schema and table, creating them if necessary.

### Migration Controller

The `islm_migrate_ctl` procedure orchestrates the execution of migrations to reach the target schema version based on the provided arguments.

### Validation Functionality

PgTAP-based validation integrated within each migration stored procedure to ensure successful migration execution apart from the pgTAP-based validation for the ISLM infrastructure.

### Repair Functionality

Procedures to manage and correct inconsistencies in the `islm_governance` table.

## Migration Execution Flow

### Initialization

Execute `islm_init` to ensure the necessary schema and metadata table exist with proper validations in pgTAP.

### Migration Identification

Identify pending migrations by comparing entries in the `islm_governance` table with the migration procedures.

### Execution

Execute the corresponding `migrate_<version>` procedure for each pending migration, recording the result in the `islm_governance` table provided that `migration_<version>_status` returns 0 indicating the migration hasn't been run.

### Validation

PgTAP-based validation within each migration stored procedure to verify execution status.

### Error Handling

Log errors, halt the process, and optionally roll back to a previous stable state using the `migration_<version>_undo` procedures.

## Setup Instructions

### Step 1: Create Schema 

Create a schema called `info_schema_lifecycle` if it does not already exist.

```sql
CREATE SCHEMA IF NOT EXISTS "info_schema_lifecycle";
```

### Step 2: Create Extension

Ensure the `uuid-ossp` extension is created within the `info_schema_lifecycle` schema.

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA "info_schema_lifecycle";
```

### Step 3: Initialize ISLM

Create a stored procedure `islm_init` to initialize the governance table `islm_governance`.

```sql
CREATE OR REPLACE PROCEDURE "info_schema_lifecycle"."islm_init"() AS $$
BEGIN
  CREATE TABLE IF NOT EXISTS "info_schema_lifecycle"."islm_governance" (
     "islm_governance_id" TEXT PRIMARY KEY NOT NULL,
      "migrate_version" TEXT NOT NULL,
      "sp_migration" TEXT NOT NULL,
      "sp_migration_undo" TEXT NOT NULL,
      "fn_migration_status" TEXT NOT NULL,
      "from_state" TEXT NOT NULL,
      "to_state" TEXT NOT NULL,
      "transition_result" JSONB NOT NULL,
      "transition_reason" TEXT NOT NULL,
      "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
      "created_by" TEXT DEFAULT 'UNKNOWN',
      "updated_at" TIMESTAMPTZ,
      "updated_by" TEXT,
      "deleted_at" TIMESTAMPTZ,
      "deleted_by" TEXT,
      "activity_log" JSONB,
      UNIQUE("sp_migration", "from_state", "to_state")
  );
EXCEPTION
  WHEN duplicate_table THEN
    NULL;
END;
$$ LANGUAGE PLPGSQL;
```

### Step 4: Migration Control Procedure

Create a stored procedure `islm_migrate_ctl` to handle migration tasks (migrate or rollback).

```sql
CREATE OR REPLACE PROCEDURE "info_schema_lifecycle"."islm_migrate_ctl"("task" TEXT, "target_version" TEXT) AS $$
BEGIN
  DECLARE
    r RECORD;
    t TEXT;
    extracted_ts TEXT;
  BEGIN
    CASE task
    WHEN 'migrate' THEN
      FOR r IN (
        SELECT sp_migration,sp_migration_undo,fn_migration_status FROM info_schema_lifecycle.islm_governance WHERE from_state = 'None' AND to_state = 'SQL Loaded' AND (migrate_version NOT IN (SELECT migrate_version FROM info_schema_lifecycle.islm_governance WHERE from_state = 'SQL Loaded' AND to_state = 'Migrated')) AND
        (migrate_version IS NULL OR to_timestamp(substring(migrate_version FROM '(\d{14})$'), 'YYYYMMDDHH24MISS')::timestamp<=to_timestamp(substring(target_version FROM '(\d{14})$'), 'YYYYMMDDHH24MISS')::timestamp)
        ORDER BY migrate_version
      ) LOOP
        -- Check if migration has been executed
        -- Construct procedure and status function names
          DECLARE
            procedure_name TEXT := format('info_schema_lifecycle."%s"()', r.sp_migration);
            procedure_undo_name TEXT := format('info_schema_lifecycle."%s"()', r.sp_migration_undo);
            status_function_name TEXT := format('info_schema_lifecycle."%s"()', r.fn_migration_status);
            islm_governance_id TEXT:= info_schema_lifecycle.uuid_generate_v4();
            status INT;
            migrate_insertion_sql TEXT;
          BEGIN
            -- Check if migration has been executed
            --EXECUTE SELECT (status_function_name)::INT INTO status;
            EXECUTE 'SELECT ' || status_function_name INTO status;
  
            IF status = 0 THEN
              -- Call the migration procedure
              EXECUTE  'call ' || procedure_name;
  
              -- Insert into the governance table
              migrate_insertion_sql := $dynSQL$
                INSERT INTO info_schema_lifecycle.islm_governance ("islm_governance_id","migrate_version", "sp_migration", "sp_migration_undo", "fn_migration_status", "from_state", "to_state", "transition_result", "transition_reason") VALUES ($1, $2, $3, $4, $5, 'SQL Loaded', 'Migrated', '{}', 'Migration') ON CONFLICT DO NOTHING
              $dynSQL$;
              EXECUTE migrate_insertion_sql USING islm_governance_id, target_version, r.sp_migration, r.sp_migration_undo, r.fn_migration_status;
            END IF;
          END;
      END LOOP;
    WHEN 'rollback' THEN
    -- Implement rollback logic here...
    -- Construct procedure names
      DECLARE
        migrate_rb_insertion_sql TEXT;
        procedure_name text;
        procedure_undo_name text;
        status_function_name text;
        islm_governance_id text;
        sp_migration_undo_sql RECORD;
      BEGIN
        SELECT sp_migration,sp_migration_undo,fn_migration_status FROM info_schema_lifecycle.islm_governance WHERE from_state = 'SQL Loaded' AND to_state = 'Migrated' AND migrate_version=target_version AND (target_version IN (SELECT migrate_version FROM info_schema_lifecycle.islm_governance WHERE from_state = 'SQL Loaded' AND to_state = 'Migrated' ORDER BY migrate_version DESC LIMIT 1))  INTO sp_migration_undo_sql;
        IF sp_migration_undo_sql IS NOT NULL THEN
          procedure_name := format('info_schema_lifecycle."%s"()', sp_migration_undo_sql.sp_migration);
          procedure_undo_name := format('info_schema_lifecycle."%s"()', sp_migration_undo_sql.sp_migration_undo);
          status_function_name := format('info_schema_lifecycle."%s"()', sp_migration_undo_sql.fn_migration_status);
          islm_governance_id := info_schema_lifecycle.uuid_generate_v4();
          EXECUTE  'call ' || procedure_undo_name;
  
          -- Insert the governance table
          migrate_rb_insertion_sql := $dynSQL$
                    INSERT INTO info_schema_lifecycle.islm_governance ("islm_governance_id","migrate_version", "sp_migration", "sp_migration_undo", "fn_migration_status", "from_state", "to_state", "transition_result", "transition_reason") VALUES ($1, $2, $3, $4, $5, 'Migrated', 'Rollback', '{}', 'Rollback for migration') ON CONFLICT DO NOTHING
  
                  $dynSQL$;
          EXECUTE migrate_rb_insertion_sql USING islm_governance_id, target_version, sp_migration_undo_sql.sp_migration, sp_migration_undo_sql.sp_migration_undo, sp_migration_undo_sql.fn_migration_status;
        ELSE
          RAISE EXCEPTION 'Cannot perform a rollback for this version';
        END IF;
      END;
    ELSE
      RAISE EXCEPTION 'Unknown task: %', task;
    END CASE;
  
  END;
  
END;
$$ LANGUAGE PLPGSQL;
```

### Step 5: Create Migration Procedures

Create stored procedures for each migration and their corresponding undo procedures.

```sql
CREATE OR REPLACE PROCEDURE "info_schema_lifecycle"."migrate_Vupdate-table1_20240704095545"() AS $migrateVersionSP$
BEGIN
  IF info_schema_lifecycle."migration_Vupdate-table1_20240704095545_status"() = 0 THEN
        DECLARE
        islm_test_status BOOLEAN;
    BEGIN                 
        SELECT success INTO islm_test_status FROM info_schema_lifecycle.pgtap_tests_result ORDER BY id DESC LIMIT 1;

        IF NOT islm_test_status THEN
            RAISE EXCEPTION 'ISLM testcase is failed. Migration not allowed.';
        END IF;
    END;
-- Add any PostgreSQL you need either manually constructed or SQLa.
-- Your code will be placed automatically into a ISLM migration stored procedure.
-- Use SQLa or Atlas for any code that you need. For example:

   ALTER TABLE diagnostics ADD COLUMN elaboration JSONB;

    
        
  END IF;
END

$migrateVersionSP$ LANGUAGE PLPGSQL;
 
CREATE OR REPLACE PROCEDURE "info_schema_lifecycle"."migration_Vupdate-table1_20240704095545_undo"() AS $migrateVersionUndo$
BEGIN
  -- Add any PostgreSQL you need either manually constructed or SQLa.
  -- Your code will be placed automatically into a ISLM rollback stored procedure.
  -- DROP table if exists "sample_schema".sample_table1;
     ALTER TABLE diagnostics DROP COLUMN IF EXISTS elaboration;
          
END;
$migrateVersionUndo$ LANGUAGE PLPGSQL;
```

### Step 6: Create Migration Status Functions

Create functions to check the status of migrations.

```sql
CREATE OR REPLACE FUNCTION "info_schema_lifecycle"."migration_Vupdate-table1_20240704095545_status"() RETURNS integer AS $fnMigrateVersionStatus$
DECLARE
  status INTEGER := 0; -- Initialize status to 0 (not executed)
BEGIN
  -- Add any PostgreSQL you need either manually constructed or SQLa.
  -- Your code will be placed automatically into a ISLM status stored function.
  -- All your checks must be idempotent and not have any side effects.
  -- Use information_schema and other introspection capabilities of PostgreSQL
  -- instead of manually checking. For example:
  
  IF EXISTS (
    SELECT column_name FROM information_schema.columns 
    WHERE table_name = 'diagnostics' 
    AND column_name = 'elaboration'
  ) THEN
    status := 1; -- Set status to 1 (already executed)
  END IF;
  
  RETURN status; -- Return the status
          
END;

$fnMigrateVersionStatus$ LANGUAGE PLPGSQL;
```

### Step 7: Insert Initial Governance Record

Insert an initial record into the `islm_governance` table.

```sql
CALL "info_schema_lifecycle".islm_init();
INSERT INTO "info_schema_lifecycle"."islm_governance" ("islm_governance_id", "migrate_version", "sp_migration", "sp_migration_undo", "fn_migration_status", "from_state", "to_state", "transition_result", "transition_reason", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "deleted_by", "activity_log") VALUES ('9c2be3c0-4053-11ef-ac78-755dd19247df', 'Vupdate-table1_20240704095545', 'migrate_Vupdate-table1_20240704095545', 'migration_Vupdate-table1_20240704095545_undo', 'migration_Vupdate-table1_20240704095545_status', 'None', 'SQL Loaded', '{}', 'SQL load for migration', (CURRENT_TIMESTAMP), 'Admin', NULL, NULL, NULL, NULL, NULL) ON CONFLICT DO NOTHING;
```

### Step 8: Execute Migration

Call the `islm_migrate_ctl` procedure to execute the migration.

```sql
CALL "info_schema_lifecycle".islm_migrate_ctl('migrate', 'Vupdate-table1_20240704095545');
```

### Step 8: Execute Rollback

Call the `islm_migrate_ctl` procedure to execute the rollback.

```sql
CALL "info_schema_lifecycle".islm_migrate_ctl('rollback', 'Vupdate-table1_20240704095545');
```

## Use of SQLAide for generating the required SQL for migration

1. Import the necessary modules and define a `PgMigrateObj` object:

    ```typescript
      import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
      import { pgSQLa } from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/pgdcp/deps.ts";
      import * as migrate from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/postgres/migrate.ts";
      import * as uuid from "https://deno.land/std@0.209.0/uuid/mod.ts";

      const namespace = uuid.v1.generate().toString();

      const migrationInput:migrate.MigrationVersion = {
          version: "update-table1",
          dateTime: new Date(2024, 7, 4, 9, 55, 45),
        };
      const infoSchemaLifecycle = SQLa.sqlSchemaDefn("info_schema_lifecycle", {
          isIdempotent: true,
        });
        const ctx = SQLa.typicalSqlEmitContext({
          sqlDialect: SQLa.postgreSqlDialect(),
        });

      const PgMigrateObj = migrate.PgMigrate.init(
          () => ctx,
          infoSchemaLifecycle.sqlNamespace,
        );

        const islmTestDependencies = [
          "path/to/islm/testcases/islm.pgtap.psql",
        ] as const;
    ```

2. Create the version in the specified format:
    ```typescript
    const formattedDate = PgMigrateObj.formatDateToCustomString(
      migrationInput.dateTime,
    );
    export const migrateVersion = "V" + migrationInput.version + "_" + formattedDate;
    ```

3. Define the migration using the `migrationScaffold` method. Specify the
   migration version, arguments, and migration SQL script:

   ```typescript
   const createMigrationProcedure = PgMigrateObj
    .migrationScaffold(
      migrationInput,
      {},
      (args) =>
        pgSQLa.typedPlPgSqlBody(
          "",
          args,
          ctx,
          { autoBeginEnd: false },
        )`
            -- Check if the pgTAP test cases which was run for the ISLM infrastructure code was successful
                DECLARE
                    islm_test_status BOOLEAN;
                BEGIN                 
                    SELECT success INTO islm_test_status FROM ${infoSchemaLifecycle.sqlNamespace}.pgtap_tests_result ORDER BY id DESC LIMIT 1;

                    IF NOT islm_test_status THEN
                        RAISE EXCEPTION 'ISLM testcase is failed. Migration not allowed.';
                    END IF;
                END;
            -- Add any PostgreSQL you need either manually constructed or SQLa.
            -- Your code will be placed automatically into a ISLM migration stored procedure.
            -- Use SQLa or Atlas for any code that you need. For example:

              ALTER TABLE diagnostics ADD COLUMN elaboration JSONB;

                
          `,
      (args) =>
        pgSQLa.typedPlPgSqlBody("", args, ctx)`
            -- Add any PostgreSQL you need either manually constructed or SQLa.
            -- Your code will be placed automatically into a ISLM rollback stored procedure.
            -- DROP table if exists "sample_schema".sample_table1;
              ALTER TABLE diagnostics DROP COLUMN IF EXISTS elaboration;
          `,
      (args) =>
        pgSQLa.typedPlPgSqlBody("", args, ctx)`
            -- Add any PostgreSQL you need either manually constructed or SQLa.
            -- Your code will be placed automatically into a ISLM status stored function.
            -- All your checks must be idempotent and not have any side effects.
            -- Use information_schema and other introspection capabilities of PostgreSQL
            -- instead of manually checking. For example:

            IF EXISTS (
              SELECT column_name FROM information_schema.columns 
              WHERE table_name = 'diagnostics' 
              AND column_name = 'elaboration'
            ) THEN
              status := 1; -- Set status to 1 (already executed)
            END IF;

            RETURN status; -- Return the status
          `,
    );
   ```

3. Customize the migration as needed, including defining rollback and status
   functions above.

4. Create the initial insert statement for loading the migration script

   ```typescript
   const islmGovernanceInsertion = PgMigrateObj.content()
    .islmGovernance.insertDML([
      {
        islm_governance_id: namespace,
        migrate_version: migrateVersion,
        sp_migration: PgMigrateObj.prependMigrateSPText + migrateVersion,
        sp_migration_undo: PgMigrateObj.prependMigrationSPText + migrateVersion +
          PgMigrateObj.appendMigrationUndoSPText,
        fn_migration_status: PgMigrateObj.prependMigrationSPText +
          migrateVersion +
          PgMigrateObj.appendMigrationStatusFnText,
        from_state: migrate.TransitionStatus.NONE,
        to_state: migrate.TransitionStatus.SQLLOADED,
        transition_reason: "SQL load for migration",
        transition_result: "{}",
        created_at: PgMigrateObj.sqlEngineNow,
        created_by: "Admin",
      },
    ], {
      onConflict: {
        SQL: () => `ON CONFLICT DO NOTHING`,
      },
      sqlNS:infoSchemaLifecycle
    });

   ```

5. Create and export a SQL Data Definition Language (DDL) function to generate
   SQL for the migrations:

   ```typescript
    function sqlDDLGenerateIslm() {
      return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
        
        ${PgMigrateObj.infoSchemaLifecycle}

        DROP EXTENSION IF EXISTS "uuid-ossp" CASCADE;

        ${PgMigrateObj.content().extn}

        ${PgMigrateObj.content().spIslmGovernance}

        ${PgMigrateObj.content().spIslmMigrateSP}

        CALL ${PgMigrateObj.infoSchemaLifecycle.sqlNamespace}.${PgMigrateObj.content().spIslmGovernance.routineName}();
        

        `;
    }

    function sqlDDLGenerateMigration() {
      return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
        
        ${createMigrationProcedure.migrateSP}

        ${createMigrationProcedure.rollbackSP}

        ${createMigrationProcedure.statusFn}

        ${islmGovernanceInsertion}

        

        `;
    }

    function sqlDDLMigrate() {
      return SQLa.SQL<EmitContext>(dvts.ddlOptions)`    
        

        CALL ${PgMigrateObj.infoSchemaLifecycle.sqlNamespace}.${PgMigrateObj.content().spIslmMigrateSP.routineName}('migrate','${migrateVersion}');

        `;
    }

    function sqlDDLRollback() {
      return SQLa.SQL<EmitContext>(dvts.ddlOptions)`    
        

        CALL ${PgMigrateObj.infoSchemaLifecycle.sqlNamespace}.${PgMigrateObj.content().spIslmMigrateSP.routineName}('rollback','${migrateVersion}');

        `;
    }

    export function generated() {
      const ctx = SQLa.typicalSqlEmitContext({
        sqlDialect: SQLa.postgreSqlDialect(),
      });
      const testDependencies:string[] = [];
      for (const filePath of islmTestDependencies) {
        try {
          const absolutePath = import.meta.resolve(filePath);
          testDependencies.push(absolutePath);
        } catch (err) {
          console.error(`Error reading filepath ${filePath}:`, err);
          throw err;
        }
      }

      const driverGenerateIslmSQL = ws.unindentWhitespace(sqlDDLGenerateIslm().SQL(ctx));
      const driverGenerateMigrationSQL = ws.unindentWhitespace(sqlDDLGenerateMigration().SQL(ctx));
      const driverMigrateSQL = ws.unindentWhitespace(sqlDDLMigrate().SQL(ctx));
      const driverRollbackSQL = ws.unindentWhitespace(sqlDDLRollback().SQL(ctx));
      return {
        driverGenerateIslmSQL,
        driverGenerateMigrationSQL,
        driverMigrateSQL,
        driverRollbackSQL,
        pumlERD: dvts.pumlERD(ctx).content,
        testDependencies
      };
    }
   ```


## Conclusion

The Information Schema Lifecycle Manager (ISLM) offers a structured PostgreSQL-native avenue to manage schema migrations. By employing idempotency checks and PgTAP for validation, along with handling migration logic through dedicated stored procedures, ISLM ensures reliable migration execution and robust error handling. This design promotes a maintainable and trustworthy database evolution process.


