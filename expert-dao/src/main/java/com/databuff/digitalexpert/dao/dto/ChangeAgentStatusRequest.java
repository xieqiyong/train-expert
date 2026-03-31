package com.databuff.digitalexpert.dao.dto;

import com.databuff.digitalexpert.dao.enums.AgentStatusOperation;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ChangeAgentStatusRequest(
        @NotNull List<Long> agentIds,
        @NotNull AgentStatusOperation operation
) {
}
