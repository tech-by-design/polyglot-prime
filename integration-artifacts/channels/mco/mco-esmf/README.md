# MCO ESMF Channels

This folder contains the BridgeLink channels that ingest the monthly **ESMF** member file from each Managed Care Organization. Each MCO has its own channel built from a shared template. The channel validates the file and every member record, writes success/rejection receipts back to the MCO, records the batch in the `mco_data` schema, and streams valid data to the NYeC data lake SFTP.

---

## 📁 Folder Structure

```
mco-esmf/
├── <MCO>.xml                    # One ESMF channel per MCO (22 channels)
├── aetna-sftp-bridge.xml        # Aetna SFTP Bridge – pulls Aetna files into the landing bucket
└── channel-groups/
    └── mco-esmf.xml             # "MCO - ESMF" channel group containing all 23 channels
```

---

## 📦 MCO Channels

Every MCO channel polls its landing folder `${s3BucketName}/bridgelink-inbound/mco-sftp-<folder>` every **60 seconds** (File Reader, S3 scheme) and deletes the source file after processing.

| Channel | File | MCO Code | Landing Folder | Version |
|---------|------|----------|----------------|---------|
| Aetna | [Aetna.xml](Aetna.xml) | `AET` | `mco-sftp-aetna` | 0.1.16 |
| Amida Care | [Amida Care.xml](Amida%20Care.xml) | `AMI` | `mco-sftp-amida_care` | 0.1.17 |
| Anthem | [Anthem.xml](Anthem.xml) | `ANT` | `mco-sftp-anthem_elevance_health` | 0.1.16 |
| CDPHP | [CDPHP.xml](CDPHP.xml) | `CDP` | `mco-sftp-cdphp` | 0.1.17 |
| Elder Plan | [Elder Plan.xml](Elder%20Plan.xml) | `ELD` | `mco-sftp-elder_plan` | 0.1.17 |
| Emblem | [Emblem.xml](Emblem.xml) | `EMB` | `mco-sftp-emblem` | 0.1.17 |
| Excellus | [Excellus.xml](Excellus.xml) | `EHP` | `mco-sftp-excellus` | 0.1.18 |
| Fidelis | [Fidelis.xml](Fidelis.xml) | `FID` | `mco-sftp-fidelis` | 0.1.18 |
| Hamaspik | [Hamaspik.xml](Hamaspik.xml) | `HAM` | `mco-sftp-hamaspik` | 0.1.18 |
| Healthfirst | [Healthfirst.xml](Healthfirst.xml) | `HEF` | `mco-sftp-healthfirst_hypen` | 0.1.17 |
| Independent Health | [Independent Health.xml](Independent%20Health.xml) | `IDP` | `mco-sftp-independent_health` | 0.1.17 |
| Metroplus | [Metroplus.xml](Metroplus.xml) | `MPS` | `mco-sftp-metroplus` | 0.1.17 |
| Molina | [Molina.xml](Molina.xml) | `MOL` | `mco-sftp-molina` | 0.1.18 |
| MVP | [MVP.xml](MVP.xml) | `MVP` | `mco-sftp-mvp` | 0.1.18 |
| Nascentia | [Nascentia.xml](Nascentia.xml) | `NAS` | `mco-sftp-nascentia` | 0.1.17 |
| Riverspring | [Riverspring.xml](Riverspring.xml) | `RSG` | `mco-sftp-elderserve` | 0.1.17 |
| TxD | [TxD.xml](TxD.xml) | `TXD` | `mco-sftp-txd` | 0.1.17 |
| United | [United.xml](United.xml) | `UTD` | `mco-sftp-united` | 0.1.17 |
| Village Care | [Village Care.xml](Village%20Care.xml) | `VIC` | `mco-sftp-villagecare` | 0.1.17 |
| VNS | [VNS.xml](VNS.xml) | `VNS` | `mco-sftp-vns` | 0.1.17 |
| Wellpoint | [Wellpoint.xml](Wellpoint.xml) | `WHB` | `mco-sftp-wellpoint_highmark` | 0.1.17 |
| Wellpoint NYS | [Wellpoint NYS.xml](Wellpoint%20NYS.xml) | `WPN` | `mco-sftp-wellpoint_ny` | 0.1.17 |

