package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ConversationWorkflowResponse(
        String sessionId,
        List<ConversationWorkflowEventResponse> events
) {
}
