package com.example.TextileManagement.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class PrivateObjectStorageService {
    private final boolean enabled;
    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public PrivateObjectStorageService(@Value("${app.storage.mode:database}") String mode,
            @Value("${app.storage.bucket:}") String bucket,
            @Value("${app.storage.region:us-east-1}") String region,
            @Value("${app.storage.endpoint:}") String endpoint,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.secret-key:}") String secretKey) {
        this.enabled = "s3".equalsIgnoreCase(mode);
        this.bucket = bucket == null ? "" : bucket.trim();
        if (!enabled) {
            client = null;
            presigner = null;
            return;
        }
        if (this.bucket.isBlank() || isBlank(region) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new IllegalStateException("Private object storage is not configured");
        }
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(credentials);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(credentials);
        if (!isBlank(endpoint)) {
            URI endpointUri = URI.create(endpoint.trim());
            S3Configuration configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
            clientBuilder.endpointOverride(endpointUri).serviceConfiguration(configuration);
            presignerBuilder.endpointOverride(endpointUri).serviceConfiguration(configuration);
        }
        client = clientBuilder.build();
        presigner = presignerBuilder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void put(String key, byte[] data, String contentType) {
        requireEnabled();
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(data));
    }

    public byte[] read(String key) {
        requireEnabled();
        return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    public void delete(String key) {
        requireEnabled();
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public URI signedGet(String key) {
        requireEnabled();
        GetObjectRequest getObject = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getObject)
                .build();
        return URI.create(presigner.presignGetObject(request).url().toString());
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Private object storage is disabled");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
