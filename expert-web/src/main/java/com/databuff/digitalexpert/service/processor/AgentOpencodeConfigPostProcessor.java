package com.databuff.digitalexpert.service.processor;

import com.databuff.digitalexpert.dao.bo.TrainingContext;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.service.AgentRuntimeConfigService;
import com.databuff.digitalexpert.service.TrainingPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(0)
@Component
public class AgentOpencodeConfigPostProcessor implements TrainingPostProcessor {

    @Autowired
    private AgentRuntimeConfigService agentRuntimeConfigService;

    @Override
    public boolean supports(TrainingContext context) {
        return context != null
                && context.trainingTask() != null
                && context.status() == TrainingTaskStatus.SUCCEEDED
                && context.expert() != null
                && context.expert().getId() != null;
    }

    @Override
    public void postProcess(TrainingContext context) {
        Long expertId = context.expert().getId();
        // OpenCode 运行时配置与技能部署解耦，训练成功后单独刷新关联 Agent 的 opencode.json。
        agentRuntimeConfigService.refreshAgentsByExpert(expertId);
        log.info("训练后置处理完成，已刷新专家关联的 AI Agent OpenCode 配置, taskId={}, expertId={}",
                context.trainingTask().getTaskId(), expertId);
    }
}
