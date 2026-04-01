package com.databuff.digitalexpert.dao.dto;

public record ForwardTrainingSubmitResponse(
        ExpertSummaryResponse expert,
        boolean createdExpert,
        ExpertTrainingTaskResponse trainingTask,
        String sourceType,
        String sourceVersion
) {
}
