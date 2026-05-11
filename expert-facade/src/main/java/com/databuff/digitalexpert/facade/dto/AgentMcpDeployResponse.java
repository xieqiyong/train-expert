package com.databuff.digitalexpert.facade.dto;

import java.util.List;

public record AgentMcpDeployResponse(
        Integer agentCount,
        Integer mcpCount,
        List<AgentOpencodeRefreshResponse> agents
) {
}
