package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SubmitExpertTrainingTaskRequest(
        @NotNull Long expertId,
        @NotEmpty List<@Valid TrainingSourceRequest> sources,
        String trainingGoal,
        List<Long> attachmentIds
) {
    public CreateExpertTrainingTaskRequest toTrainingTaskRequest() {
        return new CreateExpertTrainingTaskRequest(sources, trainingGoal, attachmentIds);
    }
}
