package com.databuff.digitalexpert.dao.dto;

import com.databuff.digitalexpert.dao.enums.ExpertStatusOperation;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ChangeExpertStatusRequest(
        @NotNull List<Long> expertIds,
        @NotNull ExpertStatusOperation operation
) {
}
