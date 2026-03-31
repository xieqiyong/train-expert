package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AgentBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.AgentConfigResponse;
import com.databuff.digitalexpert.dao.dto.AgentSummaryResponse;
import com.databuff.digitalexpert.dao.dto.CreateAgentRequest;
import com.databuff.digitalexpert.dao.dto.UpdateAgentBindingsRequest;
import com.databuff.digitalexpert.dao.entity.AiAgentEntity;
import com.databuff.digitalexpert.dao.enums.AgentStatus;
import com.databuff.digitalexpert.dao.enums.AgentStatusOperation;
import java.util.List;

public interface AiAgentService {

    AgentSummaryResponse createAgent(CreateAgentRequest request);

    List<AgentSummaryResponse> listAgentsByNames(List<String> names, AgentStatus status);

    AgentConfigResponse getConfig(Long agentId);

    AgentBindingUpdateResponse updateBindings(Long agentId, UpdateAgentBindingsRequest request);

    List<AgentSummaryResponse> changeStatus(List<Long> agentIds, AgentStatusOperation operation);

    AiAgentEntity requireAgent(Long agentId);
}
