package com.databuff.digitalexpert.service.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.config.ExpertProperties;
import com.databuff.digitalexpert.dao.bo.TrainingContext;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertAgentBindingEntity;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.dao.mapper.ExpertAgentBindingMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.service.TrainingPostProcessor;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Order(0)
@Component
public class SkillOutputPostProcessor implements TrainingPostProcessor {

    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private ExpertAgentBindingMapper expertAgentBindingMapper;
    @Autowired
    private ExpertProperties properties;
    @Autowired
    private ZipArchiveService zipArchiveService;

    @Override
    public boolean supports(TrainingContext context) {
        return context != null
                && context.trainingTask() != null
                && context.status() == TrainingTaskStatus.SUCCEEDED
                && context.skillIds() != null
                && !context.skillIds().isEmpty();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postProcess(TrainingContext context) {
        List<OutputTarget> outputTargets = resolveOutputTargets();
        if (outputTargets.isEmpty()) {
            clearExpertAgentBindings(context);
            log.info("未配置 skills-output 输出目录，跳过技能包解压后置处理, taskId={}",
                    context.trainingTask() == null ? null : context.trainingTask().getTaskId());
            return;
        }

        log.info("训练后置处理开始, taskId={}, outputTargets={}",
                context.trainingTask() == null ? null : context.trainingTask().getTaskId(),
                outputTargets);

        List<String> failures = new ArrayList<>();
        for (Long skillId : context.skillIds()) {
            SkillPackageEntity skillPackage = skillPackageMapper.selectById(skillId);
            if (skillPackage == null) {
                failures.add("技能包不存在: " + skillId);
                continue;
            }
            if (!StringUtils.hasText(skillPackage.getPackagePath())) {
                failures.add("技能包路径为空: " + skillId);
                continue;
            }
            for (OutputTarget outputTarget : outputTargets) {
                try {
                    extractSkillPackage(context, skillPackage, outputTarget);
                } catch (Exception ex) {
                    failures.add("skillId=" + skillId
                            + ", agentName=" + outputTarget.agentName()
                            + ", outputRoot=" + outputTarget.outputRoot()
                            + ", reason=" + ex.getMessage());
                }
            }
        }

        if (!failures.isEmpty()) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "技能包解压到 skills-output 目录失败: " + String.join(" | ", failures));
        }
        refreshExpertAgentBindings(context, outputTargets);
    }

    private List<OutputTarget> resolveOutputTargets() {
        if (properties.getAgent() == null || properties.getAgent().getSkillsOutput() == null) {
            return List.of();
        }
        LinkedHashMap<String, OutputTarget> outputTargetMap = new LinkedHashMap<>();
        for (ExpertProperties.SkillOutput configuredTarget : properties.getAgent().getSkillsOutput()) {
            if (configuredTarget == null) {
                continue;
            }
            String agentName = normalizeText(configuredTarget.getName());
            String outputPath = normalizeText(configuredTarget.getPath());
            if (!StringUtils.hasText(agentName) || !StringUtils.hasText(outputPath)) {
                continue;
            }
            OutputTarget target = new OutputTarget(agentName, outputPath);
            OutputTarget previousTarget = outputTargetMap.putIfAbsent(agentName, target);
            if (previousTarget != null && !previousTarget.outputRoot().equals(outputPath)) {
                throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                        "skills-output 存在重复 agent 名称且路径不一致: " + agentName);
            }
        }
        return List.copyOf(outputTargetMap.values());
    }

    private void extractSkillPackage(TrainingContext context, SkillPackageEntity skillPackage, OutputTarget outputTarget) {
        Path rootDirectory = Path.of(outputTarget.outputRoot()).toAbsolutePath().normalize();
        String outputDirectoryName = resolveOutputDirectoryName(skillPackage);
        Path tempDirectory = rootDirectory.resolve(".tmp")
                .resolve(context.trainingTask().getTaskId())
                .resolve(outputDirectoryName)
                .toAbsolutePath()
                .normalize();
        Path targetDirectory = rootDirectory.resolve(outputDirectoryName).toAbsolutePath().normalize();
        ensureWithinRoot(rootDirectory, tempDirectory);
        ensureWithinRoot(rootDirectory, targetDirectory);

        try {
            recreateDirectory(tempDirectory);
            zipArchiveService.extractZipToDirectory(
                    Path.of(skillPackage.getPackagePath()).toAbsolutePath().normalize(),
                    tempDirectory,
                    true
            );
            validateExtractedSkillDirectory(tempDirectory);
            replaceDirectory(tempDirectory, targetDirectory);
            log.info("训练后置处理单点完成, taskId={}, skillId={}, agentName={}, outputPath={}",
                    context.trainingTask().getTaskId(),
                    skillPackage.getId(),
                    outputTarget.agentName(),
                    targetDirectory);
            log.info("技能包解压完成, taskId={}, skillId={}, outputPath={}",
                    context.trainingTask().getTaskId(),
                    skillPackage.getId(),
                    targetDirectory);
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private void refreshExpertAgentBindings(TrainingContext context, List<OutputTarget> outputTargets) {
        DigitalExpertEntity expert = requireExpert(context);
        expertAgentBindingMapper.delete(new LambdaQueryWrapper<ExpertAgentBindingEntity>()
                .eq(ExpertAgentBindingEntity::getExpertId, expert.getId()));

        LocalDateTime now = LocalDateTime.now();
        for (OutputTarget outputTarget : outputTargets) {
            ExpertAgentBindingEntity entity = new ExpertAgentBindingEntity();
            entity.setExpertId(expert.getId());
            entity.setExpertName(expert.getName());
            entity.setAgentName(outputTarget.agentName());
            entity.setAgentPath(Path.of(outputTarget.outputRoot()).toAbsolutePath().normalize().toString());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            expertAgentBindingMapper.insert(entity);
        }
    }

    private void clearExpertAgentBindings(TrainingContext context) {
        if (context == null || context.expert() == null || context.expert().getId() == null) {
            return;
        }
        expertAgentBindingMapper.delete(new LambdaQueryWrapper<ExpertAgentBindingEntity>()
                .eq(ExpertAgentBindingEntity::getExpertId, context.expert().getId()));
    }

    private DigitalExpertEntity requireExpert(TrainingContext context) {
        if (context == null || context.expert() == null || context.expert().getId() == null) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "training post process missing expert context");
        }
        return context.expert();
    }

    private String resolveOutputDirectoryName(SkillPackageEntity skillPackage) {
        String archiveFileName = resolveArchiveFileName(skillPackage);
        String baseName = archiveFileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? archiveFileName.substring(0, archiveFileName.length() - 4)
                : archiveFileName;
        if (!StringUtils.hasText(baseName)) {
            return "skill-" + skillPackage.getId();
        }
        return baseName;
    }

    private String resolveArchiveFileName(SkillPackageEntity skillPackage) {
        if (StringUtils.hasText(skillPackage.getPackageName())) {
            return Path.of(skillPackage.getPackageName()).getFileName().toString();
        }
        if (StringUtils.hasText(skillPackage.getPackagePath())) {
            return Path.of(skillPackage.getPackagePath()).getFileName().toString();
        }
        return "skill-" + skillPackage.getId() + ".zip";
    }

    private void validateExtractedSkillDirectory(Path skillDirectory) {
        if (!Files.isDirectory(skillDirectory)) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "解压后的技能目录不存在: " + skillDirectory);
        }
        if (!Files.isRegularFile(skillDirectory.resolve("SKILL.md"))) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "解压后的技能目录缺少 SKILL.md: " + skillDirectory);
        }
    }

    private void recreateDirectory(Path directory) {
        deleteDirectory(directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "创建技能输出临时目录失败: " + directory);
        }
    }

    private void replaceDirectory(Path sourceDirectory, Path targetDirectory) {
        deleteDirectory(targetDirectory);
        try {
            Files.createDirectories(targetDirectory.getParent());
            Files.move(sourceDirectory, targetDirectory, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "替换技能输出目录失败: " + targetDirectory);
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("删除目录失败: " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "删除目录失败: " + directory);
        } catch (IllegalStateException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void ensureWithinRoot(Path rootDirectory, Path targetPath) {
        if (!targetPath.startsWith(rootDirectory)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "技能输出路径超出根目录范围: " + targetPath);
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record OutputTarget(
            String agentName,
            String outputRoot
    ) {
    }
}
