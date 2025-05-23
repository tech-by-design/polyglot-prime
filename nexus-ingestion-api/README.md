# Nexus Ingestion API

This Spring Boot controller handles large file and JSON-based ingestion requests, saving payloads and metadata to **Amazon S3**, and publishing notifications to **Amazon SQS**.

## Features

- Handles FHIR and CCDA `Bundle` JSON POST requests.
- Handles `multipart/form-data` file uploads (`.zip` only).
- Stores incoming payloads in AWS S3 with metadata.
- Publishes ingestion notifications to AWS SQS FIFO queue.
- Automatically generates metadata for each request.
- Graceful error handling and logging.

## Endpoints

### 1. FHIR / CCDA JSON Bundles

**POST** `/Bundle`, `/Bundle/`,  
**POST** `/Bundle/$validate`, `/Bundle/$validate/`,  
**POST** `/ccda/Bundle`, `/ccda/Bundle/`,  
**POST** `/ccda/Bundle/$validate`, `/ccda/Bundle/$validate/`

#### Request
- **Headers**:
  - `x-tenant-id` – (optional) Tenant identifier
  - `User-Agent` – (optional) Source system identifier
  - `Content-Disposition` – (optional) Filename hint
- **Body**: Raw JSON payload of FHIR/CCDA bundle

#### Response
```json
{
  "messageId": "sqs-message-id",
  "interactionId": "uuid",
  "fullS3Path": "s3://bucket/path/to/file.json",
  "timestamp": "millisecondsSinceEpoch"
}
```

---

### 2. CSV ZIP Bundle Upload

**POST** `/flatfile/csv/Bundle`,  
**POST** `/flatfile/csv/Bundle/$validate`

#### Request
- **Content-Type**: `multipart/form-data`
- **Form Field**: `file` – must be a `.zip` file
- **Headers**:
  - `x-tenant-id` – (optional)
  - `User-Agent` – (optional)

#### Response
```json
{
  "messageId": "sqs-message-id",
  "interactionId": "uuid",
  "fullS3Path": "s3://bucket/path/to/file.zip",
  "timestamp": "millisecondsSinceEpoch"
}
```

---

## Running the application
Run the command `mvn clean compile spring-boot:run` to start the application. It will be using the default port `8080`.


## Metadata Saved to S3

For each request, a metadata file is saved with the following structure:

```json
{
  "tenantId": "example-tenant",
  "interactionId": "uuid",
  "msgType": "fhir | ccda | csv",
  "uploadDate": "yyyy-MM-dd",
  "timestamp": "millisecondsSinceEpoch",
  "fileName": "original_filename",
  "fileSize": "bytes",
  "sourceSystem": "Mirth Connect",
  "s3ObjectPath": "s3://bucket/path/to/file",
  "headers": [
    { "headerName": "value" },
    ...
  ]
}
```

---

## SQS Notification Payload

A message is sent to the FIFO queue with:

```json
{
  "tenantId": "example-tenant",
  "interactionId": "uuid",
  "requestUrl": "/Bundle",
  "msgType": "fhir",
  "timestamp": "millisecondsSinceEpoch",
  "fileName": "original_filename",
  "fileSize": "bytes",
  "s3ObjectId": "path/to/file",
  "s3ObjectPath": "s3://bucket/path/to/file"
}
```

---

## Exception Handling

If any error occurs during processing, a JSON response is returned:

```json
{
  "error": "Error message"
}
```

---

## Configuration & Dependencies

Make sure the following are configured:

- `Constants.BUCKET_NAME` – Target S3 bucket name
- `Constants.FIFO_Q_URL` – SQS FIFO queue URL
- AWS credentials must be configured for:
  - `software.amazon.awssdk.services.s3.S3Client`
  - `software.amazon.awssdk.services.sqs.SqsClient`
- `AwsService` should implement:
  - `saveToS3(String bucket, String key, String content, Map<String, String> metadata)`
  - `saveToS3(Map<String, String> headers, MultipartFile file)`

---

## Logging

All errors and key process milestones are logged using `Slf4j`.

---

## Notes

- Only `.zip` files are allowed for multipart upload.
- JSON requests are validated via file extension and endpoint paths.
- Metadata filenames are generated using UUID and timestamp.
- Interaction tracking is implemented via `interactionId`.
