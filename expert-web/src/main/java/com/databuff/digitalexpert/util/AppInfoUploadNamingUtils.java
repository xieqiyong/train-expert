package com.databuff.digitalexpert.util;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import java.nio.file.Path;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public final class AppInfoUploadNamingUtils {

    private AppInfoUploadNamingUtils() {
    }

    public static String resolveOriginalFileName(MultipartFile file) {
        String originalFileName = file == null ? null : file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "上传文件名不能为空");
        }
        return Path.of(originalFileName).getFileName().toString();
    }

    public static String resolveAppName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "无法从空文件名解析应用名称");
        }
        String rawName = originalFileName;
        int appInfoIndex = originalFileName.indexOf("_app_info");
        if (appInfoIndex > 0) {
            rawName = originalFileName.substring(0, appInfoIndex);
        } else {
            int dotIndex = originalFileName.indexOf('.');
            if (dotIndex > 0) {
                rawName = originalFileName.substring(0, dotIndex);
            }
        }
        String normalized = rawName.trim()
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "无法从文件名解析应用名称");
        }
        return normalized;
    }
}
