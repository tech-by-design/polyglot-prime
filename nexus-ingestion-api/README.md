# IngestionAPI Overview

**IngestionAPI** is a source-agnostic data ingestion service that supports multiple transport protocols including **HTTPS**, **MLLP**, and **SOAP**. It is designed to reliably handle healthcare data payloads and integrate seamlessly with downstream AWS services.

## Key Features

- **Multi-Protocol Support**  
  Accepts messages over:
  - **HTTPS (REST)**
  - **MLLP** (commonly used for HL7v2 messages)
  - **SOAP** (for legacy systems integration)

- **Automatic Acknowledgement Generation**  
  The system automatically generates and returns **well-structured acknowledgements (ACKs)** upon receiving a message:
  - **MLLP**: HL7 ACK messages
  - **SOAP**: Standards-compliant SOAP responses
  - **HTTPS**: Structured HTTP JSON responses

  These acknowledgements confirm receipt and ensure interoperability with external systems.

- **File Handling and Metadata**  
  Upon receiving any message (regardless of protocol):
  - A unique `interactionId` and a `timestamp` are generated.
  - The file is uploaded to Amazon S3 under a structured path:  
    ```
    s3://<bucket-name>/data/<YYYY>/<MM>/<DD>/<timestamp>-<interactionId>-<filename>
    ```
  - Metadata is generated and stored as a JSON file:  
    ```
    s3://<bucket-name>/metadata/<YYYY>/<MM>/<DD>/<timestamp>/<interactionId>/metadata.json
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
- **Structured Messaging to Amazon SQS**  
  A message is pushed to **Amazon SQS** with a deterministic `messageGroupId` to ensure **FIFO** ordering and grouping.

- **SQS Message Group ID Construction**  
  Each message sent to SQS includes a `messageGroupId` that uniquely identifies the source-destination network context. This helps maintain ordered processing within a group.

  **Message Group ID Logic:**
  ```text
  messageGroupId = <source_ip>_<destination_ip>_<port>

  #### Source IP

  The source IP is extracted from request headers, in the following order:

  - `x-forwarded-for` (primary source, typically set by reverse proxies like NGINX or load balancers)
  - Fallback to `x-real-ip` if `x-forwarded-for` is absent

  #### Destination IP and Port

  These are obtained from internal headers added by the gateway or internal networking layer:

  - `x-server-ip`
  - `x-server-port`

  This combination of client and destination IP/port forms a stable and traceable message group identity, used to construct the `messageGroupId`.
  ```

  **Note** : Accurate population of these headers by upstream infrastructure is crucial to maintain correct SQS grouping behavior.


- **Sample SQS Message**
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

- **HTTPS Upload**
  - Accepts `multipart/form-data` with zip or data files.
  - Payloads can be tested via the following cURL:

  ```bash
  curl -X POST http://<hostname>/ingest \
    -H "X-TechBD-Tenant-ID: <TENANT_ID>" \
    -F "file=@<PATH_TO_ZIP_FILE>;type=application/zip"
  ```

  - Health check:

  ```bash
  curl http://<hostname>/
  ```
  
- **MLLP (Minimal Lower Layer Protocol)**
  - The application listens on ports defined via the `HL7_MLLP_PORTS` environment variable:

  ```bash
  export HL7_MLLP_PORTS=2575,2576-2580
  ```

  - Apache Camel routes are dynamically created during deployment for these ports.
  - HL7 messages received via MLLP will receive an **ACK** with an **InteractionID** included in the `NTE` segment:

  ```
  MSH|^~\&|ReceivingApp|ReceivingFac|SendingApp|SendingFac|20250723103935.413+0000||ACK^A01^ACK|301|P|2.5.1
  MSA|AA|MSG00001
  NTE|1||InteractionID: 522a160f-e56e-4e9f-a412-17a63ce89da5
  ```

  - Health check for MLLP:

  ```bash
  curl http://<hostname>/actuator/health/mllp
  ```
     - Example Response for health check***
      ```
      {
        "status": "UP",
        "details": {
          "MLLP Ports": {
            "Port 2575": "UP",
            "Port 2576": "UP",
            "Port 2577": "UP"
          }
        }
      }
      ```

  **Note** :This health check only verifies that the application is actively listening on the expected port.
    It does not validate whether the network layer is correctly provisioned to accept traffic to this port —
    including configurations such as NLB, security groups, firewall rules, or routing settings.


- **SOAP Endpoints**
  - SOAP routes support **PIX** and **PNR** requests.
  - Standard SOAP acknowledgements are returned.

- **Feature Flags via Togglz**
  Togglz is used to dynamically enable or disable features at runtime.

- **🔍 DEBUG_LOG_REQUEST_HEADERS**
  Logs all incoming HTTP headers when enabled. Useful for debugging HTTPS/MLLP issues.

  ```yaml
  togglz:
    enabled: true
    features:
      DEBUG_LOG_REQUEST_HEADERS:
        enabled: true
  ```

  If  `DEBUG_LOG_REQUEST_HEADERS` is enabled headers available in request can be viewed in **CloudWatch** using either the `interactionId` or by searching `DEBUG_LOG_REQUEST_HEADERS`.

- **📲 Togglz REST API Endpoints**

  | Endpoint | Description |
  |----------|-------------|
  | `GET /api/features/{featureName}` | Check status of a feature |
  | `POST /api/features/{featureName}/enable` | Enable a feature |
  | `POST /api/features/{featureName}/disable` | Disable a feature |

  **Example Usage**:

  ```bash
  curl -X POST http://<hostname>:8080/api/features/DEBUG_LOG_REQUEST_HEADERS/enable
  ```