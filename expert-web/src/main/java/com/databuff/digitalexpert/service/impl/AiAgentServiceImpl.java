package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AgentBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.AgentConfigResponse;
import com.databuff.digitalexpert.dao.dto.AgentExpertBindingResponse;
import com.databuff.digitalexpert.dao.dto.AgentSkillBindingResponse;
import com.databuff.digitalexpert.dao.dto.AgentSummaryResponse;
import com.databuff.digitalexpert.dao.dto.CreateAgentRequest;
import com.databuff.digitalexpert.dao.dto.UpdateAgentBindingsRequest;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.entity.AgentSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.AiAgentEntity;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.enums.AgentStatus;
import com.databuff.digitalexpert.dao.enums.AgentStatusOperation;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.mapper.AgentExpertBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AgentSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AiAgentMapper;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.service.AgentDeploymentService;
import com.databuff.digitalexpert.service.AiAgentService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAgentServiceImpl implements AiAgentService {

    @Autowired
    private AiAgentMapper aiAgentMapper;
    @Autowired
    private AgentSkillBindingMapper agentSkillBindingMapper;
    @Autowired
    private AgentExpertBindingMapper agentExpertBindingMapper;
    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private AgentDeploymentService agentDeploymentService;

    @Override
    @Transactional
    public AgentSummaryResponse createAgent(CreateAgentRequest request) {
        String name = normalizeName(request.name());
        String path = normalizeAgentPath(request.path());
        ensureAgentNameUnique(name);
        ensureAgentPathUnique(path);

        LocalDateTime now = LocalDateTime.now();
        AiAgentEntity entity = new AiAgentEntity();
        entity.setAgentName(name);
        entity.setDescription(normalizeOptionalText(request.description()));
        entity.setAgentPath(path);
        entity.setStatus(AgentStatus.DRAFT.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        aiAgentMapper.insert(entity);
        return toSummary(entity);
    }

    @Override
    public List<AgentSummaryResponse> listAgentsByNames(List<String> names, AgentStatus status) {
        List<String> normalizedNames = normalizeQueryNames(names);
        String normalizedStatus = status == null ? null : status.name();
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
    public AgentConfigResponse getConfig(Long agentId) {
        AiAgentEntity agent = requireAgent(agentId);
        return new AgentConfigResponse(
                agent.getId(),
                agent.getAgentName(),
                agent.getDescription(),
                agent.getAgentPath(),
                agent.getStatus(),
                loadDirectSkills(agentId),
                loadExperts(agentId),
                agentDeploymentService.listEffectiveSkills(agentId)
        );
    }

    @Override
    @Transactional
    public AgentBindingUpdateResponse updateBindings(Long agentId, UpdateAgentBindingsRequest request) {
        AiAgentEntity agent = requireAgent(agentId);
        List<Long> skillIds = deduplicateIds(request == null ? null : request.skills());
        List<Long> expertIds = deduplicateIds(request == null ? null : request.experts());

        for (Long skillId : skillIds) {
            requireSkillPackage(skillId);
        }
        for (Long expertId : expertIds) {
            requireExpert(expertId);
        }

        agentSkillBindingMapper.delete(new LambdaQueryWrapper<AgentSkillBindingEntity>()
                .eq(AgentSkillBindingEntity::getAgentId, agentId));
        agentExpertBindingMapper.delete(new LambdaQueryWrapper<AgentExpertBindingEntity>()
                .eq(AgentExpertBindingEntity::getAgentId, agentId));

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < skillIds.size(); i++) {
            AgentSkillBindingEntity entity = new AgentSkillBindingEntity();
            entity.setAgentId(agentId);
            entity.setSkillId(skillIds.get(i));
            entity.setSortNo(i + 1);
            entity.setCreatedAt(now);
            agentSkillBindingMapper.insert(entity);
        }
        for (int i = 0; i < expertIds.size(); i++) {
            AgentExpertBindingEntity entity = new AgentExpertBindingEntity();
            entity.setAgentId(agentId);
            entity.setExpertId(expertIds.get(i));
            entity.setSortNo(i + 1);
            entity.setCreatedAt(now);
            agentExpertBindingMapper.insert(entity);
        }

        aiAgentMapper.update(null, new LambdaUpdateWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getId, agentId)
                .set(AiAgentEntity::getUpdatedAt, now));
        agentDeploymentService.refreshAgent(agent.getId());
        return new AgentBindingUpdateResponse(agentId, true);
    }

    @Override
    @Transactional
    public List<AgentSummaryResponse> changeStatus(List<Long> agentIds, AgentStatusOperation operation) {
        if (operation == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent status operation cannot be null");
        }
        if (agentIds == null || agentIds.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent ids cannot be empty");
        }

        List<AgentSummaryResponse> results = new ArrayList<>();
        for (Long agentId : agentIds) {
            AiAgentEntity agent = requireAgent(agentId);
            LocalDateTime now = LocalDateTime.now();
            switch (operation) {
                case ENABLE -> {
                    agent.setStatus(AgentStatus.ACTIVE.name());
                    agent.setActivatedAt(now);
                }
                case DISABLE -> {
                    agent.setStatus(AgentStatus.DISABLED.name());
                    agent.setDisabledAt(now);
                }
            }
            agent.setUpdatedAt(now);
            aiAgentMapper.updateById(agent);
            agentDeploymentService.refreshAgent(agentId);
            results.add(toSummary(agent));
        }
        return results;
    }

    @Override
    public AiAgentEntity requireAgent(Long agentId) {
        AiAgentEntity agent = aiAgentMapper.selectById(agentId);
        if (agent == null) {
            throw BusinessException.notFound(ErrorCode.AGENT_NOT_FOUND, "AI Agent not found: " + agentId);
        }
        return agent;
    }

    private List<AgentSkillBindingResponse> loadDirectSkills(Long agentId) {
        List<AgentSkillBindingEntity> bindings = agentSkillBindingMapper.selectList(
                new LambdaQueryWrapper<AgentSkillBindingEntity>()
                        .eq(AgentSkillBindingEntity::getAgentId, agentId)
                        .orderByAsc(AgentSkillBindingEntity::getSortNo, AgentSkillBindingEntity::getId)
        );
        List<AgentSkillBindingResponse> responses = new ArrayList<>();
        for (AgentSkillBindingEntity binding : bindings) {
            SkillPackageEntity skillPackage = requireSkillPackage(binding.getSkillId());
            responses.add(new AgentSkillBindingResponse(
                    skillPackage.getId(),
                    skillPackage.getName(),
                    skillPackage.getDescription(),
                    skillPackage.getPackageName(),
                    skillPackage.getPackagePath()
            ));
        }
        return responses;
    }

    private List<AgentExpertBindingResponse> loadExperts(Long agentId) {
        List<AgentExpertBindingEntity> bindings = agentExpertBindingMapper.selectList(
                new LambdaQueryWrapper<AgentExpertBindingEntity>()
                        .eq(AgentExpertBindingEntity::getAgentId, agentId)
                        .orderByAsc(AgentExpertBindingEntity::getSortNo, AgentExpertBindingEntity::getId)
        );
        List<AgentExpertBindingResponse> responses = new ArrayList<>();
        for (AgentExpertBindingEntity binding : bindings) {
            DigitalExpertEntity expert = requireExpert(binding.getExpertId());
            responses.add(new AgentExpertBindingResponse(
                    expert.getId(),
                    expert.getName(),
                    expert.getStatus()
            ));
        }
        return responses;
    }

    private SkillPackageEntity requireSkillPackage(Long skillId) {
        SkillPackageEntity skillPackage = skillPackageMapper.selectById(skillId);
        if (skillPackage == null) {
            throw BusinessException.notFound(ErrorCode.SKILL_PACKAGE_NOT_FOUND, "Skill package not found: " + skillId);
        }
        return skillPackage;
    }

    private DigitalExpertEntity requireExpert(Long expertId) {
        DigitalExpertEntity expert = digitalExpertMapper.selectById(expertId);
        if (expert == null) {
            throw BusinessException.notFound(ErrorCode.EXPERT_NOT_FOUND, "Expert not found: " + expertId);
        }
        return expert;
    }

    private void ensureAgentNameUnique(String name) {
        Long count = aiAgentMapper.selectCount(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getAgentName, name));
        if (count != null && count > 0) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "AI Agent name already exists: " + name);
        }
    }

    private void ensureAgentPathUnique(String path) {
        Long count = aiAgentMapper.selectCount(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getAgentPath, path));
        if (count != null && count > 0) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "AI Agent path already exists: " + path);
        }
    }

    private AgentSummaryResponse toSummary(AiAgentEntity entity) {
        return new AgentSummaryResponse(
                entity.getId(),
                entity.getAgentName(),
                entity.getDescription(),
                entity.getAgentPath(),
                entity.getStatus()
        );
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

    private String normalizeName(String name) {
        if (name == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent name cannot be empty");
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent name cannot be empty");
        }
        return normalized;
    }

    private String normalizeAgentPath(String path) {
        if (path == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent path cannot be empty");
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent path cannot be empty");
        }
        if (!isAbsolutePath(normalized)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "AI Agent path must be absolute");
        }
        return normalized;
    }

    private boolean isAbsolutePath(String path) {
        return path.startsWith("/") || path.matches("^[A-Za-z]:/.*") || path.startsWith("//");
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
