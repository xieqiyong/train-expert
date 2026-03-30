package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record AgentBindingGroupResponse(
        String agentName,
        String agentPath,
        List<AgentBindingExpertResponse> experts
) {
}
