package com.databuff.digitalexpert.dao.dto;

public record ExpertSummaryResponse(
        Long id,
        String name,
        String description,
        String expertType,
        String status
) {
}
