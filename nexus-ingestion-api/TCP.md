# NettyTcpServer – TCP Connection & Message Processing Overview

## Server Configuration & Connection Lifecycle

The `NettyTcpServer` is a Netty-based TCP server starting on a configurable port `TCP_DISPATCHER_PORT`(default `7980`) that supports both MLLP (HL7) and generic TCP delimited message protocols. Each incoming TCP connection is assigned a unique **session ID** at `initChannel` time, and the pipeline is initialized with a `ReadTimeoutHandler`, an optional `HAProxyMessageDecoder` (in non-development environments), a custom `DelimiterBasedFrameDecoder`, and a main message handler. When a connection is established, the HAProxy protocol header is parsed first to extract the real client and destination IP/port, which are stored on the channel and used to resolve a `keepAliveTimeout` from port configuration — if found, the `ReadTimeoutHandler` is swapped for an `IdleStateHandler` to support persistent connections. The `DelimiterBasedFrameDecoder` inspects the first byte of each incoming frame to distinguish MLLP-wrapped HL7 messages (starting with `0x0B`) from TCP-delimited messages (configurable via environment variables), accumulating fragments until the matching end markers are found.

## Port Configuration & Protocol Routing

Once a complete frame is assembled, the server resolves the matching **`PortEntry`** from port configuration using the destination port and source IP extracted from the HAProxy header. The `PortEntry`'s `responseType` field determines how the message is processed: if `responseType` is `"mllp"` or `"outbound"`, the message is treated as an MLLP/HL7 message; otherwise it is handled as a generic TCP message. For ports configured as `"outbound"`, the server additionally expects a **ZNT segment** to be present in the HL7 message — if it is missing after both HAPI-based and manual extraction attempts, a NACK is sent immediately and the message is not processed further. The `keepAliveTimeout` from the resolved `PortEntry` also controls whether the channel is kept open after a response (keep-alive mode) or closed after sending response.

## Sample TCP Port Configurations

```json
{"port":2575,"responseType":"outbound","protocol":"TCP","keepAliveTimeout":200}
```

**Description**
Configuration for traffic on port `2575` over TCP, indicating that the connection is handled as a raw TCP/MLLP stream. With `responseType = outbound`, the system expects inbound messages to be HL7 messages wrapped with MLLP delimiters, validates message structure and required segments, and returns a NACK if validation fails (for example, when required segments like `ZNT` are missing). The `keepAliveTimeout` defines how long (in seconds) the connection remains open after a response is sent, allowing multiple messages over the same TCP connection; if no data is received within this duration, the connection is closed.

---

```json
{"port":1610,"responseType":"tcp","protocol":"TCP","route":"/hold","queue":"txd-sbx-outbound-queue.fifo","dataDir":"/outbound","metadataDir":"/outbound"}
```

**Description**
Configuration for traffic on port `1610` using raw TCP with `responseType = tcp`, where message boundaries are determined using configured TCP delimiters instead of MLLP. Incoming payloads are processed and routed to the `txd-sbx-outbound-queue.fifo` SQS queue, with traffic mapped to the `/hold` route. Since the route is `/hold`, payloads are written to the hold bucket instead of the default S3 bucket, while still using the `/outbound` prefix for both data and metadata storage.

---

```json
{"port":2574,"responseType":"mllp","protocol":"TCP","dataDir":"/inbound","metadataDir":"/inbound"}
```

**Description**
Configuration for traffic on port `2574` using `responseType = mllp`, where inbound messages must be wrapped with standard MLLP delimiters. The system processes HL7 messages using MLLP framing and stores payloads and metadata under the `/inbound` prefix in S3. Messages are routed to the default queue, and unlike the outbound flow, segments such as `ZNT` are not validated in this configuration.

## Sample HL7 messaage with ZNT segment:

