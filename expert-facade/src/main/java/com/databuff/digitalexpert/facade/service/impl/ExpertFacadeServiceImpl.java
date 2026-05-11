package com.databuff.digitalexpert.facade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.dto.ExpertBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
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
