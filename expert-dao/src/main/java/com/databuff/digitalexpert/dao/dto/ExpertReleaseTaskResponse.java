package com.databuff.digitalexpert.dao.dto;

import java.time.LocalDateTime;

public record ExpertReleaseTaskResponse(
        String taskId,
        Long expertId,
        String status,
        String configJsonPath,
        String zipPackagePath,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
