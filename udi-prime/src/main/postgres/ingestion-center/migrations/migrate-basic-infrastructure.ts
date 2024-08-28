#!/usr/bin/env -S deno run --allow-all

/**
 * This TypeScript file implements a SQL migration feature for PostgreSQL databases using Deno.
 * It provides methods for defining and executing migrations.
 *
 * @module Information_Schema_Lifecycle_Management_Migration
 */

import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
import { pgSQLa } from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/pgdcp/deps.ts";
// import * as migrate from "../../../../../../../../netspective-labs/sql-aide/pattern/postgres/migrate.ts";

// deconstructed modules provide convenient access to internal imports
const { typical: typ, typical: { SQLa, ws } } = dvp;

type EmitContext = dvp.typical.SQLa.SqlEmitContext;

interface MigrationVersion {
  readonly description: string;
  readonly dateTime: Date;
}

enum StateStatus {
  STATEFUL = "_stateful_",
  IDEMPOTENT = "_idempotent_",
}

const prependMigrateSPText = "migrate_";
const appendMigrateUndoSPText = "_undo";
const appendMigrateStatusFnText = "_status";

const ingressSchema = SQLa.sqlSchemaDefn("techbd_udi_ingress", {
  isIdempotent: true,
});

const assuranceSchema = SQLa.sqlSchemaDefn("techbd_udi_assurance", {
  isIdempotent: true,
});

const orchCtlSchema = SQLa.sqlSchemaDefn("techbd_orch_ctl", {
  isIdempotent: true,
});

const diagnosticsSchema = SQLa.sqlSchemaDefn("techbd_udi_diagnostics", {
  isIdempotent: true,
});
export const dvts = dvp.dataVaultTemplateState<EmitContext>({
  defaultNS: ingressSchema,
});
const {
  text,
  jsonbNullable,
  jsonB,
  boolean,
  textNullable,
  dateTime,
  dateTimeNullable,
  integer
} = dvts.domains;
const { ulidPrimaryKey: primaryKey } = dvts.keys;

