package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.SkillPackageResponse;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.PackageStatus;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.service.SkillPackageService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.service.storage.SkillArchiveMetadata;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillPackageServiceImpl implements SkillPackageService {

    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private SharedStorageService sharedStorageService;
    @Autowired
    private ZipArchiveService zipArchiveService;

    @Override
    @Transactional
    public SkillPackageResponse upload(MultipartFile file) {
        byte[] archiveBytes = readBytes(file);
        SkillArchiveMetadata metadata = zipArchiveService.inspectSkillArchive(archiveBytes, file.getOriginalFilename());
        String packageName = resolveFileName(file.getOriginalFilename(), "skill-package.zip");

        LocalDateTime now = LocalDateTime.now();
        SkillPackageEntity entity = new SkillPackageEntity();
        // 剔除名称和描述两边的双引号或者空格
        entity.setName(metadata.name().trim());
        entity.setDescription(metadata.description().trim());
        entity.setPackageName(packageName);
        entity.setChecksum(zipArchiveService.sha256Hex(archiveBytes));
        entity.setStatus(PackageStatus.ACTIVE.name());
        entity.setPackagePath("");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        skillPackageMapper.insert(entity);

        var directory = sharedStorageService.resolveSkillDirectory(entity.getId());
        sharedStorageService.recreateDirectory(directory);
        var packagePath = directory.resolve(packageName);
        sharedStorageService.writeBytes(packagePath, archiveBytes);
        entity.setPackagePath(sharedStorageService.toStoragePath(packagePath));
        skillPackageMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    public SkillPackageResponse getById(Long id) {
        return toResponse(requireById(id));
    }

    @Override
    public List<SkillPackageResponse> listPackages() {
        return skillPackageMapper.selectList(null).stream()
                .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SkillPackageEntity requireById(Long id) {
        SkillPackageEntity entity = skillPackageMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound(ErrorCode.SKILL_PACKAGE_NOT_FOUND, "技能包不存在: " + id);
        }
        return entity;
    }

    private SkillPackageResponse toResponse(SkillPackageEntity entity) {
        return new SkillPackageResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPackageName(),
                entity.getPackagePath(),
                entity.getChecksum(),
                entity.getStatus()
        );
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能包文件不能为空");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "读取技能包失败");
        }
    }

    private String resolveFileName(String originalFilename, String fallback) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        String normalized = Path.of(originalFilename).getFileName().toString();
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return fallback;
        }
        return normalized;
    }
}
