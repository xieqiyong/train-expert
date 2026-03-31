package com.databuff.digitalexpert.dao.dto;

public record AgentSummaryResponse(
        Long agentId,
        String name,
        String description,
        String path,
        String status
) {
}
