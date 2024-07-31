#!/usr/bin/env -S deno run --allow-all

/**
 * This TypeScript file implements a SQL migration feature for PostgreSQL databases using Deno.
 * It provides methods for defining and executing migrations.
 *
 * @module Information_Schema_Lifecycle_Management_Migration
 */

import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
import { pgSQLa } from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/pgdcp/deps.ts";
import * as tmpl from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/render/emit/mod.ts";
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

const ctx = SQLa.typicalSqlEmitContext({
  sqlDialect: SQLa.postgreSqlDialect(),
});


const prependMigrateSPText = "migrate_";

const ingressSchema = SQLa.sqlSchemaDefn("techbd_udi_ingress", {
  isIdempotent: true,
});
export const dvts = dvp.dataVaultTemplateState<EmitContext>({
  defaultNS: ingressSchema,
});
export const {
  text,
  jsonbNullable,
  jsonB,
  boolean,
  textNullable,
  serial,
  date,
  jsonText,
  createdAt
} = dvts.domains;

/*
 * The following function, techbd_udi_ingress.hub_fhir_bundle_upserted, is designed to insert a record into the hub_fhir_bundle table
*/
const fnHubFhirBundleUpsertBuilder = pgSQLa
  .storedRoutineBuilder(
    "hub_fhir_bundle_upserted",
    {
      fhir_bundle_key: text(),
      created_by: textNullable(),
      provenance: textNullable(),
      created_at: createdAt(),
      upsert_behavior: boolean().default(true),
    },
  );
export const fnHubFhirBundleUpsert = pgSQLa.storedFunction(
  fnHubFhirBundleUpsertBuilder.routineName,
  fnHubFhirBundleUpsertBuilder.argsDefn,
  "TEXT",
  (name, args) => pgSQLa.typedPlPgSqlBody(name, args, ctx),
  {
    embeddedStsOptions: tmpl.typicalSqlTextSupplierOptions(),
    autoBeginEnd: false,
    isIdempotent: true,
    sqlNS: ingressSchema,
    headerBodySeparator: "$fnHubFhirBundleUpsert$",
  },
)`
    declare
        hub_request_http_client_row techbd_udi_ingress.hub_interaction%ROWTYPE;
        hub_exception_row 			techbd_udi_ingress.hub_exception;
        err_returned_sqlstate 		text;	-- Variable to store SQLSTATE of the error
        err_message_text 			text;	-- Variable to store the message text of the error
        err_pg_exception_detail 	text;	-- Variable to store the detail of the error
        err_pg_exception_hint 		text;	-- Variable to store the hint of the error
        err_pg_exception_context 	text; 	-- Variable to store the context of the error
        
        v_error_msg TEXT;
        v_sqlstate TEXT;
        v_pg_detail TEXT;
        v_pg_hint TEXT;
        v_pg_context TEXT;
        v_created_at TIMESTAMPTZ := COALESCE(created_at, CURRENT_TIMESTAMP);
        v_created_by TEXT := COALESCE(created_by, current_user);
        v_provenance TEXT := COALESCE(provenance, 'unknown');
        v_exception_id TEXT; 
        v_hub_fhir_bundle_id TEXT;
        
    BEGIN
      
      BEGIN
        v_hub_fhir_bundle_id := gen_random_uuid()::text;
      
            INSERT INTO techbd_udi_ingress.hub_fhir_bundle (hub_fhir_bundle_id, key, created_at, created_by, provenance)
            VALUES (v_hub_fhir_bundle_id, fhir_bundle_key, v_created_at, v_created_by, v_provenance)
            ;       
        EXCEPTION
            WHEN unique_violation THEN
            IF NOT hub_upsert_behavior THEN
                -- Capture exception details
                GET STACKED DIAGNOSTICS
                v_error_msg = MESSAGE_TEXT,
                v_sqlstate = RETURNED_SQLSTATE,
                v_pg_detail = PG_EXCEPTION_DETAIL,
                v_pg_hint = PG_EXCEPTION_HINT,
                v_pg_context = PG_EXCEPTION_CONTEXT;

                -- Call register_issue to log the exception and get the exception ID
                PERFORM techbd_udi_ingress.register_issue( NULL, fhir_bundle_key, v_error_msg, v_sqlstate, v_pg_detail, v_pg_hint, v_pg_context, v_created_by, v_provenance);
            END IF;
          v_hub_fhir_bundle_id := NULL;
        END;
      RETURN v_hub_fhir_bundle_id;
    EXCEPTION
      WHEN OTHERS THEN
          -- Capture exception details
        GET STACKED DIAGNOSTICS
            v_error_msg = MESSAGE_TEXT,
            v_sqlstate = RETURNED_SQLSTATE,
            v_pg_detail = PG_EXCEPTION_DETAIL,
            v_pg_hint = PG_EXCEPTION_HINT,
            v_pg_context = PG_EXCEPTION_CONTEXT;

        -- Log the exception, reusing the previous exception ID if it exists
        PERFORM techbd_udi_ingress.register_issue(
        COALESCE(v_exception_id, NULL), 
      fhir_bundle_key, 
      v_error_msg, 
      v_sqlstate, 
      v_pg_detail, 
      v_pg_hint, 
      v_pg_context, 
      v_created_by, 
      v_provenance);
      RETURN NULL;     
    END;
    `;
