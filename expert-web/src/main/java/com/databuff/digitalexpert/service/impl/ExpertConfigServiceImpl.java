package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.ExpertConfigMcpResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigSkillResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigStaticPackageResponse;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertMcpBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertReleaseTaskEntity;
import com.databuff.digitalexpert.dao.entity.ExpertSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertStaticPackageBindingEntity;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.entity.StaticPackageEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertMcpBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertReleaseTaskMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertStaticPackageBindingMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.dao.mapper.StaticPackageMapper;
import com.databuff.digitalexpert.service.ExpertConfigService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ExpertConfigServiceImpl implements ExpertConfigService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private ExpertStaticPackageBindingMapper expertStaticPackageBindingMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private ExpertMcpBindingMapper expertMcpBindingMapper;
    @Autowired
    private ExpertReleaseTaskMapper expertReleaseTaskMapper;
    @Autowired
    private StaticPackageMapper staticPackageMapper;


    @Override
    public ExpertConfigResponse getConfig(Long expertId) {
        DigitalExpertEntity expert = requireExpert(expertId);
        List<ExpertConfigSkillResponse> skills = loadSkillConfigs(expertId);
        List<ExpertConfigStaticPackageResponse> staticPackages = loadStaticPackageConfigs(expertId);
        List<ExpertConfigMcpResponse> mcps = loadMcpConfigs(expertId);
        String lastTaskStatus = loadLastTaskStatus(expert.getLastReleaseTaskId());
        return new ExpertConfigResponse(
                expert.getId(),
                expert.getName(),
                expert.getAliasName(),
                expert.getDescription(),
                expert.getPrompt(),
                expert.getExpertType(),
                expert.getExpertSource(),
                expert.getStatus(),
                expert.getSharedPath(),
                expert.getConfigJsonPath(),
                expert.getZipPackagePath(),
                expert.getReleaseVersion(),
                expert.getLastReleaseTaskId(),
                lastTaskStatus,
                skills,
                staticPackages,
                mcps
        );
    }

    @Override
    public DigitalExpertEntity requireExpert(Long expertId) {
        DigitalExpertEntity expert = digitalExpertMapper.selectById(expertId);
        if (expert == null) {
            throw BusinessException.notFound(ErrorCode.EXPERT_NOT_FOUND, "专家不存在: " + expertId);
        }
        return expert;
    }

    private List<ExpertConfigSkillResponse> loadSkillConfigs(Long expertId) {
        List<ExpertSkillBindingEntity> bindings = expertSkillBindingMapper.selectList(
                new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                        .eq(ExpertSkillBindingEntity::getExpertId, expertId)
                        .orderByAsc(ExpertSkillBindingEntity::getSortNo, ExpertSkillBindingEntity::getId)
        );
        List<ExpertConfigSkillResponse> responses = new ArrayList<>();
        for (ExpertSkillBindingEntity binding : bindings) {
            SkillPackageEntity skillPackage = skillPackageMapper.selectById(binding.getSkillId());
            if (skillPackage == null) {
                throw BusinessException.notFound(
                        ErrorCode.SKILL_PACKAGE_NOT_FOUND,
                        "技能包不存在: " + binding.getSkillId()
                );
            }
            responses.add(new ExpertConfigSkillResponse(
                    skillPackage.getId(),
                    skillPackage.getName(),
                    skillPackage.getDescription(),
                    skillPackage.getPackageName(),
                    skillPackage.getPackagePath()
            ));
        }
        return responses;
    }

    private List<ExpertConfigStaticPackageResponse> loadStaticPackageConfigs(Long expertId) {
        List<ExpertStaticPackageBindingEntity> bindings = expertStaticPackageBindingMapper.selectList(
                new LambdaQueryWrapper<ExpertStaticPackageBindingEntity>()
                        .eq(ExpertStaticPackageBindingEntity::getExpertId, expertId)
                        .orderByAsc(ExpertStaticPackageBindingEntity::getSortNo, ExpertStaticPackageBindingEntity::getId)
        );
        List<ExpertConfigStaticPackageResponse> responses = new ArrayList<>();
        for (ExpertStaticPackageBindingEntity binding : bindings) {
            StaticPackageEntity staticPackage = staticPackageMapper.selectById(binding.getStaticPackageId());
            if (staticPackage == null) {
                throw BusinessException.notFound(
                        ErrorCode.STATIC_PACKAGE_NOT_FOUND,
                        "静态资源包不存在: " + binding.getStaticPackageId()
                );
            }
            responses.add(new ExpertConfigStaticPackageResponse(
                    staticPackage.getId(),
                    staticPackage.getName(),
                    staticPackage.getStaticType(),
                    staticPackage.getDescription(),
                    staticPackage.getPackageName(),
                    staticPackage.getPackagePath()
            ));
        }
        return responses;
    }

    private List<ExpertConfigMcpResponse> loadMcpConfigs(Long expertId) {
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

    private String loadLastTaskStatus(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        ExpertReleaseTaskEntity task = expertReleaseTaskMapper.selectOne(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getTaskId, taskId));
        return task == null ? null : task.getStatus();
    }

    private List<String> parseToolWhitelist(String toolWhitelistJson) {
        if (toolWhitelistJson == null || toolWhitelistJson.isBlank()) {
            return List.of();
        }
        List<String> result = JSON.parseArray(toolWhitelistJson, String.class);
        return result == null ? List.of() : result;
    }
}
