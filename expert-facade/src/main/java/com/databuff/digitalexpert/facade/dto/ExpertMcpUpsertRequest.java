package com.databuff.digitalexpert.facade.dto;

import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ExpertMcpUpsertRequest(
        @NotNull Long expertId,
        @NotNull @Valid McpBindingRequest mcp
) {
}
