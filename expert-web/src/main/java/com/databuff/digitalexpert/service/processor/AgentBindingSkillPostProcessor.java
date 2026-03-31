package com.databuff.digitalexpert.service.processor;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.bo.TrainingContext;
import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.entity.AgentExpertBindingEntity;
import com.databuff.digitalexpert.dao.entity.AiAgentEntity;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.dao.mapper.AgentExpertBindingMapper;
import com.databuff.digitalexpert.dao.mapper.AiAgentMapper;
import com.databuff.digitalexpert.service.AgentDeploymentService;
import com.databuff.digitalexpert.service.TrainingPostProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Order(-10)
@Component
public class AgentBindingSkillPostProcessor implements TrainingPostProcessor {

    @Autowired
    private AgentDeploymentService agentDeploymentService;
    @Autowired
    private AiAgentMapper aiAgentMapper;
    @Autowired
    private AgentExpertBindingMapper agentExpertBindingMapper;

    @Override
    public boolean supports(TrainingContext context) {
        return context != null
                && context.trainingTask() != null
                && context.status() == TrainingTaskStatus.SUCCEEDED
                && context.expert() != null
                && context.expert().getId() != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postProcess(TrainingContext context) {
        DigitalExpertEntity expert = context.expert();
        int createdBindingCount = autoBindJavaAgentExpertIfNeeded(context, expert.getId());
        agentDeploymentService.refreshActiveAgentsByExpert(expert.getId());
        log.info("训练后置处理完成，已刷新专家关联的 AI Agent, taskId={}, expertId={}, autoBindingCreatedCount={}",
                context.trainingTask().getTaskId(),
                expert.getId(),
                createdBindingCount);
    }

    private int autoBindJavaAgentExpertIfNeeded(TrainingContext context, Long expertId) {
        if (!isJavaAgentReportedTraining(context)) {
            return 0;
        }

        List<AiAgentEntity> autoBindingAgents = aiAgentMapper.selectList(
                new LambdaQueryWrapper<AiAgentEntity>()
                        .eq(AiAgentEntity::getAutoBinding, 1)
                        .orderByAsc(AiAgentEntity::getId)
        );
        if (autoBindingAgents == null || autoBindingAgents.isEmpty()) {
            log.info("检测到 JavaAgent 上报训练，但未找到开启自动绑定的 AI Agent, taskId={}, expertId={}",
                    context.trainingTask().getTaskId(), expertId);
            return 0;
        }

        int createdCount = 0;
        for (AiAgentEntity agent : autoBindingAgents) {
            if (agent == null || agent.getId() == null) {
                continue;
            }
            AgentExpertBindingEntity existingBinding = agentExpertBindingMapper.selectOne(
                    new LambdaQueryWrapper<AgentExpertBindingEntity>()
                            .eq(AgentExpertBindingEntity::getAgentId, agent.getId())
                            .eq(AgentExpertBindingEntity::getExpertId, expertId)
                            .last("limit 1")
            );
            if (existingBinding != null) {
                continue;
            }

            AgentExpertBindingEntity entity = new AgentExpertBindingEntity();
            entity.setAgentId(agent.getId());
            entity.setExpertId(expertId);
            entity.setSortNo(resolveNextSortNo(agent.getId()));
            entity.setCreatedAt(LocalDateTime.now());
            agentExpertBindingMapper.insert(entity);
            createdCount++;
        }

        log.info("JavaAgent 上报训练自动绑定处理完成, taskId={}, expertId={}, autoAgentCount={}, createdBindingCount={}",
                context.trainingTask().getTaskId(),
                expertId,
                autoBindingAgents.size(),
                createdCount);
        return createdCount;
    }

    private int resolveNextSortNo(Long agentId) {
        return agentExpertBindingMapper.selectList(
                        new LambdaQueryWrapper<AgentExpertBindingEntity>()
                                .eq(AgentExpertBindingEntity::getAgentId, agentId)
                                .orderByAsc(AgentExpertBindingEntity::getSortNo, AgentExpertBindingEntity::getId)
                ).stream()
                .map(AgentExpertBindingEntity::getSortNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private boolean isJavaAgentReportedTraining(TrainingContext context) {
        if (context == null || context.trainingTask() == null) {
            return false;
        }
        String sourceManifestJson = context.trainingTask().getSourceManifestJson();
        if (!StringUtils.hasText(sourceManifestJson)) {
            return false;
        }
        List<TrainingSourceRequest> sources;
        try {
            sources = JSON.parseArray(sourceManifestJson, TrainingSourceRequest.class);
        } catch (Exception ex) {
            log.warn("解析训练来源失败，跳过 JavaAgent 自动绑定, taskId={}",
                    context.trainingTask().getTaskId(), ex);
            return false;
        }
        if (sources == null || sources.isEmpty()) {
            return false;
        }
        return sources.stream().anyMatch(this::isAppInfoSource);
    }

    private boolean isAppInfoSource(TrainingSourceRequest source) {
        if (source == null
                || !TrainingSourceType.LOCAL_PATH.name().equals(source.sourceType())
                || !StringUtils.hasText(source.sourceValue())) {
            return false;
        }
        try {
            Path directory = Path.of(source.sourceValue()).normalize();
            if (!Files.isDirectory(directory)) {
                return false;
            }
            Path appJsonPath = directory.resolve("app.json");
            Path jarsDirectory = directory.resolve("jars");
            boolean matchesDirectoryName = directory.getFileName() != null
                    && "app_info".equalsIgnoreCase(directory.getFileName().toString());
            return matchesDirectoryName
                    && Files.isRegularFile(appJsonPath)
                    && Files.isDirectory(jarsDirectory);
        } catch (Exception ex) {
            return false;
        }
    }
}
