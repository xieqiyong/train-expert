package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAgentExpertsCommand(
        @NotNull Long agentId,
        List<Long> expertIds
) {
}
