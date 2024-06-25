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

const provenanceHub = dvts.hubTable("provenance", {
  hub_provenance_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

const deviceSat = provenanceHub.satelliteTable(
  "device",
  {
    sat_provenance_device_id: primaryKey(),
    hub_provenance_id: provenanceHub.references
      .hub_provenance_id(),
    name: text(),
    state: text(),
    boundary: text(),
    segmentation: jsonbNullable(),
    state_sysinfo: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

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

const ingestSessionHub = dvts.hubTable("operation_session", {
  hub_operation_session_id: primaryKey(),
  key: text(),
  nature: text().default("ingest"),
  ...dvts.housekeeping.columns,
});

const sessionMetadataSat = ingestSessionHub.satelliteTable("meta_data", {
  sat_operation_session_meta_data_id: primaryKey(),
  hub_operation_session_id: ingestSessionHub.references
    .hub_operation_session_id(),
  device_id: text(),
  version: text(),
  orch_started_at: date(),
  orch_finished_at: date(),
  qe_identifier: text(),
  content_hash: text(),
  args_json: jsonB,
  diagnostics_json: jsonbNullable(),
  diagnostics_md: textNullable(),
  elaboration: jsonbNullable(),
  ...dvts.housekeeping.columns,
}, {
  constraints: (props, tableName) => {
    const c = SQLa.tableConstraints(tableName, props);
    return [
      c.unique("hub_operation_session_id", "content_hash"),
    ];
  },
});

const ingestSessionEntryHub = dvts.hubTable("operation_session_entry", {
  hub_operation_session_entry_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

const entryMetadataSat = ingestSessionEntryHub.satelliteTable("payload", {
  sat_operation_session_entry_payload_id: primaryKey(),
  hub_operation_session_entry_id: ingestSessionEntryHub.references
    .hub_operation_session_entry_id(),
  ingest_src: text(),
  ingest_table_name: textNullable(),
  ingest_payload: jsonB,
  content_type: text(),
  elaboration: jsonbNullable(),
  ...dvts.housekeeping.columns,
});

const entrySessionStateSat = ingestSessionEntryHub.satelliteTable(
  "session_state",
  {
    sat_operation_session_entry_session_state_id: primaryKey(),
    hub_operation_session_entry_id: ingestSessionEntryHub.references
      .hub_operation_session_entry_id(),
    from_state: text(),
    to_state: text(),
    transition_result: textNullable(),
    transition_reason: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const entrySessionExecSat = ingestSessionEntryHub.satelliteTable(
  "session_exec",
  {
    sat_operation_session_entry_session_exec_id: primaryKey(),
    hub_operation_session_entry_id: ingestSessionEntryHub.references
      .hub_operation_session_entry_id(),
    exec_nature: text(),
    parent_exec_id: textNullable(),
    namespace: textNullable(),
    exec_identity: textNullable(),
    exec_code: text(),
    exec_status: integer(),
    input_text: textNullable(),
    exec_error_text: textNullable(),
    output_text: textNullable(),
    output_nature: textNullable(),
    narrative_md: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const entrySessionIssueSat = ingestSessionEntryHub.satelliteTable(
  "session_issue",
  {
    sat_operation_session_entry_session_issue_id: primaryKey(),
    hub_operation_session_entry_id: ingestSessionEntryHub.references
      .hub_operation_session_entry_id(),
    issue_type: textNullable(),
    issue_message: textNullable(),
    level: textNullable(),
    issue_column: integerNullable(),
    issue_row: integerNullable(),
    message_id: textNullable(),
    ignorableError: boolean().default(false),
    invalid_value: textNullable(),
    comment: textNullable(),
    display: textNullable(),
    disposition: textNullable(),
    remediation: textNullable(),
    validation_engine_payload: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const entrySessionIssuePayloadSat = ingestSessionEntryHub.satelliteTable(
  "session_issue_payload",
  {
    sat_operation_session_entry_session_issue_payload_id: primaryKey(),
    hub_operation_session_entry_id: ingestSessionEntryHub.references
      .hub_operation_session_entry_id(),
    validation_engine_payload: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const sessionEntryLink = dvts.linkTable("session_entry", {
  link_session_entry_id: primaryKey(),
  hub_operation_session_id: ingestSessionHub.references
    .hub_operation_session_id(),
  hub_operation_session_entry_id: ingestSessionEntryHub.references
    .hub_operation_session_entry_id(),
  ...dvts.housekeeping.columns,
});

const sessionRequestLink = dvts.linkTable("session_interaction", {
  link_session_interaction_id: primaryKey(),
  hub_operation_session_id: ingestSessionHub.references
    .hub_operation_session_id(),
  hub_interaction_id: interactionHub.references
    .hub_interaction_id(),
  ...dvts.housekeeping.columns,
});

const sessionDeviceLink = dvts.linkTable("session_provenance", {
  link_session_provenance_id: primaryKey(),
  hub_operation_session_id: ingestSessionHub.references
    .hub_operation_session_id(),
  hub_provenance_id: provenanceHub.references
    .hub_provenance_id(),
  ...dvts.housekeeping.columns,
});

const hubException = dvts.hubTable("exception", {
  hub_exception_id: primaryKey(),
  key: textNullable(),
  ...dvts.housekeeping.columns,
});

const hubExceptionDiagnosticSat = hubException.satelliteTable(
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

const hubExceptionHttpClientSat = hubException.satelliteTable(
  "http_client",
  {
    sat_exception_http_client_id: primaryKey(),
    hub_exception_id: hubException.references.hub_exception_id(),
    http_req: jsonTextNullable(), // eg: Permission Denied to the file
    http_resp: jsonTextNullable(),
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
    "./views-simple.sql",
    "./stored-routines.psql",
  ] as const;
  const testDependencies = [
    "../../../test/postgres/ingestion-center/stored-routines-unittest.psql",
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

      ${sessionIdentifierType}

      ${provenanceHub}

      ${deviceSat}

      ${interactionHub}

      ${interactionHttpRequestSat}

      ${fileExchangeProtocol}

      ${fileExchangeProtocol.seedDML}

      ${interactionfileExchangeSat}

      ${ingestSessionHub}

      ${ingestSessionEntryHub}

      ${sessionMetadataSat}

      ${entryMetadataSat}

      ${entrySessionStateSat}

      ${entrySessionExecSat}

      ${entrySessionIssueSat}

      ${entrySessionIssuePayloadSat}

      ${sessionRequestLink}

      ${sessionEntryLink}

      ${sessionDeviceLink}

      ${hubException}

      ${hubExceptionDiagnosticSat}

      ${hubExceptionHttpClientSat}

      ${pgTapFixturesJSON}
      
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
