package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
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
import com.databuff.digitalexpert.service.AgentRuntimeConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class AgentRuntimeConfigServiceImpl implements AgentRuntimeConfigService {

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
    public void refreshAgentConfig(Long agentId) {
        if (agentId == null) {
            return;
        }
        AiAgentEntity agent = requireAgent(agentId);
        Path rootPath = resolveRootPath(agent);
        JSONObject config = buildOpencodeConfig(agent);
        writeOpencodeConfig(rootPath.resolve("opencode.json"), config);
        int mcpCount = config.getJSONObject("mcp") == null ? 0 : config.getJSONObject("mcp").size();
        log.info("AI Agent OpenCode 配置刷新完成, agentId={}, outputPath={}, mcpCount={}",
                agentId, rootPath.resolve("opencode.json"), mcpCount);
    }

    @Override
    public void refreshAgentsByExpert(Long expertId) {
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
            refreshAgentConfig(agentId);
        }
    }

    private JSONObject buildOpencodeConfig(AiAgentEntity agent) {
        JSONObject config = parseBaseConfig(agent.getOpencodeConfigJson());
        if (!config.containsKey("$schema")) {
            config.put("$schema", DEFAULT_SCHEMA);
        }
        JSONObject mergedMcp = copyObject(config.getJSONObject("mcp"));
        JSONObject mergedPermission = copyObject(config.getJSONObject("permission"));
        Map<String, EffectiveMcpBinding> effectiveBindings = loadEffectiveMcpBindings(agent.getId());
        for (EffectiveMcpBinding binding : effectiveBindings.values()) {
            JSONObject mcpItem = new JSONObject();
            mcpItem.put("type", "remote");
            mcpItem.put("url", binding.url());
            mergedMcp.put(binding.bindingName(), mcpItem);
            applyPermissions(mergedPermission, binding);
        }
        config.put("mcp", mergedMcp);
        config.put("permission", mergedPermission);
        return config;
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
                log.warn("AI Agent MCP 绑定名称冲突，已保留先出现的配置, agentId={}, bindingName={}, keptExpertId={}, keptExpertName={}, skippedExpertId={}, skippedExpertName={}",
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

    private JSONObject parseBaseConfig(String rawConfig) {
        if (!StringUtils.hasText(rawConfig)) {
            return new JSONObject();
        }
        try {
            JSONObject parsed = JSON.parseObject(rawConfig);
            return parsed == null ? new JSONObject() : parsed;
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent OpenCode 基础配置 JSON 非法");
        }
    }

    private JSONObject copyObject(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        return JSON.parseObject(JSON.toJSONString(source));
    }

    private void applyPermissions(JSONObject permissionConfig, EffectiveMcpBinding binding) {
        String prefix = binding.bindingName() + "_";
        if (binding.toolWhitelist().isEmpty()) {
            permissionConfig.put(prefix + "*", "allow");
            return;
        }
        permissionConfig.put(prefix + "*", "deny");
        for (String tool : binding.toolWhitelist()) {
            permissionConfig.put(prefix + tool, "allow");
        }
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
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent MCP 工具白名单格式非法");
        }
    }

    private Path resolveRootPath(AiAgentEntity agent) {
        String configuredRoot = normalizePath(agent.getRootPath());
        if (configuredRoot != null) {
            return Path.of(configuredRoot).toAbsolutePath().normalize();
        }
        String agentPath = normalizePath(agent.getAgentPath());
        if (agentPath == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent 根目录未配置，且无法从技能目录推导");
        }
        Path path = Path.of(agentPath).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent 根目录未配置，且无法从技能目录推导");
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
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "写入 AI Agent OpenCode 配置失败");
        }
    }

    private AiAgentEntity requireAgent(Long agentId) {
        AiAgentEntity agent = aiAgentMapper.selectById(agentId);
        if (agent == null) {
            throw BusinessException.notFound(ErrorCode.AGENT_NOT_FOUND, "AI Agent not found: " + agentId);
        }
        return agent;
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
