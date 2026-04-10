package com.databuff.digitalexpert.dao.dto;

import java.time.LocalDateTime;

public record ConversationSessionSummaryResponse(
        String sessionId,
        String sessionTitle,
        String directory,
        String workspace,
        String providerId,
        String modelId,
        String agent,
        String status,
        String lastError,
        Integer messageCount,
        Integer workflowCount,
        LocalDateTime lastSyncedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
