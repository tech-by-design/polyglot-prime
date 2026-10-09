# MCO HRSN Outbound Channels (OB from SHIN-NY)

This folder contains the BridgeLink channels that deliver **HRSN (Health-Related Social Needs) screening data** from NYeC / SHIN-NY to Managed Care Organizations. There are two delivery paths: daily flat-file extracts, and individual FHIR Bundles.

---

## 📁 Folder Structure

```
mco-hrsn/
├── 01-pick-up-hrsn-flat-files-from-nyec.xml   # Step 1 – poll NYeC for control files
├── 02-deliver-flat-files-to-mcos.xml          # Step 2 – validate and deliver data files to the MCO
├── deliver-fhir-bundles-to-mcos.xml           # FHIR Bundle delivery (HTTP, port 8002)
└── channel-groups/
    └── mco-ob-from-shin-ny.xml                # "MCO - OB from SHIN-NY" channel group (all three)
```

---

## 📦 Channels

| Channel | File | Version | Source | Tag |
|---------|------|---------|--------|-----|
| 01 - Pick up HRSN Flat files from NYeC | [01-pick-up-hrsn-flat-files-from-nyec.xml](01-pick-up-hrsn-flat-files-from-nyec.xml) | 0.1.1 | SFTP File Reader, every 60 s | `flatfile` |
| 02 - Deliver Flat Files to MCOs | [02-deliver-flat-files-to-mcos.xml](02-deliver-flat-files-to-mcos.xml) | 0.1.0 | Channel Reader (fed by channel 01) | `flatfile` |
| Deliver FHIR Bundles to MCOs | [deliver-fhir-bundles-to-mcos.xml](deliver-fhir-bundles-to-mcos.xml) | 0.1.0 | HTTP Listener, port `8002` | `fhir` |

---

## 🔄 Flat-File Flow (01 → 02)

### 01 - Pick up HRSN Flat files from NYeC
- Polls `${screeningdlHost}/outbound/mco/screenings/dailyfiles` over SFTP for files that match `(?i)^.*Control.*$`.
- Validates the control file name (`<MCO_CODE>_control_<YYYYMMDD>.txt`), its header (`controlFileHeader`), and its delimiters. It also checks that the MCO has a preference record in `screening_extracts.mco_screening_preferences`.
- Records the extract in `screening_extracts.mco_screening_extracts` with status `INPROGRESS`, or `ERROR` when validation fails.
- Valid control files are passed to channel 02 (`Destination 1`, Channel Writer). Errors are written to `${errorFilePath}` (`Destination 2`).

### 02 - Deliver Flat Files to MCOs
- For each data file listed in the control file, fetches the file from the NYeC inbound folder and checks its header against the expected header for its type:

  | File type | Configuration key |
  |-----------|-------------------|
  | Control | `controlFileHeader` |
  | Patient | `patientFileHeader` |
  | Screening | `screnningFileHeader` |
  | Screening response | `screeningResponseFileHeader` |
  | Consent | `consentFileHeader` |
  | Referral | `referralFileHeader` |
  | Assessment | `assessmentFileHeader` |

- Sets the extract status to `VALIDATED` or `ERROR`.
- When every file is valid, copies the files to the MCO's `dailyFiles` folder and archives them to `processFiles`, using the per-MCO paths in `screeningLocation`. Errors are written to `${errorFilePath}`.

---

## 🔄 FHIR Bundle Flow

**Deliver FHIR Bundles to MCOs** listens on port `8002` for a JSON request that contains `attributed_source` (the MCO code) and a bundle ID. The channel:

1. Checks that `attributed_source` is configured in `screeningLocation` (with `bundleOutbound` and/or `errorFiles`) and that the MCO is in the screening preference list.
2. Reads the Bundle from the data lake (`Read Bundle From DLAKE`, HTTP Sender to `${fhirQuery}`).
3. Writes it to `${sftpDropLocation}` as `<MCO>_hrsnob_<yyyymmdd>_<bundleId>.json`. On failure it writes `Error_<MCO>_hrsnob_<yyyymmddhhmmss>.json` to the MCO's error folder.

---

## 🔐 Configuration

These channels read their settings from the [configuration map](../configuration-map/README.md#hrsn-channels):

- **Database**: `jdbdcDriver`, `jbdcUrl`, `dbName`, `dbPassword`
- **NYeC screening SFTP (source)**: `screeningdlHost`, `screeningdlPort`, `screeningdlUserName`, `screeningdlPassword`, `screeningdlPrivatekey`
- **MCO SFTP (destination)**: `sftpIp`, `sftpPort`, `sftpUserName`, `sftpPassword`
- **Routing**: `screeningLocation` is a JSON map keyed by MCO code (plus `NYEC`) with folder paths such as `inbound`, `dailyFiles`, `temp`, `processFiles`, `bundleOutbound` and `errorFiles`
- **Validation**: the `*FileHeader` keys, `screeningErrorCodes`, `filePart` and `errorFileHeader`

---

## 🚀 Deployment Notes

- Import [`channel-groups/mco-ob-from-shin-ny.xml`](channel-groups/mco-ob-from-shin-ny.xml) to deploy all three channels together. Channel 01 routes to channel 02 by channel ID (`8231df95-a7fa-4367-bd07-a3f892d9cd51`), so deploy both.
- The `screening_extracts` schema and the `techbd_udi_ingress.tenants` table must exist in the target database.

---

## 🔗 Related Documentation

- [MCO ESMF Channels](../mco-esmf/README.md)
- [MCO Configuration Map](../configuration-map/README.md)
- [MCO Channels](../README.md)
- [Integration Artifacts Index](../../../README.md)
