package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SubmitForwardTrainingRequest(
        @NotBlank String name,
        String description,
        String prompt,
        String expertType,
        @NotBlank String sourceType,
        String sourceValue,
        String sourceVersion,
        String trainingGoal,
        List<Long> attachmentIds
) {
}
