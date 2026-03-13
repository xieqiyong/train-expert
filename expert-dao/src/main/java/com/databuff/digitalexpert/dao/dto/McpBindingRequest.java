package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record McpBindingRequest(
        @NotBlank String bindingName,
        @NotBlank String mcpUrl,
        @NotEmpty List<@NotBlank String> toolWhitelist
) {
}

