# 1115 Waiver Channels (BridgeLink)

This folder contains the BridgeLink channels that ingest 1115 Waiver screening data in each supported format, validate it, convert it to a SHIN-NY FHIR Bundle where needed, and submit it downstream. Requests reach these channels through the [RouterChannel](../router/README.md) on port `9000`; each channel also exposes its own `/healthcheck` path.

---

## 📁 Folder Structure

```
1115/
├── ccda/
│   ├── ccda-techbd-channel-files/   # TechBD CCD Workflow.xml
│   └── ccda-techbd-schema-files/    # CDA XSDs, PHI filters, CCDA → FHIR XSLTs
├── fhir/                            # FhirBundleSubmission / Validate / Replay / HistoricalReplay
├── flatfile/                        # FlatFileCsvBundle.xml
└── hl7v2/
    ├── hl7-techbd-channel-files/    # TechBD HL7 Workflow.xml
    └── hl7-techbd-schema-files/     # HL7v2 conformance profile, HL7v2 → FHIR XSLT
```

---

## 📦 Channels

| Channel | File | Version | Port | Endpoints |
|---------|------|---------|------|-----------|
| FhirBundleValidate | [`fhir/FhirBundleValidate.xml`](fhir/FhirBundleValidate.xml) | 0.8.22 | `9001` | `POST /Bundle/$validate` |
| TechBD CCD Workflow | [`ccda/ccda-techbd-channel-files/TechBD CCD Workflow.xml`](ccda/ccda-techbd-channel-files/TechBD%20CCD%20Workflow.xml) | 0.5.45 | `9002` | `POST /ccda/Bundle`, `POST /ccda/Bundle/$validate` |
| FhirBundleSubmission | [`fhir/FhirBundleSubmission.xml`](fhir/FhirBundleSubmission.xml) | 0.8.46 | `9003` | `POST /Bundle`, `GET /tenants` |
| FlatFileCsvBundle | [`flatfile/FlatFileCsvBundle.xml`](flatfile/FlatFileCsvBundle.xml) | 0.9.30 | `9004` | `POST /flatfile/csv/Bundle`, `POST /flatfile/csv/Bundle/$validate` |
| FhirBundleReplay | [`fhir/FhirBundleReplay.xml`](fhir/FhirBundleReplay.xml) | 0.1.6 | `9005` | `POST /Bundle/replay` |
| TechBD HL7 Workflow | [`hl7v2/hl7-techbd-channel-files/TechBD HL7 Workflow.xml`](hl7v2/hl7-techbd-channel-files/TechBD%20HL7%20Workflow.xml) | 0.1.54 | `9006` | `POST /hl7v2/Bundle`, `POST /hl7v2/Bundle/$validate` |
| FhirBundleHistoricalReplay | [`fhir/FhirBundleHistoricalReplay.xml`](fhir/FhirBundleHistoricalReplay.xml) | 0.1.1 | `9007` | `POST /historical-replay/Bundle` |

### Historical bundle replay

Historical replay is supported in two ways:

- **FhirBundleSubmission (0.8.46)** applies historical handling to any Bundle whose `Bundle.id` starts with `historical-`. For those bundles it accepts `ooSize=full|lite|none` (default `lite`) and `dataLedger=true|false` (default `false`).
- **FhirBundleHistoricalReplay** is a dedicated endpoint with the same validation flow as submission and the same two query parameters, but with the defaults `ooSize=full` and `dataLedger=true`.

---

## 📚 Subfolder Documentation

| Folder | Description | Documentation |
|--------|-------------|---------------|
| `fhir` | FHIR Bundle submission and validation channels | [README](fhir/README.md) |
| `flatfile` | CSV flat-file bundle channel | [README](flatfile/README.md) |
| `ccda/ccda-techbd-channel-files` | CCDA ingestion channel | [README](ccda/ccda-techbd-channel-files/README.md) |
| `ccda/ccda-techbd-schema-files` | CCDA schemas and XSLTs | [README](ccda/ccda-techbd-schema-files/README.md) |
| `hl7v2/hl7-techbd-channel-files` | HL7v2 ingestion channel | [README](hl7v2/hl7-techbd-channel-files/README.md) |
| `hl7v2/hl7-techbd-schema-files` | HL7v2 conformance profile and XSLT | [README](hl7v2/hl7-techbd-schema-files/README.md) |

> **Schema files at runtime:** the CCDA and HL7v2 channels load their XSD/XSLT content from the `SchemaFiles` group in [Lookup Manager](../../lookup-manager/README.md). The files in the `*-schema-files` folders are the source copies of those entries.

---

## 🔗 Related Documentation

- [RouterChannel](../router/README.md)
- [Code Templates](../../code-templates/README.md)
- [Lookup Manager](../../lookup-manager/README.md)
- [BridgeLink (Nexus) Channels](../README.md)
- [Integration Artifacts Index](../../README.md)
