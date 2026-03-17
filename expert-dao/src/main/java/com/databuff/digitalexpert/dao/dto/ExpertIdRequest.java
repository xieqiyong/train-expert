package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotNull;

public record ExpertIdRequest(
        @NotNull Long expertId
) {
}
