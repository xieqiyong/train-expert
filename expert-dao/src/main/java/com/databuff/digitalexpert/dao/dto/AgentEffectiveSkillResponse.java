package com.databuff.digitalexpert.dao.dto;

public record AgentEffectiveSkillResponse(
        Long skillId,
        String name,
        String description,
        String packageName,
        String packagePath,
        String outputDirectoryName,
        String sourceType,
        Long sourceId,
        String sourceName
) {
}
