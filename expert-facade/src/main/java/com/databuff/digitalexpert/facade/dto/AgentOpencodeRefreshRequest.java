package com.databuff.digitalexpert.facade.dto;

import jakarta.validation.constraints.NotNull;

public record AgentOpencodeRefreshRequest(
        @NotNull Long agentId
) {
}
