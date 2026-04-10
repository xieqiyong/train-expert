package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ConversationSessionsResponse(
        List<ConversationSessionSummaryResponse> sessions
) {
}
