package com.databuff.digitalexpert.service.conversation;

public record ConversationSessionContext(
        String sessionId,
        String directory,
        String workspace
) {
}
