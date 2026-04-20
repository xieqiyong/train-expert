package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record CreateManualExpertRequest(
        String name,
        String description,
        String prompt,
        String expertType,
        String iconUrl,
        List<McpBindingRequest> mcps,
        boolean autoRelease,
        List<Long> agentIds
) {
}
