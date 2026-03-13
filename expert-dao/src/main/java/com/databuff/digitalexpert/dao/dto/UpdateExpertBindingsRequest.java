package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record UpdateExpertBindingsRequest(
        List<Long> skills,
        List<Long> staticPackages,
        List<McpBindingRequest> mcps
) {
}
