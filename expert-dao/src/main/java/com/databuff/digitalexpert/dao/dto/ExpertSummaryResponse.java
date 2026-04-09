package com.databuff.digitalexpert.dao.dto;

public record ExpertSummaryResponse(
        Long id,
        String name,
        String aliasName,
        String description,
        String expertType,
        String expertSource,
        String status,
        Boolean trainingInProgress
) {
}
