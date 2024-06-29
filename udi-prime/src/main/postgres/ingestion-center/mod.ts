#!/usr/bin/env -S deno run --allow-all

import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.27/pattern/data-vault/mod.ts";
//import * as dvp from "../../../../../../../netspective-labs/sql-aide/pattern/data-vault/mod.ts";

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

const dvts = dvp.dataVaultTemplateState<EmitContext>({
  defaultNS: ingressSchema,
});
const {
  text,
  date,
  jsonTextNullable,
  jsonbNullable,
  jsonB,
  integerNullable,
  integer,
  boolean,
  textNullable,
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

// TODO: need to put all construction DDL into stored procedures using ISLM;
//       see https://github.com/netspective-labs/sql-aide/tree/main/pattern/postgres
//       see https://github.com/netspective-labs/sql-aide/tree/main/pattern/pgdcp
// TODO: need to add partitioning
// TODO: need to add row level security (RLS) tied to user roles
// TODO: need to add SQL comments so that ERDs can use them
// TODO: make `CREATE EXTENSION` type-safe by using SQLa instances not strings
// TODO: should dependencies be `import`ed by Deno (instead of text files)?

function constructables() {
  const dependencies = [
    "./000_idempotent_universal.psql",
    "./001_idempotent_interaction.psql",
    "./002_idempotent_diagnostics.psql",
  ] as const;
  const testDependencies = [
    "../../../test/postgres/ingestion-center/001-idempotent-interaction-unit-test.psql",
    "../../../test/postgres/ingestion-center/003-idempotent-interaction-view-explain-plan.psql",
    "../../../test/postgres/ingestion-center/fixtures.sql",
    "../../../test/postgres/ingestion-center/suite.pgtap.psql",
  ] as const;
  return {
    // deno-fmt-ignore
    driverSQL: () => SQLa.SQL<EmitContext>(dvts.ddlOptions)`
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
      
      ${dependencies.map((dep) => `\\ir ${dep}`).join("\n")}`,
    dependencies: () => dependencies.map((dep) => import.meta.resolve(dep)),
    testDependencies: () =>
      testDependencies.map((dep) => import.meta.resolve(dep)),
  };
}

export function generated() {
  const constructed = constructables();
  const ctx = SQLa.typicalSqlEmitContext({
    sqlDialect: SQLa.postgreSqlDialect(),
  });

  // after this execution `ctx` will contain list of all tables which will be
  // passed into `dvts.pumlERD` below (ctx should only be used once)
  const driverSQL = ws.unindentWhitespace(constructed.driverSQL().SQL(ctx));
  return {
    driverSQL,
    pumlERD: dvts.pumlERD(ctx).content,
    dependencies: constructed.dependencies(),
    testDependencies: constructed.testDependencies(),
    tablesDeclared: dvts.tablesDeclared,
    viewsDeclared: dvts.viewsDeclared,
    destroySQL: ws.unindentWhitespace(`
      -- UDI does not allow \`public\`. Reasons:
      -- * Security: By removing the public schema, we reduce the risk of accidental
      --   data exposure. The public schema is accessible to all users by default, 
      --   so removing it can help enforce stricter access controls.
      -- * Organization: Using custom schemas helps organize database objects more
      --   logically, making it easier to manage and maintain.
      -- * Namespace Management: Dropping the public schema prevents accidental
      --   creation of objects in the default schema, ensuring that all objects are
      --   properly created in the intended custom schemas.

      DROP SCHEMA IF EXISTS public CASCADE;

      DROP SCHEMA IF EXISTS ${ingressSchema.sqlNamespace} cascade;
      DROP SCHEMA IF EXISTS ${assuranceSchema.sqlNamespace} cascade;
      `),
  };
}

if (import.meta.main) {
  typ.typicalCLI({
    resolve: (specifier) =>
      specifier ? import.meta.resolve(specifier) : import.meta.url,
    prepareSQL: () => generated().driverSQL,
    prepareDiagram: () => generated().pumlERD,
  }).commands.command(
    "destroy",
    new typ.cli.Command()
      .description(
        "Generate `destroy.sql` content useful during development to prepare clean DDL",
      )
      .action(() => console.log(generated().destroySQL)),
  ).parse(Deno.args);
}
