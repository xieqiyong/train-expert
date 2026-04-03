package com.databuff.digitalexpert.dao.dto;

public record ConversationFinishedResponse(
        String sessionId,
        Boolean finished
) {
}
