#!/usr/bin/env -S deno run --allow-all

/**
 * This TypeScript file implements a SQL migration feature for PostgreSQL databases using Deno.
 * It provides methods for defining and executing migrations.
 *
 * @module Information_Schema_Lifecycle_Management_Migration
 */


import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
import { pgSQLa } from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/pgdcp/deps.ts";
import * as migrate from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/postgres/migrate.ts";
import * as uuid from "https://deno.land/std@0.209.0/uuid/mod.ts";
// import * as migrate from "../../../../../../../netspective-labs/sql-aide/pattern/postgres/migrate.ts";

// deconstructed modules provide convenient access to internal imports
const { typical: { SQLa, ws } } = dvp;


type EmitContext = dvp.typical.SQLa.SqlEmitContext;

const ingressSchema = SQLa.sqlSchemaDefn("techbd_udi_ingress", {
  isIdempotent: true,
});

export const dvts = dvp.dataVaultTemplateState<EmitContext>({
  defaultNS: ingressSchema,
});


const islmTestDependencies = [
  "../../../../test/postgres/ingestion-center/islm/000-idempotent-islm-unit-test.psql",
  "../../../../test/postgres/ingestion-center/islm/islm.pgtap.psql",
] as const;

// Define a namespace UUID (you can choose any UUID)
const namespace = uuid.v1.generate().toString();

const migrationInput:migrate.MigrationVersion = {
  version: "update-table1",
  dateTime: new Date(2024, 7, 4, 9, 55, 45),
};

const ctx = SQLa.typicalSqlEmitContext({
  sqlDialect: SQLa.postgreSqlDialect(),
});

/**
 * Defines the SQL schema for an "info_schema_lifecycle" table.
 *
 * @type {Object}
 */

const infoSchemaLifecycle = SQLa.sqlSchemaDefn("info_schema_lifecycle", {
  isIdempotent: true,
});

/**
 * Initializes the PostgreSQL migration object.
 *
 * @type {Object}
 */
const PgMigrateObj = migrate.PgMigrate.init(
  () => ctx,
  infoSchemaLifecycle.sqlNamespace,
);


/**
 * Creates a migration procedure for PostgreSQL.
 *
 * @type {Object}
 */

const formattedDate = PgMigrateObj.formatDateToCustomString(
  migrationInput.dateTime,
);
export const migrateVersion = "V" + migrationInput.version + "_" + formattedDate;
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

             ALTER TABLE techbd_udi_ingress.sat_exception_diagnostics ADD COLUMN elaboration JSONB;

              
        `,
    (args) =>
      pgSQLa.typedPlPgSqlBody("", args, ctx)`
          -- Add any PostgreSQL you need either manually constructed or SQLa.
          -- Your code will be placed automatically into a ISLM rollback stored procedure.
          -- DROP table if exists "sample_schema".sample_table1;
             ALTER TABLE techbd_udi_ingress.sat_exception_diagnostics DROP COLUMN IF EXISTS elaboration;
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
            WHERE table_name = 'sat_exception_diagnostics' 
            AND column_name = 'elaboration'
          ) THEN
            status := 1; -- Set status to 1 (already executed)
          END IF;

          RETURN status; -- Return the status
        `,
  );

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


/**
 * Generates SQL Data Definition Language (DDL) for the migrations.
 *
 * @returns {string} The SQL DDL for migrations.
 */

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

/**
 * Generates SQL Data Definition Language (DDL) for the migrations.
 *
 * @returns {string} The SQL DDL for migrations.
 */

function sqlDDLGenerateMigration() {
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
    
    ${createMigrationProcedure.migrateSP}

    ${createMigrationProcedure.rollbackSP}

    ${createMigrationProcedure.statusFn}

    ${islmGovernanceInsertion}

    

    `;
}

/**
 * Generates SQL Data Definition Language (DDL) for the migrate.
 *
 * @returns {string} The SQL DDL for migrate.
 */

function sqlDDLMigrate() {
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`    
    

    CALL ${PgMigrateObj.infoSchemaLifecycle.sqlNamespace}.${PgMigrateObj.content().spIslmMigrateSP.routineName}('migrate','${migrateVersion}');

    `;
}

/**
 * Generates SQL Data Definition Language (DDL) for the rollback.
 *
 * @returns {string} The SQL DDL for rollback.
 */

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

  // after this execution `ctx` will contain list of all tables which will be
  // passed into `dvts.pumlERD` below (ctx should only be used once)
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

