package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateExpertRequest(
        @NotBlank String name,
        String description,
        String prompt
) {
}
