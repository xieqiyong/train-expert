package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.AppInfoUploadService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AppInfoUploadServiceImpl implements AppInfoUploadService {

    private static final Logger log = LoggerFactory.getLogger(AppInfoUploadServiceImpl.class);
    private static final String DEFAULT_FILE_TYPE = "jar";
    private static final String UPLOAD_DIRECTORY_NAME = "jars";
    private static final String APP_INFO_DIRECTORY_NAME = "app_info";

    @Autowired
    private SharedStorageService sharedStorageService;

    @Override
    public AppInfoUploadResult upload(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "上传文件不能为空");
        }

        String originalFileName = resolveOriginalFileName(file);
        String appName = resolveAppName(originalFileName);
        long uploadTime = Instant.now().toEpochMilli();
        Path uploadDirectory = resolveUploadDirectory(appName, uploadTime);
        sharedStorageService.createDirectories(uploadDirectory);

        Path archivePath = uploadDirectory.resolve(originalFileName).normalize();
        saveUploadedFile(file, archivePath);
        extractArchive(archivePath, uploadDirectory);

        Path appInfoPath = uploadDirectory.resolve(APP_INFO_DIRECTORY_NAME).normalize();
        if (!Files.isDirectory(appInfoPath)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "解压后未找到 app_info 目录: " + appInfoPath);
        }
        String serviceName = resolveRawServiceName(appInfoPath);

        return new AppInfoUploadResult(
                appName,
                serviceName,
                originalFileName,
                originalFileName,
                normalizeFileType(fileType),
                file.getSize(),
                archivePath.toString(),
                archivePath.toString(),
                uploadTime,
                appInfoPath.toString(),
                true
        );
    }

    private Path resolveUploadDirectory(String appName, long uploadTime) {
        return sharedStorageService.getStaticPackage()
                .resolve(UPLOAD_DIRECTORY_NAME)
                .resolve(appName)
                .resolve(String.valueOf(uploadTime))
                .normalize();
    }

    private void saveUploadedFile(MultipartFile file, Path archivePath) {
        try {
            file.transferTo(archivePath.toFile());
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "保存上传文件失败: " + archivePath);
        }
    }

    private void extractArchive(Path archivePath, Path destinationDirectory) {
        String fileName = archivePath.getFileName() == null
                ? ""
                : archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (fileName.endsWith(".zip")) {
                Path extractedTarPath = extractZip(archivePath, destinationDirectory);
                if (extractedTarPath != null && Files.exists(extractedTarPath)) {
                    extractTar(extractedTarPath, destinationDirectory, false);
                    Files.deleteIfExists(extractedTarPath);
                }
                return;
            }
            if (fileName.endsWith(".tar")) {
                extractTar(archivePath, destinationDirectory, false);
                return;
            }
            if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
                extractTar(archivePath, destinationDirectory, true);
                return;
            }
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "不支持的压缩格式: " + fileName);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "解压上传文件失败: " + archivePath);
        }
    }

    private Path extractZip(Path zipPath, Path destinationDirectory) throws IOException {
        Path extractedTarPath = null;
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path outputPath = resolveArchiveEntry(destinationDirectory, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    continue;
                }
                Files.createDirectories(outputPath.getParent());
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                String outputFileName = outputPath.getFileName() == null
                        ? ""
                        : outputPath.getFileName().toString().toLowerCase(Locale.ROOT);
                if (outputFileName.endsWith(".tar")) {
                    extractedTarPath = outputPath;
                }
            }
        }
        return extractedTarPath;
    }

    private void extractTar(Path tarPath, Path destinationDirectory, boolean gzip) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(tarPath);
             InputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             InputStream archiveInputStream = gzip
                     ? new GzipCompressorInputStream(bufferedInputStream)
                     : bufferedInputStream;
             TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(archiveInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarArchiveInputStream.getNextTarEntry()) != null) {
                Path outputPath = resolveArchiveEntry(destinationDirectory, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    continue;
                }
                Files.createDirectories(outputPath.getParent());
                Files.copy(tarArchiveInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path resolveArchiveEntry(Path destinationDirectory, String entryName) {
        Path outputPath = destinationDirectory.resolve(entryName).normalize();
        if (!outputPath.startsWith(destinationDirectory)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "压缩包路径非法: " + entryName);
        }
        return outputPath;
    }

    private String resolveOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "上传文件名不能为空");
        }
        return Path.of(originalFileName).getFileName().toString();
    }

    private String resolveAppName(String originalFileName) {
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

    private String resolveRawServiceName(Path appInfoPath) {
        Path appJsonPath = appInfoPath.resolve("app.json");
        if (!Files.isRegularFile(appJsonPath)) {
            return null;
        }
        try {
            JSONObject appInfoJson = JSON.parseObject(Files.readString(appJsonPath), JSONObject.class);
            if (appInfoJson == null) {
                return null;
            }
            String serviceName = appInfoJson.getString("serviceName");
            return StringUtils.hasText(serviceName) ? serviceName : null;
        } catch (Exception ex) {
            log.warn("读取 app.json 服务名称失败, path={}", appJsonPath, ex);
            return null;
        }
    }

    private String normalizeFileType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return DEFAULT_FILE_TYPE;
        }
        return fileType.trim();
    }
}
