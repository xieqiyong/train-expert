package com.databuff.digitalexpert.facade.dto;

public record AgentOpencodeRefreshResponse(
        Long agentId,
        String agentName,
        String rootPath,
        String outputPath,
        String opencodeJsonContent,
        Integer mcpCount
) {
}
