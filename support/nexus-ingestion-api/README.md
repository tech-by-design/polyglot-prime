# LocalStack Setup & Usage (S3 + SQS)

## Overview

LocalStack is a local AWS cloud emulator that allows you to run services like **S3** and **SQS** on your machine.  
It is used for development/testing without connecting to real AWS.

All services are exposed on:

```

[http://localhost:4566](http://localhost:4566)

````

---

## 1. Create `docker-compose.yml`

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
````

---

## 2. Install AWS CLI (Required)

```bash
# Update packages
sudo apt update

# Install dependencies
sudo apt install unzip curl -y

# Download AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"

# Unzip and install
unzip awscliv2.zip
sudo ./aws/install

# Verify installation
aws --version

# Cleanup (optional)
rm -rf aws awscliv2.zip
```

---

## 3. Create `setup-localstack.sh`

> Replace `<LOCALSTACK_DIR>` with your actual directory path
> (example: `/home/<user>/workspaces/localstack`)

```bash
#!/bin/bash

# Navigate to LocalStack folder
cd <LOCALSTACK_DIR> || {
    echo "Folder not found! Update <LOCALSTACK_DIR>"
    exit 1
}

echo "Starting Docker Compose..."
docker compose up -d

# Export LocalStack AWS environment variables
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export LOCALSTACK_ENDPOINT=http://localhost:4566

echo "Creating SQS queues..."
aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
  --queue-name txd-sbx-main-queue.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
  --queue-name test.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
  --queue-name txd-sbx-ccd-queue.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
  --queue-name txd-sbx-hold-queue.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
  --queue-name txd-sbx-util-queue.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

echo "Creating S3 buckets..."
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://local-sbx-nexus-ingestion-s3-bucket
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://local-sbx-nexus-ingestion-s3-metadata-bucket
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://local-pdr-txd-sbx-hold
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://local-pdr-txd-sbx-temp

echo "Setup completed successfully!"
```

---

## 4. Run Setup

```bash
chmod +x setup-localstack.sh
./setup-localstack.sh
```

---

## Environment (Required for Manual Commands)

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export LOCALSTACK_ENDPOINT=http://localhost:4566
```

---

## SQS Commands

### View Messages

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs receive-message \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/txd-dev-hold-queue.fifo \
  --max-number-of-messages 10 \
  --visibility-timeout 0 \
  --wait-time-seconds 0
```

---

### Clear Queue

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs purge-queue \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/txd-dev-hold-queue.fifo
```

---

## S3 Commands

### List Contents

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls \
  s3://local-sbx-nexus-ingestion-s3-bucket/error/2026/01/14/
```

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls \
  s3://local-sbx-nexus-ingestion-s3-bucket/outbound/data/2025/11/26/
```

---

### Download File

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp \
  s3://local-metadata-bucket/metadata/2025/10/07/92b84940-1862-4c2b-a4dc-5c51a6b18075_1759818558675_metadata.json \
  ./localfile.json
```

---

### Clear Bucket

```bash
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm \
  s3://local-pdr-txd-sbx-hold \
  --recursive
```
