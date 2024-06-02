#!/usr/bin/env -S deno run --allow-all
import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.20/pattern/data-vault/mod.ts";
import {
  pgSQLa,
} from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.20/pattern/pgdcp/deps.ts";

// high-modules provide convenient access to internal imports
const { typical: typ, typical: { SQLa, ws } } = dvp;

const ctx = SQLa.typicalSqlEmitContext({
  sqlDialect: SQLa.postgreSqlDialect(),
});
const techbdUdiSchema = SQLa.sqlSchemaDefn("techbd_udi", {
  isIdempotent: true,
});

type EmitContext = typeof ctx;

const searchPath = pgSQLa.pgSearchPath<
  typeof techbdUdiSchema.sqlNamespace,
  EmitContext
>(
  techbdUdiSchema,
);

const dvts = dvp.dataVaultTemplateState<EmitContext>();
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

const deviceHub = dvts.hubTable("device", {
  hub_device_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
}, {
  sqlNS: techbdUdiSchema,
});

const deviceSat = deviceHub.satelliteTable(
  "device",
  {
    sat_device_device_id: primaryKey(),
    hub_device_id: deviceHub.references
      .hub_device_id(),
    name: text(),
    state: text(),
    boundary: text(),
    segmentation: textNullable(),
    state_sysinfo: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const requestHttpClientHub = dvts.hubTable("request_http_client", {
  hub_request_http_client_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
}, {
  sqlNS: techbdUdiSchema,
});

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

const ingestSessionHub = dvts.hubTable("ingest_session", {
  hub_ingest_session_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
}, {
  sqlNS: techbdUdiSchema,
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
    issue_type: text(),
    issue_message: text(),
    level: text(),
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

function sqlDDL(
  options: {
    destroyFirst?: boolean;
    schemaName?: string;
  } = {},
) {
  const { destroyFirst, schemaName } = options;

  // deno-fmt-ignore
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
    ${
      destroyFirst && schemaName
        ? `drop schema if exists ${schemaName} cascade;`
        : "-- not destroying first (for development)"
    }
    ${
      schemaName
        ? `create schema if not exists ${schemaName};`
        : "-- no schemaName provided"
    }


    ${searchPath}

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

    ${sessionRequestLink}

    ${sessionEntryLink}

    ${sessionDeviceLink}

    ${hubException}

    ${hubExceptionDiagnosticSat}

    ${hubExceptionHttpClientSat}

    \\ir udi-ingestion-center-stored-routines.psql
`;
}

typ.typicalCLI({
  resolve: (specifier) =>
    specifier ? import.meta.resolve(specifier) : import.meta.url,
  prepareSQL: () =>
    ws.unindentWhitespace(
      sqlDDL({ destroyFirst: true, schemaName: techbdUdiSchema.sqlNamespace })
        .SQL(
          ctx,
        ),
    ),
  prepareDiagram: () => {
    sqlDDL({ destroyFirst: true, schemaName: techbdUdiSchema.sqlNamespace })
      .SQL(
        ctx,
      );
    return dvts.pumlERD(ctx).content;
  },
}).commands.parse(Deno.args);
