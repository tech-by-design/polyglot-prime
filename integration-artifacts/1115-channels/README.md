# 1115 Channels (Mirth Connect)

This folder contains the **Mirth Connect** exports of the 1115 Waiver ingestion channels, along with the schema/XSLT files and global scripts they depend on. These are the original Mirth Connect versions of the CCDA and HL7v2 workflows; the actively maintained BridgeLink (Nexus) versions live under [`channels/1115`](../channels/1115/README.md).

> **Note:** The Mirth Connect channels read their configuration from environment variables and load schema/XSLT files from disk. The BridgeLink channels read configuration and schema files from [Lookup Manager](../lookup-manager/README.md) instead.

---

## 📁 Folder Structure

```
1115-channels/
├── ccda/
│   ├── ccda-techbd-channel-files/   # TechBD CCD Workflow channel export
│   └── ccda-techbd-schema-files/    # CDA XSDs, PHI-filter and CCDA → FHIR XSLTs
├── global-scripts/                  # Mirth Connect global scripts
└── hl7v2/
    ├── hl7-techbd-channel-files/    # TechBD HL7 Workflow channel export
    └── hl7-techbd-schema-files/     # HL7v2 conformance profile and HL7v2 → FHIR XSLT
```

---

## 📦 Channels

| Channel | Export Version | Listener Port | Endpoints | Documentation |
|---------|----------------|---------------|-----------|---------------|
| TechBD CCD Workflow | 0.4.53 | `9443` | `POST /ccda/Bundle`, `POST /ccda/Bundle/$validate` | [README](ccda/ccda-techbd-channel-files/README.md) |
| TechBD HL7 Workflow | 0.1.16 | `9006` | `POST /hl7v2/Bundle`, `POST /hl7v2/Bundle/$validate` | [README](hl7v2/hl7-techbd-channel-files/README.md) |

Both channels convert the incoming document to a FHIR Bundle and forward it to `${fhirBundleSubmissionApiUrl}` (the `dest_bundle` HTTP Sender). The `$validate` paths stop after validation and return an OperationOutcome.

---

## 📚 Subfolder Documentation

| Folder | Description | Documentation |
|--------|-------------|---------------|
| `ccda/ccda-techbd-channel-files` | Mirth Connect CCDA channel | [README](ccda/ccda-techbd-channel-files/README.md) |
| `ccda/ccda-techbd-schema-files` | CDA XSDs, PHI filters and CCDA → FHIR XSLTs | [README](ccda/ccda-techbd-schema-files/README.md) |
| `hl7v2/hl7-techbd-channel-files` | Mirth Connect HL7v2 channel | [README](hl7v2/hl7-techbd-channel-files/README.md) |
| `hl7v2/hl7-techbd-schema-files` | HL7v2 conformance profile and HL7v2 → FHIR XSLT | [README](hl7v2/hl7-techbd-schema-files/README.md) |
| `global-scripts` | Mirth Connect global deploy/undeploy/pre/post-processor scripts | [README](global-scripts/README.md) |

---

## 🔗 Related Documentation

- [BridgeLink (Nexus) Channels](../channels/README.md)
- [Integration Artifacts Index](../README.md)
