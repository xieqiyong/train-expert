package com.databuff.digitalexpert.facade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.dto.AgentBindingGroupResponse;
import com.databuff.digitalexpert.dao.dto.BindExpertAgentsCommand;
import com.databuff.digitalexpert.dao.dto.ChangeExpertStatusRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.dto.ExpertBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertIdRequest;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTaskListQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.ImportExpertPackageResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.ManualUpdateExpertResponse;
import com.databuff.digitalexpert.dao.dto.SubmitExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsCommand;
import com.databuff.digitalexpert.dao.dto.UpdateExpertCommand;
import com.databuff.digitalexpert.dao.dto.UpdateManualExpertRequest;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertMcpBindingEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.entity.ExpertTrainingTaskEntity;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.dao.mapper.AgentExpertBindingMapper;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertMcpBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertTrainingTaskMapper;
import com.databuff.digitalexpert.facade.common.FacadeBusinessException;
import com.databuff.digitalexpert.facade.dto.AgentOpencodeRefreshRequest;
import com.databuff.digitalexpert.facade.dto.AgentOpencodeRefreshResponse;
import com.databuff.digitalexpert.facade.dto.ExpertMcpUpsertRequest;
import com.databuff.digitalexpert.facade.dto.ExpertMcpUpsertResponse;
import com.databuff.digitalexpert.facade.service.AgentFacadeService;
import com.databuff.digitalexpert.facade.service.ExpertFacadeService;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExpertFacadeServiceImpl implements ExpertFacadeService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertTrainingTaskMapper expertTrainingTaskMapper;
    @Autowired
    private ExpertMcpBindingMapper expertMcpBindingMapper;
    @Autowired
    private AgentExpertBindingMapper agentExpertBindingMapper;
    @Autowired
    private AgentFacadeService agentFacadeService;
    @Autowired
    private DigitalExpertService digitalExpertService;
    @Autowired
    private ExpertConfigService expertConfigService;
    @Autowired
    private ExpertReleaseService expertReleaseService;
    @Autowired
    private ExpertTrainingService expertTrainingService;

    @Override
    public ExpertSummaryResponse createExpert(CreateExpertRequest request) {
        return digitalExpertService.createExpert(request);
    }

    @Override
    public List<ExpertSummaryResponse> listExperts(ExpertBatchQueryRequest request) {
        List<String> normalizedNames = normalizeQueryNames(request == null ? null : request.names());
        List<String> normalizedAppNames = normalizeQueryAppNames(request == null ? null : request.appNames());
        String normalizedExpertType = request == null || request.expertType() == null ? null : request.expertType().name();
        String normalizedStatus = request == null || request.status() == null ? null : request.status().name();
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
    public ExpertConfigResponse getExpertConfig(ExpertIdRequest request) {
        validateExpertIdRequest(request);
        return expertConfigService.getConfig(request.expertId());
    }

    @Override
    public List<ExpertSummaryResponse> changeExpertStatus(ChangeExpertStatusRequest request) {
        if (request == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家状态变更请求不能为空");
        }
        return digitalExpertService.changeExpertStatus(request.expertIds(), request.operation());
    }

    @Override
    public ExpertBindingUpdateResponse updateBindings(UpdateExpertBindingsCommand request) {
        if (request == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家绑定请求不能为空");
        }
        return digitalExpertService.updateBindings(request.expertId(), request.toBindingsRequest());
    }

    @Override
    public ExpertReleaseTaskResponse submitReleaseTask(ExpertIdRequest request) {
        validateExpertIdRequest(request);
        return expertReleaseService.submitReleaseTask(request.expertId());
    }

    @Override
    public ExpertReleaseTaskResponse getReleaseTask(ExpertTaskRequest request) {
        validateTaskRequest(request);
        return expertReleaseService.getTask(request.expertId(), request.taskId());
    }

    @Override
    public List<ExpertReleaseTaskResponse> listReleaseTasks(ExpertTaskListQueryRequest request) {
        return expertReleaseService.listTasks(request == null ? null : request.expertId());
    }

    @Override
    public ExpertTrainingTaskResponse submitTrainingTask(SubmitExpertTrainingTaskRequest request) {
        if (request == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家训练任务请求不能为空");
        }
        return expertTrainingService.submitTrainingTask(request.expertId(), request.toTrainingTaskRequest());
    }

    @Override
    public ExpertTrainingTaskResponse getTrainingTask(ExpertTaskRequest request) {
        validateTaskRequest(request);
        return expertTrainingService.getTask(request.expertId(), request.taskId());
    }

    @Override
    public Boolean abortTrainingTask(ExpertTaskRequest request) {
        validateTaskRequest(request);
        return expertTrainingService.abortTask(request.expertId(), request.taskId());
    }

    @Override
    public List<ExpertTrainingTaskResponse> listTrainingTasks(ExpertTaskListQueryRequest request) {
        return expertTrainingService.listTasks(request == null ? null : request.expertId());
    }

    @Override
    public List<AgentBindingGroupResponse> listAgentBindings() {
        return digitalExpertService.listAgentBindings();
    }

    @Override
    public ExpertBindingUpdateResponse bindAgents(BindExpertAgentsCommand request) {
        return digitalExpertService.bindAgents(request);
    }

    @Override
    public ExpertSummaryResponse updateExpert(UpdateExpertCommand request) {
        return digitalExpertService.updateExpert(request);
    }

    @Override
    public Boolean deleteExpert(ExpertIdRequest request) {
        validateExpertIdRequest(request);
        return digitalExpertService.deleteExpert(request.expertId());
    }

    @Override
    public ImportExpertPackageResponse importPackage(MultipartFile file, boolean autoRelease) {
        return digitalExpertService.importExpertPackage(file, autoRelease);
    }

    @Override
    public ManualCreateExpertResponse createManualExpert(CreateManualExpertRequest request, List<MultipartFile> skillFiles) {
        return digitalExpertService.createManualExpert(request, skillFiles);
    }

    @Override
    public ManualUpdateExpertResponse updateManualExpert(UpdateManualExpertRequest request, List<MultipartFile> skillFiles) {
        return digitalExpertService.updateManualExpert(request, skillFiles);
    }

    @Override
    public ExpertMcpUpsertResponse upsertMcp(ExpertMcpUpsertRequest request) {
        if (request == null || request.expertId() == null || request.mcp() == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家 MCP 请求不能为空");
        }
        DigitalExpertEntity expert = requireExpert(request.expertId());
        if (!ExpertStatus.STARTED.name().equals(expert.getStatus())) {
            throw FacadeBusinessException.badRequest(ErrorCode.EXPERT_DISABLED, "专家未启动，不能直接下发 MCP");
        }

        McpBindingRequest normalizedMcp = normalizeMcp(request.mcp());
        validateMcp(normalizedMcp);
        UpsertResult upsertResult = upsertExpertMcp(expert.getId(), normalizedMcp);
        List<AgentOpencodeRefreshResponse> affectedAgents = refreshAffectedAgents(expert.getId());
        return new ExpertMcpUpsertResponse(
                expert.getId(),
                expert.getName(),
                expert.getStatus(),
                normalizedMcp.bindingName(),
                upsertResult.created(),
                affectedAgents.size(),
                affectedAgents
        );
    }

    private ExpertSummaryResponse toSummary(DigitalExpertEntity entity, boolean trainingInProgress) {
        return new ExpertSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getAliasName(),
                entity.getDescription(),
                entity.getIconUrl(),
                entity.getExpertType(),
                entity.getExpertSource(),
                entity.getStatus(),
                trainingInProgress
        );
    }

    private DigitalExpertEntity requireExpert(Long expertId) {
        DigitalExpertEntity expert = digitalExpertMapper.selectById(expertId);
        if (expert == null) {
            throw FacadeBusinessException.notFound(ErrorCode.EXPERT_NOT_FOUND, "专家不存在: " + expertId);
        }
        return expert;
    }

    private McpBindingRequest normalizeMcp(McpBindingRequest mcp) {
        List<String> toolWhitelist = mcp.toolWhitelist() == null
                ? List.of()
                : mcp.toolWhitelist().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return new McpBindingRequest(
                normalizeRequiredText(mcp.bindingName(), "MCP 绑定名称不能为空"),
                normalizeRequiredText(mcp.mcpUrl(), "MCP 地址不能为空"),
                toolWhitelist
        );
    }

    private void validateMcp(McpBindingRequest mcp) {
        URI uri;
        try {
            uri = URI.create(mcp.mcpUrl());
        } catch (Exception ex) {
            throw FacadeBusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, "MCP 地址不合法: " + mcp.mcpUrl());
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, "MCP 地址不合法: " + mcp.mcpUrl());
        }
        if ((mcp.toolWhitelist() == null ? List.<String>of() : mcp.toolWhitelist())
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(String::isBlank)) {
            throw FacadeBusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, "工具白名单包含空白项");
        }
    }

    private UpsertResult upsertExpertMcp(Long expertId, McpBindingRequest mcp) {
        List<ExpertMcpBindingEntity> existingBindings = expertMcpBindingMapper.selectList(
                new LambdaQueryWrapper<ExpertMcpBindingEntity>()
                        .eq(ExpertMcpBindingEntity::getExpertId, expertId)
                        .eq(ExpertMcpBindingEntity::getBindingName, mcp.bindingName())
                        .orderByAsc(ExpertMcpBindingEntity::getId)
        );
        LocalDateTime now = LocalDateTime.now();
        if (existingBindings == null || existingBindings.isEmpty()) {
            ExpertMcpBindingEntity entity = new ExpertMcpBindingEntity();
            entity.setExpertId(expertId);
            entity.setBindingName(mcp.bindingName());
            entity.setMcpUrl(mcp.mcpUrl());
            entity.setToolWhitelistJson(writeWhitelist(mcp.toolWhitelist()));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            expertMcpBindingMapper.insert(entity);
            return new UpsertResult(true);
        }

        ExpertMcpBindingEntity primary = existingBindings.get(0);
        primary.setMcpUrl(mcp.mcpUrl());
        primary.setToolWhitelistJson(writeWhitelist(mcp.toolWhitelist()));
        primary.setUpdatedAt(now);
        expertMcpBindingMapper.updateById(primary);
        for (int i = 1; i < existingBindings.size(); i++) {
            expertMcpBindingMapper.deleteById(existingBindings.get(i).getId());
        }
        return new UpsertResult(false);
    }

    private List<AgentOpencodeRefreshResponse> refreshAffectedAgents(Long expertId) {
        List<Long> agentIds = agentExpertBindingMapper.selectList(
                        new LambdaQueryWrapper<AgentExpertBindingEntity>()
                                .eq(AgentExpertBindingEntity::getExpertId, expertId)
                                .orderByAsc(AgentExpertBindingEntity::getAgentId, AgentExpertBindingEntity::getId)
                ).stream()
                .map(AgentExpertBindingEntity::getAgentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (agentIds.isEmpty()) {
            return List.of();
        }
        List<AgentOpencodeRefreshResponse> responses = new ArrayList<>();
        for (Long agentId : agentIds) {
            responses.add(agentFacadeService.refreshOpencode(new AgentOpencodeRefreshRequest(agentId)));
        }
        return List.copyOf(responses);
    }

    private List<String> normalizeQueryNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String name : names) {
            String normalized = normalizeOptionalText(name);
            if (normalized != null) {
                values.add(normalized);
            }
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

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateExpertIdRequest(ExpertIdRequest request) {
        if (request == null || request.expertId() == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家 ID 不能为空");
        }
    }

    private void validateTaskRequest(ExpertTaskRequest request) {
        if (request == null || request.expertId() == null || !StringUtils.hasText(request.taskId())) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "专家任务请求不能为空");
        }
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, message);
        }
        return normalized;
    }

    private String writeWhitelist(List<String> whitelist) {
        try {
            return JSON.toJSONString(whitelist == null ? List.of() : whitelist);
        } catch (Exception ex) {
            throw FacadeBusinessException.internal(ErrorCode.INTERNAL_ERROR, "序列化 MCP 工具白名单失败");
        }
    }

    private record UpsertResult(boolean created) {
    }
}
