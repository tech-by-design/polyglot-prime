# IngestionAPI

**IngestionAPI** is a source-agnostic data ingestion service that supports multiple transport protocols including **HTTPS**, **MLLP**, and **SOAP**. It is designed to reliably handle healthcare data payloads and integrate seamlessly with downstream AWS services. It is responsible for receiving data over multiple transport protocols, performing protocol-specific processing, and routing the payload to the appropriate S3 bucket and SQS queue based on configurable rules.

---

## High-Level Flow

- Client submits data via one of the supported transports — HTTP POST to `/ingest`, SOAP over `/ws` or `/xds/XDSbRepositoryWS`, or a TCP connection for MLLP or raw-framed messages.
- The service detects the protocol and parses the incoming payload — SOAP envelopes are identified by namespace, MLLP frames by start/end byte markers, and generic TCP by configured delimiters.
- Source metadata such as origin IP, destination port, path parameters, and SOAP headers are resolved to a `PortEntry` that defines routing rules, queue destination, response type, and keep-alive behaviour.
- Every message is assigned a unique `interactionId` (UUID) that is carried through logs, ACKs, S3 object paths, and downstream SQS events for end-to-end correlation.
- The payload is written to an S3 bucket under a structured date-partitioned path and a corresponding metadata JSON is stored alongside it.
- A message is placed on the configured SQS queue with a deterministic `messageGroupId` derived from  destination and source metadata to ensure ordered processing by downstream consumers.
- An acknowledgement is returned to the client — HL7 ACK/NACK for MLLP sessions, a standards-compliant SOAP response for SOAP clients, and a structured JSON response carrying the `interactionId` for HTTP callers.
- Protocol-specific metrics, port lookup results, and processing outcomes are emitted to CloudWatch and can be correlated using the `interactionId`.


## Port Configuration Matching

Every inbound request is matched against a list of port configuration records loaded at startup. In production, the list is fetched from S3 (`PORT_CONFIG_S3_BUCKET`).In the local development environment, it is read from the local file `src/main/resources/list.json`.

### How a PortEntry is Resolved

The system evaluates an ordered list of resolution strategies and returns the first matching configuration for the incoming request.

1. **Source + message type match** — if the request carries both `sourceId` and `msgType` (from the URL path `/{sourceId}/{msgType}`), the entry whose `sourceId` and `msgType` fields match is returned first.
2. **Port match** — if no exact source/type match is found, the first entry whose `port` field matches the inbound destination port is used.
3. **No match** — an `IllegalArgumentException` is thrown and the request is rejected.

### What a PortEntry Controls

Once resolved, the port configuration is applied by evaluating a set of attribute resolvers that populate the RequestContext with all necessary runtime settings. These typically include:

| Field | Purpose |
|-------|---------|
| `queue` | SQS FIFO queue the message is dispatched to |
| `dataDir` | S3 sub-path prefix for the payload object |
| `metadataDir` | S3 sub-path prefix for the metadata json |
| `route` | Inbound route the entry applies to (e.g. `/hold`, `/xds/XDSbRepositoryWS`) |
| `responseType` | ACK format returned to the caller (`mllp`, `pnr`, `pix`, etc.) |
| `protocol` | Transport layer (`HTTP`, `TCP`) |
| `execType` | Processing mode (`sync` or `async`) |
| `keepAliveTimeout` | TCP keep-alive duration in seconds |
| `mtls` | mTLS certificate profile to enforce (if set) |
| `whitelistIps` | CIDR blocks permitted to connect on this port |

### Destination Port Resolution

The `destinationPort` used for `PortEntry` matching is derived differently depending on the transport protocol:

**TCP / MLLP**
The port is read from the **PROXY protocol** header on the inbound TCP connection, which carries the real client and destination address as set by the load balancer.

In a local development environment where no load balancer is present, the PROXY protocol header is absent and dummy values are injected instead:

```java
String dummyClientIp        = "127.0.0.1";
String dummyClientPort      = "12345";
String dummyDestinationIp   = "127.0.0.1";
String dummyDestinationPort = "5555";
```

This means local TCP traffic always resolves against the `PortEntry` configured for port `5555`. In deployed environments, the actual destination port from the PROXY protocol header is used.

**HTTP / HTTPS**
HTTP / HTTPS: The port is read from the `x-forwarded-port` request header, set by the load balancer to indicate the port on which the original request arrived. If the header is absent, the system falls back to the `x-server-port` header. If neither header is present, the request may fail to match a port configuration record.

