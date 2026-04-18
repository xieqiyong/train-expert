package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateManualExpertRequest(
        @NotNull Long expertId,
        @NotBlank String name,
        String description,
        String prompt,
        String expertType,
        List<@Valid McpBindingRequest> mcps
) {
}
