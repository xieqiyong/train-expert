package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.FileUploadResponse;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.PublicFileService;
import com.databuff.digitalexpert.service.PublicFileService.PublicImageResource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class PublicFileController {

    @Autowired
    private PublicFileService publicFileService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadImage(@RequestPart("file") MultipartFile file,
                                                       HttpServletRequest request) {
        String publicPath = publicFileService.uploadImage(file);
        return ApiResponse.success(new FileUploadResponse(buildClientUrl(request, publicPath)));
    }

    @GetMapping("/public/images/{date}/{fileName:.+}")
    public ResponseEntity<FileSystemResource> getPublicImage(@PathVariable String date,
                                                             @PathVariable String fileName) {
        PublicImageResource imageResource = publicFileService.loadImage(date, fileName);
        FileSystemResource resource = new FileSystemResource(imageResource.path());
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                    .contentType(imageResource.mediaType())
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "读取图片文件失败");
        }
    }

    private String buildClientUrl(HttpServletRequest request, String publicPath) {
        String prefix = normalizePrefix(request.getHeader("X-Forwarded-Prefix"));
        if (prefix == null) {
            prefix = normalizePrefix(request.getContextPath());
        }
        return prefix == null ? publicPath : prefix + publicPath;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || "/".equals(prefix.trim())) {
            return null;
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
