package com.databuff.digitalexpert.dao.dto;

public record ConversationWorkflowEventResponse(
        String eventType,
        String workflowKind,
        String role,
        String messageId,
        String partId,
        String partType,
        String toolName,
        String toolCallId,
        String toolStatus,
        String content,
        Object detail,
        Integer sortNo
) {
}
