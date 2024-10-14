#!/usr/bin/env -S deno run --allow-all

/**
 * This TypeScript file implements a SQL migration feature for PostgreSQL databases using Deno.
 * It provides methods for defining and executing migrations.
 *
 * @module Information_Schema_Lifecycle_Management_Migration
 */

import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
import { pgSQLa } from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/pgdcp/deps.ts";
// import { pgSQLa } from "../../../../../../../../netspective-labs/sql-aide/pattern/pgdcp/deps.ts";

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
  booleanNullable,
  textNullable,
  dateTime,
  dateTimeNullable,
  integer
} = dvts.domains;
const { ulidPrimaryKey: primaryKey } = dvts.keys;

const orchctl_business_rules = dvp.typical.SQLa.tableDefinition("business_rules",{
  "worksheet":textNullable(),
  "field":textNullable(),
  "required":textNullable(),
  "Permissible Values":textNullable(),
  "True Rejection":textNullable(),
  "Warning Layer":textNullable(),
  "Resolved by QE/QCS":textNullable(),
  "src_file_row_number":integer(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_device = dvp.typical.SQLa.tableDefinition("device",{
  "device_id":textNullable(),
  "name":textNullable(),
  "state":textNullable(),
  "boundary":textNullable(),
  "segmentation":textNullable(),
  "state_sysinfo":textNullable(),
  "elaboration":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_orch_session = dvp.typical.SQLa.tableDefinition("orch_session",{
  "orch_session_id":textNullable(),
  "device_id":textNullable(),
  "version":textNullable(),
  "orch_started_at":dateTimeNullable(),
  "orch_finished_at":dateTimeNullable(),
  "elaboration":textNullable(),
  "args_json":textNullable(),
  "diagnostics_json":textNullable(),
  "diagnostics_md":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_orch_session_entry = dvp.typical.SQLa.tableDefinition("orch_session_entry",{
  "orch_session_entry_id":textNullable(),
  "session_id":textNullable(),
  "ingest_src":textNullable(),
  "ingest_table_name":dateTimeNullable(),
  "elaboration":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_orch_session_exec = dvp.typical.SQLa.tableDefinition("orch_session_exec",{
  "orch_session_exec_id":textNullable(),
  "exec_nature":textNullable(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
  "parent_exec_id":textNullable(),
  "namespace":textNullable(),
  "exec_identity":textNullable(),
  "exec_code":textNullable(),
  "exec_status":integer(),
  "input_text":textNullable(),
  "exec_error_text":textNullable(),
  "output_text":textNullable(),
  "output_nature":textNullable(),
  "narrative_md":textNullable(),
  "elaboration":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_orch_session_issue = dvp.typical.SQLa.tableDefinition("orch_session_issue",{
  "orch_session_issue_id":textNullable(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
  "issue_type":textNullable(),
  "issue_message":textNullable(),
  "issue_row":integer(),
  "issue_column":textNullable(),
  "invalid_value":textNullable(),
  "remediation":textNullable(),
  "elaboration":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_orch_session_state = dvp.typical.SQLa.tableDefinition("orch_session_state",{
  "orch_session_state_id":textNullable(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
  "from_state":textNullable(),
  "to_state":textNullable(),
  "transition_result":textNullable(),
  "transition_reason":textNullable(),
  "transitioned_at":dateTimeNullable(),
  "elaboration":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_demographic_data = dvp.typical.SQLa.tableDefinition("demographic_data",{
  "mpi_id":textNullable(),
  "pat_mrn_id":textNullable(),
  "from_state":textNullable(),
  "facility_id":textNullable(),
  "consent":textNullable(),
  "first_name":textNullable(),
  "middle_name":textNullable(),
  "last_name":textNullable(),
  "administrative_sex_code":textNullable(),
  "administrative_sex_code_description":textNullable(),
  "administrative_sex_code_system":textNullable(),
  "sex_at_birth_code":textNullable(),
  "sex_at_birth_code_description":textNullable(),
  "sex_at_birth_code_system":textNullable(),
  "pat_birth_date":textNullable(),
  "address1":textNullable(),
  "address2":textNullable(),
  "city":textNullable(),
  "state":textNullable(),
  "zip":textNullable(),
  "phone":textNullable(),
  "ssn":textNullable(),
  "gender_identity_code":textNullable(),
  "gender_identity_code_description":textNullable(),
  "gender_identity_code_system_name":textNullable(),
  "sexual_orientation_code":textNullable(),
  "sexual_orientation_code_description":textNullable(),
  "sexual_orientation_code_system_name":textNullable(),
  "preferred_language_code":textNullable(),
  "preferred_language_code_description":textNullable(),
  "preferred_language_code_system_name":textNullable(),
  "race_code":textNullable(),
  "race_code_description":textNullable(),
  "race_code_system_name":textNullable(),
  "ethnicity_code":textNullable(),
  "ethnicity_code_description":textNullable(),
  "ethnicity_code_system_name":textNullable(),
  "medicaid_cin":textNullable(),
  "src_file_row_number":integer(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
  "source_table":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_qe_admin_data = dvp.typical.SQLa.tableDefinition("qe_admin_data",{
  "pat_mrn_id":textNullable(),
  "facility_id":textNullable(),
  "facility_long_name":textNullable(),
  "organization_type":textNullable(),
  "facility_address1":textNullable(),
  "facility_address2":textNullable(),
  "facility_city":textNullable(),
  "facility_state":textNullable(),
  "facility_zip":textNullable(),
  "visit_part_2_flag":textNullable(),
  "visit_omh_flag":textNullable(),
  "sex_at_birth_code_system":textNullable(),
  "visit_opwdd_flag":textNullable(),
  "src_file_row_number":integer(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
  "source_table":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
});

const orchctl_screening = dvp.typical.SQLa.tableDefinition("screening",{
  "pat_mrn_id":textNullable(),
  "facility_id":textNullable(),
  "encounter_id":textNullable(),
  "encounter_class_code":textNullable(),
  "encounter_class_code_description":textNullable(),
  "encounter_class_code_system":textNullable(),
  "encounter_status_code":textNullable(),
  "encounter_status_code_description":textNullable(),
  "encounter_status_code_system":textNullable(),
  "encounter_type_code":textNullable(),
  "encounter_type_code_description":textNullable(),
  "encounter_type_code_system":textNullable(),
  "screening_status_code":textNullable(),
  "screening_status_code_description":textNullable(),
  "screening_status_code_system":textNullable(),
  "screening_code":textNullable(),
  "screening_code_description":textNullable(),
  "screening_code_system_name":textNullable(),
  "recorded_time":textNullable(),
  "question_code":textNullable(),
  "question_code_description":textNullable(),
  "question_code_system_name":textNullable(),
  "ucum_units":textNullable(),
  "sdoh_domain":textNullable(),
  "parent_question_code":textNullable(),
  "answer_code":textNullable(),
  "answer_code_description":textNullable(),
  "answer_code_system_name":textNullable(),
  "potential_need_indicated":textNullable(),
  "src_file_row_number":integer(),
  "session_id":textNullable(),
  "session_entry_id":textNullable(),
  "source_table":textNullable()
},{
  sqlNS: orchCtlSchema,
  isIdempotent: true
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

const interactionFhirRequestSat = interactionHub.satelliteTable(
  "fhir_request",
  {
    sat_interaction_fhir_request_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    tenant_id: text(),
    tenant_id_lower: textNullable(),
    uri: textNullable(),
    nature: textNullable(),
    payload: jsonB,
    client_ip_address: textNullable(),
    user_agent: text(),
    from_state: textNullable(),
    to_state: textNullable(),
    state_transition_reason: textNullable(),
    outbound_http_message: textNullable(),
    error_message: textNullable(),
    issues_count: integer().default(0),
    bundle_id: textNullable(),
    bundle_session_id: textNullable(),
    bundle_last_updated: dateTimeNullable(),
    organization_id: textNullable(),
    organization_name: textNullable(),
    patient_id: textNullable(),
    patient_mrn: textNullable(),
    resource_type_set: textNullable(),
    validation_initiated_at: dateTimeNullable(),
    validation_completed_at: dateTimeNullable(),
    interaction_start_time: dateTimeNullable(),
    interaction_end_time: dateTimeNullable(),
    validation_engine: textNullable(),
    ig_version: textNullable(),
    profile_url : textNullable(),
    passed: boolean().default(false),
    medicaid_cin: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const interactionUserRequestSat = interactionHub.satelliteTable(
  "user",
  {
    sat_interaction_user_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    uri: text(),
    nature: text(),
    tenant_id: textNullable(),
    user_id: textNullable(),
    user_name: textNullable(),
    user_session: textNullable(),
    user_role: textNullable(),
    client_ip_address: textNullable(),
    user_agent: textNullable(),
    interaction_start_time: dateTimeNullable(),
    interaction_end_time: dateTimeNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const interactionFhirSessionDiagnosticSat = interactionHub.satelliteTable(
  "fhir_session_diagnostic",
  {
    sat_interaction_fhir_session_diagnostic_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    tenant_id: text(),
    uri: text(),
    session_id: text(),
    severity: textNullable(),
    message: textNullable(),
    line: textNullable(),
    column: textNullable(),
    diagnostics: textNullable(),
    encountered_at: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const interactionFhirScreeningInfoSat = interactionHub.satelliteTable(
  "fhir_screening_info",
  {
    sat_interaction_fhir_screening_info_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    qe_name : text(),
    submitted_date_time: dateTimeNullable(),
    survey_date_time: textNullable(),
    patient_mrn: textNullable(),
    full_name: textNullable(),
    last_name: textNullable(),
    first_name: textNullable(),
    org_id: textNullable(),
    org_name: textNullable(),
    total_safety_score: textNullable(),
    areas_of_interest: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const interactionFhirScreeningPatientSat = interactionHub.satelliteTable(
  "fhir_screening_patient",
  {
    sat_interaction_fhir_screening_patient_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    qe_name : text(),
    patient_mrn: text(),
    patient_id: textNullable(),
    patient_type: textNullable(),
    patient_full_name: textNullable(),
    patient_first_name: textNullable(),
    patient_last_name: textNullable(),
    patient_gender: textNullable(),
    patient_birth_date: textNullable(),
    patient_address: textNullable(),
    patient_city: textNullable(),
    patient_state: textNullable(),
    patient_postal_code: textNullable(),
    patient_language: textNullable(),
    patient_ssn: textNullable(),
    org_id: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const interactionFhirScreeninOrganizationSat = interactionHub.satelliteTable(
  "fhir_screening_organization",
  {
    sat_interaction_fhir_screening_organization_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    qe_name : text(),
    org_id: textNullable(),
    org_type: textNullable(),
    org_name: textNullable(),
    org_active: textNullable(),
    org_address: textNullable(),
    org_city: textNullable(),
    org_state: textNullable(),
    org_postal_code: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const interactionFhirValidationIssueSat = interactionHub.satelliteTable(
  "fhir_validation_issue",
  {
    sat_interaction_fhir_validation_issue_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    issue : text(),
    date_time: dateTimeNullable(),
    validation_engine: textNullable(),
    ig_version: textNullable(),
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

const jsonActionRule = SQLa.tableDefinition("json_action_rule", {
  action_rule_id: text(),
  namespace: textNullable(),
  json_path: textNullable(),
  action: textNullable(),
  condition: jsonbNullable(),
  reject_json: jsonbNullable(),
  modify_json: jsonbNullable(),
  priority: integer().default(0),
  updated_at: dateTime(),
  updated_by: textNullable(),
  last_applied_at: dateTimeNullable(),
  ...dvts.housekeeping.columns
}, {
  isIdempotent: true,
  sqlNS: ingressSchema
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

      ${orchctl_business_rules}

      ${orchctl_device}

      ${orchctl_orch_session}

      ${orchctl_orch_session_entry}

      ${orchctl_orch_session_exec}

      ${orchctl_orch_session_issue}

      ${orchctl_orch_session_state}

      ${orchctl_demographic_data}

      ${orchctl_qe_admin_data}

      ${orchctl_screening}

      ${diagnosticsSchema}

      ${hubDiagnostics}

      ${diagnosticsSat}

      ${exceptionDiagnosticSat}

      ${interactionHub}

      ${interactionHttpRequestSat}

      ${interactionFhirRequestSat}

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request ALTER COLUMN passed DROP NOT NULL;

      CREATE UNIQUE INDEX IF NOT EXISTS sat_int_fhir_req_uq_hub_int_tnt_nat 
      ON techbd_udi_ingress.sat_interaction_fhir_request (hub_interaction_id, tenant_id, nature);

      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_hub_inter_id_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (hub_interaction_id);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_hub_inter_created_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (hub_interaction_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_created_idx ON techbd_udi_ingress.sat_interaction_fhir_request (created_at timestamptz_ops DESC);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_frm_state_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (from_state);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_to_state_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (to_state);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_nature_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (nature);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_payload_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING gin (payload);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_provenance_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (provenance);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_bund_id_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (bundle_id);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_bund_sess_id_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (bundle_session_id);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_org_id_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (organization_id);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_org_name_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (organization_name);
      CREATE INDEX IF NOT EXISTS sat_inter_fhir_req_patient_mrn_idx ON techbd_udi_ingress.sat_interaction_fhir_request USING btree (patient_mrn);

      ${interactionUserRequestSat}

      ${interactionFhirSessionDiagnosticSat}

      ${interactionFhirScreeningInfoSat}

      ${interactionFhirScreeningPatientSat}

      ${interactionFhirScreeninOrganizationSat}

      ${interactionFhirValidationIssueSat}

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

        
        -- Check and add 'nature_denorm' column if it does not exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                      WHERE table_name='sat_interaction_user' 
                      AND column_name='interaction_start_time') THEN
            ALTER TABLE techbd_udi_ingress.sat_interaction_user
            ADD COLUMN interaction_start_time TIMESTAMPTZ DEFAULT null;
        END IF;

        -- Check and add 'nature_denorm' column if it does not exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                      WHERE table_name='sat_interaction_user' 
                      AND column_name='interaction_end_time') THEN
            ALTER TABLE techbd_udi_ingress.sat_interaction_user
            ADD COLUMN interaction_end_time TIMESTAMPTZ DEFAULT null;
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

      ${jsonActionRule}
      ALTER TABLE techbd_udi_ingress.json_action_rule DROP CONSTRAINT IF EXISTS json_action_rule_action_check;
      ALTER TABLE techbd_udi_ingress.json_action_rule
        ADD CONSTRAINT json_action_rule_action_check
        CHECK (action = ANY (ARRAY['accept'::text, 'reject'::text, 'modify'::text]));

      ALTER TABLE techbd_udi_ingress.json_action_rule DROP CONSTRAINT IF EXISTS json_action_rule_action_rule_id_pkey;
      ALTER TABLE techbd_udi_ingress.json_action_rule
        ADD CONSTRAINT json_action_rule_action_rule_id_pkey
        PRIMARY KEY (action_rule_id);

      INSERT INTO techbd_udi_ingress.json_action_rule(
        action_rule_id,
        "namespace",
        json_path,
        "action",
        "condition",
        reject_json,
        modify_json,
        priority,
        updated_at,
        updated_by,
        last_applied_at,
        created_at,
        created_by,
        provenance
      )
      VALUES(
        '36eb7e17-107a-44ad-834e-9699b435708f',
        'NYeC Rule',
        '$.response.responseBody.OperationOutcome.validationResults[*].issues[*] ? (@.location.diagnostics == "Bundle.meta")',
        'reject',
        NULL,
        NULL,
        NULL,
        1,
        current_timestamp,
        current_user,
        current_timestamp ,
        current_timestamp,
        current_user,
        '{"Key" : "value"}'
      ) ON CONFLICT (action_rule_id) DO NOTHING;

      INSERT INTO techbd_udi_ingress.json_action_rule(
        action_rule_id,
        "namespace",
        json_path,
        "action",
        "condition",
        reject_json,
        modify_json,
        priority,
        updated_at,
        updated_by,
        last_applied_at,
        created_at,
        created_by,
        provenance
      )
      VALUES(
        'd386cb5f-472c-4f90-81f2-7fd0841544ae',
        'NYeC Rule',
        '$.response.responseBody.OperationOutcome.validationResults[*].issues[*].message ? (@ like_regex ".*HAPI-1821: \\[element=\"lastUpdated\"\\].*")',
        'reject',
        NULL,
        NULL,
        NULL,
        1,
        current_timestamp,
        current_user,
        current_timestamp,
        current_timestamp,
        current_user,
        '{"Key" : "value"}'
      ) ON CONFLICT (action_rule_id) DO NOTHING;

      CREATE INDEX IF NOT exists json_action_rule_action_idx ON techbd_udi_ingress.json_action_rule USING btree (action);
      CREATE INDEX IF NOT EXISTS json_action_rule_json_path_idx ON techbd_udi_ingress.json_action_rule USING btree (json_path);
      CREATE INDEX IF NOT EXISTS json_action_rule_last_applied_at_idx ON techbd_udi_ingress.json_action_rule USING btree (last_applied_at DESC);
      CREATE INDEX IF NOT EXISTS json_action_rule_namespace_idx ON techbd_udi_ingress.json_action_rule USING btree (namespace);
      CREATE INDEX IF NOT EXISTS json_action_rule_priority_idx ON techbd_udi_ingress.json_action_rule USING btree (priority);
      
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
        WHERE schema_name IN ('info_schema_lifecycle', 'info_schema_lifecycle_assurance');

        -- If less than 3 schemas are found, raise an error
        IF schema_count < 2 THEN
            RAISE EXCEPTION 'One or more of the required schemas info_schema_lifecycle, info_schema_lifecycle_assurance are missing';
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
