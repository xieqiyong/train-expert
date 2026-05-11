package com.databuff.digitalexpert.facade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AgentMcpDeployRequest(
        @NotEmpty List<@NotNull Long> agentIds,
        @NotEmpty List<@Valid McpConfig> mcps
) {

    public record McpConfig(
            @NotBlank String bindingName,
            @NotBlank String mcpUrl,
            Map<String, String> headers,
            Integer timeout,
            List<@NotBlank String> toolWhitelist
    ) {
    }
}
