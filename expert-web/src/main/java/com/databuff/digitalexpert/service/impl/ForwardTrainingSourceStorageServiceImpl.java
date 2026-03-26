package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.ForwardTrainingSourceStorageService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ForwardTrainingSourceStorageServiceImpl implements ForwardTrainingSourceStorageService {

    private static final String TRAINING_SOURCE_DIRECTORY = "forward-training";
    private static final String ARCHIVE_DIRECTORY_NAME = "archive";
    private static final String EXTRACTED_DIRECTORY_NAME = "source";

    @Autowired
    private SharedStorageService sharedStorageService;
    @Autowired
    private ZipArchiveService zipArchiveService;

    @Override
    public StoredDocumentSource storeDocumentPackage(String expertName, MultipartFile file) {
        if (!StringUtils.hasText(expertName)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家名称不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "文档压缩包不能为空");
        }

        String normalizedExpertName = normalizeExpertName(expertName);
        String originalFileName = resolveOriginalFileName(file);
        long timestamp = Instant.now().toEpochMilli();

        Path sourceRootDirectory = sharedStorageService.resolveTemporaryDirectory(TRAINING_SOURCE_DIRECTORY)
                .resolve(normalizedExpertName)
                .resolve(String.valueOf(timestamp))
                .normalize();
        Path archiveDirectory = sourceRootDirectory.resolve(ARCHIVE_DIRECTORY_NAME).normalize();
        Path extractedDirectory = sourceRootDirectory.resolve(EXTRACTED_DIRECTORY_NAME).normalize();
        sharedStorageService.createDirectories(archiveDirectory);
        sharedStorageService.createDirectories(extractedDirectory);

        Path archivePath = archiveDirectory.resolve(originalFileName).normalize();
        saveUploadedFile(file, archivePath);
        extractArchive(archivePath, extractedDirectory);

        return new StoredDocumentSource(
                originalFileName,
                archivePath.toString(),
                extractedDirectory.toString()
        );
    }

    private void saveUploadedFile(MultipartFile file, Path archivePath) {
        try {
            file.transferTo(archivePath.toFile());
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "保存文档压缩包失败: " + archivePath);
        }
    }

    private void extractArchive(Path archivePath, Path destinationDirectory) {
        String fileName = archivePath.getFileName() == null
                ? ""
                : archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (fileName.endsWith(".zip")) {
                zipArchiveService.extractZipToDirectory(archivePath, destinationDirectory, true);
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
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "不支持的文档压缩包格式: " + fileName);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "解压文档压缩包失败: " + archivePath);
        }
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
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "文档压缩包文件名不能为空");
        }
        return Path.of(originalFileName).getFileName().toString();
    }

    private String normalizeExpertName(String expertName) {
        String normalized = expertName.trim()
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "无法解析文档压缩包存储目录");
        }
        return normalized;
    }
}