### Aetna SFTP Bridge

[`aetna-sftp-bridge.xml`](aetna-sftp-bridge.xml) (v0.1.4) is a JavaScript Reader that polls every 60 seconds. It:

1. Logs in to Aetna's own inbound SFTP with a username and password (`aetnaInboundSftp*` keys), scans for new files and copies the first one it finds into `${s3BucketName}/${mcoSftpS3Folder}/esmf/inbound`. The **Aetna** channel watches that location.
2. Archives the source file on Aetna's SFTP so it is not copied again.
3. Emails an alert if login, scan or copy fails. When no file is found, it checks whether a "file missing" alert is needed for the current reporting window.

Set `aetnaSftpDebugLogging=true` in the configuration map to turn on JSch protocol logging. The logger is JVM-wide, so turn it off again once the connection issue is diagnosed.

---

## 🔄 Processing Workflow

1. **File name validation.** The file name must be `<MCO_CODE>_MDESMF_<YYYYMMDDHHMMSS>.txt`. Resubmissions use an `RS_` prefix and a new timestamp. A resubmission is rejected if the original file is not on record or has already been fully processed.
2. **Duplicate and window checks.** The `mco_data.mco_records` table is checked for an existing file in the current reporting month. The reporting window comes from the `startDate`/`endDate` configuration keys and may wrap across a month end. Unless `allowMultipleFiles=true`, a second file in the same window is rejected (`EC082`).
3. **Chunked record validation.** The pipe-delimited file is read in batches of `chunkSize` lines. Every record is checked: CIN, name, DOB, address, county, postal code (5 digits), plan ID (5 digits), line of business (8 digits), enrollment dates, EPOP and chronic-condition flags, waiver flags, phone numbers and PCP NPI. Error codes and descriptions come from the `errorCodes` configuration key.
4. **Status and threshold.** The batch status is set to `FULLY_PROCESSED` or `PARTIALLY_PROCESSED`. It is set to `ERRORED` when the error ratio exceeds `thresholdValue`. Counts are written to `mco_records` / `mco_record_details`, and errors to `mco_record_error_logs`.
5. **Delivery.**
   - Valid data is streamed over SSH-key SFTP to the NYeC data lake (`nyecSftp*` keys), into a folder named after the reporting month (for example, `Oct-2026`).
   - Success receipts go to `mco-sftp-<folder>/esmf/outbound/success`, and rejection/error files go to `.../esmf/outbound/error`. Copies are archived under `bridgelink-inbound/mco-sftp-<folder>/mco_success` and `mco_error`.
   - Email alerts are sent through the `smtp*` keys for file rejection, file error and successful processing.

---

## 🔐 Configuration

- **Lookup Manager (`Config-sensitive`)**: `MC_JDBC_URL`, `MC_JDBC_USERNAME`, `MC_JDBC_PASSWORD`, `MC_S3_BUCKET_NAME`, `MCO_SFTP_PRIVATE_KEY`, `MCO_SFTP_KEY_PASSPHRASE`, `MCO_DESTINATION_SFTP_PRIVATE_KEY`, `MCO_DESTINATION_SFTP_KEY_PASSPHRASE`. These are loaded in the deploy script.
- **Lookup Manager (`Config`)**: `ROCHESTER_RHIO_SFTP_KEY_PATH`.
- **Configuration map**: see [configuration-map/README.md](../configuration-map/README.md#esmf-channels).

---

## 🚀 Deployment Notes

- Import the [MCO Code Template](../../../code-templates/README.md#mco-code-template) (`insertErrorLog`) before deploying.
- To add a new MCO, copy an existing channel. Then change `mco_code` and `mco_sftp_folder` in the source transformer's `setMcoInfoInGlobalMap()`, the File Reader host, and every S3 destination path. Also add an entry to [`version.json`](../../../version.json).
- Import [`channel-groups/mco-esmf.xml`](channel-groups/mco-esmf.xml) to deploy all ESMF channels together.

---

## 🔗 Related Documentation

- [MCO HRSN Outbound Channels](../mco-hrsn/README.md)
- [MCO Configuration Map](../configuration-map/README.md)
- [MCO Channels](../README.md)
- [Integration Artifacts Index](../../../README.md)
