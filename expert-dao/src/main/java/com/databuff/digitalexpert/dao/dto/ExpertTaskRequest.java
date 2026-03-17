package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExpertTaskRequest(
        @NotNull Long expertId,
        @NotBlank String taskId
) {
}
