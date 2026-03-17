package com.databuff.digitalexpert.dao.dto;

import java.time.LocalDateTime;

public record ExpertTrainingTaskResponse(
        String taskId,
        Long expertId,
        String status,
        String sessionId,
        String outputDir,
        String releaseTaskId,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
