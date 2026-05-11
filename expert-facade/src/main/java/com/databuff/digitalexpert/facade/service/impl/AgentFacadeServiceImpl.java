package com.databuff.digitalexpert.facade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.dto.AgentBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.AgentSummaryResponse;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.entity.AiAgentEntity;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertMcpBindingEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.mapper.AgentExpertBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AiAgentMapper;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertMcpBindingMapper;
import com.databuff.digitalexpert.facade.common.FacadeBusinessException;
import com.databuff.digitalexpert.facade.dto.AgentMcpDeployRequest;
import com.databuff.digitalexpert.facade.dto.AgentMcpDeployResponse;
import com.databuff.digitalexpert.facade.dto.AgentOpencodeRefreshRequest;
import com.databuff.digitalexpert.facade.dto.AgentOpencodeRefreshResponse;
import com.databuff.digitalexpert.facade.service.AgentFacadeService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AgentFacadeServiceImpl implements AgentFacadeService {

    private static final String DEFAULT_SCHEMA = "https://opencode.ai/config.json";

    @Autowired
    private AiAgentMapper aiAgentMapper;
    @Autowired
    private AgentExpertBindingMapper agentExpertBindingMapper;
    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertMcpBindingMapper expertMcpBindingMapper;

    @Override
    public List<AgentSummaryResponse> listAgents(AgentBatchQueryRequest request) {
        List<String> normalizedNames = normalizeQueryNames(request == null ? null : request.names());
        String normalizedStatus = request == null || request.status() == null ? null : request.status().name();
        if (normalizedNames.isEmpty()) {
            LambdaQueryWrapper<AiAgentEntity> queryWrapper = new LambdaQueryWrapper<AiAgentEntity>()
                    .orderByDesc(AiAgentEntity::getId);
            if (normalizedStatus != null) {
                queryWrapper.eq(AiAgentEntity::getStatus, normalizedStatus);
            }
            return aiAgentMapper.selectList(queryWrapper).stream()
                    .map(this::toSummary)
                    .toList();
        }

        LambdaQueryWrapper<AiAgentEntity> queryWrapper = new LambdaQueryWrapper<AiAgentEntity>()
                .in(AiAgentEntity::getAgentName, normalizedNames);
        if (normalizedStatus != null) {
            queryWrapper.eq(AiAgentEntity::getStatus, normalizedStatus);
        }
        List<AiAgentEntity> agents = aiAgentMapper.selectList(queryWrapper);
        Map<String, AgentSummaryResponse> agentMap = new LinkedHashMap<>();
        for (AiAgentEntity agent : agents) {
            agentMap.put(agent.getAgentName(), toSummary(agent));
        }

        List<AgentSummaryResponse> result = new ArrayList<>();
        for (String normalizedName : normalizedNames) {
            AgentSummaryResponse response = agentMap.get(normalizedName);
            if (response != null) {
                result.add(response);
            }
        }
        return result;
    }

    @Override
    public AgentOpencodeRefreshResponse refreshOpencode(AgentOpencodeRefreshRequest request) {
        Long agentId = request == null ? null : request.agentId();
        if (agentId == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent ID 不能为空");
        }
        AiAgentEntity agent = requireAgent(agentId);
        Path rootPath = resolveRootPath(agent);
        Path outputPath = rootPath.resolve("opencode.json");
        JSONObject config = buildOpencodeConfig(agent, outputPath);
        writeOpencodeConfig(outputPath, config);
        int mcpCount = config.getJSONObject("mcp") == null ? 0 : config.getJSONObject("mcp").size();
        log.info("expert-facade 下发 AI Agent opencode.json 完成, agentId={}, outputPath={}, mcpCount={}",
                agentId, outputPath, mcpCount);
        return new AgentOpencodeRefreshResponse(
                agent.getId(),
                agent.getAgentName(),
                rootPath.toString().replace('\\', '/'),
                outputPath.toString().replace('\\', '/'),
                JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat),
                mcpCount
        );
    }

    @Override
    public AgentMcpDeployResponse deployMcpsToOpencode(AgentMcpDeployRequest request) {
        List<Long> agentIds = normalizeAgentIds(request == null ? null : request.agentIds());
        List<AgentMcpDeployRequest.McpConfig> mcps = normalizeMcpConfigs(request == null ? null : request.mcps());
        if (agentIds.isEmpty()) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent IDs cannot be empty");
        }
        if (mcps.isEmpty()) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "MCP configs cannot be empty");
        }

        List<AgentOpencodeRefreshResponse> responses = new ArrayList<>();
        for (Long agentId : agentIds) {
            AiAgentEntity agent = requireAgent(agentId);
            Path rootPath = resolveRootPath(agent);
            Path outputPath = rootPath.resolve("opencode.json");
            JSONObject config = readOpencodeConfig(outputPath);
            ensureDefaultSchema(config);
            JSONObject mergedMcp = copyObject(config.getJSONObject("mcp"));
            JSONObject mergedPermission = copyObject(config.getJSONObject("permission"));
            for (AgentMcpDeployRequest.McpConfig mcp : mcps) {
                String bindingName = mcp.bindingName().trim();
                mergedMcp.put(bindingName, buildRemoteMcp(mcp));
                applyPermissions(mergedPermission, bindingName, parseToolWhitelist(mcp.toolWhitelist()));
            }
            config.put("mcp", mergedMcp);
            config.put("permission", mergedPermission);
            writeOpencodeConfig(outputPath, config);
            responses.add(new AgentOpencodeRefreshResponse(
                    agent.getId(),
                    agent.getAgentName(),
                    rootPath.toString().replace('\\', '/'),
                    outputPath.toString().replace('\\', '/'),
                    JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat),
                    mergedMcp.size()
            ));
        }
        return new AgentMcpDeployResponse(responses.size(), mcps.size(), responses);
    }

    private JSONObject buildOpencodeConfig(AiAgentEntity agent, Path outputPath) {
        JSONObject config = readOpencodeConfig(outputPath);
        ensureDefaultSchema(config);
        JSONObject mergedMcp = copyObject(config.getJSONObject("mcp"));
        JSONObject mergedPermission = copyObject(config.getJSONObject("permission"));
        Map<String, EffectiveMcpBinding> effectiveBindings = loadEffectiveMcpBindings(agent.getId());
        for (EffectiveMcpBinding binding : effectiveBindings.values()) {
            JSONObject mcpItem = new JSONObject();
            mcpItem.put("type", "remote");
            mcpItem.put("url", binding.url());
            mcpItem.put("enabled", true);
            mergedMcp.put(binding.bindingName(), mcpItem);
            applyPermissions(mergedPermission, binding);
        }
        config.put("mcp", mergedMcp);
        config.put("permission", mergedPermission);
        return config;
    }

    private void ensureDefaultSchema(JSONObject config) {
        if (!config.containsKey("$schema")) {
            config.put("$schema", DEFAULT_SCHEMA);
        }
    }

    private Map<String, EffectiveMcpBinding> loadEffectiveMcpBindings(Long agentId) {
        LinkedHashMap<String, EffectiveMcpBinding> mergedBindings = new LinkedHashMap<>();
        List<AgentExpertBindingEntity> expertBindings = agentExpertBindingMapper.selectList(
                new LambdaQueryWrapper<AgentExpertBindingEntity>()
                        .eq(AgentExpertBindingEntity::getAgentId, agentId)
                        .orderByAsc(AgentExpertBindingEntity::getSortNo, AgentExpertBindingEntity::getId)
        );
        for (AgentExpertBindingEntity expertBinding : expertBindings) {
            DigitalExpertEntity expert = digitalExpertMapper.selectById(expertBinding.getExpertId());
            if (expert == null || !ExpertStatus.STARTED.name().equals(expert.getStatus())) {
                continue;
            }
            List<ExpertMcpBindingEntity> mcpBindings = expertMcpBindingMapper.selectList(
                    new LambdaQueryWrapper<ExpertMcpBindingEntity>()
                            .eq(ExpertMcpBindingEntity::getExpertId, expert.getId())
                            .orderByAsc(ExpertMcpBindingEntity::getBindingName, ExpertMcpBindingEntity::getId)
            );
            for (ExpertMcpBindingEntity mcpBinding : mcpBindings) {
                EffectiveMcpBinding candidate = new EffectiveMcpBinding(
                        mcpBinding.getBindingName(),
                        mcpBinding.getMcpUrl(),
                        parseToolWhitelist(mcpBinding.getToolWhitelistJson()),
                        expert.getId(),
                        expert.getName()
                );
                EffectiveMcpBinding existing = mergedBindings.get(candidate.bindingName());
                if (existing == null) {
                    mergedBindings.put(candidate.bindingName(), candidate);
                    continue;
                }
                if (existing.sameDefinition(candidate)) {
                    continue;
                }
                log.warn("expert-facade 检测到 MCP 绑定名称冲突，保留先出现的配置, agentId={}, bindingName={}, keptExpertId={}, keptExpertName={}, skippedExpertId={}, skippedExpertName={}",
                        agentId,
                        candidate.bindingName(),
                        existing.expertId(),
                        existing.expertName(),
                        candidate.expertId(),
                        candidate.expertName());
            }
        }
        return mergedBindings;
    }

    private JSONObject readOpencodeConfig(Path outputPath) {
        if (!Files.exists(outputPath)) {
            return new JSONObject();
        }
        try {
            String rawConfig = Files.readString(outputPath);
            if (!StringUtils.hasText(rawConfig)) {
                return new JSONObject();
            }
            Object parsed = JSON.parse(rawConfig);
            if (!(parsed instanceof JSONObject jsonObject)) {
                throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "opencode.json must be a JSON object");
            }
            return jsonObject;
        } catch (FacadeBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "opencode.json is invalid: " + outputPath);
        }
    }

    private JSONObject copyObject(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        return JSON.parseObject(JSON.toJSONString(source));
    }

    private JSONObject buildRemoteMcp(AgentMcpDeployRequest.McpConfig mcp) {
        JSONObject mcpItem = new JSONObject();
        mcpItem.put("type", "remote");
        mcpItem.put("url", normalizeUrl(mcp.mcpUrl()));
        mcpItem.put("enabled", true);
        Map<String, String> headers = normalizeHeaders(mcp.headers());
        if (!headers.isEmpty()) {
            mcpItem.put("headers", headers);
        }
        Integer timeout = mcp.timeout();
        if (timeout != null && timeout > 0) {
            mcpItem.put("timeout", timeout);
        }
        return mcpItem;
    }

    private void applyPermissions(JSONObject permissionConfig, EffectiveMcpBinding binding) {
        applyPermissions(permissionConfig, binding.bindingName(), binding.toolWhitelist());
    }

    private void applyPermissions(JSONObject permissionConfig, String bindingName, List<String> toolWhitelist) {
        String prefix = bindingName + "_";
        if (toolWhitelist.isEmpty()) {
            permissionConfig.put(prefix + "*", "allow");
            return;
        }
        permissionConfig.put(prefix + "*", "deny");
        for (String tool : toolWhitelist) {
            permissionConfig.put(prefix + tool, "allow");
        }
    }

    private List<Long> normalizeAgentIds(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long agentId : agentIds) {
            if (agentId != null) {
                result.add(agentId);
            }
        }
        return List.copyOf(result);
    }

    private List<AgentMcpDeployRequest.McpConfig> normalizeMcpConfigs(List<AgentMcpDeployRequest.McpConfig> mcps) {
        if (mcps == null || mcps.isEmpty()) {
            return List.of();
        }
        List<AgentMcpDeployRequest.McpConfig> result = new ArrayList<>();
        for (AgentMcpDeployRequest.McpConfig mcp : mcps) {
            if (mcp == null || !StringUtils.hasText(mcp.bindingName()) || !StringUtils.hasText(mcp.mcpUrl())) {
                continue;
            }
            result.add(mcp);
        }
        return List.copyOf(result);
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                result.put(entry.getKey().trim(), entry.getValue());
            }
        }
        return result;
    }

    private String normalizeUrl(String url) {
        return StringUtils.hasText(url) ? url.trim() : url;
    }

    private List<String> parseToolWhitelist(String toolWhitelistJson) {
        if (!StringUtils.hasText(toolWhitelistJson)) {
            return List.of();
        }
        try {
            List<String> whitelist = JSON.parseArray(toolWhitelistJson, String.class);
            if (whitelist == null || whitelist.isEmpty()) {
                return List.of();
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String tool : whitelist) {
                if (StringUtils.hasText(tool)) {
                    normalized.add(tool.trim());
                }
            }
            return List.copyOf(normalized);
        } catch (Exception ex) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent MCP 工具白名单格式非法");
        }
    }

    private List<String> parseToolWhitelist(List<String> toolWhitelist) {
        if (toolWhitelist == null || toolWhitelist.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tool : toolWhitelist) {
            if (StringUtils.hasText(tool)) {
                normalized.add(tool.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private Path resolveRootPath(AiAgentEntity agent) {
        String configuredRoot = normalizePath(agent.getRootPath());
        if (configuredRoot != null) {
            return Path.of(configuredRoot).toAbsolutePath().normalize();
        }
        String agentPath = normalizePath(agent.getAgentPath());
        if (agentPath == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent 根目录未配置，且无法从技能目录推导");
        }
        Path path = Path.of(agentPath).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            throw FacadeBusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent 根目录未配置，且无法从技能目录推导");
        }
        return parent;
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void writeOpencodeConfig(Path outputPath, JSONObject config) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(
                    outputPath,
                    JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat)
            );
        } catch (IOException ex) {
            throw FacadeBusinessException.internal(ErrorCode.INTERNAL_ERROR, "写入 AI Agent opencode.json 失败");
        }
    }

    private AiAgentEntity requireAgent(Long agentId) {
        AiAgentEntity agent = aiAgentMapper.selectById(agentId);
        if (agent == null) {
            throw FacadeBusinessException.notFound(ErrorCode.AGENT_NOT_FOUND, "AI Agent 不存在: " + agentId);
        }
        return agent;
    }

    private AgentSummaryResponse toSummary(AiAgentEntity entity) {
        return new AgentSummaryResponse(
                entity.getId(),
                entity.getAgentName(),
                entity.getDescription(),
                entity.getAgentPath(),
                resolveAgentRootPath(entity),
                entity.getStatus()
        );
    }

    private String resolveAgentRootPath(AiAgentEntity entity) {
        if (StringUtils.hasText(entity.getRootPath())) {
            return entity.getRootPath();
        }
        if (!StringUtils.hasText(entity.getAgentPath())) {
            return null;
        }
        Path agentPath = Path.of(entity.getAgentPath()).toAbsolutePath().normalize();
        Path parent = agentPath.getParent();
        return parent == null ? null : parent.toString().replace('\\', '/');
    }

    private List<String> normalizeQueryNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String name : names) {
            String normalized = normalizeName(name);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalized = name.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record EffectiveMcpBinding(
            String bindingName,
            String url,
            List<String> toolWhitelist,
            Long expertId,
            String expertName
    ) {
        private boolean sameDefinition(EffectiveMcpBinding other) {
            return Objects.equals(bindingName, other.bindingName)
                    && Objects.equals(url, other.url)
                    && Objects.equals(toolWhitelist, other.toolWhitelist);
        }
    }
}
