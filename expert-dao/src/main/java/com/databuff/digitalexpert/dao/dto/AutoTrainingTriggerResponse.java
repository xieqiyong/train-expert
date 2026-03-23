package com.databuff.digitalexpert.dao.dto;

public record AutoTrainingTriggerResponse(
        Long expertId,
        String expertName,
        boolean createdExpert,
        String taskId,
        String taskStatus
) {
}
