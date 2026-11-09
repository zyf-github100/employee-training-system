package com.zhixing.training.course;

import com.zhixing.training.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResourceStorageService {
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private final Path root;

    public ResourceStorageService(@Value("${zhixing.storage.root:.data/uploads}") String configuredRoot) {
        this.root = Paths.get(configuredRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建资源存储目录", ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("上传文件不能为空");
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件不能超过 200 MB");
        String originalName = safeName(file.getOriginalFilename());
        String extension = extension(originalName);
        String resourceType = typeOf(extension);
        validateContent(file, resourceType, extension);
        String storageKey = UUID.randomUUID() + extension;
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw invalid("文件路径不合法");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new java.security.DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(originalName, mediaType(extension), resourceType, file.getSize(), storageKey, sha256);
        } catch (IOException | NoSuchAlgorithmException ex) {
            deleteQuietly(storageKey);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED", "文件保存失败，请稍后重试");
        }
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) throw invalid("资源文件不存在");
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "资源文件不存在");
        return path;
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private static String safeName(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t\\u0000-\\u001F]", "_").trim();
        if (name.isBlank() || name.equals(".") || name.equals("..")) throw invalid("文件名不合法");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) throw invalid("文件必须包含受支持的扩展名");
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        if (!SetOfExtensions.ALLOWED.contains(ext)) throw invalid("不支持的文件类型");
        return ext;
    }

    private static String typeOf(String extension) {
        if (SetOfExtensions.VIDEO.contains(extension)) return "VIDEO";
        if (SetOfExtensions.IMAGE.contains(extension)) return "IMAGE";
        if (SetOfExtensions.PDF.contains(extension)) return "PDF";
        if (SetOfExtensions.PPT.contains(extension)) return "PPT";
        return "ATTACHMENT";
    }

    // Never trust the client-supplied Content-Type: a .jpg declared as text/html
    // would otherwise be served inline from the application origin.
    private static String mediaType(String extension) {
        return switch (extension) {
            case ".mp4", ".m4v" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".pdf" -> "application/pdf";
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private static void validateContent(MultipartFile file, String type, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] head = input.readNBytes(16);
            if ("PDF".equals(type) && !startsWith(head, new byte[]{'%', 'P', 'D', 'F', '-'})) throw invalid("PDF 文件头校验失败");
            if ("IMAGE".equals(type) && !imageHeader(head)) throw invalid("图片文件头校验失败");
            if ("PPT".equals(type) && !startsWith(head, new byte[]{'P', 'K'}) && !startsWith(head, new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0})) throw invalid("演示文稿文件头校验失败");
            if ("VIDEO".equals(type) && ".webm".equals(extension) && !startsWith(head, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})) throw invalid("WebM 文件头校验失败");
            if ("VIDEO".equals(type) && !".webm".equals(extension) && (head.length < 8 || head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p')) throw invalid("MP4 文件头校验失败");
        } catch (IOException ex) {
            throw invalid("无法读取上传文件");
        }
    }

    private static boolean imageHeader(byte[] head) {
        return startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                || startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                || startsWith(head, new byte[]{'G', 'I', 'F', '8'})
                || (startsWith(head, new byte[]{'R', 'I', 'F', 'F'}) && head.length >= 12
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P');
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED", message); }

    public record StoredFile(String originalName, String mediaType, String resourceType, long sizeBytes, String storageKey, String sha256) { }

    private static final class SetOfExtensions {
        private static final java.util.Set<String> VIDEO = java.util.Set.of(".mp4", ".webm", ".m4v");
        private static final java.util.Set<String> IMAGE = java.util.Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
        private static final java.util.Set<String> PDF = java.util.Set.of(".pdf");
        private static final java.util.Set<String> PPT = java.util.Set.of(".pptx", ".ppt");
        private static final java.util.Set<String> ALLOWED = java.util.Set.of(".mp4", ".webm", ".m4v", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf", ".pptx", ".ppt", ".doc", ".docx", ".xls", ".xlsx", ".zip");
    }
}
