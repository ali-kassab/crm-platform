package com.crmplatform.files.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f")
    long usedBytes();

    List<StoredFile> findByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    @Query("select f from StoredFile f where f.ownerId is null and f.createdAt < :before")
    List<StoredFile> findOrphansCreatedBefore(Instant before);
}
