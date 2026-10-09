# MCO Channels (BridgeLink)

This folder contains the BridgeLink channels that exchange files with New York State Managed Care Organizations (MCOs). They cover two flows:

| Flow | Direction | Folder | Summary |
|------|-----------|--------|---------|
| **ESMF** member file | MCO → TechBD → NYeC | [`mco-esmf/`](mco-esmf/README.md) | One channel per MCO picks up the monthly pipe-delimited member file, validates each record, returns success/rejection receipts to the MCO and streams valid data to the NYeC data lake SFTP. |
| **HRSN outbound** (SHIN-NY → MCO) | NYeC → TechBD → MCO | [`mco-hrsn/`](mco-hrsn/README.md) | Picks up daily HRSN screening flat files from NYeC and delivers them to each MCO. It also delivers individual FHIR Bundles to MCOs on request. |

All MCO channels share the BridgeLink **configuration map** in [`configuration-map/`](configuration-map/README.md) and the `Config` / `Config-sensitive` groups in [Lookup Manager](../../lookup-manager/README.md).

---

## 📁 Folder Structure

```
mco/
├── configuration-map/
│   └── configuration_map_export.properties   # BridgeLink configuration map
├── mco-esmf/
│   ├── <MCO>.xml                              # 22 per-MCO ESMF channels
│   ├── aetna-sftp-bridge.xml                  # Copies Aetna files into the landing bucket
│   └── channel-groups/mco-esmf.xml            # "MCO - ESMF" channel group (all of the above)
└── mco-hrsn/
    ├── 01-pick-up-hrsn-flat-files-from-nyec.xml
    ├── 02-deliver-flat-files-to-mcos.xml
    ├── deliver-fhir-bundles-to-mcos.xml
    └── channel-groups/mco-ob-from-shin-ny.xml # "MCO - OB from SHIN-NY" channel group
```

> **Channel groups vs. individual files:** each `channel-groups/*.xml` export bundles the same channels as the individual XML files beside it. Import the group to deploy the whole flow at once, or import individual channel files to update a single channel.

---

## 🗄️ Database

| Schema | Tables | Used by |
|--------|--------|---------|
| `mco_data` | `mco_records`, `mco_record_details`, `mco_record_error_logs` | ESMF channels |
| `screening_extracts` | `mco_screening_extracts`, `mco_screening_extracts_details`, `mco_screening_extracts_error_logs`, `mco_screening_preferences`, `resource_types`, `screening_error_types` | HRSN channels |
| `techbd_udi_ingress` | `tenants` | All MCO channels |

---

## 📚 Subfolder Documentation

| Folder | Description | Documentation |
|--------|-------------|---------------|
| `configuration-map` | Configuration map keys used by the MCO channels | [README](configuration-map/README.md) |
| `mco-esmf` | ESMF inbound channels (one per MCO) | [README](mco-esmf/README.md) |
| `mco-hrsn` | HRSN screening outbound channels | [README](mco-hrsn/README.md) |

---

## 🔗 Related Documentation

- [MCO Code Template](../../code-templates/README.md#mco-code-template)
- [Lookup Manager](../../lookup-manager/README.md)
- [BridgeLink (Nexus) Channels](../README.md)
- [Integration Artifacts Index](../../README.md)
