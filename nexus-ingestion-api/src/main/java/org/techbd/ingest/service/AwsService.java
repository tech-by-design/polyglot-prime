package org.techbd.ingest.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Service
public class AwsService {

    private final S3Client s3Client;

    public AwsService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String extractTenantId(Map<String, String> headers) {
        // return headers.getFirst(Constants.REQ_HEADER_TENANT_ID); // Using Constants.TENANT_ID_HEADER
        return headers.getOrDefault(Constants.REQ_HEADER_TENANT_ID, Constants.DEFAULT_TENANT_ID);
    }

    public Map<String, String> extractMetadata(MultipartFile file) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("Content-Type", file.getContentType());
        metadata.put("Original-Filename", file.getOriginalFilename());
        metadata.put("Size", String.valueOf(file.getSize()));
        return metadata;
    }

    public String saveToS3(String key,Map<String, String> headers, MultipartFile file, Map<String, String>  metadata) {
        String tenantId = extractTenantId(headers);
        // Map<String, String> metadata = extractMetadata(file);

        try {
            // String key = "uploads/" + tenantId + "/" + file.getOriginalFilename();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(Constants.BUCKET_NAME) // Using Constants.BUCKET_NAME
                    .key(key)
                    .metadata(metadata)
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromBytes(file.getBytes())
            );

            return "File uploaded successfully to S3: " + key + " (ETag: " + response.eTag() + ")";
        } catch (IOException e) {
            throw new RuntimeException("Error uploading file to S3", e);
        }
    }

    public void saveToS3(String bucketName, String fileName, String content, Map<String, String> metadata) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .metadata(metadata)
                .build();

        s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(contentBytes));
    }
}
