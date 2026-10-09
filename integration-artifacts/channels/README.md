# BridgeLink (Nexus) Channels

This folder contains the **BridgeLink** channel exports deployed on the Nexus environment. Channels are grouped by program:

- **`1115/`** – 1115 Waiver ingestion channels (FHIR, CSV flat file, CCDA and HL7v2).
- **`mco/`** – Managed Care Organization (MCO) file exchange channels (ESMF member files and HRSN screening outbound).
- **`router/`** – The public entry point that routes 1115 API requests to the right ingestion channel.

These channels require BridgeLink **4.6.1 or higher** because they read configuration and schema files from [Lookup Manager](../lookup-manager/README.md).

---

## 📁 Folder Structure

```
channels/
├── 1115/
│   ├── ccda/       # TechBD CCD Workflow + CCDA schema files
│   ├── fhir/       # FHIR Bundle submission, validation and replay channels
│   ├── flatfile/   # CSV flat-file bundle channel
│   └── hl7v2/      # TechBD HL7 Workflow + HL7v2 schema files
├── mco/
│   ├── configuration-map/   # BridgeLink configuration map used by MCO channels
│   ├── mco-esmf/            # Per-MCO ESMF inbound channels + channel group
│   └── mco-hrsn/            # HRSN screening outbound channels + channel group
└── router/                  # RouterChannel (port 9000)
```

---

## 🔀 Request Routing (1115)

All external 1115 traffic enters through `RouterChannel` on port `9000`, which forwards the request to the matching channel on `localhost`:

| Request Path | Target Channel | Port |
|--------------|----------------|------|
| `/Bundle`, `/tenants` | FhirBundleSubmission | `9003` |
| `/Bundle/$validate` | FhirBundleValidate | `9001` |
| `/Bundle/replay` | FhirBundleReplay | `9005` |
| `/historical-replay/Bundle` | FhirBundleHistoricalReplay | `9007` |
| `/ccda/Bundle`, `/ccda/Bundle/$validate` | TechBD CCD Workflow | `9002` |
| `/flatfile/csv/Bundle`, `/flatfile/csv/Bundle/$validate` | FlatFileCsvBundle | `9004` |
| `/hl7v2/Bundle`, `/hl7v2/Bundle/$validate` | TechBD HL7 Workflow | `9006` |

See the [RouterChannel README](router/README.md) for CORS, host allow-listing and mTLS handling.

---

## 📚 Subfolder Documentation

| Folder | Description | Documentation |
|--------|-------------|---------------|
| `1115` | 1115 Waiver ingestion channels | [README](1115/README.md) |
| `mco` | MCO file exchange channels | [README](mco/README.md) |
| `router` | Entry-point routing channel | [README](router/README.md) |

---

## 🔗 Related Documentation

- [Code Templates](../code-templates/README.md)
- [Lookup Manager](../lookup-manager/README.md)
- [1115 Channels (Mirth Connect)](../1115-channels/README.md)
- [Integration Artifacts Index](../README.md)