---
**File Handling and Metadata**  
  Upon receiving any message (regardless of protocol):
  - A unique `interactionId` and a `timestamp` are generated.
  - The file is uploaded to Amazon S3 under a structured path:  
    ```
    s3://<bucket-name>/<datadir-prefix>/data/<YYYY>/<MM>/<DD>/<timestamp>-<interactionId>-<filename>
    ```
  - Metadata is generated and stored as a JSON file:  
    ```
    s3://<bucket-name>/<metadata-prefix>/metadata/<YYYY>/<MM>/<DD>/<timestamp>/<interactionId>/metadata.json
    ```

- **Sample S3 Metadata**
  ```json
  {
  "key": "data/<YYYY>/<MM>/<DD>/<timestamp>-<interactionId>-<fileName>.zip",
  "json_metadata": {
    "headers": "<Map of request headers including IP and Port headers like x-real-ip, x-forwarded-for, x-server-ip, x-server-port>",
    "interactionId": "<UUID>",
    "fileName": "<file-name>.zip",
    "queryParams": null,
    "sourceSystem": "<source-system>",
    "s3DataObjectPath": "s3://<s3-bucket-name>/data/<YYYY>/<MM>/<DD>/<timestamp>-<interactionId>-<fileName>.<fileextension>",
    "s3AckMessageObjectPath": "s3://<s3-bucket-name>/data/<YYYY>/<MM>/<DD>/<timestamp>-<interactionId>-<fileName>-ack.<fileextension>",
    "protocol": "<protocol-version>",
    "uploadDate": "<YYYY-MM-DD>",
    "fileSize": "<file-size-in-bytes>",
    "requestUrl": "/ingest",
    "localAddress": "<local-address>",
    "tenantId": "<tenant-id-or-unknown>",
    "fullRequestUrl": "http://<hostname>/ingest",
    "remoteAddress": "<remote-address>",
    "timestamp": "<epoch-timestamp-in-ms>"
  }
  ```
**Structured Messaging to Amazon SQS**  
  A message is pushed to **Amazon SQS** with a deterministic `messageGroupId` to ensure **FIFO** ordering and grouping.

### Message Group ID

The `messageGroupId` is derived from the message's protocol and available metadata, evaluated in the following order:

| Priority | Protocol / Condition | Group ID |
|----------|----------------------|----------|
| 1 | MLLP | `{qe}_{facility}_{messageCode}_{deliveryType}` (ZNT segment fields; falls back to `destinationPort` if all are absent) |
| 2 | TCP | `{destinationPort}` |
| 3 | `sourceId` and `msgType` present | `{sourceId}_{msgType}` (e.g. `LAB1_ORU`) |
| 4 | `tenantId` present | `{tenantId}` |
| 5 | Fallback | `{sourceIp}_{destinationIp}_{destinationPort}` |

If none of these are available, the group ID defaults to `unknown-tenantId`.

### Sample SQS Message

```json
{
  "tenantId": "<tenantId>",
  "interactionId": "<uuid-interaction-id>",
  "requestUrl": "<originalRequestUrl>",
  "timestamp": "<epochMilliTimestamp>",
  "fileName": "<uploadedFileName>",
  "fileSize": "<fileSizeInBytes>",
  "s3ObjectId": "<s3ObjectKey>",
  "s3DataObjectPath": "s3://<bucketName>/<s3ObjectKey>",
  "s3AckMessageObjectPath": "s3://<bucketName>/<s3AckObjectKey>",
  "messageGroupId": "<sourceIp>_<destinationIp>_<destinationPort>",
  "s3Response": "Uploaded to S3: <s3ObjectKey> (ETag: \"<etagValue>\")"
}
```
### mTLS Validation

In the ALB, mTLS is configured as **pass-through**, meaning the TLS connection is not terminated at the load balancer and the encrypted traffic (including the client certificate) is forwarded directly to the backend service. AWS ALB injects the client certificate into the request using the header **`X-Amzn-Mtls-Clientcert`** (URL-encoded PEM format). The application reads this header, decodes it, and reconstructs the client certificate chain for validation. Based on the resolved port configuration, the application reads the `mtls` value (e.g., `"txd"`) and dynamically builds the S3 CA bundle path (e.g., `txd-bundle.pem` from the configured bucket). It then validates the client certificate chain against this CA bundle using **PKIX validation**, ensuring the certificate is trusted and properly signed. If validation succeeds, the request is marked as verified; otherwise, a **401 Unauthorized** response is returned.

**Sample PortConfig (single line JSON):**

