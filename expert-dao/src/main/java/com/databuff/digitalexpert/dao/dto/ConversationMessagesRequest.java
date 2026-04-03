package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationMessagesRequest(
        @NotBlank String sessionId,
        Integer limit,
        String directory,
        String workspace
) {
}
