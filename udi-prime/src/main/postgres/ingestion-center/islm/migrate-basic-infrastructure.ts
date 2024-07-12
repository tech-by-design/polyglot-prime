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
import * as modCnstrnt from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/render/ddl/table/mod.ts";
import * as uuid from "https://deno.land/std@0.209.0/uuid/mod.ts";
// import * as migrate from "../../../../../../../../netspective-labs/sql-aide/pattern/postgres/migrate.ts";

// deconstructed modules provide convenient access to internal imports
const { typical: typ, typical: { SQLa, ws } } = dvp;


type EmitContext = dvp.typical.SQLa.SqlEmitContext;

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
  serial
} = dvts.domains;
const { ulidPrimaryKey: primaryKey } = dvts.keys;

const sessionIdentifierType = SQLa.sqlTypeDefinition("session_identifier", {
  hub_session_id: text(),
  hub_session_entry_id: text(),
}, {
  embeddedStsOptions: dvts.ddlOptions,
  sqlNS: ingressSchema,
});

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
  {
    indexes: (props, tableName) => {
      const tif = modCnstrnt.tableIndexesFactory(tableName, props);
      return [
        tif.index(
          {
            indexIdentity: "sat_interaction_http_request_hub_interaction_id_idx",
            isUnique: false,
            isIdempotent: true,
            indexMethod:"btree"
          },
          "hub_interaction_id",
        ),
        tif.index(
          {
            indexIdentity: "sat_interaction_http_request_created_at_idx",
            isUnique: false,
            isIdempotent: true,
            indexMethod:"btree"
          },
          "created_at",
        ),
      ];
    },
  }
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

const hubException = dvts.hubTable("exception", {
  hub_exception_id: primaryKey(),
  key: textNullable(),
  ...dvts.housekeeping.columns,
});

const exceptionDiagnosticSat = hubException.satelliteTable(
  "diagnostics",
  {
    sat_exception_diagnostics_id: primaryKey(),
    hub_exception_id: hubException.references.hub_exception_id(),
    message: text(), // eg: Permission Denied to the file
    err_returned_sqlstate: text(),
    err_pg_exception_detail: text(),
    err_pg_exception_hint: text(),
    err_pg_exception_context: text(),
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
] as const;

const testDependencies = [
  "../../../../test/postgres/ingestion-center/001-idempotent-interaction-unit-test.psql",
  "../../../../test/postgres/ingestion-center/003-idempotent-interaction-view-explain-plan.psql",
  "../../../../test/postgres/ingestion-center/fixtures.sql",
] as const;


const islmTestDependencies = [
  "../../../../test/postgres/ingestion-center/islm/000-idempotent-islm-unit-test.psql",
  "../../../../test/postgres/ingestion-center/islm/islm.pgtap.psql",
] as const;

// Read SQL queries from files
const dependenciesSQL = await readSQLFiles(dependencies);
const testDependenciesSQL = await readSQLFiles(testDependencies);

// Define a namespace UUID (you can choose any UUID)
const namespace = uuid.v1.generate().toString();

const migrationInput:migrate.MigrationVersion = {
  version: "basic-infrastructure",
  dateTime: new Date(2024, 6, 28, 13, 16, 45),
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

const searchPathISL = pgSQLa.pgSearchPath<
  typeof infoSchemaLifecycle.sqlNamespace,
  EmitContext
>(
  infoSchemaLifecycle,
);

const pgTapIslmTestResult = SQLa.tableDefinition("pgtap_tests_result", {
  id: serial(),
  test_name: text(),
  tap_output: textNullable(),
  success: boolean(),
  ...dvts.housekeeping.columns,
}, {
  isIdempotent: true,
  sqlNS: infoSchemaLifecycle,
});

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

              DROP SCHEMA IF EXISTS public CASCADE;

              DROP SCHEMA IF EXISTS ${ingressSchema.sqlNamespace} cascade;
              DROP SCHEMA IF EXISTS ${assuranceSchema.sqlNamespace} cascade;

              ${ingressSchema}

              CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA ${ingressSchema.sqlNamespace};

              ${assuranceSchema}

              ${orchCtlSchema}

              ${diagnosticsSchema}

              ${sessionIdentifierType}

              ${interactionHub}

              ${interactionHttpRequestSat}

              ${fileExchangeProtocol}

              ${fileExchangeProtocol.seedDML}

              ${interactionfileExchangeSat}

              ${hubException}

              ${exceptionDiagnosticSat}

              ${pgTapFixturesJSON}

              ${pgTapTestResult}

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

              ${searchPathISL}
              DECLARE
                tap_op TEXT := '';
                test_result BOOLEAN;
                line TEXT;
              BEGIN
                  PERFORM * FROM ${infoSchemaLifecycle.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_http_request');
                  test_result = TRUE;

                  FOR line IN
                      SELECT * FROM ${infoSchemaLifecycle.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_http_request')
                  LOOP
                      tap_op := tap_op || line || E'\n';

                      IF line LIKE 'not ok%' THEN
                          test_result := FALSE;
                      END IF;
                  END LOOP;


                  -- Insert the test result into the test_results table
                  INSERT INTO ${assuranceSchema.sqlNamespace}.${pgTapTestResult.tableName} (migration_version, test_name, tap_output, success, created_by, provenance)
                  VALUES ('${PgMigrateObj.prependMigrationSPText + migrateVersion}', 'test_register_interaction_http_request', tap_op, test_result, 'ADMIN', 'pgtap');

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
        `,
    (args) =>
      pgSQLa.typedPlPgSqlBody("", args, ctx)`
          -- Add any PostgreSQL you need either manually constructed or SQLa.
          -- Your code will be placed automatically into a ISLM rollback stored procedure.
          -- DROP table if exists "sample_schema".sample_table1;
        `,
    (args) =>
      pgSQLa.typedPlPgSqlBody("", args, ctx)`
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

    ${pgTapIslmTestResult}

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