```json
{"port":9010,"responseType":"","protocol":"HTTP","whitelistIps":["0.0.0.0/0"],"execType":"async","mtls":"txd","route":"/","queue":"txd-sbx-util-queue.fifo"}
```

**mTLS Curl Example:**

```bash
curl --cert sandbox-mtls/client-with-chain.crt \
     --key sandbox-mtls/client.key \
     --cacert sandbox-mtls/txd-bundle.pem \
     --location "https://fhir.api.sandbox.techbd.org:9010/ingest" \
     --header "Content-Type: text/xml; charset=utf-8" \
     --header 'SOAPAction: ""' \
     --data '<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
               <SOAP-ENV:Body><test>hello</test></SOAP-ENV:Body>
             </SOAP-ENV:Envelope>'
```
## Ingestion API Endpoints

### HTTP Endpoints

Standard HTTPS endpoints for submitting healthcare payloads. Supports plain raw bodies, multipart file uploads, and SOAP envelopes. mTLS certificates can be presented for authenticated channels.

### /ingest — Raw Payload

Accepts a raw payload. The `Content-Type` header drives how the body is interpreted. The service extracts the payload, records source metadata, and routes it to the configured S3 bucket and SQS queue. The `x-forwarded-port` header is used to determine the source port for routing purposes when behind a load balancer.

```bash
curl --cert sandbox-mtls/client-with-chain.crt \
     --key sandbox-mtls/client.key \
     --cacert sandbox-mtls/txd-bundle.pem \
     --location "https://fhir.api.sandbox.techbd.org:9010/ingest" \
     --header "Content-Type: text/xml; charset=utf-8" \
     --header 'SOAPAction: ""' \
     --data '<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
               <SOAP-ENV:Body><test>hello</test></SOAP-ENV:Body>
             </SOAP-ENV:Envelope>'
```

---

### /ingest — Multipart File

Accepts a file upload as a `multipart/form-data` request. Used when the sending system packages the payload as a file rather than a raw body, such as HL7 v2 flat files or XML. The  file is extracted from the form data and processed similarly to raw payloads, with the original filename and content type recorded in the metadata. The `x-forwarded-port` header is also used for routing decisions.

```bash
curl --location "https://<host>/ingest" \
     --header "x-forwarded-port: 5555" \
     --form "file=@<payload-file>"
```
### SOAP EndPoints
Supports SOAP 1.1 and SOAP 1.2 over HTTPS. IHE-compliant transactions such as PIX, PNR, and XDS are handled through dedicated endpoints. mTLS is used for mutual authentication on all SOAP channels.

### Determining SOAP Version

The SOAP version is identified by inspecting the **namespace in the SOAP envelope** of the incoming XML payload:

* If the payload contains:

  ```
  http://www.w3.org/2003/05/soap-envelope
  ```

  → **SOAP 1.2** is used

* If the payload contains:

  ```
  http://schemas.xmlsoap.org/soap/envelope/
  ```

  → **SOAP 1.1** is used

Relevant schemas and resources used for SOAP/XDS processing can be accessed here:

