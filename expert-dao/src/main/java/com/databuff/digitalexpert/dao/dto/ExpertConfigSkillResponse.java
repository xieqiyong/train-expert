package com.databuff.digitalexpert.dao.dto;

public record ExpertConfigSkillResponse(
        Long skillId,
        String name,
        String description,
        String packageName,
        String packagePath
) {
}
