package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotNull;

public record AgentIdRequest(
        @NotNull Long agentId
) {
}
