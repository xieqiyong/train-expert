package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record UpdateAgentBindingsRequest(
        List<Long> skills,
        List<Long> experts
) {
}
