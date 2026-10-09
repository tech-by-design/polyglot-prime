# Lookup Manager Exports

This folder contains the BridgeLink **Lookup Manager** group exports. Channels read these values at runtime with `LookupHelper.get("<Group>", "<Key>")`. Lookup Manager requires BridgeLink **4.6.1 or higher**.

---

## 📦 Lookup Groups

| File | Group | Description | Entries |
|------|-------|-------------|---------|
| [`lookup_group_config_export.json`](lookup_group_config_export.json) | `Config` | Non-sensitive configuration for the TechBD workflows | 31 |
| [`lookup_group_config_sensitive_export.json`](lookup_group_config_sensitive_export.json) | `Config-sensitive` | Credentials and secrets (placeholder values only) | 10 |
| [`lookup_group_schemafiles_export.json`](lookup_group_schemafiles_export.json) | `SchemaFiles` | XSD, XSLT and conformance profile content for the CCDA and HL7v2 channels | 21 |

Each file contains a `group` object (name, description, LRU cache settings) and a `values` map of key → value.

---

## 🔑 Group Contents

### `Config`

| Keys | Purpose | Used by |
|------|---------|---------|
| `ALLOWED_HOSTS`, `ALLOWED_ORIGINS` | Host header and CORS origin allow-lists | RouterChannel |
| `BASE_FHIR_URL`, `MC_VALID_FHIR_URLS` | SHIN-NY IG base URL and accepted base URLs | CCDA, HL7v2, FlatFileCsvBundle |
| `PROFILE_URL_*` | StructureDefinition paths for generated resources | CCDA, HL7v2 |
| `MC_FHIR_BUNDLE_SUBMISSION_API_URL`, `BL_FHIR_BUNDLE_VALIDATION_API_URL`, `HUB_UI_URL` | Downstream FHIR submission/validation endpoints and Hub UI link | FHIR, CCDA, HL7v2 channels |
| `TECHBD_DEFAULT_DATALAKE_API_URL` | NYeC data lake endpoint | All 1115 channels |
| `TECHBD_CSV_SERVICE_API_URL` | CSV conversion service | FlatFileCsvBundle |
| `DATA_LEDGER_API_URL`, `DATA_LEDGER_TRACKING`, `DATA_LEDGER_DIAGNOSTICS` | NYeC Data Ledger integration | FHIR, CCDA, HL7v2, FlatFileCsvBundle |
| `MC_CCDA_SCHEMA_FOLDER` | On-disk CCDA schema folder (`/opt/bridgelink/ccda-techbd-schema-files/`) | TechBD CCD Workflow |
| `HL7_XSLT_PATH` | On-disk HL7v2 schema folder (not referenced by the current channel exports) | – |
| `ORG_TECHBD_PROCESSING_AGENT_*` | Processing-agent feature flag, tenant list and value | FhirBundleSubmission, FhirBundleReplay, FhirBundleHistoricalReplay |
| `SM_KEY_RDS_SECRETS` | AWS Secrets Manager key for RDS credentials | FhirBundleSubmission, FhirBundleValidate, FhirBundleReplay |
| `ROCHESTER_RHIO_SFTP_KEY_PATH` | SFTP key path | MCO ESMF channels |

### `Config-sensitive`

| Keys | Purpose | Used by |
|------|---------|---------|
| `MC_JDBC_URL`, `MC_JDBC_USERNAME`, `MC_JDBC_PASSWORD` | PostgreSQL connection | FHIR, CCDA, HL7v2 and MCO ESMF channels, MCO Code Template |
| `MC_S3_BUCKET_NAME` | S3 bucket for MCO landing, receipt and archive folders | MCO ESMF channels |
| `MCO_SFTP_PRIVATE_KEY`, `MCO_SFTP_KEY_PASSPHRASE` | SSH key for the source SFTP | MCO ESMF channels |
| `MCO_DESTINATION_SFTP_PRIVATE_KEY`, `MCO_DESTINATION_SFTP_KEY_PASSPHRASE` | SSH key for the destination SFTP | MCO ESMF channels |
| `TECHBD_NYEC_DATALAKE_API_KEY` | NYeC Data Lake API key | FHIR channels |
| `TECHBD_NYEC_DATALEDGER_API_KEY` | NYeC Data Ledger API key | FHIR, CCDA, HL7v2 channels |

> ⚠️ This export holds placeholder values only. Set the real values in Lookup Manager on the target server and never commit them.

### `SchemaFiles`

The source copies of these entries live in the `*-schema-files` folders:

| Keys | Source folder |
|------|---------------|
| `CDA.xsd`, `POCD_MT000040.xsd`, `datatypes-base.xsd`, `datatypes.xsd`, `NarrativeBlock.xsd`, `sdtc.xsd`, `voc.xsd`, `index.txt` | [`channels/1115/ccda/ccda-techbd-schema-files`](../channels/1115/ccda/ccda-techbd-schema-files/README.md) |
| `cda-phi-filter[-athenahealth\|-curemd\|-epic\|-medent]` | [`channels/1115/ccda/ccda-techbd-schema-files`](../channels/1115/ccda/ccda-techbd-schema-files/README.md) |
| `cda-fhir-bundle[-athenahealth\|-curemd\|-epic\|-medent\|-common-utils]` | [`channels/1115/ccda/ccda-techbd-schema-files`](../channels/1115/ccda/ccda-techbd-schema-files/README.md) |
| `hl7v2-fhir-bundle.xslt`, `hl7v2-validation-schema` | [`channels/1115/hl7v2/hl7-techbd-schema-files`](../channels/1115/hl7v2/hl7-techbd-schema-files/README.md) |

The TechBD HL7 Workflow reads `hl7v2-validation-schema` and `hl7v2-fhir-bundle.xslt` by name. The TechBD CCD Workflow reads `index.txt`, which lists the XSD files in load order, and then loads each XSD, PHI filter and XSLT by name. When you change a schema or XSLT file, update the matching `SchemaFiles` entry as well, because the channels read from Lookup Manager rather than from the repository.

---

## 🚀 Import

In the BridgeLink Administrator, open **Lookup Manager → Import Group** and select each JSON file. Then replace the placeholder values in `Config` and `Config-sensitive` with environment-specific values.

---

## 🔗 Related Documentation

- [BridgeLink (Nexus) Channels](../channels/README.md)
- [MCO Configuration Map](../channels/mco/configuration-map/README.md)
- [Integration Artifacts Index](../README.md)
