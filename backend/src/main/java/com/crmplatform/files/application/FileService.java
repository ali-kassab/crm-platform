package com.crmplatform.files.application;

import com.crmplatform.files.FileKind;
import com.crmplatform.files.FileOwnerAccess;
import com.crmplatform.files.FilePurpose;
import com.crmplatform.files.Files;
import com.crmplatform.shared.security.Permissions;
import org.springframework.beans.factory.ObjectProvider;
import com.crmplatform.files.domain.StoredFile;
import com.crmplatform.files.domain.StoredFileRepository;
import com.crmplatform.files.storage.FileStorage;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.ratelimit.RateLimiter;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Uploads, downloads and lifecycle of stored files (FR-FIL-01..04). */
@Service
public class FileService implements Files {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    static final Duration URL_TTL = Duration.ofMinutes(5);
    static final int MAX_FILES_PER_REQUEST = 10;

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final FileInspector inspector;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final ObjectProvider<FileOwnerAccess> ownerAccess;

    public FileService(StoredFileRepository files, FileStorage storage, FileInspector inspector, RateLimiter rateLimiter,
            Clock clock, ObjectProvider<FileOwnerAccess> ownerAccess) {
        this.ownerAccess = ownerAccess;
        this.files = files;
        this.storage = storage;
        this.inspector = inspector;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public List<StoredFile> upload(FileKind kind, List<Upload> uploads) {
        return upload(kind, null, uploads);
    }

    @Transactional
    public List<StoredFile> upload(FileKind kind, FilePurpose purpose, List<Upload> uploads) {
        if (uploads.isEmpty() || uploads.size() > MAX_FILES_PER_REQUEST) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Upload between 1 and 10 files per request");
        }
        UUID userId = CurrentUser.id();
        rateLimiter.check("upload:" + userId, 30, Duration.ofMinutes(1));
        UUID workspaceId = TenantContext.requireWorkspaceId();
        List<StoredFile> saved = new ArrayList<>();
        List<String> writtenKeys = new ArrayList<>();
        for (Upload upload : uploads) {
            FileInspector.Inspected inspected = inspector.inspect(kind, upload.name(), upload.content());
            String key = workspaceId + "/" + kind.name().toLowerCase() + "/" + UUID.randomUUID();
            storage.put(key, inspected.content(), inspected.contentType());
            writtenKeys.add(key);
            String thumbKey = null;
            if (inspected.thumbnail() != null) {
                thumbKey = key + "-thumb";
                storage.put(thumbKey, inspected.thumbnail(), "image/webp");
                writtenKeys.add(thumbKey);
            }
            StoredFile file = new StoredFile(kind, key, inspected.name(), inspected.contentType(),
                    inspected.content().length, sha256(inspected.content()), userId);
            file.describe(purpose, thumbKey);
            saved.add(files.save(file));
        }
        onRollback(() -> writtenKeys.forEach(this::deleteQuietly));
        return saved;
    }

    /**
     * Reads a file the caller may see. Members with {@code files:read} see every file of the
     * workspace; others (technicians) only files they uploaded or files of records an owning module
     * allows them ({@link FileOwnerAccess}). Anything else is 404, so ids don't leak.
     */
    @Transactional(readOnly = true)
    public StoredFile get(UUID id) {
        StoredFile file = files.findById(id).orElseThrow(() -> ApiException.notFound("File"));
        AuthenticatedUser user = CurrentUser.get().orElse(null);
        if (user != null && !user.has(Permissions.FILES_READ) && !mayRead(file, user.userId())) {
            throw ApiException.notFound("File");
        }
        return file;
    }

    private boolean mayRead(StoredFile file, UUID userId) {
        if (userId.equals(file.getUploadedBy())) {
            return true;
        }
        return file.isAttached() && ownerAccess.stream()
                .anyMatch(a -> a.canRead(file.getOwnerType(), file.getOwnerId(), userId));
    }

