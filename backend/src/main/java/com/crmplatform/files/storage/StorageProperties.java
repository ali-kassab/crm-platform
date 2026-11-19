package com.crmplatform.files.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param endpoint       S3 API endpoint for an S3-compatible store; blank for Amazon S3
 * @param publicEndpoint endpoint browsers can reach for presigned URLs (defaults to {@code endpoint})
 * @param createBucket   create the bucket at startup (local development only)
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("crm-files") String bucket,
        String accessKey,
        String secretKey,
        @DefaultValue("true") boolean pathStyle,
        @DefaultValue("false") boolean createBucket) {
}
