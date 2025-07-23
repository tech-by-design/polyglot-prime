# BridgeLink Channel: AwsSqsFifoQueueListener

This BridgeLink (Mirth Connect) channel listens to an AWS SQS FIFO queue, retrieves incoming messages and routes the data to other Mirth channels.

---

## 📦 Channel Overview

### **AwsSqsFifoQueueListener**
- **Type**: Polling JavaScript Receiver
- **Polling Frequency**: Every 10 seconds
- **Environment Variables Required**:
  - `AWS_REGION`
  - `AWS_SQS_URL`

---

## 🔧 Functionality

### ✅ Source Connector (JavaScript Reader)
- Uses AWS SDK v2 to:
  - Poll the configured AWS SQS FIFO queue.
  - Retrieve at most **1 message per poll**, with a 10-second wait time.
  - Log message body and receipt handle.
  - Delete the message from the queue after processing.
- Stores the following in `globalChannelMap`:
  - `sqsMsg` — the raw message body.
  - `receiptHandle` — for deletion.
  - `sqsMessageObject` — full message metadata.

### 📤 Destination Connector (JavaScript Writer)
- **Primary Tasks**:
  - Parses message to locate `s3Path` (or `fullS3Path` / `s3ObjectPath`).
  - Downloads the file content from the given S3 path using `S3Client`.
  - Logs file name and contents.
  - Optionally forwards the enriched message (with `fetchedContent`) to another channel using `router.routeMessage(...)` (currently commented out).
- **AWS SDK v2 usage**:
  - `S3Client` is built with region hardcoded to `us-east-1`.
  - Extracts S3 bucket and key from `s3Path`.

---

## 🌐 AWS Configuration

This channel assumes the AWS credentials are configured via environment or default provider chain.

### Required Environment Variables

| Variable        | Description                       |
|----------------|-----------------------------------|
| `AWS_REGION`    | AWS region (e.g., `us-east-1`)    |
| `AWS_SQS_URL`   | Full URL of the SQS FIFO queue    |

### Optional Message Fields

| Field             | Description                               |
|------------------|-------------------------------------------|
| `fullS3Path` / `s3ObjectPath` | Path to S3 object to be downloaded |
| `interactionId`   | Logged for debugging purposes              |

---

## 📁 Example Message Format

```json
{
  "tenantId": "TENANT_ID",
  "interactionId": "uuid",
  "requestUrl": "REQUEST_URL",
  "msgType": "MESSAGE_TYPE",
  "timestamp": "millisecondsSinceEpoch",
  "fileName": "original_filename",
  "fileSize": "bytes",
  "s3ObjectId": "path/to/file",
  "s3ObjectPath": "s3://bucket/path/to/file"
}

## Requirements

- BridgeLink 4.5.3+
- Properly configured Nexus environment

---