```
MSH|^~\&||GHC|||||ORU^R01|||2.5|
PID||5003637762^^^HEALTHELINK:FACID^MRN|5003637762^^^HEALTHELINK:FACID^MRN ||Cheng^Agnes^Brenda||19700908|F|Cheng^Agnes^^|9|3695 First Court^^Larchmont^KY^23302^USA^^^DOUGLAS||282-839-3300^P^PH||ENG|SINGLE|12|5433165929|185-10-7482|||||||||||N
PV1||O|||||C1^Smith^Sid^^^^^^GHC|||||||EO|||||GHC_V1|||||||||||||||||||||||||20111022094500|20111022094500|
ORC||GHC-P1|GHC-F1||||^^^201110100910||201110100912|||C1^Smith^Sid^^^^^^GHC|GHC||||||||GHC||||||||LAB|
OBR||GHC-P1|GHC-F1|RGL^Random Glucose^L|||201110101214|||||||201110100937||C1^Smith^Sid^^^^^^GHC||||||201110101227|||F|
OBX||NM|GLU^GLUCOSE||12.3|mmol/l|70-99||||F|||201110101214|
ZNT||ORU|R01|SN_ORU|ClinicianGroupSN|CohortGroupSN_Name^CohortGroupSN_Description||healthelink:GHC|5003637762^healthelink:EGSMC~64654645^healthelink:CHG|68cc652a10ae317aef21b255||
```

## Message Processing

For **HL7/MLLP messages**, the server unwraps the MLLP envelope, parses the HL7 payload using HAPI, generates an ACK, optionally extracts ZNT segment fields (message code, delivery type, facility, QE), and delegates to `MessageProcessorService`. For **generic TCP messages**, it trims the raw payload and generates a simple pipe-delimited ACK string. In both cases, if the received message framing conflicts with the port's configured protocol (e.g., TCP delimiters received on an MLLP-configured port or vice versa), processing is rejected with an appropriate NACK and the payload is stored for diagnostics.

## Session ID, Interaction ID & Persistent Connection Tracking

### Session ID

A **session ID** is a UUID generated once at `initChannel` time — the moment a TCP connection is accepted — and remains fixed for the entire lifetime of that connection. It is stored on the channel via `SESSION_ID_KEY` and included in log line emitted by the server (connection open, message received, timeout, error, connection close), making it possible to correlate all activity for a given TCP connection across the full log stream regardless of how many messages flow through it.

### Interaction ID

An **interaction ID** is a separate UUID scoped to a single message, not the connection. It is lazily assigned the first time a frame is read on the channel (inside `channelRead0` or the decoder), stored via `INTERACTION_ATTRIBUTE_KEY`, and travels with the message through parsing, ZNT extraction, `MessageProcessorService`, and the ACK/NACK response. On a one-shot (non-keep-alive) connection the interaction ID and session ID effectively have the same lifespan. On a persistent keep-alive connection, after a response is successfully flushed, the interaction ID is reset to `null` so the next inbound message on the same channel receives a fresh UUID — while the session ID remains unchanged throughout.

### Message Count on Persistent Connections

For keep-alive connections, the server tracks how many messages have been fully processed on a session using an `AtomicInteger` stored via `SESSION_MESSAGE_COUNT_KEY`, initialised to zero at `initChannel` time and incremented each time a complete frame is handed off to the main handler. This counter is reported in the `MESSAGE_FULLY_RECEIVED` log entry as `sessionMessageCount`, giving a running total of messages processed on that TCP session. When the connection eventually closes, the `TCP_SESSION_CLOSED` log entry records the final `totalMessagesProcessed` value alongside the total session duration, providing a complete picture of how heavily a persistent connection was used. Session-level attributes — session ID, session start time, and message count — are intentionally never cleared by `clearChannelAttributes()`; they are only nulled out inside `channelInactive` so the final close log always has accurate values even when cleanup runs just before `ctx.close()`.

---

## NACK Scenarios

NACKs are returned in the following situations:

- **Timeout issues** – Read timeout lapses or connection remains idle beyond the configured duration.
- **Transport / channel failures** – TCP-level exceptions or connection interruptions.
- **Message validation failures** – Payload too large, missing required segments (e.g., ZNT).
- **Protocol mismatch** – Incorrect message framing (MLLP vs TCP) based on port configuration.
- **Internal processing errors** – Unexpected failures during message processing.