* [https://drive.google.com/drive/u/0/folders/1w8eCGlwqHfsZzmMfglAxnCNi3hXL2X3K](https://drive.google.com/drive/u/0/folders/1w8eCGlwqHfsZzmMfglAxnCNi3hXL2X3K)

For SOAP message handling and schema binding, JAXB classes are generated using the XJC plugin during the build process:

```xml
<executions>
    <execution>
        <id>xjc</id>
        <goals>
            <goal>xjc</goal>
        </goals>
    </execution>
</executions>
<configuration>
    <sources>
        <source>${project.basedir}/src/main/resources/ITI/schema/HL7V3/NE2008/multicacheschemas</source>
        <source>${project.basedir}/src/main/resources/ITI/schema/IHE</source>
    </sources>
    <outputDirectory>${project.build.directory}/generated-sources/jaxb</outputDirectory>
    <clearOutputDir>false</clearOutputDir>
    <packageName>org.techbd.iti.schema</packageName>
</configuration>
```

This setup ensures that all required HL7v3 and IHE schemas are compiled into Java classes for accurate SOAP message parsing and processing.
### IHE Transactions with Pseudo Samples

* **PIX Add (PRPA_IN201301UV02)** – Create new patient

```xml
<PRPA_IN201301UV02>
  <id extension="123"/>
  <patient><name>John Doe</name></patient>
</PRPA_IN201301UV02>
```

* **PIX Update (PRPA_IN201302UV02)** – Update patient details

```xml
<PRPA_IN201302UV02>
  <id extension="123"/>
  <patient><name>John Updated</name></patient>
</PRPA_IN201302UV02>
```

* **PIX Merge (PRPA_IN201304UV02)** – Merge duplicate patients

```xml
<PRPA_IN201304UV02>
  <patient>
    <id extension="correct-id"/>
    <asOtherIDs><id extension="duplicate-id"/></asOtherIDs>
  </patient>
</PRPA_IN201304UV02>
```

* **PNR (Provide & Register Document Set)** – Used to submit clinical documents

```xml
<soapenv:Envelope>
  <soapenv:Header>
    <wsa:MessageID>urn:uuid:123...</wsa:MessageID>
    <wsa:Action>ProvideAndRegisterDocumentSet-b</wsa:Action>
  </soapenv:Header>
  <soapenv:Body>
    <ProvideAndRegisterDocumentSetRequest>
      <SubmitObjectsRequest>...</SubmitObjectsRequest>
      <Document id="Document01">...</Document>
    </ProvideAndRegisterDocumentSetRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

---

### PIX Response (ACK)

All PIX transactions return an **HL7 v3 acknowledgment (`MCCI_IN000002UV01`)** confirming processing

```xml
<SOAP-ENV:Envelope>
  <SOAP-ENV:Body>
    <MCCI_IN000002UV01>
      <acknowledgement>
        <typeCode code="CA"/>
        <targetMessage>
          <id extension="123"/>
        </targetMessage>
      </acknowledgement>
    </MCCI_IN000002UV01>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

---

### PNR Response (Registry Response)

PNR-style transactions return a **registry response** indicating success/failure:

```xml
<SOAP-ENV:Envelope>
  <SOAP-ENV:Header>
    <wsa:Action>urn:hl7-org:v3:MCCI_IN000002UV01</wsa:Action>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <RegistryResponse status="...:Success"/>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```
## SOAP Request Entry Points
### /ingest/{sourceId}/{msgType}

A parameterised ingest endpoint that allows the source system and message type to be conveyed directly in the URL path rather than derived from the payload or headers. `sourceId` identifies the sending organisation or system; `msgType` indicates the transaction type, enabling the router to apply the correct processing rules and downstream destination.


```bash
curl --location "https://fhir.api.sandbox.techbd.org/ingest/netspective/ws" \
  --cert sandbox-mtls/client-with-chain.crt \
  --key sandbox-mtls/client.key \
  --cacert sandbox-mtls/txd-bundle.pem \
  --header 'Content-Type: multipart/related; type="application/xop+xml"; boundary="Boundary_1"; start="<request@meditech.com>"; start-info="application/soap+xml"' \
  --data-binary "@<PATH_TO_MTOM_SOAP_REQUEST_FILE>"
```
When `msgType` is `pix`, `pnr`, or `ws`, the request is treated as a **SOAP-based IHE transaction**. The request is then internally routed to the `/ws` endpoint for processing. After processing, an **IHE-compliant HL7 acknowledgment (`MCCI_IN000002UV01`)** is generated and returned to the caller.

---

### /ws

A general-purpose SOAP Web Services endpoint handling IHE PIX (Patient Identity Cross-Reference) and PNR (Provide and Register) transactions. The service inspects the SOAP action or body to identify the specific IHE transaction type and routes accordingly. Supports both SOAP 1.1 and SOAP 1.2.

```
curl --location "https://fhir.api.sandbox.techbd.org/ws\
     --header "Content-Type: application/soap+xml" \
     --data "@<PATH_TO_SOAP_REQUEST_FILE
```

---

### /xds/XDSbRepositoryWS
Added for Meditech compatibility (fixed route constraint). This endpoint behaves identically to /ws.

It accepts **IHE XDS.b Provide and Register Document Set-b (ITI-41)** requests as **MTOM-encoded SOAP messages**, where:

* The SOAP envelope contains **ebXML metadata** (patient, document, submission set)
* Clinical documents (e.g., CDA) are included as **XOP attachments**
* Metadata references document content via `xop:Include`

If the incoming request does **not include a valid `Content-Type` header**, the system **dynamically reconstructs it during processing** by:

* Extracting the **MIME boundary** from the raw payload
* Inferring the **root part Content-Type** (typically `application/xop+xml`)
* Resolving the **`start` (Content-ID)** and **`start-info` (SOAP 1.1 vs 1.2)**
* Rebuilding a compliant

  ```
  multipart/related; type=...; boundary=...; start=...; start-info=...
  ```

This ensures the payload can still be correctly parsed as an **MTOM/XOP multipart message**.

```
curl --location "https://fhir.api.sandbox.techbd.org:9010/xds/XDSbRepositoryWS" \
     --cert sandbox-mtls/client-with-chain.crt \
     --key sandbox-mtls/client.key \
     --cacert sandbox-mtls/txd-bundle.pem \
     --data-binary "@TEST_FILES/xds-mtom-test.txt"
```

### TCP Route

The application exposes a configurable TCP route (`TCP_DISPATCHER_PORT`, default: `7980`) to receive inbound traffic over raw TCP. This enables integration with systems that transmit healthcare messages without using HTTP/HTTPS.

---

### MLLP (Minimal Lower Layer Protocol)

Minimal Lower Layer Protocol (MLLP) is the standard framing protocol used to transmit HL7 v2 messages over TCP connections.  
Since TCP is a continuous byte stream (no inherent message boundaries), MLLP defines **explicit delimiters** to indicate the start and end of each message.

### 🔹 MLLP Delimiters

* **Start Block (SB)** → `0x0B`
* **End Block (EB)** → `0x1C`
* **Carriage Return (CR)** → `0x0D`

### 📦 Message Format

```

<SB> HL7_MESSAGE <EB><CR>

```

In bytes:

```

0x0B ...HL7 DATA... 0x1C 0x0D

````

---

## Sample HL7 ACK Response

```text
MSH|^~\&|RECEIVING_APP|RECEIVING_FAC|SENDING_APP|SENDING_FAC|20260317034437||ACK^R01|123456|P|2.3
MSA|AA|123456
````

---

## Raw TCP (Custom Delimiters)

A raw TCP listener is available for systems that do not use MLLP framing. Message boundaries are determined using configurable delimiters.

### 🔹 TCP Delimiters (Configurable)

* **Start Delimiter** → `0x02` (STX)
* **End Delimiter 1** → `0x03` (ETX)
* **End Delimiter 2** → `0x0A` (LF)

### 📦 Message Format

```
<STX> PAYLOAD <ETX><LF>
```

In bytes:

```
0x02 ...PAYLOAD... 0x03 0x0A
```

---

## Configuration Reference

| Environment Variable               | Default           | Description                                            |
| ---------------------------------- | ----------------- | ------------------------------------------------------ |
| `TCP_DISPATCHER_PORT`              | `7980`            | TCP port to listen on                                  |
| `TCP_READ_TIMEOUT_SECONDS`         | `180`             | ReadTimeoutHandler timeout (local only — see NLB note) |
| `TCP_MAX_MESSAGE_SIZE_BYTES`       | `52428800` (50MB) | Max message size                                       |
| `TCP_MESSAGE_START_DELIMITER`      | `0x02` (STX)      | TCP mode start byte                                    |
| `TCP_MESSAGE_END_DELIMITER_1`      | `0x03` (ETX)      | TCP mode end byte 1                                    |
| `TCP_MESSAGE_END_DELIMITER_2`      | `0x0A` (LF)       | TCP mode end byte 2                                    |
| `TCP_SESSION_LOG_INTERVAL_SECONDS` | `60`              | Heartbeat log interval (0=off)                         |


---

## Feature Flags

| Flag                           | Effect                                                                      |
| ------------------------------ | --------------------------------------------------------------------------- |
| `SEND_HL7_ACK_ON_IDLE_TIMEOUT` | Sends an MLLP NACK before closing on **both** idle timeout and read timeout |
| `ADD_NTE_SEGMENT_TO_HL7_ACK`   | Includes an NTE segment with `interactionId` in the HL7 ACK                 |
| `LOG_INCOMING_MESSAGE`         | Logs full raw content when no delimiter is detected  (ONLY FOR TCP)         |

---

## Managing Feature Flags

### Enable (include NTE segment in ACK)

```
curl -X POST http://<hostname>:<port>/api/features/ADD_NTE_SEGMENT_TO_HL7_ACK/enable
```

### Disable (omit NTE segment from ACK)

```
curl -X POST http://<hostname>:<port>/api/features/ADD_NTE_SEGMENT_TO_HL7_ACK/disable
```

### Check current status

```
curl -X GET http://<hostname>:<port>/api/features/ADD_NTE_SEGMENT_TO_HL7_ACK
```
### Additional References

To set up LocalStack for emulating AWS services locally, refer to [support/nexus-ingestion-api/README.md](../support/nexus-ingestion-api/README.md).

For more details on connection establishment and negative acknowledgment sent see [TCP.md](./TCP.md).


