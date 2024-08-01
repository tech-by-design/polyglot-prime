#!/usr/bin/env -S deno run --allow-all

/**
 * This TypeScript file implements a SQL migration feature for PostgreSQL databases using Deno.
 * It provides methods for defining and executing migrations.
 *
 * @module Information_Schema_Lifecycle_Management_Migration
 */

import * as dvp from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.34/pattern/data-vault/mod.ts";
// import * as migrate from "../../../../../../../../netspective-labs/sql-aide/pattern/postgres/migrate.ts";

// deconstructed modules provide convenient access to internal imports
const { typical: typ, typical: { SQLa, ws } } = dvp;

type EmitContext = dvp.typical.SQLa.SqlEmitContext;

export const ingressSchema = SQLa.sqlSchemaDefn("techbd_udi_ingress", {
  isIdempotent: true,
});
export const assuranceSchema = SQLa.sqlSchemaDefn("techbd_udi_assurance", {
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
  date
} = dvts.domains;
export const { ulidPrimaryKey: primaryKey } = dvts.keys;

export const interactionHub = dvts.hubTable("interaction", {
  hub_interaction_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

export const fhirBundleHub = dvts.hubTable("fhir_bundle", {
  hub_fhir_bundle_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

export const uniformResourceHub = dvts.hubTable("uniform_resource", {
  hub_uniform_resource_id: primaryKey(),
  key: text(),
  ...dvts.housekeeping.columns,
});

export const interactionHttpRequestSat = interactionHub.satelliteTable(
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

export const uniformResourceSat = uniformResourceHub.satelliteTable(
  "info",
  {
    sat_uniform_resource_info_id: primaryKey(),
    hub_uniform_resource_id: uniformResourceHub.references
      .hub_uniform_resource_id(),
    nature: jsonbNullable(),
    tenant_id: textNullable(),
    content: jsonB,
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const uniformResourceFhirSat = uniformResourceHub.satelliteTable(
  "fhir",
  {
    sat_uniform_resource_fhir_id: primaryKey(),
    hub_uniform_resource_id: uniformResourceHub.references
      .hub_uniform_resource_id(),
    resource_type: textNullable(),
    resource: jsonB,
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const uniformResourceFhirBundleSat = fhirBundleHub.satelliteTable(
  "info",
  {
    sat_fhir_bundle_info_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    nature: jsonB,
    tenant_id: textNullable(),
    bundle_id: textNullable(),
    type: textNullable(),
    content: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirBundleResourcePatientSat = fhirBundleHub.satelliteTable(
  "resource_patient",
  {
    sat_fhir_bundle_resource_patient_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    id: text(),
    mrn: text(),
    name: jsonbNullable(),
    gender: textNullable(),
    birth_date: textNullable(),
    address: jsonbNullable(),
    meta: jsonbNullable(),
    language: textNullable(),
    extension: jsonbNullable(),
    identifier: jsonbNullable(),
    telecom: jsonbNullable(),
    deceased_boolean: boolean(),
    deceased_date_time: date(),
    marital_status: text(),
    multiple_birth_boolean: boolean(),
    multiple_birth_integer: textNullable(),
    contact: textNullable(),
    communication: jsonbNullable(),
    general_practitioner: textNullable(),
    content: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirBundleResourceObservationSat = fhirBundleHub.satelliteTable(
  "resource_observation",
  {
    sat_fhir_bundle_resource_observation_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    mrn: text(),
    meta: jsonbNullable(),
    status: textNullable(),
    category: jsonbNullable(),
    code: textNullable(),
    subject: textNullable(),
    effective_date_time: date(),
    issued: date(),
    value_codeable_concept: jsonbNullable(),
    interpretation: jsonbNullable(),
    content: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirBundleResourceServiceRequestSat = fhirBundleHub.satelliteTable(
  "resource_service_request",
  {
    sat_fhir_bundle_resource_service_request_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    id: text(),
    mrn: text(),
    meta: jsonbNullable(),
    extension: jsonbNullable(),
    occurence_date: textNullable(),
    occurence_period: textNullable(),
    status: textNullable(),
    intent: textNullable(),
    category: textNullable(),
    priority: textNullable(),
    subject: textNullable(),
    code: textNullable(),
    supporting_info: textNullable(),
    encounter: textNullable(),
    authored_on: textNullable(),
    requester: textNullable(),
    reason_code: textNullable(),
    insurance: textNullable(),
    specimen: textNullable(),
    identifier: textNullable(),
    requisition: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirBundleResourceDiagnosisSat = fhirBundleHub.satelliteTable(
  "resource_diagnosis",
  {
    sat_fhir_bundle_resource_diagnosis_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    id: text(),
    mrn: text(),
    meta: jsonbNullable(),
    extension: jsonbNullable(),
    clinical_status: textNullable(),
    verification_status: textNullable(),
    slices_for_category: textNullable(),
    severity: textNullable(),
    code: textNullable(),
    subject: textNullable(),
    encounter: textNullable(),
    onset_date_time: date(),
    record_date: date(),
    asserter: textNullable(),
    stage: textNullable(),
    note: jsonbNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirBundleResourceEncounterSat = fhirBundleHub.satelliteTable(
  "resource_encounter",
  {
    sat_fhir_bundle_resource_encounter_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    id: text(),
    mrn: text(),
    meta: jsonbNullable(),
    extension: jsonbNullable(),
    identifier: textNullable(),
    status: textNullable(),
    status_history: textNullable(),
    class: textNullable(),
    class_history: textNullable(),
    priority: textNullable(),
    type: textNullable(),
    service_type: textNullable(),
    subject: textNullable(),
    episode_of_care: textNullable(),
    service_provider: textNullable(),
    participant: textNullable(),
    appointment: textNullable(),
    period: textNullable(),
    length: textNullable(),
    reason_ode: textNullable(),
    reason_reference: textNullable(),
    diagnosis: textNullable(),
    account: textNullable(),
    hospitilization: textNullable(),
    location: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirBundleMetaDataSat = fhirBundleHub.satelliteTable(
  "meta_data",
  {
    sat_fhir_bundle_meta_data_id: primaryKey(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    cid: textNullable(),
    loaded_at: date(),
    status_key: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const fhirResourceMetaDataSat = uniformResourceHub.satelliteTable(
  "meta_data",
  {
    sat_uniform_resource_meta_data_id: primaryKey(),
    hub_uniform_resource_id: uniformResourceHub.references
      .hub_uniform_resource_id(),
    cid: textNullable(),
    loaded_at: date(),
    status_key: textNullable(),
    elaboration: jsonbNullable(),
    ...dvts.housekeeping.columns,
  },
);

export const uniformResourceFhirBundleLink = dvts.linkTable(
  "uniform_resource_fhir_bundle",
  {
    link_uniform_resource_fhir_bundle_id: primaryKey(),
    hub_uniform_resource_id: uniformResourceHub.references
      .hub_uniform_resource_id(),
    hub_fhir_bundle_id: fhirBundleHub.references.hub_fhir_bundle_id(),
    ...dvts.housekeeping.columns,
  },
  {
    isIdempotent: true,
    constraints: (props, tableName) => {
      const c = SQLa.tableConstraints(tableName, props);
      return [
        c.unique("hub_uniform_resource_id", "hub_fhir_bundle_id"),
      ];
    },
  }
);

export enum EnumFileExchangeProtocol {
  SFTP = "SFTP",
  S3 = "S3",
}

export const fileExchangeProtocol = typ.textEnumTable(
  "file_exchange_protocol",
  EnumFileExchangeProtocol,
  { isIdempotent: true, sqlNS: ingressSchema },
);

export const interactionfileExchangeSat = interactionHub.satelliteTable(
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

export const hubException = dvts.hubTable("exception", {
  hub_exception_id: primaryKey(),
  key: textNullable(),
  ...dvts.housekeeping.columns,
});

export const exceptionDiagnosticSat = hubException.satelliteTable(
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

export const pgTapFixturesJSON = SQLa.tableDefinition("pgtap_fixtures_json", {
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

export const pgTapTestResult = SQLa.tableDefinition("pgtap_test_result", {
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
 * Generates SQL Data Definition Language (DDL) for the migrations.
 *
 * @returns {string} The SQL DDL for migrations.
 */

function sqlDDLGenerateMigration() {
  return SQLa.SQL<EmitContext>(dvts.ddlOptions)`
    ${interactionHub}

    ${fhirBundleHub}      

    ${uniformResourceHub}

    ${uniformResourceSat}

    ${uniformResourceFhirSat}

    ${uniformResourceFhirBundleSat}

    ${fhirBundleResourcePatientSat}

    ${fhirBundleResourceObservationSat}

    ${fhirBundleResourceServiceRequestSat}

    ${fhirBundleResourceDiagnosisSat}

    ${fhirBundleResourceEncounterSat}

    ${fhirBundleMetaDataSat}

    ${fhirResourceMetaDataSat}

    ${uniformResourceFhirBundleLink}

    ${interactionHttpRequestSat}

    ${fileExchangeProtocol}    

    ${interactionfileExchangeSat}

    ${hubException}

    ${exceptionDiagnosticSat}

    ${pgTapFixturesJSON}

    ${pgTapTestResult}
    
    `;
}

export function generated() {
  const ctx = SQLa.typicalSqlEmitContext({
    sqlDialect: SQLa.postgreSqlDialect(),
  });

  // after this execution `ctx` will contain list of all tables which will be
  // passed into `dvts.pumlERD` below (ctx should only be used once)
  const driverGenerateMigrationSQL = ws.unindentWhitespace(
    sqlDDLGenerateMigration().SQL(ctx),
  );
  return {
    driverGenerateMigrationSQL,
    pumlERD: dvts.pumlERD(ctx).content
  };
}
