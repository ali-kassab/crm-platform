package com.crmplatform.files;

import java.util.UUID;

/**
 * Lets an owning module decide whether a user limited to their own work may open a file attached
 * to one of its records (e.g. operations: files of jobs and visits assigned to the technician).
 * Users with {@code files:read} skip this check; everyone else may open only files they uploaded
 * or files an implementation explicitly allows.
 */
public interface FileOwnerAccess {

    /** @return true if {@code userId} may read files owned by {@code (ownerType, ownerId)} */
    boolean canRead(String ownerType, UUID ownerId, UUID userId);
}
