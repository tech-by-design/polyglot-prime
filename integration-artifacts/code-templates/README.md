# BridgeLink Code Templates

This folder contains the BridgeLink **Code Template** exports shared across the channels in [`channels/`](../channels/README.md). Import these before deploying channels that call their functions, or the channel scripts will fail with `ReferenceError`s.

---

## 📦 Templates

| File | Library | Version | Functions | Used by |
|------|---------|---------|-----------|---------|
| [`TechBD Functions.xml`](TechBD%20Functions.xml) | TechBD Functions | 0.1.0 | `setErrorResponse`, `createJsonResponse` | All 1115 channels and RouterChannel |
| [`UuidUtil.xml`](UuidUtil.xml) | UuidUtil | 0.1.0 | `getUuidGenerator`, `generateUuid` | All 1115 channels and RouterChannel |
| [`SSL Certificate Validation.xml`](SSL%20Certificate%20Validation.xml) | SSL Certificate Validation | 0.1.0 | `performCertValidation`, `validateClientCertificate`, `validateCertificateChain`, `validateCertificateLeaf`, `validateCertificateSubject`, `simpleValidation`, `updateRequestHeaderSize` | RouterChannel |
| [`MCO Code Template.xml`](MCO%20Code%20Template.xml) | *(single template)* `insertLog` | 0.1.0 | `insertErrorLog` | All MCO ESMF channels |

---

## 📄 Template Details

### TechBD Functions

- **`setErrorResponse(statusCode, errorMessage, map)`** sets the response status, content type and JSON error body on the response map.
- **`createJsonResponse(status, message)`** builds a JSON response payload.

### UuidUtil

- **`getUuidGenerator()`** lazily creates a time-ordered (UUIDv7) generator with `com.fasterxml.uuid.Generators.timeBasedEpochGenerator()` and caches it in `globalMap` under `UUID_GENERATOR`.
- **`generateUuid()`** returns a new UUID from that generator. The channels use it for interaction IDs.

### SSL Certificate Validation

This library validates mTLS client certificates that the AWS Application Load Balancer forwards in the `X-Amzn-Mtls-Clientcert-Chain` header.

- **`performCertValidation(validationType, truststoreName, allowedPattern)`** is the entry point called from RouterChannel's filter, as `performCertValidation("chain", "Util-sbx-bl-net.JKS", null)`. It URL-decodes the header and validates it against the named truststore.
- **`validateClientCertificate`**, **`validateCertificateChain`**, **`validateCertificateLeaf`** (certificate pinning), **`validateCertificateSubject`** (Subject DN / SAN pattern matching) and **`simpleValidation`** are the individual strategies.
- **`updateRequestHeaderSize()`** is a deploy-script helper that raises Jetty's request header limit above the default 8 KB. A forwarded certificate chain with two or more intermediates can exceed that limit and cause `HTTP 431` errors.

### MCO Code Template

- **`insertErrorLog(batchId, batchDetailsId, fileName, mcoId, error, rootFolder, mcoFolder)`** writes a processing error to `mco_data.mco_record_error_logs`. It reads the database credentials from Lookup Manager (`Config-sensitive`: `MC_JDBC_URL`, `MC_JDBC_USERNAME`, `MC_JDBC_PASSWORD`) and the error types from the `errorTypes` configuration map key.

Unlike the other files, this export is a single `<codeTemplate>` rather than a `<codeTemplateLibrary>`. Import it into an existing library, or create one for it.

---

## 🚀 Import

In the BridgeLink Administrator, open **Channels → Edit Code Templates → Import Libraries** (or **Import Code Templates** for `MCO Code Template.xml`). Then make sure each library is enabled for the channels listed above.

---

## 🔗 Related Documentation

- [RouterChannel](../channels/router/README.md)
- [1115 Waiver Channels](../channels/1115/README.md)
- [MCO Channels](../channels/mco/README.md)
- [Integration Artifacts Index](../README.md)
