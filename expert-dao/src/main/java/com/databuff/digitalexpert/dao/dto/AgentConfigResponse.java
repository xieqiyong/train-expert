package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record AgentConfigResponse(
        Long agentId,
        String name,
        String description,
        String path,
        String rootPath,
        String opencodeConfigJson,
        String status,
        List<AgentSkillBindingResponse> directSkills,
        List<AgentExpertBindingResponse> experts,
        List<AgentEffectiveSkillResponse> effectiveSkills
) {
}
