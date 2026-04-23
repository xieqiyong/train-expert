package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record TrainingSourceRequest(
        @NotBlank String sourceType,
        @NotBlank String sourceValue,
        String sourceVersion,
        String sourceRole,
        Long attachmentId,
        String sourceName,
        String usagePrompt,
        String accessUrl
) {

    public TrainingSourceRequest(String sourceType, String sourceValue, String sourceVersion) {
        this(sourceType, sourceValue, sourceVersion, null, null, null, null, null);
    }
}
