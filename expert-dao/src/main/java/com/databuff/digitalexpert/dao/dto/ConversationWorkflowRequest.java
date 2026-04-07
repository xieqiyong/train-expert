package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationWorkflowRequest(
        @NotBlank String sessionId,
        Integer limit
) {
}