/*
 * This function attempts to insert a new row into the techbd_udi_ingress.hub_uniform_resource table with the given parameters
*/
const fnHubUniformResourceUpsertBuilder = pgSQLa
  .storedRoutineBuilder(
    "hub_uniform_resource_upserted",
    {
      uniform_resource_key: text(),
      created_by: textNullable(),
      provenance: textNullable(),
      created_at: createdAt(),
      upsert_behavior: boolean().default(true),
    },
  );
export const fnHubUniformResourceUpsert = pgSQLa.storedFunction(
  fnHubUniformResourceUpsertBuilder.routineName,
  fnHubUniformResourceUpsertBuilder.argsDefn,
  "TEXT",
  (name, args) => pgSQLa.typedPlPgSqlBody(name, args, ctx),
  {
    embeddedStsOptions: tmpl.typicalSqlTextSupplierOptions(),
    autoBeginEnd: false,
    isIdempotent: true,
    sqlNS: ingressSchema,
    headerBodySeparator: "$fnHubUniformResourceUpsert$",
  },
)`
    declare
        hub_request_http_client_row techbd_udi_ingress.hub_interaction%ROWTYPE;
        hub_exception_row 			techbd_udi_ingress.hub_exception;
        err_returned_sqlstate 		text;	-- Variable to store SQLSTATE of the error
        err_message_text 			text;	-- Variable to store the message text of the error
        err_pg_exception_detail 	text;	-- Variable to store the detail of the error
        err_pg_exception_hint 		text;	-- Variable to store the hint of the error
        err_pg_exception_context 	text; 	-- Variable to store the context of the error
        
        v_error_msg TEXT;
        v_sqlstate TEXT;
        v_pg_detail TEXT;
        v_pg_hint TEXT;
        v_pg_context TEXT;
        v_created_at TIMESTAMPTZ := COALESCE(created_at, CURRENT_TIMESTAMP);
        v_created_by TEXT := COALESCE(created_by, current_user);
        v_provenance TEXT := COALESCE(provenance, 'unknown');
        v_exception_id TEXT; 
        v_hub_uniform_resource_id TEXT;
        
    BEGIN
      
      BEGIN
        v_hub_uniform_resource_id := gen_random_uuid()::text;
      
            INSERT INTO techbd_udi_ingress.hub_uniform_resource (hub_uniform_resource_id, key, created_at, created_by, provenance)
            VALUES (v_hub_uniform_resource_id, uniform_resource_key, v_created_at, v_created_by, v_provenance)
            ;       
        EXCEPTION
            WHEN unique_violation THEN
                IF NOT hub_upsert_behavior THEN
                    -- Capture exception details
                    GET STACKED DIAGNOSTICS
                    v_error_msg = MESSAGE_TEXT,
                    v_sqlstate = RETURNED_SQLSTATE,
                    v_pg_detail = PG_EXCEPTION_DETAIL,
                    v_pg_hint = PG_EXCEPTION_HINT,
                    v_pg_context = PG_EXCEPTION_CONTEXT;

                    -- Call register_issue to log the exception and get the exception ID
                    PERFORM techbd_udi_ingress.register_issue( NULL, interaction_key, v_error_msg, v_sqlstate, v_pg_detail, v_pg_hint, v_pg_context, v_created_by, v_provenance);
                END IF;
              v_hub_uniform_resource_id := NULL;
        END;
      RETURN v_hub_uniform_resource_id;
    EXCEPTION
      WHEN OTHERS THEN
          -- Capture exception details
        GET STACKED DIAGNOSTICS
            v_error_msg = MESSAGE_TEXT,
            v_sqlstate = RETURNED_SQLSTATE,
            v_pg_detail = PG_EXCEPTION_DETAIL,
            v_pg_hint = PG_EXCEPTION_HINT,
            v_pg_context = PG_EXCEPTION_CONTEXT;

        -- Log the exception, reusing the previous exception ID if it exists
        PERFORM techbd_udi_ingress.register_issue(
        COALESCE(v_exception_id, NULL), 
      uniform_resource_key, 
      v_error_msg, 
      v_sqlstate, 
      v_pg_detail, 
      v_pg_hint, 
      v_pg_context, 
      v_created_by, 
      v_provenance);
      RETURN NULL;     
    END;
    `;

// Read SQL queries from files
const testMigrateDependenciesWithPgtap = [
  "../../../../test/postgres/ingestion-center/004-idempotent-migrate-unit-test.psql",
  "../../../../test/postgres/ingestion-center/suite.pgtap.psql",
] as const;

const infoSchemaLifecycle = SQLa.sqlSchemaDefn("info_schema_lifecycle", {
  isIdempotent: true,
});


export const migrationInput: MigrationVersion = {
  description: "ddl-sr-interaction",
  dateTime: new Date(2024, 7, 30, 21, 16),
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

      
      ${fnHubFhirBundleUpsert}

      
      ${fnHubUniformResourceUpsert}
      



      
    END
  `;

/**
 * Generates SQL Data Definition Language (DDL) for the migrations.
 *
 * @returns {string} The SQL DDL for migrations.
 */

function sqlDDLGenerateMigration() {
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
    
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

      DROP PROCEDURE IF EXISTS "${migrateSP.sqlNS?.sqlNamespace}"."${migrateSP.routineName}" CASCADE;

      `),
    testDependencies,
  };
}
