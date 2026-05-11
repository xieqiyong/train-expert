package com.databuff.digitalexpert.facade.dto;

import java.util.List;

public record ExpertMcpUpsertResponse(
        Long expertId,
        String expertName,
        String expertStatus,
        String bindingName,
        Boolean created,
        Integer affectedAgentCount,
        List<AgentOpencodeRefreshResponse> affectedAgents
) {
}
