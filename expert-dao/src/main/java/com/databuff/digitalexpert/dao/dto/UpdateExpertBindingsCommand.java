package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateExpertBindingsCommand(
        @NotNull Long expertId,
        List<Long> skills,
        List<Long> staticPackages,
        List<@Valid McpBindingRequest> mcps
) {
    public UpdateExpertBindingsRequest toBindingsRequest() {
        return new UpdateExpertBindingsRequest(skills, staticPackages, mcps);
    }
}
