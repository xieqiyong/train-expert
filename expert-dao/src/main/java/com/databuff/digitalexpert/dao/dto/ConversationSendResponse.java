package com.databuff.digitalexpert.dao.dto;

public record ConversationSendResponse(
        String sessionId,
        String messageId,
        Boolean accepted
) {
}
