package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AgentBindingExpertResponse;
import com.databuff.digitalexpert.dao.dto.AgentBindingGroupResponse;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ForwardTrainingSubmitResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import com.databuff.digitalexpert.dao.dto.SubmitForwardTrainingRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.SkillPackageResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.entity.AiAgentEntity;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertMcpBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertReleaseTaskEntity;
import com.databuff.digitalexpert.dao.entity.ExpertSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertStaticPackageBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertAgentBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertTrainingTaskEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.enums.ExpertStatusOperation;
import com.databuff.digitalexpert.dao.enums.ExpertType;
import com.databuff.digitalexpert.dao.enums.ReleaseTaskStatus;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.dao.mapper.AgentExpertBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AiAgentMapper;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertMcpBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertReleaseTaskMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertStaticPackageBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertAgentBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertTrainingTaskMapper;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.AgentDeploymentService;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import com.databuff.digitalexpert.service.SkillPackageService;
import com.databuff.digitalexpert.service.StaticPackageService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.config.ExpertProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DigitalExpertServiceImpl implements DigitalExpertService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private ExpertStaticPackageBindingMapper expertStaticPackageBindingMapper;
    @Autowired
    private AiAgentMapper aiAgentMapper;
    @Autowired
    private AgentExpertBindingMapper agentExpertBindingMapper;
    @Autowired
    private ExpertMcpBindingMapper expertMcpBindingMapper;
    @Autowired
    private ExpertReleaseTaskMapper expertReleaseTaskMapper;
    @Autowired
    private ExpertTrainingTaskMapper expertTrainingTaskMapper;
    @Autowired
    private ExpertAgentBindingMapper expertAgentBindingMapper;
    @Autowired
    private SkillPackageService skillPackageService;
    @Autowired
    private StaticPackageService staticPackageService;
    @Autowired
    private ExpertConfigService expertConfigService;
    @Autowired
    private ExpertReleaseService expertReleaseService;
    @Autowired
    private ExpertTrainingService expertTrainingService;
    @Autowired
    private AgentDeploymentService agentDeploymentService;
    @Autowired
    private SharedStorageService sharedStorageService;
    @Autowired
    private ExpertProperties expertProperties;

    @Override
    @Transactional
    public ExpertSummaryResponse createExpert(CreateExpertRequest request) {
        String name = normalizeName(request.name());
        ensureExpertNameUnique(name);
        LocalDateTime now = LocalDateTime.now();
        DigitalExpertEntity entity = new DigitalExpertEntity();
        entity.setName(name);
        entity.setDescription(normalizeOptionalText(request.description()));
        entity.setPrompt(normalizeOptionalText(request.prompt()));
        entity.setExpertType(resolveExpertType(request.expertType()));
        entity.setStatus(ExpertStatus.DRAFT.name());
        entity.setReleaseVersion(0);
        entity.setTrainingVersion(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        digitalExpertMapper.insert(entity);
        return toSummary(entity);
    }

    @Override
    public List<ExpertSummaryResponse> listExpertsByNames(List<String> names,
                                                          ExpertType expertType,
                                                          List<String> appNames,
                                                          ExpertStatus status) {
        List<String> normalizedNames = normalizeQueryNames(names);
        List<String> normalizedAppNames = normalizeQueryAppNames(appNames);
        String normalizedExpertType = expertType == null ? null : expertType.name();
        String normalizedStatus = status == null ? null : status.name();
        LambdaQueryWrapper<DigitalExpertEntity> queryWrapper = new LambdaQueryWrapper<DigitalExpertEntity>()
                .orderByDesc(DigitalExpertEntity::getId);
        if (!normalizedNames.isEmpty()) {
            queryWrapper.in(DigitalExpertEntity::getName, normalizedNames);
        }
        if (!normalizedAppNames.isEmpty()) {
            queryWrapper.in(DigitalExpertEntity::getAppName, normalizedAppNames);
        }
        if (normalizedExpertType != null) {
            queryWrapper.eq(DigitalExpertEntity::getExpertType, normalizedExpertType);
        }
        if (normalizedStatus != null) {
            queryWrapper.eq(DigitalExpertEntity::getStatus, normalizedStatus);
        }
        List<DigitalExpertEntity> experts = digitalExpertMapper.selectList(queryWrapper);
        Set<Long> trainingExpertIds = loadTrainingExpertIds(experts);
        if (normalizedNames.isEmpty()) {
            return experts.stream()
                    .map(expert -> toSummary(expert, trainingExpertIds.contains(expert.getId())))
                    .toList();
        }
        Map<String, ExpertSummaryResponse> expertMap = new LinkedHashMap<>();
        for (DigitalExpertEntity expert : experts) {
            expertMap.put(expert.getName(), toSummary(expert, trainingExpertIds.contains(expert.getId())));
        }
        List<ExpertSummaryResponse> result = new ArrayList<>();
        for (String normalizedName : normalizedNames) {
            ExpertSummaryResponse expert = expertMap.get(normalizedName);
            if (expert != null) {
                result.add(expert);
            }
        }
        return result;
    }

    @Override
    public List<AgentBindingGroupResponse> listAgentBindings() {
        List<AgentExpertBindingEntity> bindings = agentExpertBindingMapper.selectList(
                new LambdaQueryWrapper<AgentExpertBindingEntity>()
                        .orderByAsc(AgentExpertBindingEntity::getAgentId,
                                AgentExpertBindingEntity::getSortNo,
                                AgentExpertBindingEntity::getId)
        );
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }

        Set<Long> bindingExpertIds = bindings.stream()
                .map(AgentExpertBindingEntity::getExpertId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> bindingAgentIds = bindings.stream()
                .map(AgentExpertBindingEntity::getAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (bindingExpertIds.isEmpty() || bindingAgentIds.isEmpty()) {
            return List.of();
        }

        Map<Long, DigitalExpertEntity> startedExpertMap = digitalExpertMapper.selectList(
                        new LambdaQueryWrapper<DigitalExpertEntity>()
                                .in(DigitalExpertEntity::getId, bindingExpertIds)
                                .eq(DigitalExpertEntity::getStatus, ExpertStatus.STARTED.name())
                ).stream()
                .collect(Collectors.toMap(
                        DigitalExpertEntity::getId,
                        expert -> expert,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (startedExpertMap.isEmpty()) {
            return List.of();
        }

        Map<Long, AiAgentEntity> agentMap = aiAgentMapper.selectList(
                        new LambdaQueryWrapper<AiAgentEntity>()
                                .in(AiAgentEntity::getId, bindingAgentIds)
                ).stream()
                .collect(Collectors.toMap(
                        AiAgentEntity::getId,
                        agent -> agent,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (agentMap.isEmpty()) {
            return List.of();
        }

        Map<Long, AgentBindingAccumulator> groupMap = new LinkedHashMap<>();
        for (AgentExpertBindingEntity binding : bindings) {
            if (binding == null) {
                continue;
            }
            DigitalExpertEntity expert = startedExpertMap.get(binding.getExpertId());
            AiAgentEntity agent = agentMap.get(binding.getAgentId());
            if (expert == null || agent == null) {
                continue;
            }
            String agentName = normalizeOptionalText(agent.getAgentName());
            String agentPath = normalizeOptionalText(agent.getAgentPath());
            if (agentName == null) {
                continue;
            }
            AgentBindingAccumulator accumulator = groupMap.computeIfAbsent(
                    agent.getId(),
                    key -> new AgentBindingAccumulator(agentName, agentPath)
            );
            accumulator.experts().add(new AgentBindingExpertResponse(
                    expert.getId(),
                    expert.getName()
            ));
        }

        return groupMap.values().stream()
                .map(accumulator -> new AgentBindingGroupResponse(
                        accumulator.agentName(),
                        accumulator.agentPath(),
                        List.copyOf(accumulator.experts())
                ))
                .toList();
    }

    @Override
    @Transactional
    public ManualCreateExpertResponse createManualExpert(CreateManualExpertRequest request, List<MultipartFile> skillFiles) {
        List<MultipartFile> normalizedFiles = normalizeSkillFiles(skillFiles);
        if (normalizedFiles.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能文件不能为空");
        }

        ExpertSummaryResponse expert = createExpert(new CreateExpertRequest(
                request.name(),
                request.description(),
                request.prompt(),
                request.expertType()
        ));

        List<SkillPackageResponse> uploadedSkills = new ArrayList<>();
        for (MultipartFile skillFile : normalizedFiles) {
            uploadedSkills.add(skillPackageService.upload(skillFile));
        }

        updateBindings(expert.id(), new UpdateExpertBindingsRequest(
                uploadedSkills.stream().map(SkillPackageResponse::id).toList(),
                List.of(),
                request.mcps()
        ));

        ExpertReleaseTaskResponse releaseTask = null;
        if (request.autoRelease()) {
            releaseTask = expertReleaseService.submitReleaseTask(expert.id());
        }
        return new ManualCreateExpertResponse(expert, uploadedSkills, releaseTask);
    }

    @Override
    @Transactional
    public ForwardTrainingSubmitResponse submitForwardTraining(SubmitForwardTrainingRequest request) {
        if (request == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "正向训练请求不能为空");
        }
        String sourceType = normalizeForwardSourceType(request.sourceType());
        String sourceValue = normalizeForwardSourceValue(sourceType, request.sourceValue());
        String sourceVersion = normalizeForwardSourceVersion(sourceType, request.sourceVersion());

        ForwardExpertResolution resolution = resolveOrCreateForwardExpert(request);
        ExpertTrainingTaskResponse trainingTask = expertTrainingService.submitTrainingTask(
                resolution.expert().id(),
                new CreateExpertTrainingTaskRequest(
                        List.of(new TrainingSourceRequest(sourceType, sourceValue, sourceVersion)),
                        normalizeOptionalText(request.trainingGoal())
                )
        );
        return new ForwardTrainingSubmitResponse(
                resolution.expert(),
                resolution.created(),
                trainingTask,
                sourceType,
                sourceVersion
        );
    }

    @Override
    @Transactional
    public ExpertBindingUpdateResponse updateBindings(Long expertId, UpdateExpertBindingsRequest request) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (ExpertStatus.DISABLED.name().equals(expert.getStatus())) {
            throw BusinessException.conflict(ErrorCode.EXPERT_DISABLED, "专家已被禁用: " + expertId);
        }

        List<Long> skillIds = deduplicateIds(request == null ? null : request.skills());
        List<Long> staticPackageIds = deduplicateIds(request == null ? null : request.staticPackages());
        List<McpBindingRequest> mcps = normalizeMcps(request == null ? null : request.mcps());

        for (Long skillId : skillIds) {
            skillPackageService.requireById(skillId);
        }
        for (Long staticPackageId : staticPackageIds) {
            staticPackageService.requireById(staticPackageId);
        }
        validateMcpBindings(mcps);

        expertSkillBindingMapper.delete(new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                .eq(ExpertSkillBindingEntity::getExpertId, expertId));
        expertStaticPackageBindingMapper.delete(new LambdaQueryWrapper<ExpertStaticPackageBindingEntity>()
                .eq(ExpertStaticPackageBindingEntity::getExpertId, expertId));
        expertMcpBindingMapper.delete(new LambdaQueryWrapper<ExpertMcpBindingEntity>()
                .eq(ExpertMcpBindingEntity::getExpertId, expertId));

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < skillIds.size(); i++) {
            ExpertSkillBindingEntity entity = new ExpertSkillBindingEntity();
            entity.setExpertId(expertId);
            entity.setSkillId(skillIds.get(i));
            entity.setSortNo(i + 1);
            entity.setCreatedAt(now);
            expertSkillBindingMapper.insert(entity);
        }
        for (int i = 0; i < staticPackageIds.size(); i++) {
            ExpertStaticPackageBindingEntity entity = new ExpertStaticPackageBindingEntity();
            entity.setExpertId(expertId);
            entity.setStaticPackageId(staticPackageIds.get(i));
            entity.setSortNo(i + 1);
            entity.setCreatedAt(now);
            expertStaticPackageBindingMapper.insert(entity);
        }
        for (McpBindingRequest mcp : mcps) {
            ExpertMcpBindingEntity entity = new ExpertMcpBindingEntity();
            entity.setExpertId(expertId);
            entity.setBindingName(mcp.bindingName());
            entity.setMcpUrl(mcp.mcpUrl());
            entity.setToolWhitelistJson(writeWhitelist(mcp.toolWhitelist()));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            expertMcpBindingMapper.insert(entity);
        }

        digitalExpertMapper.update(null, new LambdaUpdateWrapper<DigitalExpertEntity>()
                .eq(DigitalExpertEntity::getId, expertId)
                .set(DigitalExpertEntity::getUpdatedAt, now));
        return new ExpertBindingUpdateResponse(expertId, true);
    }

    @Override
    @Transactional
    public boolean deleteExpert(Long expertId) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (hasActiveReleaseTask(expertId)) {
            throw BusinessException.conflict(
                    ErrorCode.ACTIVE_RELEASE_TASK_EXISTS,
                    "专家存在进行中的发布任务: " + expertId
            );
        }
        if (hasActiveTrainingTask(expertId)) {
            throw BusinessException.conflict(
                    ErrorCode.ACTIVE_TRAINING_TASK_EXISTS,
                    "专家存在进行中的训练任务: " + expertId
            );
        }

        List<Long> affectedAgentIds = agentExpertBindingMapper.selectList(
                        new LambdaQueryWrapper<AgentExpertBindingEntity>()
                                .eq(AgentExpertBindingEntity::getExpertId, expertId)
                                .orderByAsc(AgentExpertBindingEntity::getAgentId, AgentExpertBindingEntity::getId)
                ).stream()
                .map(AgentExpertBindingEntity::getAgentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        agentExpertBindingMapper.delete(new LambdaQueryWrapper<AgentExpertBindingEntity>()
                .eq(AgentExpertBindingEntity::getExpertId, expertId));
        expertAgentBindingMapper.delete(new LambdaQueryWrapper<ExpertAgentBindingEntity>()
                .eq(ExpertAgentBindingEntity::getExpertId, expertId));
        expertSkillBindingMapper.delete(new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                .eq(ExpertSkillBindingEntity::getExpertId, expertId));
        expertStaticPackageBindingMapper.delete(new LambdaQueryWrapper<ExpertStaticPackageBindingEntity>()
                .eq(ExpertStaticPackageBindingEntity::getExpertId, expertId));
        expertMcpBindingMapper.delete(new LambdaQueryWrapper<ExpertMcpBindingEntity>()
                .eq(ExpertMcpBindingEntity::getExpertId, expertId));
        expertReleaseTaskMapper.delete(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getExpertId, expertId));
        expertTrainingTaskMapper.delete(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getExpertId, expertId));
        digitalExpertMapper.deleteById(expertId);

        deleteExpertStorage(expertId);
        for (Long agentId : affectedAgentIds) {
            agentDeploymentService.refreshAgent(agentId);
        }
        return true;
    }

    @Override
    @Transactional
    public List<ExpertSummaryResponse> changeExpertStatus(List<Long> expertIds, ExpertStatusOperation operation) {
        if (operation == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家状态操作不能为空");
        }
        if (expertIds == null || expertIds.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家ID列表不能为空");
        }
        List<ExpertSummaryResponse> results = new ArrayList<>();
        for (Long expertId : expertIds) {
            ExpertSummaryResponse result = switch (operation) {
                case ENABLE -> disableExpertInternal(expertId, ExpertStatus.STARTED);
                case DISABLE -> disableExpertInternal(expertId, ExpertStatus.DISABLED);
            };
            results.add(result);
        }
        return results;
    }

    private ExpertSummaryResponse disableExpertInternal(Long expertId, ExpertStatus expertStatus) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (hasActiveReleaseTask(expertId)) {
            throw BusinessException.conflict(
                    ErrorCode.ACTIVE_RELEASE_TASK_EXISTS,
                    "专家存在进行中的发布任务: " + expertId
            );
        }
        if (hasActiveTrainingTask(expertId)) {
            throw BusinessException.conflict(
                    ErrorCode.ACTIVE_TRAINING_TASK_EXISTS,
                    "专家存在进行中的训练任务: " + expertId
            );
        }
        LocalDateTime now = LocalDateTime.now();
        expert.setStatus(expertStatus.name());
        if(expertStatus.equals(ExpertStatus.DISABLED)){
            expert.setUpdatedAt(now);
        }
        expert.setDisabledAt(now);
        digitalExpertMapper.updateById(expert);
        return toSummary(expert);
    }

    private boolean hasActiveReleaseTask(Long expertId) {
        Long count = expertReleaseTaskMapper.selectCount(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getExpertId, expertId)
                .in(ExpertReleaseTaskEntity::getStatus, ReleaseTaskStatus.activeTaskStatuses()));
        return count != null && count > 0;
    }

    private boolean hasActiveTrainingTask(Long expertId) {
        Long count = expertTrainingTaskMapper.selectCount(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getExpertId, expertId)
                .in(ExpertTrainingTaskEntity::getStatus, TrainingTaskStatus.activeTaskStatuses()));
        return count != null && count > 0;
    }

    private void deleteExpertStorage(Long expertId) {
        sharedStorageService.deleteRecursively(sharedStorageService.resolveExpertRoot(expertId));
        deleteTrainingOutputDirectory(expertId);
    }

    private void deleteTrainingOutputDirectory(Long expertId) {
        String outputRoot = expertProperties.getTraining().getOutputRoot();
        if (outputRoot == null || outputRoot.isBlank() || expertId == null) {
            return;
        }
        Path trainingRoot = Path.of(outputRoot).toAbsolutePath().normalize();
        Path expertDirectory = trainingRoot.resolve(String.valueOf(expertId)).toAbsolutePath().normalize();
        if (!expertDirectory.startsWith(trainingRoot) || !Files.exists(expertDirectory)) {
            return;
        }
        try (var stream = Files.walk(expertDirectory)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("删除训练输出目录失败: " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "删除专家训练输出目录失败: " + expertDirectory);
        }
    }

    private void ensureExpertNameUnique(String name) {
        Long count = digitalExpertMapper.selectCount(new LambdaQueryWrapper<DigitalExpertEntity>()
                .eq(DigitalExpertEntity::getName, name));
        if (count != null && count > 0) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "专家名称已存在: " + name);
        }
    }

    private ForwardExpertResolution resolveOrCreateForwardExpert(SubmitForwardTrainingRequest request) {
        String name = normalizeName(request.name());
        String description = normalizeOptionalText(request.description());
        String prompt = normalizeOptionalText(request.prompt());
        String expertType = resolveExpertType(request.expertType());

        DigitalExpertEntity existingExpert = findExpertByName(name);
        if (existingExpert == null) {
            ExpertSummaryResponse createdExpert = createExpert(new CreateExpertRequest(
                    name,
                    description,
                    prompt,
                    expertType
            ));
            return new ForwardExpertResolution(createdExpert, true);
        }

        existingExpert.setDescription(description);
        existingExpert.setPrompt(prompt);
        existingExpert.setExpertType(expertType);
        existingExpert.setUpdatedAt(LocalDateTime.now());
        digitalExpertMapper.updateById(existingExpert);
        return new ForwardExpertResolution(toSummary(existingExpert), false);
    }

    private DigitalExpertEntity findExpertByName(String name) {
        return digitalExpertMapper.selectOne(new LambdaQueryWrapper<DigitalExpertEntity>()
                .eq(DigitalExpertEntity::getName, name)
                .last("limit 1"));
    }

    private String normalizeForwardSourceType(String sourceType) {
        if (sourceType == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "训练源类型不能为空");
        }
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        if (!TrainingSourceType.GIT_URL.name().equals(normalized)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "当前仅支持 GIT_URL 正向训练");
        }
        return normalized;
    }

    private String normalizeForwardSourceValue(String sourceType, String sourceValue) {
        if (TrainingSourceType.GIT_URL.name().equals(sourceType)) {
            if (sourceValue == null || sourceValue.isBlank()) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "Git 仓库地址不能为空");
            }
            return sourceValue.trim();
        }
        return normalizeOptionalText(sourceValue);
    }

    private String normalizeForwardSourceVersion(String sourceType, String sourceVersion) {
        if (TrainingSourceType.GIT_URL.name().equals(sourceType)) {
            if (sourceVersion == null || sourceVersion.isBlank()) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "Git 分支或版本号不能为空");
            }
            return sourceVersion.trim();
        }
        return normalizeOptionalText(sourceVersion);
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家名称不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ExpertSummaryResponse toSummary(DigitalExpertEntity entity) {
        return toSummary(entity, false);
    }

    private ExpertSummaryResponse toSummary(DigitalExpertEntity entity, boolean trainingInProgress) {
        return new ExpertSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getExpertType(),
                entity.getStatus(),
                trainingInProgress
        );
    }

    private List<String> normalizeQueryNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String name : names) {
            values.add(normalizeName(name));
        }
        return List.copyOf(values);
    }

    private List<String> normalizeQueryAppNames(List<String> appNames) {
        if (appNames == null || appNames.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String appName : appNames) {
            String normalized = normalizeOptionalText(appName);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private Set<Long> loadTrainingExpertIds(List<DigitalExpertEntity> experts) {
        if (experts == null || experts.isEmpty()) {
            return Set.of();
        }
        Set<Long> expertIds = experts.stream()
                .map(DigitalExpertEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (expertIds.isEmpty()) {
            return Set.of();
        }
        return expertTrainingTaskMapper.selectList(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                        .in(ExpertTrainingTaskEntity::getExpertId, expertIds)
                        .in(ExpertTrainingTaskEntity::getStatus, TrainingTaskStatus.activeTaskStatuses()))
                .stream()
                .map(ExpertTrainingTaskEntity::getExpertId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveExpertType(String expertType) {
        if (expertType == null || expertType.isBlank()) {
            return ExpertType.SERVICE.name();
        }
        String normalizedType = expertType.trim();
        if ("服务类型".equals(normalizedType)) {
            return ExpertType.SERVICE.name();
        }
        if ("内置类型".equals(normalizedType)) {
            return ExpertType.BUILTIN.name();
        }
        if ("故障分析".equals(normalizedType)) {
            return ExpertType.FAULT_ANALYSIS.name();
        }
        normalizedType = normalizedType.toUpperCase(Locale.ROOT);
        try {
            return ExpertType.valueOf(normalizedType).name();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(
                    ErrorCode.INVALID_REQUEST,
                    "专家类型不支持，当前仅支持：SERVICE、BUILTIN、FAULT_ANALYSIS"
            );
        }
    }

    private List<Long> deduplicateIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> values = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                values.add(id);
            }
        }
        return List.copyOf(values);
    }

    private List<McpBindingRequest> normalizeMcps(List<McpBindingRequest> mcps) {
        if (mcps == null || mcps.isEmpty()) {
            return List.of();
        }
        List<McpBindingRequest> result = new ArrayList<>();
        for (McpBindingRequest mcp : mcps) {
            if (mcp == null) {
                continue;
            }
            List<String> toolWhitelist = mcp.toolWhitelist() == null
                    ? List.of()
                    : mcp.toolWhitelist().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .toList();
            result.add(new McpBindingRequest(
                    normalizeOptionalText(mcp.bindingName()),
                    normalizeOptionalText(mcp.mcpUrl()),
                    toolWhitelist
            ));
        }
        return result;
    }

    private void validateMcpBindings(List<McpBindingRequest> mcps) {
        Set<String> bindingNames = new LinkedHashSet<>();
        for (McpBindingRequest mcp : mcps) {
            if (mcp.bindingName() == null || mcp.bindingName().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, "MCP 绑定名称不能为空");
            }
            if (mcp.mcpUrl() == null || mcp.mcpUrl().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, "MCP 地址不能为空");
            }
            if (!bindingNames.add(mcp.bindingName())) {
                throw BusinessException.badRequest(
                        ErrorCode.MCP_BINDING_INVALID,
                        "MCP 绑定名称重复: " + mcp.bindingName()
                );
            }
            URI uri = URI.create(mcp.mcpUrl());
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw BusinessException.badRequest(
                        ErrorCode.MCP_BINDING_INVALID,
                        "MCP 地址不合法: " + mcp.mcpUrl()
                );
            }
            List<String> toolWhitelist = mcp.toolWhitelist() == null ? List.of() : mcp.toolWhitelist();
            if (toolWhitelist.stream().filter(Objects::nonNull).map(String::trim).anyMatch(String::isBlank)) {
                throw BusinessException.badRequest(
                        ErrorCode.MCP_BINDING_INVALID,
                        "工具白名单包含空白项"
                );
            }
        }
    }

    private String writeWhitelist(List<String> whitelist) {
        try {
            return JSON.toJSONString(whitelist == null ? List.of() : whitelist);
        } catch (Exception ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "序列化 MCP 工具白名单失败");
        }
    }

    private List<MultipartFile> normalizeSkillFiles(List<MultipartFile> skillFiles) {
        if (skillFiles == null || skillFiles.isEmpty()) {
            return List.of();
        }
        return skillFiles.stream()
                .filter(Objects::nonNull)
                .filter(file -> !file.isEmpty())
                .toList();
    }

    private record AgentBindingAccumulator(
            String agentName,
            String agentPath,
            List<AgentBindingExpertResponse> experts
    ) {

        private AgentBindingAccumulator(String agentName, String agentPath) {
            this(agentName, agentPath, new ArrayList<>());
        }
    }

    private record ForwardExpertResolution(
            ExpertSummaryResponse expert,
            boolean created
    ) {
    }
}
