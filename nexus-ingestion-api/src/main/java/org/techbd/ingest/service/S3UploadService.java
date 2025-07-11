package org.techbd.ingest.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class S3UploadService {

    private final S3Client s3Client;

    public S3UploadService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadFile(String key, String bucketName, MultipartFile file, Map<String, String> metadata) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(metadata)
                .build();

        PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        return "Uploaded to S3: " + key + " (ETag: " + response.eTag() + ")";
    }

    public void uploadStringContent(String bucketName, String fileName, String content, Map<String, String> metadata) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .metadata(metadata)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(contentBytes));
    }
}

