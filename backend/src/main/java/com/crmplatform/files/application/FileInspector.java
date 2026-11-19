package com.crmplatform.files.application;

import com.crmplatform.files.FileKind;
import com.crmplatform.shared.error.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates uploads by content (not by the client's claimed type) and normalises images: decoded,
 * downscaled to at most 2048 px and re-encoded as WebP, which also strips metadata such as EXIF
 * (VALIDATION_AND_ERROR_HANDLING.md §5).
 */
@Component
public class FileInspector {

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int MAX_EDGE = 2048;

    private static final Map<String, String> DOCUMENT_TYPES = Map.of(
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/x-tika-msoffice", "doc|xls|ppt",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/vnd.ms-excel", "xls",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx",
            "application/vnd.ms-powerpoint", "ppt",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx",
            "application/x-tika-ooxml", "docx|xlsx|pptx",
            "application/zip", "docx|xlsx|pptx");

    private final Tika tika = new Tika();

    public Inspected inspect(FileKind kind, String originalName, byte[] content) {
        String detected = tika.detect(content, originalName);
        return switch (kind) {
            case IMAGE -> image(detected, originalName, content);
            case DOCUMENT -> document(detected, originalName, content);
        };
    }

    private Inspected image(String detected, String originalName, byte[] content) {
        if (content.length > MAX_IMAGE_BYTES) {
            throw tooLarge("Images must be 5 MB or smaller");
        }
        if (!detected.equals("image/jpeg") && !detected.equals("image/png") && !detected.equals("image/webp")) {
            throw unsupported("Images must be JPEG, PNG or WebP");
        }
        try {
            BufferedImage image = readBounded(content);
            BufferedImage scaled = downscale(image);
            byte[] webp = encodeWebp(scaled);
            return new Inspected(replaceExtension(originalName, "webp"), "image/webp", webp);
        } catch (IOException | RuntimeException e) {
            throw unsupported("The image could not be read");
        }
    }

    private Inspected document(String detected, String originalName, byte[] content) {
        if (content.length > MAX_DOCUMENT_BYTES) {
            throw tooLarge("Documents must be 10 MB or smaller");
        }
        String extension = extension(originalName);
        String allowed = DOCUMENT_TYPES.get(detected);
        if (allowed == null || !("|" + allowed + "|").contains("|" + extension + "|")) {
            throw unsupported("Documents must be PDF, Word, Excel or PowerPoint files");
        }
        String contentType = switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            default -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        };
        return new Inspected(sanitizeName(originalName), contentType, content);
    }

    private static BufferedImage readBounded(byte[] content) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("no reader");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw tooLarge("Images must be at most 40 megapixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage downscale(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longEdge = Math.max(w, h);
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        int nw = w;
        int nh = h;
        if (longEdge > MAX_EDGE) {
            double ratio = (double) MAX_EDGE / longEdge;
            nw = Math.max(1, (int) Math.round(w * ratio));
            nh = Math.max(1, (int) Math.round(h * ratio));
        }
        BufferedImage out = new BufferedImage(nw, nh, type);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeWebp(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IOException("WebP writer unavailable");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    param.setCompressionType(types[0]);
                }
                param.setCompressionQuality(0.8f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    static String sanitizeName(String name) {
        String base = name == null ? "file" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|*?:]", "_").trim();
        if (base.isEmpty()) {
            base = "file";
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String replaceExtension(String name, String ext) {
        String clean = sanitizeName(name);
        int dot = clean.lastIndexOf('.');
        return (dot > 0 ? clean.substring(0, dot) : clean) + "." + ext;
    }

    private static String extension(String name) {
        String clean = sanitizeName(name);
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? "" : clean.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ApiException unsupported(String message) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message);
    }

    private static ApiException tooLarge(String message) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message);
    }

    public record Inspected(String name, String contentType, byte[] content) {
    }
}
