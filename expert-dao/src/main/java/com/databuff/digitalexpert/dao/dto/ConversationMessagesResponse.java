package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ConversationMessagesResponse(
        String sessionId,
        List<ConversationMessageResponse> messages
) {
}
