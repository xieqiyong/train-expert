package com.databuff.digitalexpert.dao.dto;

public record ConversationAbortResponse(
        String sessionId,
        Boolean aborted
) {
}
