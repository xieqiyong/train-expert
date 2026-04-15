package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BindExpertAgentsCommand(
        @NotNull Long expertId,
        @NotEmpty List<@NotNull Long> agentIds
) {
}