    @Transactional(readOnly = true)
    public String downloadUrl(UUID id) {
        StoredFile file = get(id);
        String attachmentName = file.getKind() == FileKind.DOCUMENT ? file.getOriginalName() : null;
        return storage.presignGet(file.getStorageKey(), URL_TTL, attachmentName);
    }

    /** Small WebP preview of an image (lists, mobile); 404 for documents. */
    @Transactional(readOnly = true)
    public String thumbnailUrl(UUID id) {
        StoredFile file = get(id);
        if (file.getThumbnailKey() == null) {
            throw ApiException.notFound("File");
        }
        return storage.presignGet(file.getThumbnailKey(), URL_TTL, null);
    }

    /** Uploaders and admins may delete files that are not attached to any record (FR-FIL-03). */
    @Transactional
    public void deleteUnattached(UUID id) {
        StoredFile file = get(id);
        AuthenticatedUser user = CurrentUser.require();
        if (file.isAttached()) {
            throw ApiException.conflict("FILE_IN_USE", "The file is attached to a record");
        }
        if (!user.userId().equals(file.getUploadedBy()) && !user.isAdminOrOwner()) {
            throw ApiException.forbidden("Only the uploader or an admin can delete this file");
        }
        remove(file);
    }

    // ------------------------------------------------------------------ Files (public API)

    @Override
    @Transactional
    public void attach(Collection<UUID> fileIds, FileKind expectedKind, String ownerType, UUID ownerId, String field) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        Set<UUID> unique = new HashSet<>(fileIds);
        List<StoredFile> found = files.findAllById(unique);
        if (found.size() != unique.size()) {
            throw ApiException.referenceNotFound(field);
        }
        Instant now = Instant.now(clock);
        for (StoredFile file : found) {
            if (expectedKind != null && file.getKind() != expectedKind) {
                throw ApiException.badRequest("INVALID_FILE_KIND", "Expected a " + expectedKind.name().toLowerCase()
                        + " file in " + field);
            }
            if (file.isAttached() && !file.isOwnedBy(ownerType, ownerId)) {
                throw ApiException.conflict("FILE_IN_USE", "A file in " + field + " is already used elsewhere");
            }
            file.attachTo(ownerType, ownerId, now);
        }
    }

    @Override
    @Transactional
    public void detachAllExcept(String ownerType, UUID ownerId, Collection<UUID> keep) {
        Set<UUID> keepSet = keep == null ? Set.of() : new HashSet<>(keep);
        files.findByOwnerTypeAndOwnerId(ownerType, ownerId).stream()
                .filter(f -> !keepSet.contains(f.getId()))
                .forEach(this::remove);
    }

    @Override
    @Transactional
    public void delete(UUID fileId) {
        files.findById(fileId).ifPresent(this::remove);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, FileSummary> summaries(Collection<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        return files.findAllById(new HashSet<>(fileIds)).stream()
                .collect(Collectors.toMap(StoredFile::getId, FileService::summary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileSummary> ownedBy(String ownerType, UUID ownerId) {
        return files.findByOwnerTypeAndOwnerId(ownerType, ownerId).stream().map(FileService::summary).toList();
    }

    /** Deletes files never attached within 24 hours (FR-FIL-04); runs across all workspaces. */
    @Transactional
    public int purgeOrphans() {
        List<StoredFile> orphans = files.findOrphansCreatedBefore(Instant.now(clock).minus(Duration.ofHours(24)));
        orphans.forEach(this::remove);
        return orphans.size();
    }

    // ------------------------------------------------------------------ helpers

    private void remove(StoredFile file) {
        file.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        String key = file.getStorageKey();
        String thumbKey = file.getThumbnailKey();
        afterCommit(() -> {
            deleteQuietly(key);
            if (thumbKey != null) {
                deleteQuietly(thumbKey);
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored object (will remain orphaned): {}", e.getMessage());
        }
    }

    static FileSummary summary(StoredFile f) {
        return new FileSummary(f.getId(), f.getKind(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                f.getPurpose(), f.getThumbnailKey() != null);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void onRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        action.run();
                    }
                }
            });
        }
    }

    public record Upload(String name, byte[] content) {
    }

}
