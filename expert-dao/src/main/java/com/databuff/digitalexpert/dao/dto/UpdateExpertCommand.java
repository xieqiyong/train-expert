package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateExpertCommand(
        @NotNull Long expertId,
        @NotBlank String name,
        String aliasName,
        String description,
        String prompt,
        String expertType
) {
}
