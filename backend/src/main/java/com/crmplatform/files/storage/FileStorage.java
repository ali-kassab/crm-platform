package com.crmplatform.files.storage;

import java.time.Duration;

/** Object storage port (INTEGRATION_SPECIFICATION.md). */
public interface FileStorage {

    void put(String key, byte[] content, String contentType);

    void delete(String key);

    /** Short-lived download URL; {@code attachmentName} forces a download with that file name. */
    String presignGet(String key, Duration ttl, String attachmentName);
}
