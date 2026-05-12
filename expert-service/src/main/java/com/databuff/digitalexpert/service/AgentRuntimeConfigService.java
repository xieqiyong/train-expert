package com.databuff.digitalexpert.service;

public interface AgentRuntimeConfigService {

    void refreshAgentConfig(Long agentId);

    void refreshAgentsByExpert(Long expertId);
}
