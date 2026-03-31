package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AgentEffectiveSkillResponse;
import java.util.List;

public interface AgentDeploymentService {

    List<AgentEffectiveSkillResponse> listEffectiveSkills(Long agentId);

    void refreshAgent(Long agentId);

    void refreshActiveAgentsByExpert(Long expertId);
}
