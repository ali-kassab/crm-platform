package com.crmplatform.files;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public API of the files module. Owning modules attach uploaded files to their records; a file
 * belongs to exactly one owner (EC-37) and is deleted from storage when detached.
 */
public interface Files {

    /**
     * Attaches files to an owner. Every id must exist in the active workspace, be of the expected
     * kind and be unattached or already attached to this same owner.
     *
     * @param field request field name used in validation errors
     */
    void attach(Collection<UUID> fileIds, FileKind expectedKind, String ownerType, UUID ownerId, String field);

    /** Deletes files that belong to the owner but are no longer in {@code keep}. */
    void detachAllExcept(String ownerType, UUID ownerId, Collection<UUID> keep);

    /** Deletes the file from storage and marks it deleted. */
    void delete(UUID fileId);

    Map<UUID, FileSummary> summaries(Collection<UUID> fileIds);

    List<FileSummary> ownedBy(String ownerType, UUID ownerId);

    /** {@code purpose} may be null; {@code hasThumbnail} is true for images (small WebP preview). */
    record FileSummary(UUID id, FileKind kind, String name, String contentType, long sizeBytes, FilePurpose purpose,
            boolean hasThumbnail) {
    }
}
