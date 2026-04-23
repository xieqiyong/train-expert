package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateExpertTrainingTaskRequest(
        @NotEmpty List<@Valid TrainingSourceRequest> sources,
        String trainingGoal,
        List<Long> attachmentIds
) {

    public CreateExpertTrainingTaskRequest(List<TrainingSourceRequest> sources, String trainingGoal) {
        this(sources, trainingGoal, List.of());
    }
}
