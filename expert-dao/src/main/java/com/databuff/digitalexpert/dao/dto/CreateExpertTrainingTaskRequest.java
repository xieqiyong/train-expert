package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateExpertTrainingTaskRequest(
        @NotEmpty List<@Valid TrainingSourceRequest> sources,
        String trainingGoal
) {
}
