package com.crmplatform.files.domain;

import com.crmplatform.files.FileKind;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "stored_files")
@SQLRestriction("deleted_at is null")
public class StoredFile extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FileKind kind;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "owner_type", length = 30)
    private String ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "attached_at")
    private Instant attachedAt;

    protected StoredFile() {
    }

    public StoredFile(FileKind kind, String storageKey, String originalName, String contentType,
            long sizeBytes, String checksumSha256, UUID uploadedBy) {
        this.kind = kind;
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.uploadedBy = uploadedBy;
    }

    public boolean isAttached() {
        return ownerId != null;
    }

    public boolean isOwnedBy(String type, UUID id) {
        return type.equals(ownerType) && id.equals(ownerId);
    }

    public void attachTo(String type, UUID id, Instant at) {
        this.ownerType = type;
        this.ownerId = id;
        this.attachedAt = at;
    }

    public FileKind getKind() {
        return kind;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }
}
