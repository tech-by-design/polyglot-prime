# MCO Configuration Map

This folder contains `configuration_map_export.properties`, the BridgeLink **Configuration Map** export used by the [MCO channels](../README.md). Channels read these values with `configurationMap.get("<key>")`, or reference them as `${key}` in connector settings.

> ⚠️ **Secrets:** treat this file as a template. Credentials (database, SFTP and SMTP passwords and private keys) should be set on the target BridgeLink server or in Lookup Manager's `Config-sensitive` group, and real values should not be committed here.

---

## 🚀 Import

In the BridgeLink Administrator, open **Settings → Configuration Map → Import** and select `configuration_map_export.properties`. Redeploy the MCO channels so they pick up the new values.

---

## 🔑 Keys

### Shared

| Key | Purpose |
|-----|---------|
| `jdbdcDriver`, `jbdcUrl`, `dbName`, `dbPassword` | PostgreSQL connection (HRSN channels; ESMF channels read the URL and credentials from Lookup Manager) |
| `smtpHostName`, `smtpPort`, `smtpUsername`, `smtpPassword`, `smtpFromEmail` | SMTP settings for alert emails |
| `alertEmails` | Recipients of file-rejection/error alerts |
| `emailList` | JSON recipient list for ESMF status emails |
| `sftpIp`, `sftpPort`, `sftpUserName`, `sftpPassword` | MCO-facing SFTP endpoint |

### ESMF channels

| Key | Purpose |
|-----|---------|
| `startDate`, `endDate` | Day-of-month bounds of the monthly submission window (the window may wrap across a month end) |
| `allowMultipleFiles` | When `false`, a second file in the same reporting month is rejected (`EC082`) |
| `chunkSize` | Number of lines processed per batch |
| `thresholdValue` | Error ratio above which a file is marked `ERRORED` |
| `errorCodes`, `errorTypes` | JSON maps of validation error codes/descriptions and error types |
| `reprocessableErrors`, `reprocessUrl` | JSON list of technical errors that trigger an automatic reprocess call, and the endpoint called |
| `countyCode` | JSON list of valid county codes (from the ESMF specification) |
| `isTesting` | Marks the environment as internal/testing when building alert emails |
| `nyecSftpDns`, `nyecSftpPort`, `nyecSftpUsername`, `nyecSftpPrivateKey` | NYeC data lake SFTP that receives valid member data |
| `aetnaInboundSftpHost`, `aetnaInboundSftpPort`, `aetnaInboundSftpUsername`, `aetnaInboundSftpPassword`, `aetnaInboundSftpFolder` | Aetna's own inbound SFTP (used by the Aetna SFTP Bridge) |
| `aetnaSftpDebugLogging` | Turns on JVM-wide JSch debug logging when `true` |

### HRSN channels

| Key | Purpose |
|-----|---------|
| `screeningdlHost`, `screeningdlPort`, `screeningdlUserName`, `screeningdlPassword`, `screeningdlPrivatekey` | NYeC screening SFTP (HRSN source) |
| `screeningLocation` | JSON map of per-MCO folder paths (`inbound`, `dailyFiles`, `temp`, `processFiles`, `bundleOutbound`, `errorFiles`, ...) |
| `controlFileHeader`, `patientFileHeader`, `screnningFileHeader`, `screeningResponseFileHeader`, `consentFileHeader`, `referralFileHeader`, `assessmentFileHeader` | Expected pipe-delimited headers for each HRSN file type |
| `errorFileHeader` | Header for generated error files |
| `screeningErrorCodes` | JSON map of HRSN validation error codes |
| `filePart` | JSON list of the data-file types expected in a control file |

### Keys not used by the exported channels

`deleteControlFileFromInbound` is defined in the export, but none of the channels in this repository read it.

### Keys read by channels but missing from this export

Add these on the server if the corresponding feature is used:

| Key | Read by |
|-----|---------|
| `sftpPassword` | ESMF channels, `02-deliver-flat-files-to-mcos.xml` |
| `nyecSftpPassphrase` | ESMF channels (passphrase for `nyecSftpPrivateKey`) |
| `amidaCareSftpDebugLogging` | Amida Care ESMF channel |

---

## 🔗 Related Documentation

- [MCO ESMF Channels](../mco-esmf/README.md)
- [MCO HRSN Outbound Channels](../mco-hrsn/README.md)
- [Lookup Manager](../../../lookup-manager/README.md)
- [MCO Channels](../README.md)
- [Integration Artifacts Index](../../../README.md)
