package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.StaticPackageResponse;
import com.databuff.digitalexpert.dao.entity.StaticPackageEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.PackageStatus;
import com.databuff.digitalexpert.dao.mapper.StaticPackageMapper;
import com.databuff.digitalexpert.service.StaticPackageService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class StaticPackageServiceImpl implements StaticPackageService {

    @Autowired
    private StaticPackageMapper staticPackageMapper;
    @Autowired
    private SharedStorageService sharedStorageService;
    @Autowired
    private ZipArchiveService zipArchiveService;

    @Override
    @Transactional
    public StaticPackageResponse upload(String name, String description, MultipartFile file) {
        byte[] packageBytes = readBytes(file);
        String packageName = resolveFileName(file);

        LocalDateTime now = LocalDateTime.now();
        StaticPackageEntity entity = new StaticPackageEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setPackageName(packageName);
        entity.setChecksum(zipArchiveService.sha256Hex(packageBytes));
        entity.setStatus(PackageStatus.ACTIVE.name());
        entity.setPackagePath("");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        staticPackageMapper.insert(entity);

        var directory = sharedStorageService.resolveStaticPackageDirectory(entity.getId());
        sharedStorageService.recreateDirectory(directory);
        var packagePath = directory.resolve(packageName);
        sharedStorageService.writeBytes(packagePath, packageBytes);
        entity.setPackagePath(sharedStorageService.toStoragePath(packagePath));
        staticPackageMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    public StaticPackageResponse getById(Long id) {
        return toResponse(requireById(id));
    }

    @Override
    public StaticPackageEntity requireById(Long id) {
        StaticPackageEntity entity = staticPackageMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound(
                    ErrorCode.STATIC_PACKAGE_NOT_FOUND,
                    "Static package not found: " + id
            );
        }
        return entity;
    }

    private StaticPackageResponse toResponse(StaticPackageEntity entity) {
        return new StaticPackageResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPackageName(),
                entity.getPackagePath(),
                entity.getChecksum(),
                entity.getStatus()
        );
    }

    private String resolveFileName(MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "Static package file name is required");
        }
        return Path.of(original).getFileName().toString();
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "Static package file is required");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "Failed to read static package");
        }
    }
}
