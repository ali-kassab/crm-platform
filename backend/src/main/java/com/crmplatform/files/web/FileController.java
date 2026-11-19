package com.crmplatform.files.web;

import com.crmplatform.files.FileKind;
import com.crmplatform.files.application.FileService;
import com.crmplatform.files.domain.StoredFile;
import com.crmplatform.shared.error.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files")
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('files:upload')")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestParam FileKind kind,
            @RequestParam(required = false) com.crmplatform.files.FilePurpose purpose,
            @RequestPart("files") List<MultipartFile> parts)
            throws IOException {
        List<FileService.Upload> uploads = new ArrayList<>();
        for (MultipartFile part : parts) {
            if (part.isEmpty()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Empty files are not allowed");
            }
            uploads.add(new FileService.Upload(part.getOriginalFilename(), part.getBytes()));
        }
        return new UploadResponse(files.upload(kind, purpose, uploads).stream().map(FileResponse::of).toList());
    }

    @GetMapping("/{id}")
    public FileResponse get(@PathVariable UUID id) {
        return FileResponse.of(files.get(id));
    }

    @GetMapping("/{id}/url")
    public DownloadUrl url(@PathVariable UUID id) {
        return new DownloadUrl(files.downloadUrl(id), Instant.now().plusSeconds(300));
    }

    /** Small WebP preview for image lists (mobile-friendly); 404 for documents. */
    @GetMapping("/{id}/thumbnail-url")
    public DownloadUrl thumbnailUrl(@PathVariable UUID id) {
        return new DownloadUrl(files.thumbnailUrl(id), Instant.now().plusSeconds(300));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        files.deleteUnattached(id);
    }

    public record FileResponse(UUID id, FileKind kind, String name, String contentType, long sizeBytes,
            boolean attached, Instant createdAt, com.crmplatform.files.FilePurpose purpose, boolean hasThumbnail) {
        static FileResponse of(StoredFile f) {
            return new FileResponse(f.getId(), f.getKind(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                    f.isAttached(), f.getCreatedAt(), f.getPurpose(), f.getThumbnailKey() != null);
        }
    }

    public record UploadResponse(List<FileResponse> files) {
    }

    public record DownloadUrl(String url, Instant expiresAt) {
    }
}
