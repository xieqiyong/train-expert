package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertMcpBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertReleaseTaskEntity;
import com.databuff.digitalexpert.dao.entity.ExpertSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertStaticPackageBindingEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertMcpBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertReleaseTaskMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertStaticPackageBindingMapper;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.SkillPackageService;
import com.databuff.digitalexpert.service.StaticPackageService;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DigitalExpertServiceImpl implements DigitalExpertService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private ExpertStaticPackageBindingMapper expertStaticPackageBindingMapper;
    @Autowired
    private ExpertMcpBindingMapper expertMcpBindingMapper;
    @Autowired
    private ExpertReleaseTaskMapper expertReleaseTaskMapper;
    @Autowired
    private SkillPackageService skillPackageService;
    @Autowired
    private StaticPackageService staticPackageService;
    @Autowired
    private ExpertConfigService expertConfigService;

    @Override
    @Transactional
    public ExpertSummaryResponse createExpert(CreateExpertRequest request) {
        LocalDateTime now = LocalDateTime.now();
        DigitalExpertEntity entity = new DigitalExpertEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(ExpertStatus.DRAFT.name());
        entity.setReleaseVersion(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        digitalExpertMapper.insert(entity);
        return toSummary(entity);
    }

    @Override
    @Transactional
    public ExpertBindingUpdateResponse updateBindings(Long expertId, UpdateExpertBindingsRequest request) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (ExpertStatus.DISABLED.name().equals(expert.getStatus())) {
            throw BusinessException.conflict(ErrorCode.EXPERT_DISABLED, "Expert is disabled: " + expertId);
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
        expertConfigService.evict(expertId);
        return new ExpertBindingUpdateResponse(expertId, true);
    }

    @Override
    @Transactional
    public ExpertSummaryResponse disableExpert(Long expertId) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (hasActiveReleaseTask(expertId)) {
            throw BusinessException.conflict(
                    ErrorCode.ACTIVE_RELEASE_TASK_EXISTS,
                    "Active release task exists for expert: " + expertId
            );
        }
        LocalDateTime now = LocalDateTime.now();
        expert.setStatus(ExpertStatus.DISABLED.name());
        expert.setDisabledAt(now);
        expert.setUpdatedAt(now);
        digitalExpertMapper.updateById(expert);
        expertConfigService.evict(expertId);
        return toSummary(expert);
    }

    private boolean hasActiveReleaseTask(Long expertId) {
        Long count = expertReleaseTaskMapper.selectCount(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getExpertId, expertId)
                .in(ExpertReleaseTaskEntity::getStatus, List.of("PENDING", "RUNNING")));
        return count != null && count > 0;
    }

    private ExpertSummaryResponse toSummary(DigitalExpertEntity entity) {
        return new ExpertSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
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

    private List<McpBindingRequest> normalizeMcps(List<McpBindingRequest> mcps) {
        if (mcps == null || mcps.isEmpty()) {
            return List.of();
        }
        List<McpBindingRequest> result = new ArrayList<>();
        for (McpBindingRequest mcp : mcps) {
            if (mcp != null) {
                result.add(mcp);
            }
        }
        return result;
    }

    private void validateMcpBindings(List<McpBindingRequest> mcps) {
        Set<String> bindingNames = new LinkedHashSet<>();
        for (McpBindingRequest mcp : mcps) {
            if (!bindingNames.add(mcp.bindingName())) {
                throw BusinessException.badRequest(
                        ErrorCode.MCP_BINDING_INVALID,
                        "Duplicate MCP binding name: " + mcp.bindingName()
                );
            }
            URI uri = URI.create(mcp.mcpUrl());
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw BusinessException.badRequest(
                        ErrorCode.MCP_BINDING_INVALID,
                        "Invalid MCP URL: " + mcp.mcpUrl()
                );
            }
            if (mcp.toolWhitelist().stream().filter(Objects::nonNull).map(String::trim).anyMatch(String::isBlank)) {
                throw BusinessException.badRequest(
                        ErrorCode.MCP_BINDING_INVALID,
                        "Tool whitelist contains blank entry"
                );
            }
        }
    }

    private String writeWhitelist(List<String> whitelist) {
        try {
            return JSONObject.toJSONString(whitelist);
        } catch (Exception ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "Failed to serialize MCP tool whitelist");
        }
    }
}