const interactionHub = dvts.hubTable("interaction", {
  hub_interaction_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

const interactionHttpRequestSat = interactionHub.satelliteTable(
  "http_request",
  {
    sat_interaction_http_request_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    nature: jsonbNullable(),
    content_type: textNullable(),
    payload: jsonB,
    from_state: textNullable(),
    to_state: textNullable(),
    state_transition_reason: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

enum EnumFileExchangeProtocol {
  SFTP = "SFTP",
  S3 = "S3",
}

const fileExchangeProtocol = typ.textEnumTable(
  "file_exchange_protocol",
  EnumFileExchangeProtocol,
  { isIdempotent: true, sqlNS: ingressSchema },
);

const interactionfileExchangeSat = interactionHub.satelliteTable(
  "file_exchange",
  {
    sat_interaction_file_exchange_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    protocol: fileExchangeProtocol.references.code(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const hubExpectation = dvts.hubTable("expectation", {
  hub_expectation_id: primaryKey(),
  key: textNullable(),
  ...dvts.housekeeping.columns,
});

const expectationHttpRequestSat = hubExpectation.satelliteTable(
  "http_request",
  {
    sat_expectation_http_request_id: primaryKey(),
    hub_expectation_id: hubExpectation.references.hub_expectation_id(),
    content_type: textNullable(), // eg: Permission Denied to the file
    payload: jsonB,
    ...dvts.housekeeping.columns,
  },
);

const hubDiagnostics = dvts.hubTable("diagnostic", {
  hub_diagnostic_id: primaryKey(),
  key: textNullable(),
  ...dvts.housekeeping.columns,
});

const diagnosticsSat = hubDiagnostics.satelliteTable(
  "log",
  {
    sat_diagnostic_log_id: primaryKey(),
    hub_diagnostic_id: hubDiagnostics.references.hub_diagnostic_id(),
    diagnostic_log_level: text(), 
    diagnostic_log_message: text(),
    user_id: text(),
    status: text(),
    parent_diagnostic_log_id: textNullable(),
    hierarchy_level: integer().default(0),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const exceptionDiagnosticSat = hubDiagnostics.satelliteTable(
  "exception",
  {
    sat_diagnostic_exception_id: primaryKey(),
    hub_diagnostic_id: hubDiagnostics.references.hub_diagnostic_id(),
    exception_type: text(),
    message: text(), // eg: Permission Denied to the file
    err_returned_sqlstate: textNullable(),
    err_pg_exception_detail: textNullable(),
    err_pg_exception_hint: textNullable(),
    err_pg_exception_context: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const pgTapFixturesJSON = SQLa.tableDefinition("pgtap_fixtures_json", {
  name: textNullable(),
  jsonb: jsonbNullable(),
}, {
  isIdempotent: true,
  sqlNS: assuranceSchema,
  constraints: (props, tableName) => {
    const c = SQLa.tableConstraints(tableName, props);
    return [
      c.unique("name"),
    ];
  },
});

// Function to read SQL from a list of .psql files
async function readSQLFiles(filePaths: readonly string[]): Promise<string[]> {
  const sqlContents = [];
  for (const filePath of filePaths) {
    try {
      const absolutePath = new URL(filePath, import.meta.url).pathname;
      const data = await Deno.readTextFile(absolutePath);
      sqlContents.push(data);
    } catch (err) {
      console.error(`Error reading file ${filePath}:`, err);
      throw err;
    }
  }
  return sqlContents;
}

// List of dependencies and test dependencies
const dependencies = [
  "../000_idempotent_universal.psql",
  "../001_idempotent_interaction.psql",
  "../002_idempotent_diagnostics.psql",
  "../003_idempotent_migration.psql",
] as const;

const testMigrateDependencies = [
  "../../../../test/postgres/ingestion-center/001-idempotent-interaction-unit-test.psql",
  "../../../../test/postgres/ingestion-center/003-idempotent-interaction-view-explain-plan.psql",
  "../../../../test/postgres/ingestion-center/004-idempotent-migrate-unit-test.psql",
  "../../../../test/postgres/ingestion-center/fixtures.sql",
] as const;

// Read SQL queries from files
const dependenciesSQL = await readSQLFiles(dependencies);
const testDependenciesSQL = await readSQLFiles(testMigrateDependencies);
const testMigrateDependenciesWithPgtap = [
  ...testMigrateDependencies,
  "../../../../test/postgres/ingestion-center/suite.pgtap.psql",
] as const;

const ctx = SQLa.typicalSqlEmitContext({
  sqlDialect: SQLa.postgreSqlDialect(),
});

const infoSchemaLifecycle = SQLa.sqlSchemaDefn("info_schema_lifecycle", {
  isIdempotent: true,
});

const searchPathAssurance = pgSQLa.pgSearchPath<
  typeof assuranceSchema.sqlNamespace,
  EmitContext
>(
  assuranceSchema,
);

const pgTapTestResult = SQLa.tableDefinition("pgtap_test_result", {
  migration_version: text(),
  test_name: text(),
  tap_output: textNullable(),
  success: boolean(),
  ...dvts.housekeeping.columns,
}, {
  isIdempotent: true,
  sqlNS: assuranceSchema,
});

export const migrationInput: MigrationVersion = {
  description: "basic-infra",
  dateTime: new Date(2024, 6, 28, 13, 16),
};
function formatDateToCustomString(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth()).padStart(2, "0"); // Month is zero-based
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");

  return `${year}_${month}_${day}_${hours}_${minutes}`;
}

export const migrateVersion = formatDateToCustomString(migrationInput.dateTime);

const migrateSP = pgSQLa.storedProcedure(
  prependMigrateSPText + "v" + migrateVersion + StateStatus.IDEMPOTENT +
    migrationInput.description,
  {},
  (name, args, _) =>
    pgSQLa.typedPlPgSqlBody(name, args, ctx, {
      autoBeginEnd: false,
    }),
  {
    embeddedStsOptions: SQLa.typicalSqlTextSupplierOptions(),
    autoBeginEnd: false,
    isIdempotent: true,
    sqlNS: infoSchemaLifecycle,
    headerBodySeparator: "$migrateVersionSP$",
  },
)`
    BEGIN


      ${ingressSchema}

      CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA ${ingressSchema.sqlNamespace};

      ${assuranceSchema}

      ${orchCtlSchema}

      ${diagnosticsSchema}

      ${hubDiagnostics}

      ${diagnosticsSat}

      ${exceptionDiagnosticSat}

      ${interactionHub}

      ${interactionHttpRequestSat}

      ${fileExchangeProtocol}

      BEGIN
        ${fileExchangeProtocol.seedDML}
      EXCEPTION
          WHEN unique_violation THEN
              RAISE NOTICE 'Enum already exists. Insert skipped.';
      END;

      ${interactionfileExchangeSat}

      ${hubExpectation}

      ${expectationHttpRequestSat}

      ${pgTapFixturesJSON}

      ${pgTapTestResult}

        -- Check and add 'nature_denorm' column if it does not exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                      WHERE table_name='sat_interaction_http_request' 
                      AND column_name='nature_denorm') THEN
            ALTER TABLE techbd_udi_ingress.sat_interaction_http_request
            ADD COLUMN nature_denorm TEXT DEFAULT null;
        END IF;

        -- Check and add 'tenant_id_denorm' column if it does not exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                      WHERE table_name='sat_interaction_http_request' 
                      AND column_name='tenant_id_denorm') THEN
            ALTER TABLE techbd_udi_ingress.sat_interaction_http_request
            ADD COLUMN tenant_id_denorm TEXT DEFAULT null;
        END IF;

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_hub_interaction_id_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING btree (hub_interaction_id);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_nature_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING gin (nature);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_from_state_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING btree (from_state);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_to_state_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING btree (to_state);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_created_at_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING btree (created_at);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_provenance_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING btree (provenance);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_jsonb_extracted_payload_idx 
      ON techbd_udi_ingress.sat_interaction_http_request USING GIN (payload);

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_jsonb_extracted_nature_idx 
      ON techbd_udi_ingress.sat_interaction_http_request 
      USING btree ((payload->>'nature'));

      CREATE INDEX IF NOT EXISTS sat_interaction_http_request_jsonb_extracted_tenant_id_idx 
      ON techbd_udi_ingress.sat_interaction_http_request 
      USING btree ((payload->'nature'->>'tenant_id'));

      ANALYZE techbd_udi_ingress.sat_interaction_http_request;
      
      
      ${dependenciesSQL}

      CREATE EXTENSION IF NOT EXISTS pgtap SCHEMA ${assuranceSchema.sqlNamespace};
      
      ${testDependenciesSQL}

      ${searchPathAssurance}
      DECLARE
        tap_op TEXT := '';
        test_result BOOLEAN;
        line TEXT;
      BEGIN
          PERFORM * FROM ${assuranceSchema.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_http_request');
          test_result = TRUE;

          FOR line IN
              SELECT * FROM ${assuranceSchema.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_http_request')
          LOOP
              tap_op := tap_op || line || E'\n';

              IF line LIKE 'not ok%' THEN
                  test_result := FALSE;
              END IF;
          END LOOP;


          -- Insert the test result into the test_results table
          INSERT INTO ${assuranceSchema.sqlNamespace}.${pgTapTestResult.tableName} (migration_version, test_name, tap_output, success, created_by, provenance)
          VALUES ('${migrateVersion}', 'test_register_interaction_http_request', tap_op, test_result, 'ADMIN', 'pgtap');

          -- Check if the test passed
          IF NOT test_result THEN
              RAISE EXCEPTION 'Test failed: %', 'test_register_interaction_http_request';
          END IF;
      EXCEPTION
          -- Handle the specific error
          WHEN others THEN
              -- Raise an error with a custom message
              RAISE EXCEPTION 'Error occurred while executing runtests: %', SQLERRM;
      END;
    END
  `;

const rollbackSP = pgSQLa.storedProcedure(
  prependMigrateSPText + "v" + migrateVersion + StateStatus.IDEMPOTENT +
    migrationInput.description + appendMigrateUndoSPText,
  {},
  (name, args, _) =>
    pgSQLa.typedPlPgSqlBody(name, args, ctx, {
      autoBeginEnd: false,
    }),
  {
    embeddedStsOptions: SQLa.typicalSqlTextSupplierOptions(),
    autoBeginEnd: false,
    isIdempotent: true,
    sqlNS: infoSchemaLifecycle,
    headerBodySeparator: "$migrateVersionUSP$",
  },
)`
    BEGIN
    -- Add any PostgreSQL you need either manually constructed or SQLa.
    -- Your code will be placed automatically into a ISLM rollback stored procedure.
    -- DROP table if exists "sample_schema".sample_table1;
    END
  `;
const statusFn = pgSQLa.storedFunction(
  prependMigrateSPText + "v" + migrateVersion + StateStatus.IDEMPOTENT +
    migrationInput.description + appendMigrateStatusFnText,
  {},
  "integer",
  (name, args) =>
    pgSQLa.typedPlPgSqlBody(name, args, ctx, {
      autoBeginEnd: false,
    }),
  {
    embeddedStsOptions: SQLa.typicalSqlTextSupplierOptions(),
    autoBeginEnd: false,
    isIdempotent: true,
    sqlNS: infoSchemaLifecycle,
    headerBodySeparator: "$fnMigrateVersionStatus$",
  },
)`
    DECLARE
      status INTEGER := 0; -- Initialize status to 0 (not executed)
    BEGIN
      -- Add any PostgreSQL you need either manually constructed or SQLa.
      -- Your code will be placed automatically into a ISLM status stored function.
      -- All your checks must be idempotent and not have any side effects.
      -- Use information_schema and other introspection capabilities of PostgreSQL
      -- instead of manually checking. For example:
      
      -- IF EXISTS (
      --  SELECT FROM information_schema.columns
      --  WHERE table_name = 'sample_table1'
      -- ) THEN
      --  status := 1; -- Set status to 1 (already executed)
      -- END IF;
      RETURN status; -- Return the status
              
    END;
  `;

/**
 * Generates SQL Data Definition Language (DDL) for the migrations.
 *
 * @returns {string} The SQL DDL for migrations.
 */

function sqlDDLGenerateMigration() {
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
    \\set ON_ERROR_STOP on
    DO $$
      DECLARE
        table_count int;
        schema_count int;
      BEGIN

        -- Check if the required schemas exist
        SELECT count(*)
        INTO schema_count
        FROM information_schema.schemata
        WHERE schema_name IN ('info_schema_lifecycle', 'info_schema_lifecycle_assurance', 'techbd_orch_ctl');

        -- If less than 3 schemas are found, raise an error
        IF schema_count < 3 THEN
            RAISE EXCEPTION 'One or more of the required schemas info_schema_lifecycle, info_schema_lifecycle_assurance, techbd_orch_ctl are missing';
        END IF;

        -- Check if the required tables exist in the specified schema
        SELECT count(*)
        INTO table_count
        FROM information_schema.tables
        WHERE table_schema = 'techbd_orch_ctl'
        AND table_name IN ('business_rules', 'demographic_data', 'device', 'orch_session', 'orch_session_entry', 'orch_session_exec', 'orch_session_issue', 'orch_session_state', 'qe_admin_data', 'screening');

        -- If less than 10 tables are found, raise an error
        IF table_count < 10 THEN
            RAISE EXCEPTION 'One or more required tables are missing in schema techbd_orch_ctl';
        END IF;
      END;
      $$;

    
    ${migrateSP}

    ${rollbackSP}

    ${statusFn}

    `;
}

export function generated() {
  const ctx = SQLa.typicalSqlEmitContext({
    sqlDialect: SQLa.postgreSqlDialect(),
  });
  const testDependencies: string[] = [];
  for (const filePath of testMigrateDependenciesWithPgtap) {
    try {
      const absolutePath = import.meta.resolve(filePath);
      testDependencies.push(absolutePath);
    } catch (err) {
      console.error(`Error reading filepath ${filePath}:`, err);
      throw err;
    }
  }

  // after this execution `ctx` will contain list of all tables which will be
  // passed into `dvts.pumlERD` below (ctx should only be used once)
  const driverGenerateMigrationSQL = ws.unindentWhitespace(
    sqlDDLGenerateMigration().SQL(ctx),
  );
  return {
    driverGenerateMigrationSQL,
    pumlERD: dvts.pumlERD(ctx).content,
    destroySQL: ws.unindentWhitespace(`

      DROP SCHEMA IF EXISTS public CASCADE;

      DROP SCHEMA IF EXISTS ${ingressSchema.sqlNamespace} cascade;
      DROP SCHEMA IF EXISTS ${assuranceSchema.sqlNamespace} cascade;
      DROP SCHEMA IF EXISTS ${diagnosticsSchema.sqlNamespace} cascade;

      DROP PROCEDURE IF EXISTS "${migrateSP.sqlNS?.sqlNamespace}"."${migrateSP.routineName}" CASCADE;
      DROP PROCEDURE IF EXISTS "${rollbackSP.sqlNS?.sqlNamespace}"."${rollbackSP.routineName}" CASCADE;
      DROP FUNCTION IF EXISTS "${statusFn.sqlNS?.sqlNamespace}"."${statusFn.routineName}" CASCADE;


      `),
    testDependencies,
  };
}
