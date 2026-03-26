package com.databuff.digitalexpert.dao.dto;

import com.databuff.digitalexpert.dao.enums.ExpertStatusOperation;
import jakarta.validation.constraints.NotNull;

public record ChangeExpertStatusRequest(
        @NotNull Long expertId,
        @NotNull ExpertStatusOperation operation
) {
}
