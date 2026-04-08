package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRequest(
        @NotBlank String name,
        String description,
        @NotBlank String path,
        String rootPath,
        String opencodeConfigJson
) {
}
