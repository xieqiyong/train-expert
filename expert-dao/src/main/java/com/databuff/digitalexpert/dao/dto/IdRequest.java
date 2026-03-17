package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotNull;

public record IdRequest(
        @NotNull Long id
) {
}
