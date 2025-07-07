package org.techbd.service.aws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Service
public class AwsService {

    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AwsService(Region region) {
        this.sqsClient = SqsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Public method to fetch the S3 file referenced by an SQS message from a given queue.
     */
    public Optional<File> fetchS3FileFromQueue(String queueUrl) {
        return readMessageFromQueue(queueUrl)
                .flatMap(message -> extractS3PathFromMessage(message)
                        .map(s3Path -> {
                            deleteMessageFromQueue(queueUrl, message);
                            return downloadFileFromS3(s3Path);
                        }));
    }

    /**
     * Public method to read one message from the given queue.
     */
    public Optional<Message> readMessageFromQueue(String queueUrl) {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(10)
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(0));
    }

    /**
     * Extracts the s3ObjectPath from the message body.
     */
    public Optional<String> extractS3PathFromMessage(Message message) {
        try {
            JsonNode root = objectMapper.readTree(message.body());
            String s3Path = root.path("s3ObjectPath").asText(null);
            if (s3Path != null && s3Path.contains("/")) {
                return Optional.of(s3Path);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse SQS message: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Downloads the S3 object identified by s3ObjectPath as a File.
     */
    public File downloadFileFromS3(String s3Path) {
        try {
            String bucket = s3Path.substring(0, s3Path.indexOf("/"));
            String key = s3Path.substring(s3Path.indexOf("/") + 1);

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            File tempFile = Files.createTempFile("s3-", "-" + new File(key).getName()).toFile();

            try (OutputStream out = new FileOutputStream(tempFile)) {
                s3Object.transferTo(out);
            }

            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to download S3 object: " + s3Path, e);
        }
    }

    /**
     * Deletes the specified message from the given queue.
     */
    public void deleteMessageFromQueue(String queueUrl, Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }
}
