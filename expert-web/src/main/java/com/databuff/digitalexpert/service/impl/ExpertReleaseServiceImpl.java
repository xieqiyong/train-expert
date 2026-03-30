package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.ExpertConfigMcpResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigSkillResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigStaticPackageResponse;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertMcpBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertReleaseTaskEntity;
import com.databuff.digitalexpert.dao.entity.ExpertSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertStaticPackageBindingEntity;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.entity.StaticPackageEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.enums.ReleaseTaskStatus;
import com.databuff.digitalexpert.dao.enums.TriggerType;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertMcpBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertReleaseTaskMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertStaticPackageBindingMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.dao.mapper.StaticPackageMapper;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.storage.PackagedFile;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import com.databuff.digitalexpert.util.TaskIdGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class ExpertReleaseServiceImpl implements ExpertReleaseService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private StaticPackageMapper staticPackageMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private ExpertStaticPackageBindingMapper expertStaticPackageBindingMapper;
    @Autowired
    private ExpertMcpBindingMapper expertMcpBindingMapper;
    @Autowired
    private SharedStorageService sharedStorageService;
    @Autowired
    private ZipArchiveService zipArchiveService;
    @Autowired
    @Qualifier("releaseTaskExecutor")
    private Executor releaseTaskExecutor;
    @Autowired
    private ExpertConfigService expertConfigService;
    @Autowired
    private ExpertReleaseTaskMapper expertReleaseTaskMapper;

    @Override
    @Transactional
    public ExpertReleaseTaskResponse submitReleaseTask(Long expertId) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (ExpertStatus.DISABLED.name().equals(expert.getStatus())) {
            throw BusinessException.conflict(ErrorCode.EXPERT_DISABLED, "专家已被禁用: " + expertId);
        }

        LocalDateTime now = LocalDateTime.now();
        ExpertReleaseTaskEntity entity = new ExpertReleaseTaskEntity();
        entity.setTaskId(TaskIdGenerator.nextReleaseTaskId());
        entity.setExpertId(expertId);
        entity.setStatus(ReleaseTaskStatus.PENDING.name());
        entity.setTriggerType(TriggerType.MANUAL.name());
        entity.setRequestedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        expertReleaseTaskMapper.insert(entity);
        submitTaskAfterCommit(entity.getTaskId());
        return toResponse(entity);
    }

    private void submitTaskAfterCommit(String taskId) {
        Runnable submitAction = () -> releaseTaskExecutor.execute(() -> runTask(taskId));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submitAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submitAction.run();
            }
        });
    }

    @Override
    public ExpertReleaseTaskResponse getTask(Long expertId, String taskId) {
        ExpertReleaseTaskEntity entity = expertReleaseTaskMapper.selectOne(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getTaskId, taskId)
                .eq(ExpertReleaseTaskEntity::getExpertId, expertId));
        if (entity == null) {
            throw BusinessException.notFound(ErrorCode.RELEASE_TASK_NOT_FOUND, "发布任务不存在: " + taskId);
        }
        return toResponse(entity);
    }

    @Override
    public List<ExpertReleaseTaskResponse> listTasks(Long expertId) {
        LambdaQueryWrapper<ExpertReleaseTaskEntity> queryWrapper = new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .orderByDesc(ExpertReleaseTaskEntity::getId);
        if (expertId != null) {
            queryWrapper.eq(ExpertReleaseTaskEntity::getExpertId, expertId);
        }
        return expertReleaseTaskMapper.selectList(queryWrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    public void runTask(String taskId) {
        ExpertReleaseTaskEntity task = requireTask(taskId);
        Path stagingDirectory = null;
        try {
            markTaskRunning(task);
            DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
            if (ExpertStatus.DISABLED.name().equals(expert.getStatus())) {
                throw BusinessException.conflict(ErrorCode.EXPERT_DISABLED, "发布过程中专家被禁用");
            }

            List<SkillPackageEntity> skillPackages = loadSkillPackages(expert.getId());
            List<StaticPackageEntity> staticPackages = loadStaticPackages(expert.getId());
            List<ExpertConfigMcpResponse> mcps = loadMcps(expert.getId());
            validatePackagePaths(skillPackages, staticPackages);

            stagingDirectory = sharedStorageService.resolveExpertStagingDirectory(expert.getId(), taskId);
            sharedStorageService.recreateDirectory(stagingDirectory);
            updateStagingPath(taskId, stagingDirectory);

            Path currentDirectory = sharedStorageService.resolveExpertCurrentDirectory(expert.getId());
            Path currentConfigPath = currentDirectory.resolve("expert-config.json");
            String expertPackageFileName = resolveExpertPackageFileName(expert);
            Path currentZipPath = currentDirectory.resolve(expertPackageFileName);
            ExpertConfigResponse configResponse = buildConfig(
                    expert,
                    currentDirectory,
                    currentConfigPath,
                    currentZipPath,
                    skillPackages,
                    staticPackages,
                    mcps
            );

            Path stagingConfigPath = stagingDirectory.resolve("expert-config.json");
            writeJson(stagingConfigPath, configResponse);
            Path stagingZipPath = stagingDirectory.resolve(expertPackageFileName);
            zipArchiveService.buildExpertPackage(
                    stagingZipPath,
                    stagingConfigPath,
                    toPackagedSkillFiles(skillPackages),
                    toPackagedStaticPackageFiles(staticPackages)
            );

            sharedStorageService.promoteExpertDirectory(stagingDirectory, currentDirectory);
            markTaskSuccess(task, currentConfigPath, currentZipPath);
            updateExpertAfterRelease(expert, taskId, currentDirectory, currentConfigPath, currentZipPath);
        } catch (Exception ex) {
            log.error("Release task {} failed", taskId, ex);
            if (stagingDirectory != null && sharedStorageService.exists(stagingDirectory)) {
                sharedStorageService.deleteRecursively(stagingDirectory);
            }
            markTaskFailed(taskId, ex.getMessage());
        }
    }

    private ExpertReleaseTaskEntity requireTask(String taskId) {
        ExpertReleaseTaskEntity entity = expertReleaseTaskMapper.selectOne(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getTaskId, taskId));
        if (entity == null) {
            throw BusinessException.notFound(ErrorCode.RELEASE_TASK_NOT_FOUND, "发布任务不存在: " + taskId);
        }
        return entity;
    }

    private void markTaskRunning(ExpertReleaseTaskEntity task) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(ReleaseTaskStatus.RUNNING.name());
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        expertReleaseTaskMapper.updateById(task);
    }

    private void updateStagingPath(String taskId, Path stagingDirectory) {
        ExpertReleaseTaskEntity task = requireTask(taskId);
        task.setStagingPath(sharedStorageService.toStoragePath(stagingDirectory));
        task.setUpdatedAt(LocalDateTime.now());
        expertReleaseTaskMapper.updateById(task);
    }

    private void markTaskSuccess(ExpertReleaseTaskEntity task, Path configPath, Path zipPath) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(ReleaseTaskStatus.SUCCEEDED.name());
        task.setConfigJsonPath(sharedStorageService.toStoragePath(configPath));
        task.setZipPackagePath(sharedStorageService.toStoragePath(zipPath));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertReleaseTaskMapper.updateById(task);
    }

    private void markTaskFailed(String taskId, String failureReason) {
        ExpertReleaseTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(ReleaseTaskStatus.FAILED.name());
        task.setFailureReason(truncateFailureReason(failureReason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertReleaseTaskMapper.updateById(task);
    }

    private void updateExpertAfterRelease(DigitalExpertEntity expert,
                                          String taskId,
                                          Path currentDirectory,
                                          Path currentConfigPath,
                                          Path currentZipPath) {
        LocalDateTime now = LocalDateTime.now();
        expert.setStatus(ExpertStatus.STARTED.name());
        expert.setSharedPath(sharedStorageService.toStoragePath(currentDirectory));
        expert.setConfigJsonPath(sharedStorageService.toStoragePath(currentConfigPath));
        expert.setZipPackagePath(sharedStorageService.toStoragePath(currentZipPath));
        expert.setLastReleaseTaskId(taskId);
        expert.setReleaseVersion((expert.getReleaseVersion() == null ? 0 : expert.getReleaseVersion()) + 1);
        expert.setStartedAt(now);
        expert.setUpdatedAt(now);
        digitalExpertMapper.updateById(expert);
    }

    private List<SkillPackageEntity> loadSkillPackages(Long expertId) {
        List<ExpertSkillBindingEntity> bindings = expertSkillBindingMapper.selectList(
                new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                        .eq(ExpertSkillBindingEntity::getExpertId, expertId)
                        .orderByAsc(ExpertSkillBindingEntity::getSortNo, ExpertSkillBindingEntity::getId)
        );
        List<SkillPackageEntity> result = new ArrayList<>();
        for (ExpertSkillBindingEntity binding : bindings) {
            SkillPackageEntity entity = skillPackageMapper.selectById(binding.getSkillId());
            if (entity == null) {
                throw BusinessException.notFound(
                        ErrorCode.SKILL_PACKAGE_NOT_FOUND,
                        "技能包不存在: " + binding.getSkillId()
                );
            }
            result.add(entity);
        }
        return result;
    }

    private List<StaticPackageEntity> loadStaticPackages(Long expertId) {
        List<ExpertStaticPackageBindingEntity> bindings = expertStaticPackageBindingMapper.selectList(
                new LambdaQueryWrapper<ExpertStaticPackageBindingEntity>()
                        .eq(ExpertStaticPackageBindingEntity::getExpertId, expertId)
                        .orderByAsc(ExpertStaticPackageBindingEntity::getSortNo, ExpertStaticPackageBindingEntity::getId)
        );
        List<StaticPackageEntity> result = new ArrayList<>();
        for (ExpertStaticPackageBindingEntity binding : bindings) {
            StaticPackageEntity entity = staticPackageMapper.selectById(binding.getStaticPackageId());
            if (entity == null) {
                throw BusinessException.notFound(
                        ErrorCode.STATIC_PACKAGE_NOT_FOUND,
                        "静态资源包不存在: " + binding.getStaticPackageId()
                );
            }
            result.add(entity);
        }
        return result;
    }

    private List<ExpertConfigMcpResponse> loadMcps(Long expertId) {
        return expertMcpBindingMapper.selectList(
                        new LambdaQueryWrapper<ExpertMcpBindingEntity>()
                                .eq(ExpertMcpBindingEntity::getExpertId, expertId)
                                .orderByAsc(ExpertMcpBindingEntity::getBindingName, ExpertMcpBindingEntity::getId))
                .stream()
                .map(binding -> new ExpertConfigMcpResponse(
                        binding.getBindingName(),
                        binding.getMcpUrl(),
                        parseToolWhitelist(binding.getToolWhitelistJson())
                ))
                .toList();
    }

    private void validatePackagePaths(List<SkillPackageEntity> skillPackages, List<StaticPackageEntity> staticPackages) {
        for (SkillPackageEntity skillPackage : skillPackages) {
            sharedStorageService.ensureFileExists(Path.of(skillPackage.getPackagePath()));
        }
        for (StaticPackageEntity staticPackage : staticPackages) {
            sharedStorageService.ensureFileExists(Path.of(staticPackage.getPackagePath()));
        }
    }

    private ExpertConfigResponse buildConfig(DigitalExpertEntity expert,
                                             Path currentDirectory,
                                             Path currentConfigPath,
                                             Path currentZipPath,
                                             List<SkillPackageEntity> skillPackages,
                                             List<StaticPackageEntity> staticPackages,
                                             List<ExpertConfigMcpResponse> mcps) {
        return new ExpertConfigResponse(
                expert.getId(),
                expert.getName(),
                expert.getDescription(),
                expert.getPrompt(),
                expert.getExpertType(),
                ExpertStatus.STARTED.name(),
                sharedStorageService.toStoragePath(currentDirectory),
                sharedStorageService.toStoragePath(currentConfigPath),
                sharedStorageService.toStoragePath(currentZipPath),
                (expert.getReleaseVersion() == null ? 0 : expert.getReleaseVersion()) + 1,
                expert.getLastReleaseTaskId(),
                ReleaseTaskStatus.RUNNING.name(),
                skillPackages.stream()
                        .map(skill -> new ExpertConfigSkillResponse(
                                skill.getId(),
                                skill.getName(),
                                skill.getDescription(),
                                skill.getPackageName(),
                                skill.getPackagePath()
                        ))
                        .toList(),
                staticPackages.stream()
                        .map(staticPackage -> new ExpertConfigStaticPackageResponse(
                                staticPackage.getId(),
                                staticPackage.getName(),
                                staticPackage.getStaticType(),
                                staticPackage.getDescription(),
                                staticPackage.getPackageName(),
                                staticPackage.getPackagePath()
                        ))
                        .toList(),
                mcps
        );
    }

    private void writeJson(Path target, ExpertConfigResponse configResponse) {
        try {
            Files.createDirectories(target.getParent());
            String json = JSON.toJSONString(configResponse);
            Files.writeString(target, json);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "写入专家配置 JSON 失败");
        }
    }

    private List<PackagedFile> toPackagedSkillFiles(List<SkillPackageEntity> skillPackages) {
        return skillPackages.stream()
                .map(entity -> new PackagedFile(
                        "skills/" + resolveArchiveDirectoryName(entity.getPackageName()),
                        Path.of(entity.getPackagePath())
                ))
                .toList();
    }

    private List<PackagedFile> toPackagedStaticPackageFiles(List<StaticPackageEntity> staticPackages) {
        return staticPackages.stream()
                .map(entity -> new PackagedFile(
                        "static_packages/" + entity.getPackageName(),
                        Path.of(entity.getPackagePath())
                ))
                .toList();
    }

    private List<String> parseToolWhitelist(String toolWhitelistJson) {
        if (toolWhitelistJson == null || toolWhitelistJson.isBlank()) {
            return List.of();
        }
        List<String> result = JSON.parseArray(toolWhitelistJson, String.class);
        return result == null ? List.of() : result;
    }

    private ExpertReleaseTaskResponse toResponse(ExpertReleaseTaskEntity entity) {
        return new ExpertReleaseTaskResponse(
                entity.getTaskId(),
                entity.getExpertId(),
                entity.getStatus(),
                entity.getConfigJsonPath(),
                entity.getZipPackagePath(),
                entity.getFailureReason(),
                entity.getRequestedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private String truncateFailureReason(String failureReason) {
        if (failureReason == null) {
            return "Unknown failure";
        }
        return failureReason.length() > 1800 ? failureReason.substring(0, 1800) : failureReason;
    }

    private String resolveExpertPackageFileName(DigitalExpertEntity expert) {
        return expert.getId() + ".zip";
    }

    private String resolveArchiveDirectoryName(String fileName) {
        if (fileName == null) {
            return "package";
        }
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String normalized = normalizeArchiveName(baseName);
        return normalized == null ? "package" : normalized;
    }

    private String normalizeArchiveName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String normalized = rawName.trim()
                .replaceAll("[^\\p{L}\\p{N}._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");
        return normalized.isBlank() ? null : normalized;
    }
}
