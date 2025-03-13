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
  integer,
  blobTextNullable
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


const interactionCsvRequestSat = interactionHub.satelliteTable(
  "flat_file_csv_request",
  {
    sat_interaction_flat_file_csv_request_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    tenant_id: text(),
    tenant_id_lower: textNullable(),
    uri: textNullable(),
    nature: textNullable(),
    group_id: textNullable(),
    status: textNullable(),
    validation_result_payload: jsonbNullable(),
    screening_data_payload_text: textNullable(),
    demographic_data_payload_text: textNullable(),
    qe_admin_data_payload_text: textNullable(),
    screening_data_file_name: textNullable(),
    demographic_data_file_name: textNullable(),
    qe_admin_data_file_name: textNullable(),
    client_ip_address: textNullable(),
    user_agent: textNullable(),
    from_state: textNullable(),
    to_state: textNullable(),
    state_transition_reason: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

const interactionZipRequestSat = interactionHub.satelliteTable(
  "zip_file_request",
  {
    sat_interaction_zip_file_request_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    tenant_id: text(),
    tenant_id_lower: textNullable(),
    uri: textNullable(),
    nature: textNullable(),
    group_id: textNullable(),
    status: textNullable(),
    csv_zip_file_name: textNullable(),
    client_ip_address: textNullable(),
    user_agent: textNullable(),
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


const interactionHl7RequestSat = interactionHub.satelliteTable(
  "hl7_request",
  {
    sat_interaction_hl7_request_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    tenant_id: text(),
    tenant_id_lower: textNullable(),
    uri: textNullable(),
    nature: textNullable(),
    payload: jsonbNullable(),
    client_ip_address: textNullable(),
    user_agent: textNullable(),
    from_state: textNullable(),
    to_state: textNullable(),
    state_transition_reason: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);


const interactionCcdaRequestSat = interactionHub.satelliteTable(
  "ccda_request",
  {
    sat_interaction_ccda_request_id: primaryKey(),
    hub_interaction_id: interactionHub.references
      .hub_interaction_id(),
    tenant_id: text(),
    tenant_id_lower: textNullable(),
    uri: textNullable(),
    nature: textNullable(),
    payload: jsonbNullable(),
    ccda_payload_text: textNullable(),
    client_ip_address: textNullable(),
    user_agent: textNullable(),
    from_state: textNullable(),
    to_state: textNullable(),
    state_transition_reason: textNullable(),
    elaboration: jsonbNullable(),
    origin: textNullable(),
    ...dvts.housekeeping.columns,
  },
);


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

      ${diagnosticsSchema}

      ${hubDiagnostics}

      ${diagnosticsSat}

      ${exceptionDiagnosticSat}

      ${interactionHub}

      ${interactionHttpRequestSat}

      ${interactionFhirRequestSat}

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request ALTER COLUMN passed DROP NOT NULL;
      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request ALTER COLUMN user_agent DROP NOT NULL;

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

      ${interactionCsvRequestSat}

      ${interactionZipRequestSat}

      ${fileExchangeProtocol}
      
      ${interactionHl7RequestSat}    
      
      ${interactionCcdaRequestSat}    

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request ALTER COLUMN user_agent DROP NOT NULL;
      ALTER TABLE techbd_udi_ingress.sat_interaction_flat_file_csv_request ALTER COLUMN user_agent DROP NOT NULL;
      ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request ALTER COLUMN user_agent DROP NOT NULL;
      
      CREATE UNIQUE INDEX IF NOT EXISTS sat_int_hl7_req_uq_hub_int_tnt_nat ON techbd_udi_ingress.sat_interaction_hl7_request USING btree (hub_interaction_id, tenant_id, nature);
      CREATE INDEX IF NOT EXISTS sat_inter_hl7_req_created_at_idx ON techbd_udi_ingress.sat_interaction_hl7_request USING btree (created_at DESC);
      CREATE INDEX IF NOT EXISTS sat_inter_hl7_req_frm_state_idx ON techbd_udi_ingress.sat_interaction_hl7_request USING btree (from_state);
      CREATE INDEX IF NOT EXISTS sat_inter_hl7_req_hub_inter_id_idx ON techbd_udi_ingress.sat_interaction_hl7_request USING btree (hub_interaction_id);
      CREATE INDEX IF NOT EXISTS sat_inter_hl7_req_nature_idx ON techbd_udi_ingress.sat_interaction_hl7_request USING btree (nature);
      CREATE INDEX IF NOT EXISTS sat_inter_hl7_req_payload_idx ON techbd_udi_ingress.sat_interaction_hl7_request USING gin (payload);
      CREATE INDEX IF NOT EXISTS sat_inter_hl7_req_to_state_idx ON techbd_udi_ingress.sat_interaction_hl7_request USING btree (to_state);
      
      DROP INDEX IF EXISTS sat_interaction_fhir_validation_issue_idx_date_issue ;
      CREATE INDEX IF NOT EXISTS sat_interaction_fhir_validation_idx_date_time ON techbd_udi_ingress.sat_interaction_fhir_validation_issue (date_time);
      CREATE INDEX IF NOT EXISTS sat_interaction_fhir_validation_idx_issue_partial ON techbd_udi_ingress.sat_interaction_fhir_validation_issue (issue) WHERE issue LIKE '%has not been checked because it is unknown%' OR issue LIKE '%Unknown profile%' OR issue LIKE '%Unknown extension%' OR issue LIKE '%Unknown Code System%' OR issue LIKE '%not found%' OR issue LIKE '%has not been checked because it could not be found%' OR issue LIKE '%Unable to find a match for profile%' OR issue LIKE '%None of the codings provided%' OR issue LIKE '%Unable to expand ValueSet%' OR issue LIKE '%Slicing cannot be evaluated%' OR issue LIKE '%could not be resolved%';
      CREATE INDEX IF NOT EXISTS sat_interaction_fhir_session_diagnostic_idx_encountered_at ON techbd_udi_ingress.sat_interaction_fhir_session_diagnostic (encountered_at);

      CREATE UNIQUE INDEX IF NOT EXISTS sat_int_ccda_req_uq_hub_int_tnt_nat ON techbd_udi_ingress.sat_interaction_ccda_request USING btree (hub_interaction_id, tenant_id, nature);
      CREATE INDEX IF NOT EXISTS sat_inter_ccda_req_created_at_idx ON techbd_udi_ingress.sat_interaction_ccda_request USING btree (created_at DESC);
      CREATE INDEX IF NOT EXISTS sat_inter_ccda_req_frm_state_idx ON techbd_udi_ingress.sat_interaction_ccda_request USING btree (from_state);
      CREATE INDEX IF NOT EXISTS sat_inter_ccda_req_hub_inter_id_idx ON techbd_udi_ingress.sat_interaction_ccda_request USING btree (hub_interaction_id);
      CREATE INDEX IF NOT EXISTS sat_inter_ccda_req_nature_idx ON techbd_udi_ingress.sat_interaction_ccda_request USING btree (nature);
      CREATE INDEX IF NOT EXISTS sat_inter_ccda_req_payload_idx ON techbd_udi_ingress.sat_interaction_ccda_request USING gin (payload);
      CREATE INDEX IF NOT EXISTS sat_inter_ccda_req_to_state_idx ON techbd_udi_ingress.sat_interaction_ccda_request USING btree (to_state);



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

        ALTER TABLE techbd_udi_ingress.sat_interaction_http_request	ADD COLUMN IF NOT EXISTS payload_text text NULL;

        
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


        -- Check and add 'primary_org_id' column if it does not exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                      WHERE table_name='sat_interaction_fhir_screening_patient' 
                      AND column_name='primary_org_id') THEN
            ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_screening_patient
            ADD COLUMN primary_org_id TEXT  NULL;
        END IF;

         -- Check and add 'primary_org_id' column if it does not exist and drop it if it exists
          IF EXISTS (
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'techbd_udi_ingress'
                AND table_name = 'sat_interaction_fhir_screening_organization'
                AND column_name = 'primary_org_id'
          ) THEN
              ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_screening_organization
              DROP COLUMN primary_org_id;
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
      ALTER TABLE techbd_udi_ingress.json_action_rule ADD COLUMN IF NOT EXISTS description TEXT NULL;
      ALTER TABLE techbd_udi_ingress.json_action_rule DROP CONSTRAINT IF EXISTS json_action_rule_action_check;
      ALTER TABLE techbd_udi_ingress.json_action_rule ADD CONSTRAINT json_action_rule_action_check CHECK ((action = ANY (ARRAY['accept'::text, 'reject'::text, 'modify'::text, 'discard'::text])));

      ALTER TABLE techbd_udi_ingress.json_action_rule DROP CONSTRAINT IF EXISTS json_action_rule_action_rule_id_pkey;
      ALTER TABLE techbd_udi_ingress.json_action_rule
        ADD CONSTRAINT json_action_rule_action_rule_id_pkey
        PRIMARY KEY (action_rule_id);
      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request ADD COLUMN IF NOT EXISTS techbd_disposition_action TEXT NULL;  
      
      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_validation_issue ADD COLUMN IF NOT EXISTS severity TEXT NULL;     
      
      ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request ADD COLUMN IF NOT EXISTS csv_zip_file_content Bytea NULL;   
      
      TRUNCATE TABLE techbd_udi_ingress.json_action_rule;

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
        '$.response.responseBody.OperationOutcome.validationResults[*].operationOutcome.issue[*] ? (@.diagnostics like_regex ".*Meta.lastUpdated: minimum required = 1" && @.location[*] like_regex ".*Bundle.meta" && @.severity like_regex ".*error")',
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
        '189b6342-3797-459f-9a4a-b8a71015f082',
        'NYeC Rule',
        '$.response.responseBody.OperationOutcome.validationResults[*].operationOutcome.issue[*] ? (@.diagnostics like_regex ".*lastUpdated.*" && @.severity like_regex ".*fatal.*")',
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

      INSERT INTO techbd_udi_ingress.json_action_rule (
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
      VALUES (
        'eeeb6342-3797-459f-9a4a-b8a71015f082',
        'NYeC Rule',
        '$.response.responseBody.OperationOutcome.validationResults[*].issues[*].message ? (@ like_regex ".*TECHBD-1000: Invalid or Partial JSON.*")',
        'discard',
        NULL,
        NULL,
        NULL,
        5000,
        current_timestamp,
        current_user,
        current_timestamp,
        current_timestamp,
        current_user,
        '{"Key" : "value"}'
      )
      ON CONFLICT (action_rule_id) DO NOTHING;

      INSERT INTO techbd_udi_ingress.json_action_rule (
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
      VALUES (
        'ffeb6342-3797-459f-9a4a-b8a71015f082',
        'NYeC Rule',
        '$.response.responseBody.OperationOutcome.validationResults[*].issues[*].message ? (@ like_regex ".*TECHBD-1001*")',
        'discard',
        NULL,
        NULL,
        NULL,
        5000,
        current_timestamp,
        current_user,
        current_timestamp,
        current_timestamp,
        current_user,
        '{"Key" : "value"}'
      )
      ON CONFLICT (action_rule_id) DO NOTHING;

      INSERT INTO techbd_udi_ingress.json_action_rule (
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
      VALUES (
        'ggeb6342-3797-459f-9a4a-b8a71015f082',
        'NYeC Rule',
        '$.response.responseBody.OperationOutcome.validationResults[*].issues[*].message ? (@ like_regex ".*TECHBD-1002*")',
        'discard',
        NULL,
        NULL,
        NULL,
        5000,
        current_timestamp,
        current_user,
        current_timestamp,
        current_timestamp,
        current_user,
        '{"Key" : "value"}'
      )
      ON CONFLICT (action_rule_id) DO NOTHING;



      CREATE INDEX IF NOT exists json_action_rule_action_idx ON techbd_udi_ingress.json_action_rule USING btree (action);
      CREATE INDEX IF NOT EXISTS json_action_rule_json_path_idx ON techbd_udi_ingress.json_action_rule USING btree (json_path);
      CREATE INDEX IF NOT EXISTS json_action_rule_last_applied_at_idx ON techbd_udi_ingress.json_action_rule USING btree (last_applied_at DESC);
      CREATE INDEX IF NOT EXISTS json_action_rule_namespace_idx ON techbd_udi_ingress.json_action_rule USING btree (namespace);
      CREATE INDEX IF NOT EXISTS json_action_rule_priority_idx ON techbd_udi_ingress.json_action_rule USING btree (priority);


      IF NOT EXISTS (
          SELECT 1
          FROM pg_constraint
          WHERE conname = 'sat_interaction_flat_file_csv_request_hub_interaction_id_fkey'
      ) THEN
          ALTER TABLE sat_interaction_flat_file_csv_request
          ADD CONSTRAINT sat_interaction_flat_file_csv_request_hub_interaction_id_fkey
          FOREIGN KEY (hub_interaction_id)
          REFERENCES hub_interaction(id);
      END IF;

      IF NOT EXISTS (
          SELECT 1
          FROM pg_constraint
          WHERE conname = 'sat_interaction_hl7_request_hub_interaction_id_fkey'
      ) THEN
          ALTER TABLE techbd_udi_ingress.sat_interaction_hl7_request ADD CONSTRAINT sat_interaction_hl7_request_hub_interaction_id_fkey FOREIGN KEY (hub_interaction_id) REFERENCES techbd_udi_ingress.hub_interaction(hub_interaction_id);
      END IF;

      IF NOT EXISTS (
          SELECT 1
          FROM pg_constraint
          WHERE conname = 'sat_interaction_zip_file_request_hub_interaction_id_fkey'
      ) THEN
        ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request ADD CONSTRAINT sat_interaction_zip_file_request_hub_interaction_id_fkey FOREIGN KEY (hub_interaction_id) REFERENCES techbd_udi_ingress.hub_interaction(hub_interaction_id);
      END IF;

      ALTER TABLE techbd_udi_ingress.sat_interaction_flat_file_csv_request 
        DROP COLUMN IF EXISTS screening_consent_data_payload_text,
        DROP COLUMN IF EXISTS screening_encounter_data_payload_text,
        DROP COLUMN IF EXISTS screening_location_data_payload_text,
        DROP COLUMN IF EXISTS screening_resources_data_payload_text,
        DROP COLUMN IF EXISTS screening_consent_data_file_name,
        DROP COLUMN IF EXISTS screening_encounter_data_file_name,	
        DROP COLUMN IF EXISTS screening_location_data_file_name,
        DROP COLUMN IF EXISTS screening_resources_data_file_name,
        DROP COLUMN IF EXISTS zip_file_sat_interaction_id,
        ADD COLUMN IF NOT EXISTS screening_observation_data_payload_text text NULL,  
        ADD COLUMN IF NOT EXISTS screening_profile_data_payload_text text NULL,  
        ADD COLUMN IF NOT EXISTS screening_observation_data_file_name text NULL,
        ADD COLUMN IF NOT EXISTS screening_profile_data_file_name text NULL,
        ADD COLUMN IF NOT EXISTS zip_file_hub_interaction_id text NULL;

      ALTER TABLE techbd_udi_ingress.sat_interaction_flat_file_csv_request 
        DROP COLUMN IF EXISTS screening_data_payload_text,
        DROP COLUMN IF EXISTS screening_data_file_name;


      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request ADD COLUMN IF NOT EXISTS patient_mrn_source_system TEXT NULL;
      
      ALTER TABLE techbd_udi_ingress.sat_interaction_flat_file_csv_request 
      DROP CONSTRAINT IF EXISTS sat_interaction_flat_file_csv_request_zip_file_sat_interaction_id_fkey;

      
      
      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request 
        ADD COLUMN IF NOT EXISTS source_type text NULL, 
        ADD COLUMN IF NOT EXISTS source_hub_interaction_id text NULL; 
        
      ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request  
        ADD COLUMN IF NOT EXISTS origin text NULL, 
        ADD COLUMN IF NOT EXISTS validation_result_payload jsonb NULL;

      ALTER TABLE techbd_udi_ingress.sat_interaction_hl7_request 
        ADD COLUMN IF NOT EXISTS client_ip_address TEXT NULL, 
            ADD COLUMN IF NOT EXISTS hl7_payload_text TEXT NULL,
            ADD COLUMN IF NOT EXISTS origin TEXT NULL; 

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request 
	      ADD COLUMN IF NOT EXISTS group_hub_interaction_id TEXT NULL;      

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request 
	      ADD COLUMN IF NOT EXISTS is_bundle_valid BOOLEAN NULL;      
        
      ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request  
        ADD COLUMN IF NOT EXISTS sftp_session_id text NULL;

      ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request 
        DROP COLUMN IF EXISTS misc_errors;

      ALTER TABLE techbd_udi_ingress.sat_interaction_zip_file_request 
        ADD COLUMN IF NOT EXISTS general_errors jsonb NULL;
      
      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_session_diagnostic 
        ADD COLUMN IF NOT EXISTS ig_version text NULL;

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_session_diagnostic 
        ADD COLUMN IF NOT EXISTS validation_engine text NULL;

      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_session_diagnostic 
        ADD COLUMN IF NOT EXISTS bundle_id text NULL;
        
      ALTER TABLE techbd_udi_ingress.sat_interaction_fhir_request 
	      ADD COLUMN IF NOT EXISTS bundle_type TEXT NULL;

      ALTER TABLE techbd_udi_ingress.sat_interaction_user 
	      ADD COLUMN IF NOT EXISTS user_session_hash TEXT NULL;   

      UPDATE techbd_udi_ingress.sat_interaction_user 
        SET user_session_hash = md5(user_session) WHERE user_session_hash IS NULL;        

      ${dependenciesSQL}

      CREATE EXTENSION IF NOT EXISTS pgtap SCHEMA ${assuranceSchema.sqlNamespace};
      
      ${testDependenciesSQL}

      ${searchPathAssurance}
      DECLARE
        tap_op TEXT := '';
        test_result BOOLEAN;
        line TEXT;
      BEGIN
          PERFORM * FROM ${assuranceSchema.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_http_request'::text);
          test_result = TRUE;

          FOR line IN
              SELECT * FROM ${assuranceSchema.sqlNamespace}.runtests('techbd_udi_assurance'::name, 'test_register_interaction_http_request'::text)
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
