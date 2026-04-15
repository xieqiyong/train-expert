package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record CreateManualExpertRequest(
        String name,
        String description,
        String prompt,
        String expertType,
        List<McpBindingRequest> mcps,
        boolean autoRelease,
        List<Long> agentIds
) {
}
