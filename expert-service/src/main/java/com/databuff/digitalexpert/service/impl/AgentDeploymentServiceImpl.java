package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AgentEffectiveSkillResponse;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.entity.AgentSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.AgentSkillDeploymentEntity;
import com.databuff.digitalexpert.dao.entity.AiAgentEntity;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.enums.AgentStatus;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.mapper.AgentExpertBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AgentSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AgentSkillDeploymentMapper;
import com.databuff.digitalexpert.dao.mapper.AiAgentMapper;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.service.AgentDeploymentService;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AgentDeploymentServiceImpl implements AgentDeploymentService {

    @Autowired
    private AiAgentMapper aiAgentMapper;
    @Autowired
    private AgentSkillBindingMapper agentSkillBindingMapper;
    @Autowired
    private AgentExpertBindingMapper agentExpertBindingMapper;
    @Autowired
    private AgentSkillDeploymentMapper agentSkillDeploymentMapper;
    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private ZipArchiveService zipArchiveService;

    @Override
    public List<AgentEffectiveSkillResponse> listEffectiveSkills(Long agentId) {
        AiAgentEntity agent = requireAgent(agentId);
        return resolveEffectiveSkills(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshAgent(Long agentId) {
        AiAgentEntity agent = requireAgent(agentId);
        Path agentRoot = resolveRootDirectory(agent.getAgentPath());
        if (!AgentStatus.ACTIVE.name().equals(agent.getStatus())) {
            clearManagedSkills(agent, agentRoot);
            log.info("AI Agent 未启用，已清理受管技能目录, agentId={}, status={}",
                    agent.getId(), agent.getStatus());
            return;
        }

        List<AgentEffectiveSkillResponse> effectiveSkills = resolveEffectiveSkills(agent);
        List<AgentSkillDeploymentEntity> existingDeployments = agentSkillDeploymentMapper.selectList(
                new LambdaQueryWrapper<AgentSkillDeploymentEntity>()
                        .eq(AgentSkillDeploymentEntity::getAgentId, agentId)
                        .orderByAsc(AgentSkillDeploymentEntity::getId)
        );
        Map<String, AgentSkillDeploymentEntity> existingMap = new LinkedHashMap<>();
        for (AgentSkillDeploymentEntity deployment : existingDeployments) {
            existingMap.put(deployment.getSkillDirectoryName(), deployment);
        }

        for (AgentEffectiveSkillResponse effectiveSkill : effectiveSkills) {
            deploySkill(agent, agentRoot, effectiveSkill);
        }

        Set<String> currentDirectories = effectiveSkills.stream()
                .map(AgentEffectiveSkillResponse::outputDirectoryName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Map.Entry<String, AgentSkillDeploymentEntity> entry : existingMap.entrySet()) {
            if (!currentDirectories.contains(entry.getKey())) {
                deleteDirectory(agentRoot.resolve(entry.getKey()).toAbsolutePath().normalize());
            }
        }
        replaceDeployments(agent.getId(), effectiveSkills);
        log.info("AI Agent 技能部署刷新完成, agentId={}, skillCount={}", agent.getId(), effectiveSkills.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshActiveAgentsByExpert(Long expertId) {
        if (expertId == null) {
            return;
        }
        List<Long> agentIds = agentExpertBindingMapper.selectList(
                        new LambdaQueryWrapper<AgentExpertBindingEntity>()
                                .eq(AgentExpertBindingEntity::getExpertId, expertId)
                                .orderByAsc(AgentExpertBindingEntity::getAgentId, AgentExpertBindingEntity::getId)
                ).stream()
                .map(AgentExpertBindingEntity::getAgentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (Long agentId : agentIds) {
            refreshAgent(agentId);
        }
    }

    private List<AgentEffectiveSkillResponse> resolveEffectiveSkills(AiAgentEntity agent) {
        LinkedHashMap<String, AgentEffectiveSkillResponse> resolvedSkills = new LinkedHashMap<>();

        List<AgentSkillBindingEntity> directBindings = agentSkillBindingMapper.selectList(
                new LambdaQueryWrapper<AgentSkillBindingEntity>()
                        .eq(AgentSkillBindingEntity::getAgentId, agent.getId())
                        .orderByAsc(AgentSkillBindingEntity::getSortNo, AgentSkillBindingEntity::getId)
        );
        for (AgentSkillBindingEntity binding : directBindings) {
            SkillPackageEntity skillPackage = requireSkillPackage(binding.getSkillId());
            String directoryName = resolveOutputDirectoryName(skillPackage);
            resolvedSkills.putIfAbsent(directoryName, toEffectiveSkill(skillPackage, directoryName,
                    "DIRECT_SKILL", skillPackage.getId(), skillPackage.getName()));
        }

        List<AgentExpertBindingEntity> expertBindings = agentExpertBindingMapper.selectList(
                new LambdaQueryWrapper<AgentExpertBindingEntity>()
                        .eq(AgentExpertBindingEntity::getAgentId, agent.getId())
                        .orderByAsc(AgentExpertBindingEntity::getSortNo, AgentExpertBindingEntity::getId)
        );
        for (AgentExpertBindingEntity binding : expertBindings) {
            DigitalExpertEntity expert = requireExpert(binding.getExpertId());
            if (!ExpertStatus.STARTED.name().equals(expert.getStatus())) {
                continue;
            }
            List<ExpertSkillBindingEntity> expertSkillBindings = expertSkillBindingMapper.selectList(
                    new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                            .eq(ExpertSkillBindingEntity::getExpertId, expert.getId())
                            .orderByAsc(ExpertSkillBindingEntity::getSortNo, ExpertSkillBindingEntity::getId)
            );
            for (ExpertSkillBindingEntity expertSkillBinding : expertSkillBindings) {
                SkillPackageEntity skillPackage = requireSkillPackage(expertSkillBinding.getSkillId());
                String directoryName = resolveOutputDirectoryName(skillPackage);
                resolvedSkills.putIfAbsent(directoryName, toEffectiveSkill(skillPackage, directoryName,
                        "EXPERT", expert.getId(), expert.getName()));
            }
        }
        return List.copyOf(resolvedSkills.values());
    }

    private AgentEffectiveSkillResponse toEffectiveSkill(SkillPackageEntity skillPackage,
                                                         String outputDirectoryName,
                                                         String sourceType,
                                                         Long sourceId,
                                                         String sourceName) {
        return new AgentEffectiveSkillResponse(
                skillPackage.getId(),
                skillPackage.getName(),
                skillPackage.getDescription(),
                skillPackage.getPackageName(),
                skillPackage.getPackagePath(),
                outputDirectoryName,
                sourceType,
                sourceId,
                sourceName
        );
    }

    private void deploySkill(AiAgentEntity agent, Path agentRoot, AgentEffectiveSkillResponse effectiveSkill) {
        SkillPackageEntity skillPackage = requireSkillPackage(effectiveSkill.skillId());
        Path tempDirectory = agentRoot.resolve(".tmp")
                .resolve(String.valueOf(agent.getId()))
                .resolve(effectiveSkill.outputDirectoryName())
                .toAbsolutePath()
                .normalize();
        Path targetDirectory = agentRoot.resolve(effectiveSkill.outputDirectoryName())
                .toAbsolutePath()
                .normalize();
        ensureWithinRoot(agentRoot, tempDirectory);
        ensureWithinRoot(agentRoot, targetDirectory);

        try {
            recreateDirectory(tempDirectory);
            zipArchiveService.extractZipToDirectory(
                    Path.of(skillPackage.getPackagePath()).toAbsolutePath().normalize(),
                    tempDirectory,
                    true
            );
            validateExtractedSkillDirectory(tempDirectory);
            replaceDirectory(tempDirectory, targetDirectory);
            log.info("AI Agent 技能部署完成, agentId={}, skillId={}, sourceType={}, outputPath={}",
                    agent.getId(), skillPackage.getId(), effectiveSkill.sourceType(), targetDirectory);
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private void replaceDeployments(Long agentId, List<AgentEffectiveSkillResponse> effectiveSkills) {
        agentSkillDeploymentMapper.delete(new LambdaQueryWrapper<AgentSkillDeploymentEntity>()
                .eq(AgentSkillDeploymentEntity::getAgentId, agentId));
        LocalDateTime now = LocalDateTime.now();
        for (AgentEffectiveSkillResponse effectiveSkill : effectiveSkills) {
            AgentSkillDeploymentEntity entity = new AgentSkillDeploymentEntity();
            entity.setAgentId(agentId);
            entity.setSkillId(effectiveSkill.skillId());
            entity.setSkillDirectoryName(effectiveSkill.outputDirectoryName());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            agentSkillDeploymentMapper.insert(entity);
        }
    }

    private void clearManagedSkills(AiAgentEntity agent, Path agentRoot) {
        List<AgentSkillDeploymentEntity> deployments = agentSkillDeploymentMapper.selectList(
                new LambdaQueryWrapper<AgentSkillDeploymentEntity>()
                        .eq(AgentSkillDeploymentEntity::getAgentId, agent.getId())
                        .orderByAsc(AgentSkillDeploymentEntity::getId)
        );
        for (AgentSkillDeploymentEntity deployment : deployments) {
            deleteDirectory(agentRoot.resolve(deployment.getSkillDirectoryName()).toAbsolutePath().normalize());
        }
        agentSkillDeploymentMapper.delete(new LambdaQueryWrapper<AgentSkillDeploymentEntity>()
                .eq(AgentSkillDeploymentEntity::getAgentId, agent.getId()));
    }

    private AiAgentEntity requireAgent(Long agentId) {
        AiAgentEntity agent = aiAgentMapper.selectById(agentId);
        if (agent == null) {
            throw BusinessException.notFound(ErrorCode.AGENT_NOT_FOUND, "AI Agent not found: " + agentId);
        }
        return agent;
    }

    private DigitalExpertEntity requireExpert(Long expertId) {
        DigitalExpertEntity expert = digitalExpertMapper.selectById(expertId);
        if (expert == null) {
            throw BusinessException.notFound(ErrorCode.EXPERT_NOT_FOUND, "Expert not found: " + expertId);
        }
        return expert;
    }

    private SkillPackageEntity requireSkillPackage(Long skillId) {
        SkillPackageEntity skillPackage = skillPackageMapper.selectById(skillId);
        if (skillPackage == null) {
            throw BusinessException.notFound(ErrorCode.SKILL_PACKAGE_NOT_FOUND, "Skill package not found: " + skillId);
        }
        return skillPackage;
    }

    private Path resolveRootDirectory(String agentPath) {
        if (!StringUtils.hasText(agentPath)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent path cannot be empty");
        }
        return Path.of(agentPath).toAbsolutePath().normalize();
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
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "Extracted skill directory is missing: " + skillDirectory);
        }
        if (!Files.isRegularFile(skillDirectory.resolve("SKILL.md"))) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "Extracted skill directory missing SKILL.md: " + skillDirectory);
        }
    }

    private void recreateDirectory(Path directory) {
        deleteDirectory(directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "Failed to create temp skill directory: " + directory);
        }
    }

    private void replaceDirectory(Path sourceDirectory, Path targetDirectory) {
        deleteDirectory(targetDirectory);
        try {
            Files.createDirectories(targetDirectory.getParent());
            Files.move(sourceDirectory, targetDirectory, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "Failed to replace skill directory: " + targetDirectory);
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
                    throw new IllegalStateException("Failed to delete path: " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "Failed to delete directory: " + directory);
        } catch (IllegalStateException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void ensureWithinRoot(Path rootDirectory, Path targetPath) {
        if (!targetPath.startsWith(rootDirectory)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                    "Skill output path escapes agent root: " + targetPath);
        }
    }
}
