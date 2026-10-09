# Integration Artifacts

This directory contains all integration artifacts, schemas, and channel configurations used across the Tech by Design ecosystem. Each subfolder focuses on a specific integration technology, standard, or workflow.

## 📁 Folder Structure

```
integration-artifacts/
├── 1115-channels/        # Mirth Connect exports of the 1115 CCDA and HL7v2 workflows
├── channels/             # BridgeLink (Nexus) channel exports
│   ├── 1115/             #   1115 Waiver ingestion: FHIR, flat file, CCDA, HL7v2
│   ├── mco/              #   MCO file exchange: ESMF inbound, HRSN outbound, configuration map
│   └── router/           #   RouterChannel – entry point for 1115 API traffic
├── code-templates/       # Shared BridgeLink code template libraries
├── lookup-manager/       # BridgeLink Lookup Manager group exports
└── version.json          # Version, editor and date of every exported artifact
```

## 📚 Subfolder Documentation

Below is a table of the main subfolders and links to their documentation:

| Folder Name      | Description                                        | Documentation Link            |
|------------------|----------------------------------------------------|---------------------------|
| 1115-channels (Mirth Connect) | Index of the Mirth Connect 1115 channels | [Documentation](1115-channels/README.md) |
| ccda (Mirth Connect) | Mirth Connect CCDA channel files               | [Documentation](1115-channels/ccda/ccda-techbd-channel-files/README.md) |
| ccda schemas (Mirth Connect) | Mirth Connect CCDA TechBD schema files | [Documentation](1115-channels/ccda/ccda-techbd-schema-files/README.md) |
| hl7v2 (Mirth Connect) | Mirth Connect HL7v2 channel files             | [Documentation](1115-channels/hl7v2/hl7-techbd-channel-files/README.md) |
| hl7v2 schemas (Mirth Connect) | Mirth Connect HL7v2 schema files      | [Documentation](1115-channels/hl7v2/hl7-techbd-schema-files/README.md) |
| global scripts (Mirth Connect) | Mirth Connect global scripts         | [Documentation](1115-channels/global-scripts/README.md) |
| channels (Nexus) | Index of the BridgeLink channels, port map and request routing | [Documentation](channels/README.md) |
| 1115 (Nexus)     | Index of the 1115 Waiver ingestion channels        | [Documentation](channels/1115/README.md) |
| ccda (Nexus)     | Nexus CCDA channel files                           | [Documentation](channels/1115/ccda/ccda-techbd-channel-files/README.md) |
| ccda schemas (Nexus) | Nexus CCDA TechBD schema files                 | [Documentation](channels/1115/ccda/ccda-techbd-schema-files/README.md) |
| fhir (Nexus)     | Nexus FHIR channel files                           | [Documentation](channels/1115/fhir/README.md) |
| flatfile (Nexus) | Nexus Flat file channels                           | [Documentation](channels/1115/flatfile/README.md) |
| hl7v2 (Nexus)    | Nexus HL7v2 channel files                          | [Documentation](channels/1115/hl7v2/hl7-techbd-channel-files/README.md) |
| hl7v2 schemas (Nexus) | Nexus HL7v2 schema files                      | [Documentation](channels/1115/hl7v2/hl7-techbd-schema-files/README.md) |
| router (Nexus)   | RouterChannel – routing, CORS, host and mTLS checks | [Documentation](channels/router/README.md) |
| mco (Nexus)      | Index of the MCO file exchange channels            | [Documentation](channels/mco/README.md) |
| mco-esmf (Nexus) | Per-MCO ESMF inbound channels and Aetna SFTP Bridge | [Documentation](channels/mco/mco-esmf/README.md) |
| mco-hrsn (Nexus) | HRSN screening outbound channels (flat file and FHIR) | [Documentation](channels/mco/mco-hrsn/README.md) |
| mco configuration map (Nexus) | BridgeLink configuration map for the MCO channels | [Documentation](channels/mco/configuration-map/README.md) |
| code-templates   | Shared BridgeLink code template libraries          | [Documentation](code-templates/README.md) |
| lookup-manager   | BridgeLink Lookup Manager group exports (Config, Config-sensitive, SchemaFiles) | [Documentation](lookup-manager/README.md) |

## 🏷️ Versioning

[`version.json`](version.json) records the current version, last editor and date of every exported channel, code template, Lookup Manager group and configuration map. Update the matching entry whenever you re-export an artifact.

> **Note:** Some subfolders may contain additional documentation or schema files. Please refer to each subfolder's README for detailed usage.
