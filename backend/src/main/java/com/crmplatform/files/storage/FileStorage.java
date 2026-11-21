package com.crmplatform.files.storage;

import java.time.Duration;

/** Object storage port (INTEGRATION_SPECIFICATION.md). */
public interface FileStorage {

    void put(String key, byte[] content, String contentType);

    void delete(String key);

    /** The stored bytes, for server-side use (e.g. embedding a photo in a PDF). */
    byte[] get(String key);

    /** Short-lived download URL; {@code attachmentName} forces a download with that file name. */
    String presignGet(String key, Duration ttl, String attachmentName);

    /** Whether the bucket answers a metadata request right now (no object is listed or read). */
    boolean reachable();
}