---

## MLLP NACK Scenarios (HL7 ACK Format)

### 1. Read Timeout Exceeded

Occurs when no data is received on the channel for `TCP_READ_TIMEOUT_SECONDS`. Applies when no `keepAliveTimeout` is configured on the port entry.
> Sent only if feature flag `SEND_HL7_ACK_ON_IDLE_TIMEOUT` is enabled; otherwise the connection closes silently.

```
MSH|^~\&|SERVER|LOCAL|CLIENT|REMOTE|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|UNKNOWN|Read timeout: No complete message received within 180 seconds
ERR|||207^Application internal error^HL70357||E|||Read timeout occurred
```

---

### 2. Idle Timeout Exceeded

Occurs when no data is received beyond the `keepAliveTimeout` configured on the port entry.
> Sent only if feature flag `SEND_HL7_ACK_ON_IDLE_TIMEOUT` is enabled; otherwise the connection closes silently.

```
MSH|^~\&|SERVER|LOCAL|CLIENT|REMOTE|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|UNKNOWN|Read idle timeout: No data received within 60 seconds
ERR|||207^Application internal error^HL70357||E|||Idle timeout occurred
```

---

### 3. Channel Exception

Raised due to low-level TCP/channel failures.

```
MSH|^~\&|SERVER|LOCAL|CLIENT|REMOTE|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|UNKNOWN|Channel exception: <sanitized error message>
ERR|||207^Application internal error^HL70357||E|||Channel exception occurred
```

---

### 4. Message Size Exceeded

When the incoming payload exceeds the configured size limit (`TCP_MAX_MESSAGE_SIZE_BYTES`).

```
MSH|^~\&|SERVER|LOCAL|CLIENT|REMOTE|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|<msgControlId>|Message size 55000000 bytes exceeds maximum allowed size of 52428800 bytes
ERR|||207^Application error^HL70357||E|||Message size 55000000 bytes exceeds maximum allowed size
```

---

### 5. Missing ZNT Segment

When the required ZNT segment is absent on a port configured as `"outbound"`.

```
MSH|^~\&|<receivingApp>|<receivingFac>|<sendingApp>|<sendingFac>|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|<msgControlId>|Missing ZNT segment
ERR|||207^Application error^HL70357||E|||Missing ZNT segment
```

---

### 6. Internal Processing Error

Occurs due to server-side exceptions during message processing.

```
MSH|^~\&|<receivingApp>|<receivingFac>|<sendingApp>|<sendingFac>|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|<msgControlId>|<exception message>
ERR|||207^Application error^HL70357||E|||<exception message truncated to 80 chars>
```

---

### 7. TCP Delimiter Found, MLLP Expected

When the received message framing does not match the port's configured protocol.

```
MSH|^~\&|<receivingApp>|<receivingFac>|<sendingApp>|<sendingFac>|2025-01-15T10:00:00Z||ACK|a1b2c3d4e5f6g7h8i9j0|P|2.5
MSA|AR|<msgControlId>|As per port configuration, expecting HL7 message with MLLP wrappers. Received message delimited with TCP delimiters instead.
ERR|||207^Application error^HL70357||E|||As per port configuration, expecting HL7 message with MLLP wrappers
```

---

## TCP Generic NACK Scenarios (Pipe-Delimited Format)

### 1. Message Size Exceeded

```
NACK|InteractionId^<uuid>|ErrorTraceId^<traceId>|ERROR|Message size 55000000 bytes exceeds maximum allowed size of 52428800 bytes|2025-01-15T10:00:00Z
```

---

### 2. Internal Processing Error

```
NACK|InteractionId^<uuid>|ErrorTraceId^<traceId>|ERROR|<sanitized exception message>|2025-01-15T10:00:00Z
```

---

### 3. MLLP Wrapper Found, TCP Expected

```
NACK|<interactionId>|<errorTraceId>|MLLP_WRAPPER_FOUND_EXPECTED_TCP|As per port configuration, expecting TCP-delimited message. Received message with MLLP wrappers instead.|2025-01-15T10:00:00Z
```