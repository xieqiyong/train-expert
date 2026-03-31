package com.databuff.digitalexpert.dao.dto;

public record AgentSkillBindingResponse(
        Long skillId,
        String name,
        String description,
        String packageName,
        String packagePath
) {
}
