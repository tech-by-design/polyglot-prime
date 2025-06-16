#!/usr/bin/env -S deno run --allow-all

/**
 * This TypeScript file implements a SQL migration feature for PostgreSQL databases using Deno.
 * It provides methods for defining and executing migrations.
 *
 * @module Information_Schema_Lifecycle_Management_Migration
 */


import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
import { pgSQLa } from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/pgdcp/deps.ts";
import * as ddlTable from "./models-dv.ts";
// import * as migrate from "../../../../../../../../netspective-labs/sql-aide/pattern/postgres/migrate.ts";

// deconstructed modules provide convenient access to internal imports
const { typical: { SQLa, ws } } = dvp;

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

const ingressSchema = SQLa.sqlSchemaDefn("techbd_udi_ingress", {
  isIdempotent: true,
});

const assuranceSchema = SQLa.sqlSchemaDefn("techbd_udi_assurance", {
  isIdempotent: true,
});

const diagnosticsSchema = SQLa.sqlSchemaDefn("techbd_udi_diagnostics", {
  isIdempotent: true,
});

const dvts = dvp.dataVaultTemplateState<EmitContext>({
  defaultNS: ingressSchema,
});



const testMigrateDependenciesWithPgtap = [
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


export const migrationInput: MigrationVersion = {
  description: "ddl-table",
  dateTime: new Date(2024, 7, 30, 14, 52),
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

      ${diagnosticsSchema}

      ${ddlTable.interactionHub}

      ${ddlTable.fhirBundleHub}      

      ${ddlTable.uniformResourceHub}

      ${ddlTable.uniformResourceSat}

      ${ddlTable.uniformResourceFhirSat}

      ${ddlTable.uniformResourceFhirBundleSat}

      ${ddlTable.fhirBundleResourcePatientSat}

      ${ddlTable.fhirBundleResourceObservationSat}

      ${ddlTable.fhirBundleResourceServiceRequestSat}

      ${ddlTable.fhirBundleResourceDiagnosisSat}

      ${ddlTable.fhirBundleResourceEncounterSat}

      ${ddlTable.fhirBundleMetaDataSat}

      ${ddlTable.fhirResourceMetaDataSat}

      ${ddlTable.uniformResourceFhirBundleLink}

      ${ddlTable.interactionHttpRequestSat}

      ${ddlTable.fileExchangeProtocol}

      BEGIN
        ${ddlTable.fileExchangeProtocol.seedDML}
      EXCEPTION
          WHEN unique_violation THEN
              RAISE NOTICE 'Enum already exists. Insert skipped.';
      END;

      ${ddlTable.interactionfileExchangeSat}

      ${ddlTable.hubException}

      ${ddlTable.exceptionDiagnosticSat}

      ${ddlTable.pgTapFixturesJSON}

      ${ddlTable.pgTapTestResult}      
      
      CREATE EXTENSION IF NOT EXISTS pgtap SCHEMA ${assuranceSchema.sqlNamespace};
      

      ${searchPathAssurance}
      DECLARE
        tap_op TEXT := '';
        test_result BOOLEAN;
        line TEXT;
      BEGIN
          PERFORM * FROM ${assuranceSchema.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_requests');
          test_result = TRUE;

          FOR line IN
              SELECT * FROM ${assuranceSchema.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_requests')
          LOOP
              tap_op := tap_op || line || E'\n';

              IF line LIKE 'not ok%' THEN
                  test_result := FALSE;
              END IF;
          END LOOP;


          -- Insert the test result into the test_results table
          INSERT INTO ${assuranceSchema.sqlNamespace}.${ddlTable.pgTapTestResult.tableName} (migration_version, test_name, tap_output, success, created_by, provenance)
          VALUES ('${migrateVersion}', 'test_register_interaction_requests', tap_op, test_result, 'ADMIN', 'pgtap');

          -- Check if the test passed
          IF NOT test_result THEN
              RAISE EXCEPTION 'Test failed: %', 'test_register_interaction_requests';
          END IF;
      EXCEPTION
          -- Handle the specific error
          WHEN others THEN
              -- Raise an error with a custom message
              RAISE EXCEPTION 'Error occurred while executing runtests: %', SQLERRM;
      END;
    END
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


      `),
    testDependencies,
  };
}
