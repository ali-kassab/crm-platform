package com.crmplatform.files.storage;

import com.crmplatform.shared.error.ApiException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 adapter used for Amazon S3 and S3-compatible stores (RustFS locally, S3Mock in tests).
 * Objects are private; downloads go through presigned URLs (ADR-0009).
 */
@Component
class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;

    S3FileStorage(StorageProperties props) {
        this.props = props;
        AwsCredentialsProvider credentials = props.accessKey() == null || props.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build();
        var clientBuilder = S3Client.builder().region(Region.of(props.region())).credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        var presignerBuilder = S3Presigner.builder().region(Region.of(props.region())).credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            clientBuilder.endpointOverride(URI.create(props.endpoint()));
            presignerBuilder.endpointOverride(URI.create(props.publicEndpoint() == null || props.publicEndpoint().isBlank()
                    ? props.endpoint() : props.publicEndpoint()));
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @PostConstruct
    void ensureBucket() {
        if (!props.createBucket()) {
            return;
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            log.info("Created storage bucket {}", props.bucket());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            } else {
                log.warn("Could not verify storage bucket {}: {}", props.bucket(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("Storage unavailable at startup: {}", e.getMessage());
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(contentType)
                    .build(), RequestBody.fromBytes(content));
        } catch (RuntimeException e) {
            log.error("Storage upload failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "File storage is temporarily unavailable");
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
    }

    @Override
    public String presignGet(String key, Duration ttl, String attachmentName) {
        GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(props.bucket()).key(key);
        if (attachmentName != null) {
            String encoded = URLEncoder.encode(attachmentName, StandardCharsets.UTF_8).replace("+", "%20");
            get.responseContentDisposition("attachment; filename*=UTF-8''" + encoded);
        }
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl)
                .getObjectRequest(get.build()).build()).url().toString();
    }
}
