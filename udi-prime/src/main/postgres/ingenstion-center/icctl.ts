#!/usr/bin/env -S deno run --allow-all

import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.26/pattern/data-vault/mod.ts";
// import * as dvp from "../../../../../../../netspective-labs/sql-aide/pattern/data-vault/mod.ts";

// deconstructed modules provide convenient access to internal imports
const { typical: typ, typical: { SQLa, ws } } = dvp;

const ctx = SQLa.typicalSqlEmitContext({
  sqlDialect: SQLa.postgreSqlDialect(),
});
type EmitContext = typeof ctx;

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

// TODO: rename `device` hub to `provenance`
const deviceHub = dvts.hubTable("device", {
  hub_device_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

// TODO: rename `sat_device_device` to `sat_provenance_device`
const deviceSat = deviceHub.satelliteTable(
  "device",
  {
    sat_device_device_id: primaryKey(),
    hub_device_id: deviceHub.references
      .hub_device_id(),
    name: text(),
    state: text(),
    boundary: text(),
    segmentation: jsonbNullable(),
    state_sysinfo: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

// TODO: rename `hub_request_http_client` to `hub_interaction`
const requestHttpClientHub = dvts.hubTable("request_http_client", {
  hub_request_http_client_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

// TODO: rename `sat_request_http_client_meta_data` to `sat_interaction_http_request`
const requestHttpClientSat = requestHttpClientHub.satelliteTable(
  "meta_data",
  {
    sat_request_http_client_meta_data_id: primaryKey(),
    hub_request_http_client_id: requestHttpClientHub.references
      .hub_request_http_client_id(),
    request_payload: jsonB,
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

// TODO: add `sat_interaction_file_exchange` (for SFTP, etc.)
//       add `protocol` as a column and create enum for SFTP, S3, etc.

const ingestSessionHub = dvts.hubTable("ingest_session", {
  hub_ingest_session_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

const sessionMetadataSat = ingestSessionHub.satelliteTable("meta_data", {
  sat_ingest_session_meta_data_id: primaryKey(),
  hub_ingest_session_id: ingestSessionHub.references.hub_ingest_session_id(),
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
      c.unique("hub_ingest_session_id", "content_hash"),
    ];
  },
});

const ingestSessionEntryHub = dvts.hubTable("ingest_session_entry", {
  hub_ingest_session_entry_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

const entryMetadataSat = ingestSessionEntryHub.satelliteTable("payload", {
  sat_ingest_session_entry_payload_id: primaryKey(),
  hub_ingest_session_entry_id: ingestSessionEntryHub.references
    .hub_ingest_session_entry_id(),
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
    sat_ingest_session_entry_session_state_id: primaryKey(),
    hub_ingest_session_entry_id: ingestSessionEntryHub.references
      .hub_ingest_session_entry_id(),
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
    sat_ingest_session_entry_session_exec_id: primaryKey(),
    hub_ingest_session_entry_id: ingestSessionEntryHub.references
      .hub_ingest_session_entry_id(),
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
    sat_ingest_session_entry_session_issue_id: primaryKey(),
    hub_ingest_session_entry_id: ingestSessionEntryHub.references
      .hub_ingest_session_entry_id(),
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
    sat_ingest_session_entry_session_issue_payload_id: primaryKey(),
    hub_ingest_session_entry_id: ingestSessionEntryHub.references
      .hub_ingest_session_entry_id(),
    validation_engine_payload: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const sessionEntryLink = dvts.linkTable("session_entry", {
  link_session_entry_id: primaryKey(),
  hub_ingest_session_id: ingestSessionHub.references
    .hub_ingest_session_id(),
  hub_ingest_session_entry_id: ingestSessionEntryHub.references
    .hub_ingest_session_entry_id(),
  ...dvts.housekeeping.columns,
});

const sessionRequestLink = dvts.linkTable("session_request", {
  link_session_request_id: primaryKey(),
  hub_ingest_session_id: ingestSessionHub.references
    .hub_ingest_session_id(),
  hub_request_http_client_id: requestHttpClientHub.references
    .hub_request_http_client_id(),
  ...dvts.housekeeping.columns,
});

const sessionDeviceLink = dvts.linkTable("session_device", {
  link_session_device_id: primaryKey(),
  hub_ingest_session_id: ingestSessionHub.references
    .hub_ingest_session_id(),
  hub_device_id: deviceHub.references
    .hub_device_id(),
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

function destroyDDL() {
  // deno-fmt-ignore
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
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
    DROP SCHEMA IF EXISTS ${orchCtlSchema.sqlNamespace} cascade;`;
}

// TODO: need to put all construction DDL into stored procedures using ISLM;
//       see https://github.com/netspective-labs/sql-aide/tree/main/pattern/postgres
// TODO: need to add partitioning
// TODO: need to add SQL comments so that ERDs can use them

function constructDDL() {
  // deno-fmt-ignore
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
    SET client_min_messages TO warning;

    CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA public;
    CREATE EXTENSION IF NOT EXISTS pgTAP SCHEMA public;

    ${ingressSchema}
    ${assuranceSchema}
    ${orchCtlSchema}

    ${sessionIdentifierType}

    ${deviceHub}

    ${deviceSat}

    ${requestHttpClientHub}

    ${requestHttpClientSat}

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
        
    \\ir views-simple.sql

    \\ir stored-routines.psql

    \\ir suite.pgtap.psql`;
}

typ.typicalCLI({
  resolve: (specifier) =>
    specifier ? import.meta.resolve(specifier) : import.meta.url,
  prepareSQL: () => ws.unindentWhitespace(constructDDL().SQL(ctx)),
  prepareDiagram: () => {
    constructDDL().SQL(ctx);
    return dvts.pumlERD(ctx).content;
  },
}).commands.command(
  "destroy",
  new typ.cli.Command()
    .description(
      "Generate `destroy.sql` content useful during development to prepare clean DDL",
    )
    .action(() => console.log(ws.unindentWhitespace(destroyDDL().SQL(ctx)))),
).parse(Deno.args);
