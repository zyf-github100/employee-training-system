package com.zhixing.training.course;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
public class ResourceController {
    private final ResourceRepository resources;
    private final ResourceStorageService storage;
    private final CourseVersionService revisions;

    public ResourceController(ResourceRepository resources, ResourceStorageService storage, CourseVersionService revisions) {
        this.resources = resources;
        this.storage = storage;
        this.revisions = revisions;
    }

    @PostMapping(value = "/admin/course-chapters/{chapterId}/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LearningDtos.ResourceView> upload(@PathVariable long chapterId,
                                                         @RequestPart("file") MultipartFile file,
                                                         @RequestParam(required = false, defaultValue = "") String title,
                                                         @RequestParam(defaultValue = "1") int sortNo) {
        if (title != null && title.length() > 160) throw new ApiException(HttpStatus.BAD_REQUEST, "RESOURCE_TITLE_INVALID", "资源名称不能超过 160 个字符");
        ResourceStorageService.StoredFile saved = storage.store(file);
        try {
            return ApiResponse.ok(resources.insert(chapterId, title, sortNo, saved));
        } catch (RuntimeException ex) {
            storage.deleteQuietly(saved.storageKey());
            throw ex;
        }
    }

    @DeleteMapping("/admin/course-resources/{resourceId}")
    public ApiResponse<Void> delete(@PathVariable long resourceId) {
        ResourceRepository.ResourceRecord record = resources.delete(resourceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在"));
        if (!"PUBLISHED".equals(record.courseStatus())) storage.deleteQuietly(record.storageKey());
        return ApiResponse.ok();
    }

    @GetMapping("/resources/{resourceId}/content")
    public ResponseEntity<Resource> content(@PathVariable long resourceId,
                                            @AuthenticationPrincipal Jwt jwt,
                                            Authentication authentication,
                                            HttpServletRequest request) throws IOException {
        ResourceRepository.ResourceRecord record = resources.findRecord(resourceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在"));
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            long userId = Long.parseLong(jwt.getSubject());
            if (!"PUBLISHED".equals(record.courseStatus()) || !"PUBLISHED".equals(record.status()) || !resources.enrolled(record.courseId(), userId) || !revisions.resourceAllowed(record.courseId(), userId, record.id())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "RESOURCE_FORBIDDEN", "当前账号没有访问该资源的权限");
            }
        }
        Path path = storage.resolve(record.storageKey());
        FileSystemResource body = new FileSystemResource(path);
        String mediaType = record.mediaType();
        MediaType contentType = parseMediaType(mediaType, path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(Files.size(path));
        boolean inline = "VIDEO".equals(record.resourceType()) || "PDF".equals(record.resourceType()) || "IMAGE".equals(record.resourceType());
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(record.originalName() == null ? record.title() : record.originalName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl("private, max-age=300");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private static MediaType parseMediaType(String value, Path path) {
        try {
            if (value != null && !value.isBlank()) return MediaType.parseMediaType(value);
            String detected = Files.probeContentType(path);
            return detected == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(detected);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
