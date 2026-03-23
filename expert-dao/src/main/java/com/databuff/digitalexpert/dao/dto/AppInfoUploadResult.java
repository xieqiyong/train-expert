package com.databuff.digitalexpert.dao.dto;

public record AppInfoUploadResult(
        String appName,
        String originalFileName,
        String storageFileName,
        String fileType,
        Long fileSize,
        String storagePath,
        String accessUrl,
        Long uploadTime,
        String appInfoPath,
        boolean extracted
) {
}
