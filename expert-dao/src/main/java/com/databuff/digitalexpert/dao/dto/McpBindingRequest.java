package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record McpBindingRequest(
        @NotBlank String bindingName,
        @NotBlank String mcpUrl,
        List<@NotBlank String> toolWhitelist
) {
}
