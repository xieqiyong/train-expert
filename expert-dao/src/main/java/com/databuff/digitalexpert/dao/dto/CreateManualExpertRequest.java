package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record CreateManualExpertRequest(
        String name,
        String description,
        String prompt,
        List<McpBindingRequest> mcps,
        boolean autoRelease
) {
}
