package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record TrainingSourceRequest(
        @NotBlank String sourceType,
        @NotBlank String sourceValue,
        String sourceVersion
) {
    public TrainingSourceRequest(String sourceType, String sourceValue) {
        this(sourceType, sourceValue, null);
    }
}
