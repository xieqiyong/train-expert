package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationSessionRequest(
        @NotBlank String sessionId,
        String directory,
        String workspace
) {
}
