package com.databuff.digitalexpert.dao.dto;

public record SubmitForwardTrainingRequest(
        String name,
        String description,
        String prompt,
        String expertType,
        String sourceType,
        String sourceValue,
        String sourceVersion,
        String trainingGoal
) {
}

