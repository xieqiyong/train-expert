package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAgentBindingsCommand(
        @NotNull Long agentId,
        List<Long> skills,
        List<Long> experts
) {
    public UpdateAgentBindingsRequest toBindingsRequest() {
        return new UpdateAgentBindingsRequest(skills, experts);
    }
}
