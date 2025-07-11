# 🧪 LocalStack Setup & Integration with Java AWS SDK

This guide walks through setting up [LocalStack](https://localstack.cloud/) for local development and testing of AWS services such as S3 and SQS. It also includes Java code examples for interacting with these services using the AWS SDK v2, with no Spring `@ConfigurationProperties` setup—just direct hardcoded configuration for local testing.

---

## ✅ Prerequisites

- Docker installed and running
- Ubuntu 22.04 / WSL2 environment
- Java 17+ or 21 (for AWS SDK v2 compatibility)
- Internet access

---

## 🔧 1. Pull LocalStack Docker Image

```bash
docker pull localstack/localstack
```

---

## 🚀 2. Start LocalStack using Docker Compose

Ensure your `docker-compose.yml` has at least:

```yaml
version: "3.8"

services:
  localstack:
    container_name: localstack
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3,sqs
      - DEBUG=1
      - DOCKER_HOST=unix:///var/run/docker.sock
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
```

Then start:

```bash
docker-compose up --build
```

Wait for the log: `localstack | Ready.`

---

## 🔁 3. Update Package Index & Tools

```bash
sudo apt update
sudo apt install unzip curl -y
```

---

## 📥 4. Install AWS CLI v2

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
aws --version  # Verify installation
rm -rf aws awscliv2.zip  # Clean up
```

---

## 🪣 5. Create S3 Bucket in LocalStack

```bash
export LOCALSTACK_ENDPOINT=http://localhost:4566

aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://local-bucket
```

---

## 📬 6. Create FIFO SQS Queue in LocalStack

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
  --queue-name local-queue.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true
```

---

## 📂 7. List Created Resources

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls
aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs list-queues
```

---

## 🔗 8. Access Resources from Java 

The following Java methods show how to:

1. Upload files to LocalStack S3
2. Send a message to LocalStack SQS FIFO queue


### 📤 uploadToS3 – Upload a file to S3

```java
public void uploadToS3(MultipartFile file) throws Exception {
    try {
        String region = "us-east-1";
        String bucket = "local-bucket";
        String endpoint = "http://localhost:4566";

        String originalFilename = file.getOriginalFilename();
        String key = "xxx_" + (originalFilename != null ? originalFilename : "unnamed");
        byte[] content = file.getBytes();

        S3Client s3 = S3Client.builder()
                .endpointOverride(new URI(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClient(UrlConnectionHttpClient.create())
                .forcePathStyle(true)
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(content));

        System.out.println("✅ Uploaded to S3 bucket: " + bucket + " with key: " + key);
    } catch (Exception e) {
        System.err.println("❌ Failed to upload to S3: " + e.getMessage());
        throw e;
    }
}
```

---

### 📤📨 uploadToS3AndSqs – Upload to S3 and Send to SQS

```java
public void uploadToS3AndSqs(MultipartFile file) throws Exception {
    try {
        String region = "us-east-1";
        String bucket = "local-bucket";
        String s3Endpoint = "http://localhost:4566";
        String sqsQueueUrl = "http://localhost:4566/000000000000/local-queue.fifo";

        String originalFilename = file.getOriginalFilename();
        String key = "xxx_" + (originalFilename != null ? originalFilename : "unnamed");
        byte[] content = file.getBytes();

        S3Client s3 = S3Client.builder()
                .endpointOverride(new URI(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClient(UrlConnectionHttpClient.create())
                .forcePathStyle(true)
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(content));

        SqsClient sqs = SqsClient.builder()
                .endpointOverride(new URI(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClient(UrlConnectionHttpClient.create())
                .build();

        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(new String(content))
                .messageGroupId("upload-group")
                .messageDeduplicationId(UUID.randomUUID().toString())
                .build());

        System.out.println("📨 Message sent to SQS: " + sqsQueueUrl);
    } catch (Exception e) {
        System.err.println("❌ Failed to upload/send: " + e.getMessage());
        throw e;
    }
}
```

---

## 📚 Resources

- [LocalStack Docs](https://docs.localstack.cloud/)
- [AWS SDK v2 for Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [AWS CLI Install Guide](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-linux.html)