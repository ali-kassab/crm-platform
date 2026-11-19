# ADR-0009: Files: private bucket + presigned URLs

- Status: Accepted (2026-11-18)
- Uploads go through the API (multipart, size and type checked by content sniffing with Apache Tika).
  - Images are re-encoded to WebP (max 2048 px) to strip metadata.
  - Documents are pdf/doc(x)/xls(x)/ppt(x).
- Object key: `{workspaceId}/{kind}/{uuid}`. A `stored_files` row holds the metadata and owner.
- Downloads use 5-minute presigned GET URLs with `Content-Disposition: attachment` for documents.
- Local development uses an S3-compatible container (choice recorded in the repo ADR).

