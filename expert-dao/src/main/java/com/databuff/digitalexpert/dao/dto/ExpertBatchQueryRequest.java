package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ExpertBatchQueryRequest(
        @NotEmpty List<@NotBlank String> names
) {
}
