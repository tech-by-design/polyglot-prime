# BridgeLink Channel: RouterChannel

This folder contains `RouterChannel.xml`, the BridgeLink channel that serves as the single entry point for 1115 API traffic. It checks the request's origin, host and client certificate, then forwards the request to the matching ingestion channel on `localhost`.

**Version:** 0.2.27 (adds routing for the historical FHIR bundle replay API)

---

## 📦 Channel Overview

| Setting | Value |
|---------|-------|
| Source connector | HTTP Listener |
| Port | `9000` |
| Health check | `GET /healthcheck` → `200 {"status":200,"message":"OK"}` |
| Destinations | `response_operationoutcome` (HTTP Sender, `POST ${destinationUrl}`) for every path except `/tenants`; `tenant_api` (HTTP Sender, `GET ${destinationUrl}`) for `/tenants` only |

---

## 🔀 Routing Table

The source transformer reads the request `contextPath` and sets `destinationUrl`. A trailing `/` is accepted on every path.

| Request Path | Forwarded To | Target Channel |
|--------------|--------------|----------------|
| `/Bundle` | `http://localhost:9003/Bundle` | FhirBundleSubmission |
| `/tenants` | `http://localhost:9003/tenants` | FhirBundleSubmission |
| `/Bundle/$validate` | `http://localhost:9001/Bundle/$validate` | FhirBundleValidate |
| `/Bundle/replay` | `http://localhost:9005/Bundle/replay` | FhirBundleReplay |
| `/historical-replay/Bundle` | `http://localhost:9007/historical-replay/Bundle` | FhirBundleHistoricalReplay |
| `/ccda/Bundle`, `/ccda/Bundle/$validate` | `http://localhost:9002/...` | TechBD CCD Workflow |
| `/flatfile/csv/Bundle`, `/flatfile/csv/Bundle/$validate` | `http://localhost:9004/...` | FlatFileCsvBundle |
| `/hl7v2/Bundle`, `/hl7v2/Bundle/$validate` | `http://localhost:9006/...` | TechBD HL7 Workflow |

For HL7v2 paths, the router rejects multipart requests that carry no file part with a `400` OperationOutcome before forwarding (the "zero-file guard").

---

## 🛡 Security Filters

The source filter runs two rules, in order:

1. **`CORS-preflight-and-headers`**
   - Echoes `Access-Control-Allow-Origin` only when the `Origin` header appears in the `ALLOWED_ORIGINS` list. Arbitrary origins are never reflected.
   - Answers `OPTIONS` preflight requests directly, without routing them downstream or running the mTLS check.
   - Allows the `X-TechBD-*`, `X-SHIN-NY-IG-Version` and `Content-Type` request headers.

2. **`MTLS-client-cert-validation`**
   - Returns `400 Invalid Host header` when the `Host` header is not in `ALLOWED_HOSTS`.
   - Handles `GET /healthcheck`. Other methods on that path return `405`.
   - Skips the certificate check when `X-TechBD-Source-Type: CSV`.
   - For all other requests, calls `performCertValidation("chain", "Util-sbx-bl-net.JKS", null)` from the [SSL Certificate Validation](../../code-templates/README.md#ssl-certificate-validation) code template. It validates the client certificate chain that the AWS ALB forwards in `X-Amzn-Mtls-Clientcert-Chain`. When validation fails, the router returns `401` with an OperationOutcome (`code: security`).

---

## 🔐 Configuration (Lookup Manager)

| Group | Key | Purpose |
|-------|-----|---------|
| `Config` | `ALLOWED_ORIGINS` | Comma-separated CORS origin allow-list |
| `Config` | `ALLOWED_HOSTS` | Comma-separated `Host` header allow-list |

---

## 🚀 Deployment Notes

- Deploy the target channels (ports `9001`–`9007`) on the same BridgeLink instance, because the router forwards to `localhost`.
- Import the **SSL Certificate Validation** and **TechBD Functions** code template libraries before deploying, since the filter uses `performCertValidation` and `setErrorResponse`.
- The truststore `Util-sbx-bl-net.JKS` must be available to BridgeLink.

---

## 🔗 Related Documentation

- [1115 Waiver Channels](../1115/README.md)
- [Code Templates](../../code-templates/README.md)
- [Lookup Manager](../../lookup-manager/README.md)
- [BridgeLink (Nexus) Channels](../README.md)
- [Integration Artifacts Index](../../README.md)
